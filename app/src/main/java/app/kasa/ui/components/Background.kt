package app.kasa.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import app.kasa.ui.theme.KasaTheme
import app.kasa.ui.theme.LocalSurfaceEffects
import kotlin.random.Random

/**
 * Tasarımın imzası olan "gradyan beyaz" zemin.
 *
 * Üç radyal duraktan (sol üstte jade, sağ üstte kehribar, altta mavi) ve
 * üstlerine binen ince bir tane dokusundan (grain) oluşuyor. Tane, düz
 * gradyanlarda oluşan bant etkisini kırıyor ve yüzeye kâğıt hissi veriyor.
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
 * olduğu için araya giren kap düzeni bozmadan eklenemiyordu ve sonuç,
 * düzenleyicinin düz `surface` ile boyanması olmuştu — uygulamanın tek
 * gradyansız ekranı.
 *
 * Bu, bir kusuru da örtüyordu: 1.8'de metin girişleri cam yüzeye geçti ama
 * düzenleyicide camın arkasında gösterecek bir şey yoktu, çünkü zemin düz bir
 * renkti. Cam orada hiçbir şey yapmıyordu.
 */
@Composable
fun Modifier.kasaBackdrop(): Modifier {
    val colors = KasaTheme.colors
    val grain = rememberGrainBrush()

    // Zemin eğimle kayıyor: ışık kaynakları cihaz döndükçe yerinde duruyor
    // gibi, yüzey onların altından geçiyor gibi oluyor. Gerçek bir camın
    // arkasındaki ışığın yaptığı da bu.
    //
    // Eğim **durum nesnesi** olarak alınıyor ve yalnızca çizim sırasında
    // okunuyor. Değeri beste aşamasında okumak, her sensör örneğinde
    // uygulamanın tamamını yeniden bestelemek olurdu — bu zemin bütün
    // ekranları sarıyor.
    val tilt = rememberDeviceTiltState()
    val parallax = LocalSurfaceEffects.current.parallax

    return drawBehind {
        val shiftX = if (parallax) tilt.value.x * size.width * PARALLAX_REACH else 0f
        val shiftY = if (parallax) tilt.value.y * size.height * PARALLAX_REACH else 0f
        // Taban: yukarıdan aşağı hafif koyulaşan düz zemin
        drawRect(
            Brush.verticalGradient(
                0f to colors.gradientBase,
                0.4f to lerpToward(colors.gradientBase, colors.gradientBottom, 0.12f),
                1f to lerpToward(colors.gradientBase, colors.gradientBottom, 0.3f)
            )
        )
        // Sol üst: jade
        drawRect(
            Brush.radialGradient(
                colors = listOf(colors.gradientTopLeft, colors.gradientTopLeft.copy(alpha = 0f)),
                center = Offset(size.width * 0.06f + shiftX, -size.height * 0.08f + shiftY),
                radius = size.width * 1.2f
            )
        )
        // Sağ üst: kehribar
        drawRect(
            Brush.radialGradient(
                colors = listOf(colors.gradientTopRight, colors.gradientTopRight.copy(alpha = 0f)),
                center = Offset(size.width * 1.04f - shiftX, size.height * 0.02f + shiftY),
                radius = size.width * 0.85f
            )
        )
        // Alt: soğuk mavi
        drawRect(
            Brush.radialGradient(
                colors = listOf(colors.gradientBottom, colors.gradientBottom.copy(alpha = 0f)),
                center = Offset(size.width * 0.5f + shiftX, size.height * 1.08f - shiftY),
                radius = size.width * 1.1f
            )
        )
        // ── tane ──────────────────────────────────────────────────────
        //
        // Karışım kipi **Overlay**, düz alfa değil.
        //
        // Doku ortalaması orta gri olan bir gürültü. Düz alfayla çizilince
        // sonuç `zemin*(1-a) + gri*a` oluyor: yani saydamlık arttıkça
        // gradyan doygunluğunu kaybedip griye yıkanıyor. Görülen şey tane
        // değil, üstüne toz serpilmiş bir zemin oluyordu.
        //
        // Overlay orta griyi **kimliksiz** kabul ediyor: tam 128 olan piksel
        // zemini hiç değiştirmiyor, ondan sapan piksel zemini kendi yönünde
        // açıp koyultuyor. Ortalama korunuyor, yani renk kaymıyor ve geriye
        // yalnızca dokunun kendisi kalıyor — filmdeki tanenin yaptığı iş de
        // tam olarak bu.
        drawRect(
            brush = grain,
            size = Size(size.width, size.height),
            alpha = colors.grainAlpha,
            blendMode = BlendMode.Overlay
        )
    }
}

private fun lerpToward(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = 1f
)

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

/**
 * Işık kaynaklarının eğimle kat ettiği yol, ekranın oranı olarak.
 *
 * Küçük: gradyanın durakları zaten yumuşak ve büyük bir kayma, zemini
 * cihazla birlikte sallanan ayrı bir nesne gibi gösteriyor. Bu kadarı ise
 * yalnızca "ışık burada" duygusunu yerinden oynatıyor.
 */
private const val PARALLAX_REACH = 0.05f
