package app.kasa.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import app.kasa.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * Süresi bilinmeyen bir iş sürerken dönen halka.
 *
 * ### Neden dalgalı çubuk yetmiyordu
 *
 * Uygulamadaki tek gösterge [WavyProgress] ve o **belirli** bir ilerleme
 * istiyor: dolacak bir yer, dolduran bir sayı. Ama uygulamadaki işlerin çoğu
 * öyle değil — bir dosyanın çözülmesi, bir kasanın açılması, bir dışa
 * aktarmanın yazılması. Oralarda ilerleme çubuğu ya sıfırda duruyor (donmuş
 * görünüyor) ya da uydurulmuş bir sayıyla doluyor, ki o da yalan.
 *
 * ### Yay neden nefes alıyor
 *
 * Sabit uzunlukta dönen bir yay, saniyede bir kez aynı yerden geçiyor ve göz
 * o tekrarı hemen yakalıyor; ondan sonra hareket "dönüyor" değil "bekliyor"
 * diyor. Yayın uzunluğu dönüşten **farklı** bir hızda değişince desen kendini
 * çok daha geç tekrarlıyor ve hareket canlı kalıyor.
 *
 * Aynı sebep sistemin kendi dairesel göstergesinde de var; burada fark, yayın
 * kasanın kadran geometrisini sürdürmesi ve ucunun parlaması.
 *
 * ### Hareket kapalıyken
 *
 * Dönmüyor: sabit bir halka ve içinde duran bir yay kalıyor. Bir şeyin
 * sürdüğü bilgisi kayboluyor ama kullanıcı zaten hareketin kendisini
 * istemiyor; yerine metin ([LoadingOverlay] içindeki etiket) o işi yapıyor.
 */
@Composable
fun KasaLoader(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = KasaTheme.colors.ink3.copy(alpha = 0.18f)
) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "loader")

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SPIN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderSpin"
    )
    // Nefes dönüşten uzun ve ikisi tam katı değil: desenin tekrarı iki
    // döngünün en küçük ortak katına düşüyor, yani gözle yakalanmıyor.
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderBreath"
    )

    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * STROKE_FRACTION
        val inset = stroke / 2f
        val box = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)

        // Ray: yayın nerede gezindiğini gösteriyor. Onsuz yay boşlukta
        // dönüyor ve dönüşün bir yörüngesi olduğu okunmuyor.
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val sweep = if (reduced) MIN_SWEEP else {
            MIN_SWEEP + (MAX_SWEEP - MIN_SWEEP) * (0.5f + 0.5f * sin(breath))
        }
        val start = if (reduced) -90f else spin - sweep / 2f

        // Hâle önce: yayın **altına** geliyor.
        //
        // Sonra çizilseydi hâlenin en içteki katmanı — tam parlaklıkta ve düz
        // renk — degradeli yayın üstünü boyar ve degradeyi silerdi. Hâlenin
        // işi yayı çevrelemek, yerine geçmek değil.
        drawGlowArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            topLeft = topLeft,
            arcSize = box,
            width = stroke,
            intensity = 0.55f
        )

        // Yayın kendisi degradeyle çiziliyor: baş tarafı sönük, uç tarafı
        // parlak. Düz renkli bir yay hangi yöne gittiğini söylemiyor.
        val brush = Brush.sweepGradient(
            0.00f to color.copy(alpha = 0f),
            0.55f to color.copy(alpha = 0.55f),
            1.00f to color,
            center = center
        )
        drawArc(
            brush = brush,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Ucundaki ışık: yayın ilerleyen tarafı. Hareketin yönünü tek başına
        // bu söylüyor ve dönüş durduğunda (hareket kapalı) da yayın hangi
        // ucunun baş olduğu belli kalıyor.
        drawGlowDot(
            color = color,
            angleDegrees = start + sweep,
            radius = (box.minDimension / 2f),
            dotRadius = stroke * 0.62f
        )
    }
}

/** Yayın ucundaki hâleli nokta. */
private fun DrawScope.drawGlowDot(
    color: Color,
    angleDegrees: Float,
    radius: Float,
    dotRadius: Float
) {
    val radians = (angleDegrees * PI / 180.0).toFloat()
    val point = Offset(
        x = center.x + radius * kotlin.math.cos(radians),
        y = center.y + radius * kotlin.math.sin(radians)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0.35f), Color.Transparent),
            center = point,
            radius = dotRadius * 3f
        ),
        radius = dotRadius * 3f,
        center = point
    )
    drawCircle(color = color, radius = dotRadius, center = point)
}

