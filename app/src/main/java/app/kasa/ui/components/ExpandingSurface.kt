package app.kasa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Bir öğenin yerinden büyüyerek ekranı kaplaması.
 *
 * ### Neden ayrı bir yüzey, doğrudan içeriği büyütmek yerine
 *
 * Akla ilk gelen çözüm, açılan ekranı küçük başlatıp büyütmek. İki şekilde de
 * bozuluyor:
 *
 *  - **Ölçüyü büyütmek** (`size()` ile) her karede yeniden yerleşim demek.
 *    Metin her karede yeniden sarılıyor, liste her karede yeniden ölçülüyor;
 *    120 Hz'de bu kare bütçesinin tamamı.
 *  - **Ölçeklemek** (`scaleX/scaleY` ile) yerleşimi kurtarıyor ama en-boy
 *    oranı değiştiği için yazıyı eziyor: geçişin ortasında bütün harfler yatay
 *    olarak geriliyor ve göz bunu hemen yakalıyor.
 *
 * Burada büyüyen şey içerik değil, **altındaki yüzey**. Yalnızca bir yuvarlak
 * dikdörtgen çiziliyor; hiçbir yerleşim yapılmıyor, hiçbir metin ölçeklenmiyor.
 * İçerik tam boyunda yerleşiyor ve yüzey yeterince açıldığında üstüne
 * beliriyor. Gözün gördüğü hareket "kutu büyüdü ve içi doldu" — kullanıcının
 * dokunduğu öğe ile açılan ekran arasındaki bağ tam olarak bu.
 *
 * ### Köşe yarıçapı da geçiyor
 *
 * Arama çubuğu tam yuvarlak, ekran köşesiz. Yarıçapı sabit tutmak, büyüyen
 * kutuyu geçişin sonunda ekrana oturmayan bir hap gibi bırakıyordu.
 *
 * @param progress 0 = kaynağın yerinde ve boyunda, 1 = ekranın tamamı
 * @param origin kaynağın kök koordinatlarındaki dikdörtgeni (piksel);
 *        `null` ise büyüme yapılamıyor ve yüzey doğrudan tam ekran çiziliyor —
 *        kaynak henüz ölçülmemişse ya da ekranda değilse olan budur
 * @param originCorner kaynağın köşe yarıçapı (piksel)
 */
@Composable
fun ExpandingSurface(
    progress: Float,
    origin: Rect?,
    color: Color,
    originCornerPx: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        val full = Rect(0f, 0f, size.width, size.height)
        val from = origin ?: full
        val t = progress.coerceIn(0f, 1f)

        val left = from.left + (full.left - from.left) * t
        val top = from.top + (full.top - from.top) * t
        val right = from.right + (full.right - from.right) * t
        val bottom = from.bottom + (full.bottom - from.bottom) * t
        val corner = originCornerPx * (1f - t)

        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(corner)
        )
    }
}

/**
 * İçeriğin belirme oranı.
 *
 * Yüzey açılmaya başlar başlamaz metni göstermek, henüz dar olan bir kutuya
 * tam genişlikte yazı bindirmek demek: yazı kutunun dışına taşıyor. İçerik
 * kutunun büyük kısmı açıldıktan sonra geliyor ve geçişin son parçasında tam
 * görünür oluyor.
 *
 * Kapanışta ise tersi geçerli değil — içerik **hemen** siliniyor. Kapanan bir
 * kutunun içinde yazının küçülürken taşması, açılıştakinden daha rahatsız
 * edici: göz o an zaten geri dönmeye hazırlanıyor.
 */
fun contentRevealFraction(progress: Float, opening: Boolean): Float {
    val start = if (opening) CONTENT_START_OPENING else CONTENT_START_CLOSING
    return ((progress - start) / (1f - start)).coerceIn(0f, 1f)
}

private const val CONTENT_START_OPENING = 0.38f
private const val CONTENT_START_CLOSING = 0.62f
