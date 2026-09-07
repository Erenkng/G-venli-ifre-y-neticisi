package app.kasa.core.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Karekod çizimi.
 *
 * ### Neden ayrı bir dosya
 *
 * Kodlayıcı önce [WifiQr] içinde duruyordu, çünkü ilk kullanan oydu. İkinci
 * kullanıcı çıkınca — kurtarma anahtarını başka bir cihaza okutmak — kalması
 * yanlış olurdu: kurtarma anahtarını çizdirmek için `WifiQr` çağırmak, okuyan
 * kişiye kasa anahtarının Wi-Fi ile bir ilgisi olduğunu düşündürür.
 *
 * ### Ekrandan okunacak karekodun ölçüleri
 *
 * Hata düzeltme düzeyi `M`: karekod telefon ekranından, çoğu zaman eğik açıyla
 * ve parmak izli bir camdan okunuyor. `M` %15 kayba dayanıyor ve modül sayısını
 * gereksiz büyütmüyor — daha yükseği modülleri küçültüp okumayı zorlaştırırdı.
 */
object QrCodes {

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
}
