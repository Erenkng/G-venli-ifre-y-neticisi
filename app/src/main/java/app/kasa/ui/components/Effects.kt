package app.kasa.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.math.PI
import kotlin.math.sin
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
 * Yüzeyin **kenarında** eğimle birlikte gezinen ışık.
 *
 * ### Neden yüzeyde değil, çerçevede
 *
 * Yüzeyin tamamına yayılan bir ışık, altındaki içeriğin kontrastını
 * düşürüyor: kart numarasının, liste satırındaki adın, alan etiketinin
 * üzerinden geçen beyaz bir örtü onları okunması gereken şey olmaktan
 * çıkarıp efektin zeminine çeviriyor. Bir parola yöneticisinde ekranda duran
 * her şey okunmak için orada.
 *
 * Kenar bandı bu çakışmayı tamamen kaldırıyor: ışık yalnızca yüzeyin
 * sınırında, yani hiçbir metnin bulunmadığı yerde. Gerçek nesnelerde de
 * böyle — cilalı bir yüzeyde ilk parlayan yer kenarın kendisi, çünkü ışığı
 * en dik açıyla oradan yansıtıyor.
 *
 * ### Işık nereden geliyor
 *
 * Degradenin başlangıcı eğimle yer değiştiriyor: telefonu sola yatırınca
 * parlak uç sola kayıyor. Çerçevenin tamamı aynı anda parlasaydı hareketin
 * yönü kaybolur ve yalnızca "bir şey yanıp sönüyor" görünürdü.
 */
fun Modifier.tiltRim(
    tilt: DeviceTilt,
    corner: Dp,
    width: Dp = RIM_WIDTH,
    strength: Float = 0.5f
): Modifier = composed {
    if (!LocalExperimentalEffects.current) return@composed this

    val x by animateFloatAsState(tilt.x, label = "rimTiltX")
    val y by animateFloatAsState(tilt.y, label = "rimTiltY")

    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val radius = CornerRadius(corner.toPx())
        // Parlak ucun yeri eğimle dönüyor; karşı uç sönük kalıyor.
        val start = Offset(size.width * (0.5f - x * 0.5f), size.height * (0.5f - y * 0.5f))
        val end = Offset(size.width * (0.5f + x * 0.5f), size.height * (0.5f + y * 0.5f))
        drawGlowStroke(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = strength),
                    Color.White.copy(alpha = strength * 0.18f),
                    Color.Transparent
                ),
                start = start,
                end = end
            ),
            corner = radius.x,
            width = stroke
        )
    }
}

/**
 * Parlayan kenar çizgisi.
 *
 * ### Neden tek bir çizgi parlamıyor
 *
 * Bir kenarı `Stroke` ile bir kez çizmek, o kenarı **çizilmiş** gösteriyor;
 * parlayan bir şey ise kendi sınırının dışına ışık taşırıyor. Gerçek ışık
 * kaynağının etrafındaki hâle, kaynaktan uzaklaştıkça hızla sönen bir dağılım
 * — ve göz "bu parlıyor" kararını çizginin kendisinden değil, o dağılımdan
 * veriyor.
 *
 * ### Nasıl kuruluyor
 *
 * Aynı yol birkaç kez çiziliyor: en dıştaki en geniş ve en sönük, en içteki
 * en dar ve en parlak. Üst üste binen katmanlar toplandığında merkeze doğru
 * hızla artan bir yoğunluk çıkıyor — bir bulanıklık katmanı kurmadan hâlenin
 * yaptığı iş bu.
 *
 * Katman sayısı üç: ikisi hâleyi kurmuyor, dördüncüsü üçüncüden ayırt
 * edilmiyor ve her katman ayrı bir çizim çağrısı. Genişlik katsayıları
 * doğrusal değil — hâle yakında hızlı, uzakta yavaş sönüyor.
 *
 * ### Çekirdek neden ayrı
 *
 * En içteki çizgi tam parlaklıkta ve **ince**. O olmadan hâle bir leke gibi
 * duruyor; onunla birlikte, ışığın nereden geldiği belli olan bir kenar
 * oluyor. Işıklı yüzeylerin fotoğrafında da aynı ikili var: doymuş bir
 * çekirdek ve çevresinde yumuşak bir hâle.
 *
 * @param brush çizginin rengi ya da degradesi
 * @param corner köşe yarıçapı
 * @param width çekirdek çizginin kalınlığı
 * @param spread hâlenin çekirdeğin kaç katı kadar yayılacağı
 * @param intensity toplam parlaklık çarpanı (0-1)
 */
fun DrawScope.drawGlowStroke(
    brush: Brush,
    corner: Float,
    width: Float,
    spread: Float = GLOW_SPREAD,
    intensity: Float = 1f
) {
    if (intensity <= 0.01f || size.minDimension <= 0f) return

    val radius = CornerRadius(corner)
    // Dıştan içe: geniş ve sönük olan altta kalıyor, çekirdek en üstte.
    GLOW_LAYERS.forEach { layer ->
        val strokeWidth = width * (1f + spread * layer.width)
        val inset = strokeWidth / 2f
        // Yüzeyden taşan bir hâle, kenarın dışında da görünmeli; kutuyu
        // daraltmak yerine çizgiyi kenarın üstüne oturtuyoruz ve fazlası
        // dışarı taşıyor — kırpma varsa zaten o kesiyor.
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = radius,
            style = Stroke(width = strokeWidth),
            alpha = (layer.alpha * intensity).coerceIn(0f, 1f)
        )
    }
}