/**
 * Uzun bir işin üstünü kaplayan cam katman.
 *
 * ### Neden ekranı kapatıyor
 *
 * Bir dışa aktarma ya da anahtar döndürme sürerken ekran hâlâ dokunulabilir
 * duruyordu: kullanıcı aynı düğmeye ikinci kez basabiliyor, sekme
 * değiştirebiliyordu. Görsel olarak da hiçbir şey olmuyor gibi görünüyordu,
 * çünkü iş arka planda ve ekranda karşılığı yok.
 *
 * Katman ikisini birden çözüyor: dokunuşları yutuyor ve olan bitene bir yer
 * veriyor.
 *
 * ### Etiket neden zorunlu
 *
 * "Bekleyin" demek hiçbir şey söylemiyor. Ne beklendiğini yazmak, beklemeyi
 * kısaltmıyor ama katlanılır yapıyor — ve iş uzarsa kullanıcı neyin
 * takıldığını biliyor.
 */
@Composable
fun LoadingOverlay(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Dokunuşlar burada duruyor: altındaki ekran, iş sürerken
            // ikinci bir kez tetiklenemiyor.
            .clickableNoRipple { },
        contentAlignment = Alignment.Center
    ) {
        GlassPlate(
            shape = RoundedCornerShape(KasaRadius.xl),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KasaLoader()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink2
                )
            }
        }
    }
}

/**
 * Henüz gelmemiş içeriğin yerini tutan iskelet.
 *
 * ### Neden boş ekran değil
 *
 * Kasa açıldıktan sonra liste bir anda beliriyor ve o ana kadar ekran boş
 * duruyor. Boş bir ekran iki farklı şeyi aynı anda söylüyor: "yükleniyor" ve
 * "hiçbir şey yok". İkincisi bir parola yöneticisinde ürkütücü bir cümle.
 *
 * İskelet ikisini ayırıyor: gelecek olanın **biçimi** görünüyor, içeriği
 * değil. Kullanıcı listeyi beklerken listenin nerede olacağını da öğreniyor,
 * yani içerik geldiğinde göz zaten doğru yerde.
 *
 * ### Parıltı neden soldan sağa
 *
 * Okuma yönüyle aynı. Ters yönde geçen bir parıltı, gözü içeriğin geleceği
 * yönün tersine çekiyor.
 */
@Composable
fun SkeletonRows(
    count: Int = 6,
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    // Delege ile okunmuyor: `by` her karede bu bileşeni yeniden bestelerdi ve
    // iskeletin tamamı — altı satır, on sekiz blok — kare başına yeniden
    // kurulurdu. Değer aşağıya işlev olarak iniyor ve yalnızca çizim
    // aşamasında açılıyor.
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonPhase"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(count) { index ->
            // Her satır parıltıyı biraz geciktirerek alıyor: hepsi aynı anda
            // parlasaydı liste tek bir blok gibi yanıp sönerdi.
            val rowPhase: () -> Float =
                if (reduced) NO_SHIMMER
                else { { phase.value - index * SKELETON_STAGGER } }
            SkeletonRow(position = groupPositionOf(index, count), phase = rowPhase)
        }
    }
}

/** Hareket kapalıyken parıltı hiç çizilmiyor. */
private val NO_SHIMMER: () -> Float = { -1f }

@Composable
private fun SkeletonRow(position: GroupPosition, phase: () -> Float) {
    val colors = KasaTheme.colors
    val shape = when (position) {
        GroupPosition.ONLY -> RoundedCornerShape(KasaRadius.l)
        GroupPosition.FIRST -> RoundedCornerShape(
            topStart = KasaRadius.l, topEnd = KasaRadius.l,
            bottomStart = KasaRadius.xs, bottomEnd = KasaRadius.xs
        )
        GroupPosition.LAST -> RoundedCornerShape(
            topStart = KasaRadius.xs, topEnd = KasaRadius.xs,
            bottomStart = KasaRadius.l, bottomEnd = KasaRadius.l
        )
        GroupPosition.MIDDLE -> RoundedCornerShape(KasaRadius.xs)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape, colors.tile, opacity = 0.62f, edge = 0.4f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBlock(Modifier.size(46.dp), phase, RoundedCornerShape(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.52f).height(13.dp), phase)
            SkeletonBlock(Modifier.fillMaxWidth(0.34f).height(11.dp), phase)
        }
        SkeletonBlock(Modifier.size(18.dp), phase, RoundedCornerShape(KasaRadius.full))
    }
}

/**
 * Tek bir gri blok ve üzerinden geçen parıltı.
 *
 * Parıltı bloğun kendi genişliğine göre değil, **ekran genişliğine** göre
 * ilerliyor gibi görünsün diye faz dışarıdan geliyor: her blok kendi
 * içinde ayrı bir parıltı çalıştırsaydı, dar bloklar geniş olanlardan çok
 * daha hızlı parlar ve satır dağılırdı.
 */
