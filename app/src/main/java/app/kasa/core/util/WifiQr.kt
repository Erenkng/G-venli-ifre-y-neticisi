package app.kasa.core.util

import android.graphics.Bitmap
import app.kasa.data.model.VaultItem

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
     * Ağ yükünü karekod olarak çizer. Ölçüler [QrCodes] içinde.
     *
     * @return kodlama başarısızsa `null` (ağ adı çok uzun olabilir)
     */
    fun bitmap(payload: String, sizePx: Int): Bitmap? = QrCodes.bitmap(payload, sizePx)

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
