package app.kasa.core.util

import android.graphics.Bitmap
import android.graphics.Color
import app.kasa.data.model.VaultItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Wi-Fi kaydından karekod.
 *
 * ### Neden
 *
 * Misafire Wi-Fi parolası vermek, uygulamanın çözebileceği en sık günlük
 * sorunlardan biri ve şimdiye kadarki cevabı "parolayı oku, karşıdaki yazsın"
 * idi. Yirmi karakterlik rastgele bir parola sesle aktarılırken yanlış
 * yazılıyor ve kullanıcı sonunda parolayı kolay bir şeyle değiştiriyor —
 * yani kasa, koruduğu şeyi zayıflatmaya yol açıyordu.
 *
 * Karekod Android ve iOS'ta kamerayla okunduğunda doğrudan ağa bağlanma
 * öneriyor; parola hiç söylenmiyor, hiç yazılmıyor.
 *
 * ### Biçim
 *
 * `WIFI:T:WPA;S:ağ adı;P:parola;H:true;;` — Android ve iOS'un tanıdığı fiilî
 * standart. Kaçış kuralları biçimin kendisinden geliyor: `\`, `;`, `,`, `:`
 * ve `"` karakterleri ters eğik çizgiyle kaçırılmak zorunda, yoksa parolada
 * geçen tek bir noktalı virgül karekodu ayrıştırılamaz hâle getiriyor ve bunu
 * ancak karşıdaki kişi bağlanamayınca fark ediyorsun.
 *
 * ### Karekod cihazdan çıkmıyor
 *
 * Bitmap yalnızca bellekte üretiliyor, dosyaya yazılmıyor ve paylaşılmıyor.
 * Ekranda gösterilen bir karekod, ekran görüntüsü engeli (`FLAG_SECURE`)
 * altında; diske yazılan bir görüntü ise o korumanın tamamen dışında kalırdı.
 */
object WifiQr {

    /** Kayıt karekoda çevrilebilir mi: ağ adı olmadan anlamı yok. */
    fun isEncodable(item: VaultItem): Boolean = ssidOf(item).isNotBlank()

    fun ssidOf(item: VaultItem): String = item.extras["ssid"].orEmpty().trim()

    /**
     * `WIFI:` yükünü kurar.
     *
     * Güvenlik türü kayıtta yazılıysa kullanılıyor; yazılı değilse parola
     * varlığına bakılıyor. "nopass" yalnızca gerçekten parolasız ağda doğru;
     * parolalı bir ağı nopass olarak kodlamak, karşıdakinin bağlanamamasına
     * yol açar.
     */
    fun payload(item: VaultItem): String {
        val ssid = ssidOf(item)
        val password = item.extras["wifi_password"].orEmpty()
        val hidden = item.extras["hidden"].orEmpty().trim().lowercase()
        val declared = item.extras["security"].orEmpty().trim().uppercase()

        val type = when {
            declared.contains("WPA") -> "WPA"
            declared.contains("WEP") -> "WEP"
            declared.contains("NOPASS") || declared.contains("AÇIK") || declared.contains("OPEN") -> "nopass"
            password.isNotBlank() -> "WPA"
            else -> "nopass"
        }

        return buildString {
            append("WIFI:T:").append(type)
            append(";S:").append(escape(ssid))
            if (type != "nopass") append(";P:").append(escape(password))
            if (hidden == "true" || hidden == "evet" || hidden == "yes") append(";H:true")
            append(";;")
        }
    }

    /**
     * Karekodu tek renkli bir bitmap olarak çizer.
     *
     * Hata düzeltme düzeyi bilerek düşük tutulmuyor: karekod telefonun
     * ekranından, çoğu zaman eğik açıyla ve parmak izli bir camdan okunuyor.
     * `M` düzeyi %15 kayba dayanıyor ve modül sayısını gereksiz büyütmüyor.
     *
     * @return kodlama başarısızsa `null` (ağ adı çok uzun olabilir)
     */
    fun bitmap(payload: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }.getOrNull()

    /**
     * Biçimin ayrılmış karakterlerini kaçırır.
     *
     * Kaçırılmayan tek bir noktalı virgül karekodu ayrıştırılamaz hâle
     * getiriyor ve bu, ancak karşıdaki kişi bağlanamayınca anlaşılıyor.
     */
    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') append('\\')
            append(ch)
        }
    }
}
