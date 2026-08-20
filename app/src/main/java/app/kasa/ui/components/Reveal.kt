package app.kasa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * İçeriğin bulanıklıktan çözülerek belirmesi.
 *
 * ### Neden gecikmeli
 *
 * Kilit ekranı açıldığı anda parola alanını göstermek, kullanıcıya ilk kareden
 * itibaren "yaz" diyor. Oysa o an biyometri istemi de açılıyor ve tipik
 * kullanımda hiçbir şey yazılmayacak. Alanın işaretten sonra gelmesi sırayı
 * doğru kuruyor: önce uygulamanın kim olduğu, sonra ondan ne istendiği.
 *
 * ### Neden bulanıklık
 *
 * Saydamlıktan belirmek düz bir açılma; bulanıklıktan çözülmek ise bir şeyin
 * **odağa girmesi**. İkincisi gelen şeyin okunacak bir metin olduğunu söylüyor
 * ve göz kendiliğinden oraya gidiyor. Yarıçap sıfıra indiği anda bulanıklık
 * katmanı da bırakılıyor; sürekli açık bir blur, altındaki her kareyi yeniden
 * çizmek demek olurdu.
 *
 * ### Noktalar
 *
 * Geçişin ortasında kısa bir dağılma görünüyor: içeriğin geleceği yerde küçük
 * noktalar dışa doğru açılıp sönüyor. Yalnızca geçiş sırasında var, sonrasında
 * hiçbir şey çizilmiyor — kalıcı bir süs değil, bir hareketin izi.
 *
 * Hareket kapalıyken ([LocalReducedMotion]) ne bulanıklık ne noktalar var;
 * içerik doğrudan görünüyor.
 *
 * @param visible içerik gösterilsin mi
 * @param delayMillis görünür olduktan sonra beklenecek süre
 */
@Composable
fun KasaReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    blurRadius: Dp = 16.dp,
    lift: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val reduced = LocalReducedMotion.current
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) {
            started = false
            return@LaunchedEffect
        }
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        started = true
    }

    val progress by animateFloatAsState(
        targetValue = if (started || reduced) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else REVEAL_MILLIS),
        label = "reveal"
    )

    val remaining = 1f - progress
    val radius = blurRadius * remaining

    Box(
        modifier = modifier
            .alpha(progress)
            .offset { IntOffset(0, (lift.value * remaining).roundToInt()) }
            // Yarıçap sıfırlandığında blur katmanı hiç kurulmuyor: sürekli açık
            // bir bulanıklık, altındaki her kareyi yeniden çizmek demek.
            .then(if (radius > 0.5.dp) Modifier.blur(radius) else Modifier)
    ) {
        content()
        if (!reduced && progress > 0.02f && progress < 0.98f) {
            RevealDust(progress = progress, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Geçişin ortasında dışa açılan noktalar.
 *
 * Konumlar sabit bir tohumla üretiliyor: her açılışta aynı desen çıkıyor.
 * Rastgele bir desen her seferinde farklı görüneceği için "bir şey ters gitti"
 * hissi veriyordu; aynı hareketin tekrarı ise uygulamanın kendi imzası oluyor.
 */
@Composable
private fun RevealDust(progress: Float, modifier: Modifier = Modifier) {
    val dots = remember {
        val random = Random(DUST_SEED)
        List(DUST_COUNT) {
            Triple(
                random.nextFloat(),                       // yatay konum (0-1)
                random.nextFloat(),                       // dikey konum (0-1)
                0.6f + random.nextFloat() * 0.8f          // yarıçap çarpanı
            )
        }
    }
    val shimmer = rememberInfiniteTransition(label = "dust")
    val phase by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dustPhase"
    )

    // Görünürlük geçişin ortasında tepe yapıyor: başta ve sonda hiç yok.
    val visibility = sin(progress * PI).toFloat()

    Canvas(modifier) {
        val spread = 1f + 0.35f * progress
        dots.forEachIndexed { index, (fx, fy, scale) ->
            val angle = phase + index
            val drift = 6f * progress
            val x = (fx - 0.5f) * size.width * spread + size.width / 2f + cos(angle) * drift
            val y = (fy - 0.5f) * size.height * spread + size.height / 2f + sin(angle) * drift
            drawCircle(
                color = Color.White.copy(alpha = 0.16f * visibility * scale),
                radius = DUST_RADIUS_PX * scale,
                center = Offset(x, y)
            )
        }
    }
}

private const val REVEAL_MILLIS = 620
private const val DUST_COUNT = 26
private const val DUST_SEED = 20260820
private const val DUST_RADIUS_PX = 2.2f

/**
 * Gizli bir değer açılırken noktaların harfe dönüşmesi.
 *
 * ### Sorun
 *
 * Göz tuşuna basınca `••••••••` bir karede `Tr0ub4dor` oluyordu. Tek karelik
 * bu takas iki şeyi birden bozuyor: göz değişimi yakalayamadığı için yeni
 * metni baştan okumak zorunda kalıyor, ve alanın genişliği aynı anda
 * değiştiği için satır zıplıyor. Sonuç, kullanıcının "ne oldu" diye ikinci
 * kez bakması.
 *
 * ### Çözüm
 *
 * Değişim odak üzerinden yapılıyor: metin önce bulanıklaşıyor, bulanıklığın
 * **en yoğun** olduğu anda maske kalkıyor, sonra tekrar netleşiyor. Takasın
 * gerçekleştiği kare, hiçbir şeyin okunabilir olmadığı kare. Göz bir metnin
 * yerini başkasının aldığını değil, aynı metnin odağa girdiğini görüyor.
 *
 * Bulanıklık eğrisi bir sinüs yarım dalgası: iki uçta tam sıfır. Yarıçap
 * sıfırda bulanıklık katmanı hiç kurulmuyor, yani alan durağan hâlde
 * fazladan hiçbir çizim maliyeti taşımıyor.
 *
 * ### Hareket kapalıyken
 *
 * Bulanıklık da geçiş de yok, takas anında yapılıyor: [LocalReducedMotion]
 * açıkken kullanıcı zaten hareketin kendisini istemiyor ve odak değişimi de
 * bir hareket.
 */
@Composable
fun rememberMaskFade(revealed: Boolean): MaskFade {
    val reduced = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else MASK_FADE_MILLIS),
        label = "maskFade"
    )
    // Yarım sinüs: 0 ve 1'de sıfır, 0.5'te tepe.
    val blur = if (reduced) 0.dp
    else (sin(progress * PI).toFloat() * MASK_BLUR_MAX.value).dp

    return MaskFade(
        // Takas tam tepe noktasında: okunabilir hiçbir kare iki metni birden
        // göstermiyor.
        showPlain = progress > 0.5f,
        blur = blur
    )
}

/**
 * @param showPlain maske kalktı mı — alanın görsel dönüşümünü bu belirliyor
 * @param blur o karedeki bulanıklık yarıçapı; 0dp ise katman hiç kurulmuyor
 */
@androidx.compose.runtime.Immutable
data class MaskFade(val showPlain: Boolean, val blur: Dp)

/** Bulanıklığı yalnızca gerçekten gerekliyse uygular. */
fun Modifier.maskFade(fade: MaskFade): Modifier =
    if (fade.blur > 0.4.dp) this.blur(fade.blur) else this

private const val MASK_FADE_MILLIS = 380
private val MASK_BLUR_MAX = 9.dp
