package app.kasa.ui.components

import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.State
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
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.kasa.ui.theme.LocalSurfaceEffects
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
/**
 * Eğim, **durum nesnesi** olarak.
 *
 * [rememberDeviceTilt] değeri döndürüyor, yani onu çağıran bileşen her
 * sensör okumasında yeniden besteleniyor. Kart gibi küçük bir yüzeyde bunun
 * bedeli görünmüyor; zemin gibi bütün uygulamayı saran bir yüzeyde ise her
 * sensör okuması ekranın tamamını yeniden besteler. Bu sürüm okumayı
 * çağırana bırakıyor: değer `drawBehind` içinde okunduğunda beste hiç
 * çalışmıyor.
 */
@Composable
fun rememberDeviceTiltState(): State<DeviceTilt> = rememberDeviceTiltInternal()

@Composable
fun rememberDeviceTilt(): DeviceTilt = rememberDeviceTiltInternal().value

@Composable
private fun rememberDeviceTiltInternal(): State<DeviceTilt> {
    val enabled = LocalSurfaceEffects.current.needsTilt && !LocalReducedMotion.current
    // Kapalıyken de bir durum nesnesi dönüyor; çağıran taraf iki ayrı yol
    // yazmak zorunda kalmasın diye. Değeri hiç değişmiyor.
    val state = remember { mutableStateOf(DeviceTilt.Level) }
    if (!enabled) return state

    val context = LocalContext.current
    var tilt by state

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

    return state
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
    shape: Shape,
    width: Dp = RIM_WIDTH,
    strength: Float = 0.5f
): Modifier = composed {
    if (!LocalSurfaceEffects.current.tilt) return@composed this

    val x by animateFloatAsState(tilt.x, label = "rimTiltX")
    val y by animateFloatAsState(tilt.y, label = "rimTiltY")

    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        // Parlak ucun yeri eğimle dönüyor; karşı uç sönük kalıyor.
        val start = Offset(size.width * (0.5f - x * 0.5f), size.height * (0.5f - y * 0.5f))
        val end = Offset(size.width * (0.5f + x * 0.5f), size.height * (0.5f + y * 0.5f))
        drawGlowOutline(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = strength),
                    Color.White.copy(alpha = strength * 0.18f),
                    Color.Transparent
                ),
                start = start,
                end = end
            ),
            shape = shape,
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
 * ### Neden şekil, köşe yarıçapı değil
 *
 * Eski hâli tek bir köşe yarıçapı alıyordu ve çağıranlar oraya yuvarlak bir
 * sayı geçiyordu. Ama liste satırlarının şekli konuma göre değişiyor: gruptaki
 * ilk satırın üstü geniş, altı dar. Sabit bir yarıçapla çizilen ışık o şeklin
 * dışına taşıyor, kırpma onu kesiyor ve geriye köşeleri kare kesilmiş bir
 * dikdörtgen kalıyordu — açık temada zeminle arasındaki fark yüzünden apaçık
 * görünen şey buydu.
 *
 * ### Neden çizgi kenarın üzerinde
 *
 * Eskiden her katman kendi genişliği kadar **içeri** kaydırılıyordu, yani
 * geniş ve sönük olan katman yüzeyin içine doğru bir bant çiziyordu. Işığın
 * yeri kenar; genişleyen katman iki yana birden yayılmalı. Dışarı taşan yarısı
 * zaten kırpılıyor ve sönerek biten bir kenar bırakıyor — istenen de bu.
 *
 * @param brush çizginin rengi ya da degradesi
 * @param shape yüzeyin kendi şekli — ışık tam bu şeklin kenarı üzerinde
 * @param width çekirdek çizginin kalınlığı
 * @param spread hâlenin çekirdeğin kaç katı kadar yayılacağı
 * @param intensity toplam parlaklık çarpanı (0-1)
 */
fun DrawScope.drawGlowOutline(
    brush: Brush,
    shape: Shape,
    width: Float,
    spread: Float = GLOW_SPREAD,
    intensity: Float = 1f
) {
    if (intensity <= 0.01f || size.minDimension <= 0f) return

    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }

    // Dıştan içe: geniş ve sönük olan altta kalıyor, çekirdek en üstte.
    GLOW_LAYERS.forEach { layer ->
        drawPath(
            path = path,
            brush = brush,
            alpha = (layer.alpha * intensity).coerceIn(0f, 1f),
            style = Stroke(width = width * (1f + spread * layer.width))
        )
    }
}

/**
 * Hâlenin çizginin kendisinden ne kadar uzağa yayıldığı.
 *
 * Yay çizen çağıranlar kutularını buna göre daraltmak zorunda: kutu yalnızca
 * çekirdek çizgiyi sığdıracak kadar büyükse, en dıştaki sönük katman tuvalin
 * dışında kalıyor ve kırpılınca hâle yuvarlak değil kesik görünüyor.
 */
fun glowExtent(width: Float, spread: Float = GLOW_SPREAD): Float =
    width * (1f + spread) / 2f

