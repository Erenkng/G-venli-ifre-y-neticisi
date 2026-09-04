package app.kasa.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.LocalReducedMotion
import kotlin.coroutines.cancellation.CancellationException

/**
 * Geri hareketinin parmakla birlikte yürüyen hâli.
 *
 * ### Neden
 *
 * Uygulamanın bütün tam ekran katmanları — düzenleyici, çöp kutusu, arama,
 * tarayıcı — geri tuşuyla kapanıyordu ama kapanma **basıldıktan sonra**
 * başlıyordu. Kullanıcı kenardan içeri çekerken ekranda hiçbir şey olmuyor,
 * sonra bir anda katman gidiyordu. O aralıkta kullanıcı iki şeyi bilmiyor:
 * hareketin tanınıp tanınmadığını ve bırakırsa ne olacağını.
 *
 * Sistem bunu kendi pencereleri arasında zaten yapıyor (bir uygulamadan
 * çıkarken pencere küçülüp kenara çekiliyor). Uygulamanın kendi katmanları
 * aynı hareketi taklit etmediğinde, aynı parmak hareketi ekranın neresinde
 * yapıldığına göre iki farklı şey hissettiriyor.
 *
 * ### Dönüşüm neden bu
 *
 * Yüzey küçülüyor, çekilen kenarın **tersine** kayıyor ve köşeleri
 * yuvarlanıyor. Üçü birlikte "bu yüzey bir pencere ve şu anda ekrandan
 * ayrılıyor" diyor; tek başına saydamlık bunu söylemiyor, çünkü solmak
 * yer değiştirmenin değil yok olmanın işareti.
 *
 * Ölçek merkezi çekilen kenarın karşısında: sol kenardan çekerken yüzey sağa
 * doğru toplanıyor, yani altındaki şey soldan görünmeye başlıyor — geri
 * gidilen yer orası.
 *
 * Yarıçap sıfırdan başlıyor. Ekranı kaplayan bir yüzeyin köşesi zaten
 * ekranın köşesi; yuvarlanmaya başladığı an artık ekranı kaplamadığını
 * söylüyor.
 *
 * ### İlerleme neden bırakıldığı yerde kalıyor
 *
 * Hareket onaylandığında ilerleme sıfırlanmıyor: sıfırlansaydı yüzey önce
 * tam boyuna sıçrar, sonra çıkış animasyonu başlardı. Bırakıldığı yerden
 * devam ediyor ve sıfırlama bir sonraki açılışta yapılıyor.
 */
@Stable
class BackGesture internal constructor() {
    internal val pull = Animatable(0f)
    internal var holding by mutableStateOf(false)
    internal var fromLeft by mutableStateOf(true)

    /** 0 = dokunulmamış, 1 = hareket tamamlanmış. Çizim anında okunmalı. */
    val progress: Float get() = pull.value
}

/**
 * Geri hareketini dinler ve ilerlemesini [BackGesture] içine yazar.
 *
 * Kayıt sırası önemli: Compose'da en son kaydedilen etkin geri işleyicisi
 * kazanıyor, yani bu çağrı katmanların öncelik sırasına göre yerleştirilmeli.
 * `BackHandler` ile aynı yerde durduğu sürece davranış birebir aynı.
 *
 * @param enabled katman görünürken true.
 * @param onBack hareket **onaylandığında** çalışır; iptalde çalışmaz.
 */
