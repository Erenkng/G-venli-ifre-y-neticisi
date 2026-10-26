package app.kasa.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import app.kasa.ui.theme.KasaTheme
import kotlin.random.Random

/**
 * Uygulamanın zemini: tek bir düz renk ve üstünde algı eşiğinde bir tane.
 *
 * ### Buradan ne kaldırıldı ve neden
 *
 * Bu dosya üç radyal ışık kaynağı çiziyordu — sol üstte jade, sağ üstte
 * kehribar, altta mavi — ve bunlar cihaz eğildikçe kayıyordu. Teknik olarak
 * sorunsuzdu. Sorun tarihsel: renkli ışıkların sızdığı yarı saydam yüzeyler
 * 2021–2023 arasının imzasıydı ve bugün bakıldığında tarih veriyor. Bugünün
 * Android'i ters kurulu: zemin susuyor, renk bileşenin üstünde duruyor —
 * seçili sekmede, rozette, güç göstergesinde. Gradyanı kaldırmak o renkleri
 * görünür kılıyor; eskiden hepsi zeminin kendi rengiyle yarışıyordu.
 *
 * Eğim (parallax) ile birlikte gitti, çünkü eğimin oynattığı şey ışık
 * kaynaklarının merkeziydi. Düz bir rengin kaydırılacak bir merkezi yok.
 * Eğim okuması, dokunulan yüzeylerdeki parlama için duruyor ([KasaTile]).
 *
 * ### Tane neden kaldı
 *
 * Tanenin eski işi gradyanın bant oluşturmasını (banding) kırmaktı; o iş
 * gradyanla birlikte bitti. Yeni işi daha önemli: düz bir dolgu, üstünde
 * hiçbir şey olmayan köşelerde basılmış bir renk kartelasına benziyor. Birkaç
 * ton sapan bir doku ona yüzey hissi veriyor ve bu, "düz" ile "boş" arasındaki
 * farkın tamamı.
 *
 * Doku 96x96'lık tek bir gri gürültü bitmap'i olarak bir kez üretilip
 * tekrarlanan bir shader ile döşeniyor; her karede yeniden çizilen bir gürültü
 * hem pahalı olurdu hem de titrerdi.
 */
@Composable
fun KasaBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize().kasaBackdrop(), content = content)
}

/**
 * Aynı zemin, kendi kabını kurmadan.
 *
 * Tam ekran katmanların (düzenleyici, çöp kutusu) altındaki ekranı örtmesi
 * gerekiyor, yani her biri kendi zeminini çizmek zorunda. [KasaBackground]
 * bunun için bir `Box` daha kuruyordu; o katmanların kökü zaten bir `Column`
 * olduğu için araya giren kap düzeni bozmadan eklenemiyordu.
 */
@Composable
fun Modifier.kasaBackdrop(): Modifier {
    val canvas = KasaTheme.colors.canvas
    val alpha = KasaTheme.colors.grainAlpha
    val grain = rememberGrainBrush()

    return drawBehind {
        drawRect(canvas)
        if (alpha <= 0.001f) return@drawBehind
        // ── tane ──────────────────────────────────────────────────────
        //
        // Karışım kipi **Overlay**, düz alfa değil.
        //
        // Doku ortalaması orta gri olan bir gürültü. Düz alfayla çizilince
        // sonuç `zemin*(1-a) + gri*a` oluyor: yani saydamlık arttıkça zemin
        // doygunluğunu kaybedip griye yıkanıyor. Görülen şey tane değil,
        // üstüne toz serpilmiş bir zemin oluyordu.
        //
        // Overlay orta griyi **kimliksiz** kabul ediyor: tam 128 olan piksel
        // zemini hiç değiştirmiyor, ondan sapan piksel zemini kendi yönünde
        // açıp koyultuyor. Ortalama korunuyor, yani renk kaymıyor ve geriye
        // yalnızca dokunun kendisi kalıyor — filmdeki tanenin yaptığı iş de
        // tam olarak bu.
        drawRect(brush = grain, alpha = alpha, blendMode = BlendMode.Overlay)
    }
}

@Composable
private fun rememberGrainBrush(): ShaderBrush {
    val image = remember { generateGrain(96) }
    return remember(image) {
        ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
    }
}

/** Deterministik olmayan ama kare başına sabit gri gürültü. */
private fun generateGrain(size: Int): ImageBitmap {
    val random = Random(20260819)
    val pixels = IntArray(size * size) {
        // Salınım dar: Overlay altında geniş bir salınım zemini alacalı
        // yapıyor ve tane değil kir gibi duruyor. Filmdeki tane de zaten
        // zeminden birkaç ton sapıyor, siyah-beyaz arasında gidip gelmiyor.
        val value = 128 + random.nextInt(-GRAIN_SWING, GRAIN_SWING + 1)
        val clamped = value.coerceIn(0, 255)
        (0xFF shl 24) or (clamped shl 16) or (clamped shl 8) or clamped
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

/** Tanenin orta griden sapma payı. */
private const val GRAIN_SWING = 40
