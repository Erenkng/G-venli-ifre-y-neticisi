package app.kasa.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import app.kasa.ui.theme.LocalExperimentalEffects
import app.kasa.ui.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Deneysel yüzey efektleri.
 *
 * ### Neden ayrı bir anahtarın arkasında
 *
 * Buradaki efektlerin hepsi **bedelli**: eğim parlaması ivmeölçmeri açık
 * tutuyor, basınç çiçeklenmesi her dokunuşta bir katman çiziyor, parıltı
 * durağan bir ekranda bile kare üretiyor. Hiçbiri bilginin taşınması için
 * gerekli değil; hepsi yüzeyi daha canlı yapmak için var.
 *
 * Bedeli olan ama gerekli olmayan bir şeyin kapatılabilir olması gerekiyor.
 * Anahtar kapalıyken kod yolları **hiç çalışmıyor**: sensör dinleyicisi
 * kaydedilmiyor, sonsuz animasyon başlatılmıyor, fazladan katman kurulmuyor.
 * "Görünmez ama çalışıyor" bir efekt, kapatılmış sayılmaz.
 *
 * ### Hareket kapalıyken
 *
 * [LocalReducedMotion] açıkken hareket içeren efektler de susuyor. Kullanıcı
 * sistem düzeyinde "animasyon istemiyorum" demişse, uygulamanın kendi
 * anahtarını açık bulmuş olması bunu geçersiz kılmıyor.
 */

/** Cihazın eğimi: (-1..1, -1..1). Yatayda sola/sağa, dikeyde öne/arkaya. */
@Immutable
data class DeviceTilt(val x: Float, val y: Float) {
    companion object {
        val Level = DeviceTilt(0f, 0f)
    }
}

/**
 * İvmeölçerden okunan, yumuşatılmış eğim.
 *
 * ### Neden yumuşatma şart
 *
 * Ham ivmeölçer verisi elde tutulan bir telefonda sürekli titriyor: nabız,
 * yürüyüş, otobüs. O veriyi doğrudan bir ışığa bağlamak, yüzeyi durmadan
 * seğiren bir şeye çevirir. Üstel yumuşatma yüksek frekanslı gürültüyü
 * kesiyor, yavaş çevirmeyi geçiriyor.
 *
 * ### Neden `TYPE_ACCELEROMETER`, jiroskop değil
 *
 * İstenen şey açısal hız değil, **yerçekimine göre duruş**. İvmeölçer bunu
 * doğrudan veriyor ve her cihazda var; jiroskop bazı ucuz cihazlarda yok ve
 * entegrasyon hatası biriktiği için sabit bir referans üretmiyor.
 *
 * ### Kapalıyken hiç kaydedilmiyor
 *
 * Anahtar kapalıysa ya da cihazda sensör yoksa dinleyici hiç kurulmuyor;
 * `DisposableEffect` anahtarı [enabled] olduğu için kullanıcı ayarı
 * kapattığında kayıt anında düşüyor.
 */
@Composable
fun rememberDeviceTilt(): DeviceTilt {
    val enabled = LocalExperimentalEffects.current && !LocalReducedMotion.current
    if (!enabled) return DeviceTilt.Level

    val context = LocalContext.current
    var tilt by remember { mutableStateOf(DeviceTilt.Level) }

    DisposableEffect(enabled) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        var smoothX = 0f
        var smoothY = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Değerler m/s²; yerçekimi ~9.81. Bölünce -1..1 aralığına
                // yaklaşıyor ve kırpma uçları kesiyor.
                val rawX = (-event.values[0] / GRAVITY).coerceIn(-1f, 1f)
                val rawY = (event.values[1] / GRAVITY).coerceIn(-1f, 1f)
                smoothX += (rawX - smoothX) * SMOOTHING
                smoothY += (rawY - smoothY) * SMOOTHING
                // Küçük değişimler yazılmıyor: her örnekte durum güncellemek
                // saniyede ~50 yeniden besteleme demek ve gözle görülen bir
                // fark üretmiyor.
                if (abs(smoothX - tilt.x) > TILT_EPSILON || abs(smoothY - tilt.y) > TILT_EPSILON) {
                    tilt = DeviceTilt(smoothX, smoothY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // SENSOR_DELAY_UI: ~60 ms. Oyun hızına (SENSOR_DELAY_GAME) çıkmak
        // burada pil harcamaktan başka bir şey yapmıyor; ışık zaten
        // yumuşatılmış bir değeri izliyor.
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    return tilt
}

/**
 * Yüzeyin üzerinde eğimle birlikte kayan ışık.
 *
 * Kart yüzü gibi parlak yüzeylerde gerçek bir plastik kartın camdan yansıması
 * gibi davranıyor: telefonu çevirdikçe ışık kartın üzerinde geziniyor. Etki
 * bilerek zayıf — fark edilmesi gereken bir efekt değil, yokluğunda yüzeyin
 * ölü görünmesini engelleyen bir doku.
 */
fun Modifier.tiltSheen(tilt: DeviceTilt, strength: Float = 0.22f): Modifier = composed {
    if (!LocalExperimentalEffects.current) return@composed this

    // Işığın merkezi eğimle yer değiştiriyor; kenardan taşmaması için
    // yarıçapın yarısı kadar hareket alanı bırakılıyor.
    val x by animateFloatAsState(tilt.x, label = "tiltX")
    val y by animateFloatAsState(tilt.y, label = "tiltY")

    drawWithContent {
        drawContent()
        val center = Offset(
            size.width * (0.5f + x * 0.42f),
            size.height * (0.5f + y * 0.42f)
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = strength),
                    Color.White.copy(alpha = strength * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = hypot(size.width, size.height) * 0.55f
            )
        )
    }
}

/**
 * Basıldığı noktadan açılan yumuşak ışık.
 *
 * ### Material'ın dalgalanmasından farkı
 *
 * Material'ın `ripple`'ı bir daire çizip yüzeyin kenarında kesiyor ve rengi
 * temanın tek bir vurgusundan alıyor. Burada kesilen bir kenar yok: ışık
 * merkezden dışarı doğru **sönerek** gidiyor ve yüzeyin dışına taşmadan
 * kayboluyor. Dokunulan yer, dokunulan an belli oluyor; sınırları belli olan
 * bir daire ise dokunuşun kendisinden çok yüzeyin şeklini anlatıyor.
 *
 * Konum `pointerInput` ile alınıyor: dokunuşun gerçek koordinatı olmadan
 * çiçeklenme yüzeyin ortasından açılırdı ve o zaman "nereye dokundum"
 * bilgisini hiç taşımazdı.
 */
fun Modifier.pressBloom(color: Color = Color.White, maxAlpha: Float = 0.16f): Modifier = composed {
    if (!LocalExperimentalEffects.current) return@composed this

    var origin by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) BLOOM_IN_MILLIS else BLOOM_OUT_MILLIS),
        label = "bloom"
    )

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                origin = down.position
                pressed = true
                // Parmak kalkana ya da hareket iptal olana kadar açık kalıyor.
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .drawWithContent {
            drawContent()
            if (progress > 0.01f && origin.isSpecifiedSafely()) {
                val radius = hypot(size.width, size.height) * (0.25f + progress * 0.55f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = maxAlpha * progress),
                            Color.Transparent
                        ),
                        center = origin,
                        radius = radius
                    )
                )
            }
        }
}