@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    phase: () -> Float,
    shape: RoundedCornerShape = RoundedCornerShape(KasaRadius.s)
) {
    val colors = KasaTheme.colors
    val base = colors.ink3.copy(alpha = if (colors.isDark) 0.16f else 0.13f)
    val highlight = colors.ink3.copy(alpha = if (colors.isDark) 0.30f else 0.24f)

    Box(
        modifier
            .clip(shape)
            // Zemin duruk, yalnızca parıltı hareketli. Faz burada açılıyor:
            // çizim aşaması yeniden besteleme tetiklemiyor.
            .background(base)
            .drawBehind {
                val travel = phase()
                if (travel < 0f) return@drawBehind
                drawRect(
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to highlight,
                        1f to Color.Transparent,
                        start = Offset(travel * SKELETON_TRAVEL - SKELETON_BAND, 0f),
                        end = Offset(travel * SKELETON_TRAVEL, 0f)
                    )
                )
            }
    )
}

/** Dönüş süresi. Daha hızlısı acele, daha yavaşı takılmış görünüyor. */
private const val SPIN_MILLIS = 1150

/** Yayın uzunluk döngüsü; dönüşün tam katı değil. */
private const val BREATH_MILLIS = 1870

private const val MIN_SWEEP = 42f
private const val MAX_SWEEP = 268f

/** Halkanın kalınlığı, çapın oranı olarak. */
private const val STROKE_FRACTION = 0.10f

private const val SKELETON_MILLIS = 1500

/** Parıltının kat ettiği piksel; ekran genişliğinden geniş tutuluyor. */
private const val SKELETON_TRAVEL = 1400f
private const val SKELETON_BAND = 420f

/** Satırlar arasındaki parıltı gecikmesi, döngünün oranı olarak. */
private const val SKELETON_STAGGER = 0.06f

/**
 * İskeletin yerini alan içeriğin sıralı belirişi.
 *
 * ### Neden
 *
 * İskelet listenin **biçimini** gösteriyor, sonra içerik geliyor. İçerik tek
 * karede belirdiğinde bu iki durum arasında hiçbir bağ olmuyor: iskelet
 * kayboluyor, liste beliriyor, ikisi ayrı iki olay gibi okunuyor. Sırayla
 * gelince olan şey tek bir olay — biçim doluyor.
 *
 * Aynı sebep menülerde de geçerli ve orada zaten uygulanıyordu; liste,
 * uygulamanın sıralı belirişi olmayan tek yeriydi.
 *
 * ### Neden yalnızca bir kez
 *
 * Kaydırırken görüş alanına giren her satır belirseydi liste sürekli
 * kıpırdayan bir şey olurdu ve okunması güçleşirdi. [play] yalnızca listenin
 * ilk dolduğu pencerede açık kalıyor; sonrasında satırlar yerinde duruyor.
 *
 * ### Neden basamak sayısı sınırlı
 *
 * Gecikme sıraya bağlı olsaydı yirminci satır yarım saniye beklerdi. Ekranda
 * zaten sekiz satır var; ötesini beklemek, kullanıcının hiç görmediği bir
 * şeyi geciktirmek olurdu.
 *
 * @param step satırın sırası.
 * @param play listenin ilk doluş penceresinde true.
 */
@Composable
fun Modifier.staggeredReveal(step: Int, play: Boolean): Modifier {
    val reduced = LocalReducedMotion.current
    val spec = KasaMotion.stagger<Float>(step.coerceAtMost(REVEAL_MAX_STEP))
    val reveal = remember { Animatable(if (play) 0f else 1f) }

    // Pencere kapandığında yarım kalmış bir beliriş tamamlanıyor. Aksi
    // hâlde animasyonu iptal edilen satır yarı saydam ve kaymış hâlde
    // donup kalırdı.
    LaunchedEffect(play) { if (play) reveal.animateTo(1f, spec) else reveal.snapTo(1f) }

    if (!play && reveal.value >= 1f) return this
    if (reduced) return this

    return graphicsLayer {
        val shown = reveal.value
        alpha = shown
        translationY = (1f - shown) * REVEAL_RISE.toPx()
    }
}

/** Satırların altından yükselme mesafesi. */
private val REVEAL_RISE = 12.dp

/** Gecikmenin durduğu sıra. Ekrandaki satır sayısının biraz üstü. */
private const val REVEAL_MAX_STEP = 9

/** Listenin ilk doluş penceresi: son basamak artı giriş süresi kadar. */
const val REVEAL_WINDOW_MILLIS = 620L