/**
 * Parlayan yay.
 *
 * [drawGlowOutline] ile aynı mantık, kapalı bir yol yerine bir yay üzerinde:
 * dıştan içe genişleyen katmanlar, en içte doymuş bir çekirdek. Halkalar
 * (güç puanı, TOTP sayacı, yükleme göstergesi) uygulamanın en çok bakılan
 * grafik öğeleri ve düz bir yay onları çizim gibi gösteriyordu.
 *
 * Bütün katmanlar aynı yay üzerinde duruyor ve yalnızca kalınlık büyüyor, yani
 * ışık iki yana simetrik yayılıyor. Çağıran taraf kutusunu [glowExtent] kadar
 * daraltmak zorunda: yalnızca çekirdek çizgiyi sığdıran bir kutuda en dıştaki
 * katman tuvalin dışında kalıyor ve kırpılınca hâle kesik görünüyor.
 */
fun DrawScope.drawGlowArc(
    color: Color,
    startAngle: Float,
    sweepAngle: Float,
    topLeft: Offset,
    arcSize: Size,
    width: Float,
    spread: Float = RING_GLOW_SPREAD,
    intensity: Float = 1f
) {
    if (intensity <= 0.01f || sweepAngle == 0f) return

    // Bütün katmanlar **aynı** yay üzerinde; yalnızca kalınlık büyüyor.
    //
    // Eskiden her katman kendi kutusunu dışa doğru büyütüyordu ve en dıştaki
    // katman tuvalin dışına taşıp kırpılıyordu: hâle yuvarlak değil, kenardan
    // kesilmiş görünüyordu. Kutuyu sabit tutmak ışığı iki yana simetrik
    // yayıyor; çağıran tarafın kutuyu [glowExtent] kadar daraltması yeterli.
    GLOW_LAYERS.forEach { layer ->
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = width * (1f + spread * layer.width), cap = StrokeCap.Round),
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
 * Halkaların yayılma katsayısı.
 *
 * [GLOW_SPREAD] ince kenar çizgilerine göre ayarlı: 1dp'lik bir çerçevede
 * 3.6dp'lik bir hâle bırakıyor, yani ışık çizginin hemen yanında sönüyor.
 * Aynı katsayı 14dp'lik bir halka çizgisine uygulandığında 50dp'lik bir bulut
 * çıkıyordu — halka artık bir halka değil, ortasında çizgi olan bir leke gibi
 * duruyordu ve tuvale sığması için çemberin kendisi küçülmek zorunda kalıyordu.
 *
 * Hâle çizginin kalınlığıyla orantılı; kalın çizgide oran küçülmeli.
 */
const val RING_GLOW_SPREAD = 0.9f

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
    shape: Shape,
    color: Color = Color.White,
    width: Dp = RIM_WIDTH,
    maxAlpha: Float = 0.55f
): Modifier = composed {
    if (!LocalSurfaceEffects.current.pressBloom) return@composed this

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
            drawGlowOutline(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = maxAlpha),
                        color.copy(alpha = maxAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = origin,
                    radius = hypot(size.width, size.height) * (0.35f + progress * 0.35f)
                ),
                shape = shape,
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
    shape: Shape,
    color: Color = Color.White,
    width: Dp = RIM_WIDTH,
    alpha: Float = 0.45f
): Modifier = composed {
    if (!LocalSurfaceEffects.current.shimmer || LocalReducedMotion.current) return@composed this

    // Şerit sayfaya girildiğinde **bir kez** geçiyor.
    //
    // Eskiden 5,6 saniyede bir tekrar eden sonsuz bir döngüydü. İki sorunu
    // vardı. Ekranda duran her kart, kullanıcı ona hiç bakmasa da sürekli
    // bir animasyon çalıştırıyordu — sonsuz bir animasyonun sonu yok ve
    // bedeli kare bütçesinden çıkıyor. Ve tekrar, parıltının söylediği şeyi
    // bozuyordu: bir yüzeyin cilalı olduğunu bir kez gösteren yansıma
    // karakter, aynı yerde her beş saniyede bir tekrarlayan yansıma ise
    // bir uyarı ışığı gibi okunuyordu.
    //
    // Şimdi kart göründükten kısa bir süre sonra bir kez geçiyor ve
    // susuyor. Kullanıcı ekrandan çıkıp geri geldiğinde yeniden görüyor,
    // çünkü o an yüzeye yeniden bakıyor.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Gecikme, kartın kendi beliriş animasyonunun bitmesini bekliyor:
        // ikisi üst üste binseydi parıltı belirişin parçası sanılırdı.
        delay(SHIMMER_DELAY_MILLIS)
        phase.animateTo(1f, tween(durationMillis = SHIMMER_SWEEP_MILLIS, easing = LinearEasing))
    }

    drawWithContent {
        drawContent()
        val travel = phase.value
        if (travel <= 0f || travel >= 1f) return@drawWithContent
        val stroke = width.toPx()
        val span = size.width * 0.38f
        val head = -span + travel * (size.width + span * 2f)
        // Şeridin uçlarında hâle sönüyor: geçişin başı ve sonu, ortasıyla
        // aynı güçte parlasaydı şerit belirip kaybolmak yerine yanıp sönerdi.
        val fade = sin(travel * PI).toFloat().coerceIn(0f, 1f)
        drawGlowOutline(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                start = Offset(head, 0f),
                end = Offset(head + span, size.height)
            ),
            shape = shape,
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
    if (!LocalSurfaceEffects.current.edgeDepth) return@composed this

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
/** Şeridin yüzeyi kat etme süresi. */
private const val SHIMMER_SWEEP_MILLIS = 1500

/** Kart belirdikten sonra şeridin beklediği süre. */
private const val SHIMMER_DELAY_MILLIS = 420L
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