private fun Offset.isSpecifiedSafely(): Boolean =
    this != Offset.Unspecified && x.isFinite() && y.isFinite()

/**
 * Yüzeyin üzerinden geçen yavaş ışık şeridi.
 *
 * Cam ve cilalı yüzeylerin ortak davranışı: kaynak sabitken bile en ufak
 * hareket yüzeyde gezinen bir yansıma üretiyor. Şerit köşeden köşeye eğik
 * geçiyor ve iki geçiş arasında uzun bir sessizlik var — sürekli dönen bir
 * parıltı yükleniyor gibi görünüyor, arada bir geçen ise canlı.
 */
fun Modifier.shimmerSweep(
    color: Color = Color.White,
    alpha: Float = 0.10f
): Modifier = composed {
    if (!LocalExperimentalEffects.current || LocalReducedMotion.current) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )

    drawWithContent {
        drawContent()
        // Döngünün yalnızca ilk parçasında şerit geçiyor; gerisi sessizlik.
        if (phase > SHIMMER_ACTIVE) return@drawWithContent
        val travel = phase / SHIMMER_ACTIVE
        val span = size.width * 0.42f
        val head = -span + travel * (size.width + span * 2f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = alpha),
                    Color.Transparent
                ),
                start = Offset(head, 0f),
                end = Offset(head + span, size.height)
            ),
            size = Size(size.width, size.height)
        )
    }
}

/**
 * Ekranın uçlarına yaklaşan içeriğin geriye çekilmesi.
 *
 * ### Ne anlatıyor
 *
 * Kaydırılan bir listede satırlar ekranın kenarında bir anda kesiliyor.
 * Kenara yaklaştıkça hafifçe küçülüp soldurulduklarında liste düz bir şerit
 * değil, uçları geriye kıvrılan bir yüzey gibi okunuyor — ve gezinme çubuğunun
 * altına giren içeriğin oraya "gitmesi" doğal görünüyor.
 *
 * ### Ölçü ekranın kendisinden
 *
 * Satırın ekrandaki yeri [boundsInWindow] ile alınıyor; kabın değil pencerenin
 * ölçüsüne bakılıyor çünkü kesilme pencerenin kenarında oluyor. Etki yalnızca
 * son [FALLOFF_FRACTION] kadarlık şeritte var; listenin ortasında hiçbir
 * dönüşüm uygulanmıyor, yani kaydırmanın büyük kısmı bedelsiz.
 */
fun Modifier.edgeDepth(): Modifier = composed {
    if (!LocalExperimentalEffects.current) return@composed this

    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    var factor by remember { mutableFloatStateOf(1f) }

    this
        .onGloballyPositioned { coordinates ->
            if (windowHeight <= 0f) return@onGloballyPositioned
            val bounds = coordinates.boundsInWindow()
            val zone = windowHeight * FALLOFF_FRACTION
            val topGap = bounds.top
            val bottomGap = windowHeight - bounds.bottom
            val nearest = minOf(topGap, bottomGap)
            val next = if (nearest >= zone) 1f else (nearest / zone).coerceIn(0f, 1f)
            if (abs(next - factor) > 0.01f) factor = next
        }
        .graphicsLayer {
            // Ölçek ve saydamlık ayrı eğrilerde: tam sönmüş ama tam boyunda
            // bir satır hayalet gibi duruyordu.
            scaleX = 1f - (1f - factor) * DEPTH_SCALE
            scaleY = scaleX
            alpha = 1f - (1f - factor) * DEPTH_ALPHA
        }
}

private const val GRAVITY = 9.81f
private const val SMOOTHING = 0.12f
private const val TILT_EPSILON = 0.008f
private const val BLOOM_IN_MILLIS = 220
private const val BLOOM_OUT_MILLIS = 360
private const val SHIMMER_CYCLE_MILLIS = 5600
/** Döngünün ne kadarında şerit geçiyor; gerisi bekleme. */
private const val SHIMMER_ACTIVE = 0.28f
private const val FALLOFF_FRACTION = 0.10f
private const val DEPTH_SCALE = 0.06f
private const val DEPTH_ALPHA = 0.55f