@Composable
fun rememberBackGesture(enabled: Boolean, onBack: () -> Unit): BackGesture {
    val gesture = remember { BackGesture() }
    val reduced = LocalReducedMotion.current
    val settle = KasaMotion.medium<Float>()

    // Katman açılırken sıfırlanıyor. Onaylanan bir hareketten sonra ilerleme
    // olduğu yerde bırakıldığı için, sıfırlama açılışa kalıyor.
    LaunchedEffect(enabled) { if (enabled) gesture.pull.snapTo(0f) }

    // Toparlanma hareketin kendi eşyordamında değil: iptalde o eşyordamın
    // hangi durumda olduğuna bağlı kalmak istemiyoruz. Burası hareket
    // bırakıldığında ve katman **hâlâ açıkken** çalışıyor — yani yalnızca
    // iptalde. Onayda katman kapanıyor, `enabled` düşüyor ve toparlanma
    // çıkış animasyonunun üstüne binmiyor.
    LaunchedEffect(gesture.holding, enabled) {
        if (!gesture.holding && enabled && gesture.pull.value > 0f) {
            gesture.pull.animateTo(0f, settle)
        }
    }

    PredictiveBackHandler(enabled) { events ->
        try {
            gesture.holding = true
            gesture.pull.snapTo(0f)
            events.collect { event ->
                gesture.fromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                // Hareket kapalıyken de geri tuşu çalışıyor, yalnızca yüzey
                // kıpırdamıyor: hareketi kapatan kullanıcıya ekranı sürekli
                // ölçeklenen bir yüzey göstermek ayarın anlamını bozardı.
                if (!reduced) gesture.pull.snapTo(event.progress)
            }
            onBack()
        } catch (_: CancellationException) {
            // Hareket iptal edildi. Toparlanma yukarıdaki etkide.
        } finally {
            gesture.holding = false
        }
    }
    return gesture
}

/**
 * [rememberBackGesture] ilerlemesini yüzeye uygular.
 *
 * Değer beste sırasında değil **çizim** sırasında okunuyor: doğrudan
 * okunsaydı parmak kenarda gezindiği sürece katmanın bütün iskeleti her
 * karede yeniden kurulurdu.
 */
fun Modifier.predictiveBack(gesture: BackGesture): Modifier = graphicsLayer {
    val raw = gesture.progress
    if (raw <= 0f) return@graphicsLayer

    // Yavaşlayan eğri: hareket başladığı anda görünür bir karşılık veriyor,
    // sonuna doğru duruyor. Doğrusal olsaydı ilk milimetreler fark edilmez,
    // kullanıcı hareketin tanınmadığını sanırdı.
    val pulled = raw * (2f - raw)

    val shrink = 1f - pulled * PULL_SCALE
    scaleX = shrink
    scaleY = shrink
    transformOrigin = TransformOrigin(if (gesture.fromLeft) 1f else 0f, 0.5f)
    translationX = (if (gesture.fromLeft) 1f else -1f) * pulled * PULL_SHIFT.toPx()

    clip = true
    shape = RoundedCornerShape(PULL_CORNER * pulled)
}

/**
 * Aynı hareket, **itmeli** gezinme için.
 *
 * Ayarlarda kategoriler yan yana duruyor: içeri girerken içerik sağdan
 * geliyor, çıkarken sağa dönüyor. Orada pencere eğretilemesi yanlış olurdu —
 * kategori ekranı ayrı bir pencere değil, aynı listenin bir sonraki durağı.
 * Bu yüzden küçülme ve köşe yuvarlanması yok; yalnızca gidilen yolun tersine
 * kayma ve hafif solma.
 *
 * Yön parmağın hangi kenardan geldiğine bakmıyor: itmeli gezinmede geri,
 * ileri gidilen yolun tersi demek ve o yol her zaman aynı yönde.
 */
fun Modifier.predictiveBackPush(gesture: BackGesture): Modifier = graphicsLayer {
    val raw = gesture.progress
    if (raw <= 0f) return@graphicsLayer

    val pulled = raw * (2f - raw)
    translationX = pulled * size.width * PUSH_SHIFT
    alpha = 1f - pulled * PUSH_FADE
}

/** Hareket sonunda yüzey bu oranda küçülüyor. */
private const val PULL_SCALE = 0.11f

/** Çekilen kenarın tersine kayma. */
private val PULL_SHIFT = 18.dp

/** Ekranı kaplayan sıfır yarıçaptan pencere köşesine. */
private val PULL_CORNER = 30.dp

/** İtmeli gezinmede geri çekilirken kat edilen yol, genişliğin oranı olarak. */
private const val PUSH_SHIFT = 0.22f

/** Solma. Tam saydamlığa gitmiyor: bırakılırsa geri gelecek olan bir yüzey. */
private const val PUSH_FADE = 0.30f