/**
 * Hâlenin katmanları: genişlik çarpanı ve saydamlık.
 *
 * Saydamlıklar toplandığında merkezde ~1'e yaklaşıyor, dışta 0.10'da
 * kalıyor. Eşit dağıtılsaydı hâle düz bir kalın çizgi gibi görünürdü.
 */
private data class GlowLayer(val width: Float, val alpha: Float)

private val GLOW_LAYERS = listOf(
    GlowLayer(width = 1.0f, alpha = 0.10f),
    GlowLayer(width = 0.45f, alpha = 0.22f),
    GlowLayer(width = 0f, alpha = 1f)
)

/** Hâlenin çekirdeğe göre yayılma katsayısı. */
private const val GLOW_SPREAD = 2.6f

/**
 * Basılan noktaya en yakın kenarın parlaması.
 *
 * ### Material'ın dalgalanmasından farkı
 *
 * Material'ın `ripple`'ı yüzeyin **içini** dolduruyor ve o sırada üzerindeki
 * metnin kontrastını düşürüyor. Buradaki ışık çerçevede: dokunulan yere en
 * yakın kenar parlıyor, uzak kenar sönük kalıyor. Dokunuşun nerede olduğu
 * yine görünüyor — çerçevenin hangi tarafının parladığından — ama okunan
 * hiçbir şeyin üzerinden geçmiyor.
 *
 * Konum `pointerInput` ile alınıyor. Konum olmadan çerçevenin tamamı aynı
 * anda parlardı ve efekt "dokundum" demekten öteye geçmezdi; nereye
 * dokunduğunu da söylemesi, art arda basılan iki satırı ayırt ettiriyor.
 */
fun Modifier.pressRim(
    corner: Dp,
    color: Color = Color.White,
    width: Dp = RIM_WIDTH,
    maxAlpha: Float = 0.55f
): Modifier = composed {
    if (!LocalExperimentalEffects.current) return@composed this

    var origin by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed) BLOOM_IN_MILLIS else BLOOM_OUT_MILLIS),
        label = "pressRim"
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
            if (progress <= 0.01f || !origin.isSpecifiedSafely()) return@drawWithContent
            val stroke = width.toPx()
            drawGlowStroke(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = maxAlpha),
                        color.copy(alpha = maxAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = origin,
                    radius = hypot(size.width, size.height) * (0.35f + progress * 0.35f)
                ),
                corner = corner.toPx(),
                width = stroke,
                // Hâle basışla birlikte büyüyor: sabit bir yayılma, ışığın
                // parmakla birlikte geldiğini değil hep orada olduğunu
                // söylerdi.
                spread = 1.4f + progress * 1.6f,
                intensity = progress
            )
        }
}

private fun Offset.isSpecifiedSafely(): Boolean =
    this != Offset.Unspecified && x.isFinite() && y.isFinite()

/**
 * Çerçevenin üzerinden arada bir geçen ışık.
 *
 * Cam ve cilalı yüzeylerin ortak davranışı: kaynak sabitken bile en ufak
 * hareket kenarda gezinen bir yansıma üretiyor. Şerit yüzeyin **sınırında**
 * ilerliyor, içinden değil — içinden geçen bir şerit, altındaki metnin
 * üzerinden geçmek zorunda kalırdı.
 *
 * İki geçiş arasında uzun bir sessizlik var: sürekli dönen bir parıltı
 * "yükleniyor" gibi görünüyor, arada bir geçen ise canlı.
 */
fun Modifier.shimmerRim(
    corner: Dp,
    color: Color = Color.White,
    width: Dp = RIM_WIDTH,
    alpha: Float = 0.45f
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
        val stroke = width.toPx()
        val span = size.width * 0.38f
        val head = -span + travel * (size.width + span * 2f)
        // Şeridin uçlarında hâle sönüyor: geçişin başı ve sonu, ortasıyla
        // aynı güçte parlasaydı şerit belirip kaybolmak yerine yanıp sönerdi.
        val fade = sin(travel * PI).toFloat().coerceIn(0f, 1f)
        drawGlowStroke(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                start = Offset(head, 0f),
                end = Offset(head + span, size.height)
            ),
            corner = corner.toPx(),
            width = stroke,
            intensity = fade
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
/**
 * Kenar bandının kalınlığı.
 *
 * İnce tutuluyor: kalın bir band yüzeye geri dönüyor ve "çerçeve" olmaktan
 * çıkıp bir kenar dolgusuna dönüşüyor.
 */
private val RIM_WIDTH = 2.dp
private const val FALLOFF_FRACTION = 0.10f
private const val DEPTH_SCALE = 0.06f
private const val DEPTH_ALPHA = 0.55f
