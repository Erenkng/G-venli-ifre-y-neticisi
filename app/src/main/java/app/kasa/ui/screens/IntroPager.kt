package app.kasa.ui.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import app.kasa.core.util.Haptics
import app.kasa.core.util.rememberHapticPlayer
import app.kasa.R
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.theme.KasaTheme
import app.kasa.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kurulumdan önceki tanıtım.
 *
 * ### Neden var
 *
 * Uygulama daha önce ilk kareden itibaren "ana parolanı gir" diyordu. O ekran
 * kullanıcıdan hayatının en önemli parolalarından birini, hiçbir bağlam
 * vermeden, üç saniye içinde istiyordu. Sonucu tahmin edilebilir: acele
 * seçilmiş bir parola, ya da uygulamayı kapatıp bir daha açmamak.
 *
 * Üç sayfa, üç cümle. Anlatılan şey özellik listesi değil, üç karar:
 * kasa tek bir dosya, her tür kendi alanında, hiçbir şey cihazdan çıkmıyor.
 *
 * ### Neden kaydırmalı ve neden paralaks
 *
 * Sayfaların kendi hızıyla kayması, kullanıcının nerede olduğunu ve kaç
 * sayfa kaldığını dokunma hissinden anlamasını sağlıyor. Grafik ve yazı
 * **farklı hızlarda** kayıyor: grafik sayfadan yavaş, yazı sayfadan hızlı.
 * Tek hızda kayan bir sayfa düz bir slayt; farklı hız derinlik veriyor ve göz
 * grafiği yazının arkasında bir katman olarak okuyor.
 *
 * Hareket kapalıyken ([LocalReducedMotion]) grafikler durağan çiziliyor;
 * paralaks kalıyor çünkü o kaydırmanın kendisinin bir parçası, ayrı bir
 * animasyon değil.
 */
@Composable
fun IntroPager(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        IntroPage(R.string.intro_1_title, R.string.intro_1_body, IntroScene.VAULT),
        IntroPage(R.string.intro_2_title, R.string.intro_2_body, IntroScene.TYPES),
        IntroPage(R.string.intro_3_title, R.string.intro_3_body, IntroScene.OFFLINE)
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val onLastPage = pagerState.currentPage == pages.lastIndex

    // Sahnelerin ortak saati.
    //
    // Faz eskiden her sayfanın kendi içinde kuruluyordu: üç sayfa, üç sonsuz
    // animasyon ve ekranda görünmeyen ikisi de çalışıyordu. Tek bir saat hem
    // o iki animasyonu ortadan kaldırıyor hem de sahneleri birbirine
    // bağlıyor — kaydırırken iki sahne aynı anda görünüyor ve ayrı saatlerde
    // dönmeleri, kaymanın kendisini bozan bir gürültüydü.
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "intro")
    val animatedPhase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SCENE_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "introPhase"
    )
    // Hareket kapalıyken sabit bir kare: sahne durağan çiziliyor ama boş
    // kalmıyor.
    val phase: State<Float> =
        if (reduced) remember { mutableStateOf(0f) } else animatedPhase

    // Sayfa değişiminde bir yer değiştirme titreşimi. Düğmeye değil sayfanın
    // kendisine bağlı: kaydırarak geçmek de aynı olay ve düğmeye bağlansaydı
    // kullanıcının iki geçiş yolundan biri sessiz kalırdı.
    //
    // İlk değer atlanıyor; yoksa tanıtım ekranı, kullanıcı hiçbir şey
    // yapmadan açılırken titriyor.
    val play = rememberHapticPlayer()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { play(Haptics.Kind.NAV) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            // Sayfanın kendi konumundan uzaklığı: 0 tam ortada, ±1 tam dışarıda.
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        // Genişlik sınırı çizimin ekranı taşırmasını
                        // engelliyor. En-boy oranı sabit olduğu için geniş bir
                        // pencerede `fillMaxWidth` yüksekliği de ekranın
                        // ötesine taşıyor ve altındaki yazıyı ekran dışına
                        // itiyordu — tablette yatay tutulduğunda görülen şey
                        // buydu.
                        .widthIn(max = INTRO_ART_MAX)
                        .fillMaxWidth()
                        .aspectRatio(1.15f)
                ) {
                    // Çizim tek bir düzlem değil, iki.
                    //
                    // Grafik ve yazı zaten farklı hızlarda kayıyordu, yani iki
                    // derinlik vardı. Ama grafiğin kendisi tek parça olduğu
                    // için, içindeki her şey aynı uzaklıktaydı: halkalar,
                    // uydular ve kadran hep birlikte hareket ediyor ve
                    // sahne bir fotoğraf gibi düz okunuyordu.
                    //
                    // Şimdi her sahnenin uzak parçaları (halkalar, duvarlar,
                    // arkadaki kartlar) yakın parçalarından ayrı çiziliyor ve
                    // daha yavaş kayıyor. Aralarındaki hız farkı, gözün
                    // derinlik olarak okuduğu tek şey — ışık ya da gölge
                    // değil, **paralaks**.
                    IntroPlane(
                        scene = pages[page].scene,
                        plane = ScenePlane.FAR,
                        phase = phase,
                        offset = offset,
                        drift = PARALLAX_FAR,
                        fade = 0.7f
                    )
                    IntroPlane(
                        scene = pages[page].scene,
                        plane = ScenePlane.NEAR,
                        phase = phase,
                        offset = offset,
                        drift = PARALLAX_NEAR,
                        fade = 0.95f
                    )
                }

                Spacer(Modifier.height(40.dp))

                Column(
                    Modifier.graphicsLayer {
                        // Üç düzlemin en öndeki: sayfadan **hızlı** kayıyor.
                        translationX = offset * size.width * PARALLAX_TEXT
                        alpha = (1f - abs(offset) * 1.4f).coerceIn(0f, 1f)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(pages[page].title),
                        style = KasaTheme.text.hero,
                        color = KasaTheme.colors.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(pages[page].body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = KasaTheme.colors.ink2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        PageDots(
            count = pages.size,
            current = pagerState.currentPage,
            fraction = pagerState.currentPageOffsetFraction
        )

        Spacer(Modifier.height(24.dp))

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            KasaButton(
                text = stringResource(if (onLastPage) R.string.intro_start else R.string.intro_next),
                onClick = {
                    if (onLastPage) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            // Atlamak her sayfada duruyor ama son sayfada gizleniyor: orada
            // "Başla" ile aynı şeyi yapan iki düğme olurdu.
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                if (!onLastPage) {
                    KasaButton(
                        text = stringResource(R.string.intro_skip),
                        onClick = onFinish,
                        tone = ButtonTone.TEXT,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private data class IntroPage(val title: Int, val body: Int, val scene: IntroScene)

private enum class IntroScene { VAULT, TYPES, OFFLINE }

/**
 * Sayfa göstergesi.
 *
 * Etkin nokta uzun bir hap; geçiş sırasında iki nokta arasında **sürekli**
 * kayıyor, yani göstergenin kendisi de kaydırmanın nerede olduğunu söylüyor.
 * Sıçrayan bir gösterge, parmağın altındaki hareketle göstergeyi
 * ilişkilendirmeyi bozardı.
 */
@Composable
private fun PageDots(count: Int, current: Int, fraction: Float) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.outlineVariant
    val position = current - fraction

    Canvas(Modifier.height(10.dp).fillMaxWidth()) {
        val dot = 7.dp.toPx()
        val gap = 9.dp.toPx()
        val pillExtra = 14.dp.toPx()
        val totalWidth = count * dot + (count - 1) * gap + pillExtra
        var x = (size.width - totalWidth) / 2f

        repeat(count) { index ->
            // Etkin genişlik, göstergenin o noktaya olan yakınlığıyla orantılı.
            val nearness = (1f - abs(position - index)).coerceIn(0f, 1f)
            val width = dot + pillExtra * nearness
            drawRoundRect(
                color = lerpColor(idle, active, nearness),
                topLeft = Offset(x, (size.height - dot) / 2f),
                size = Size(width, dot),
                cornerRadius = CornerRadius(dot / 2f)
            )
            x += width + gap
        }
    }
}

private fun lerpColor(from: Color, to: Color, fraction: Float) = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction
)

/**
 * Sahnenin bir derinlik düzlemi.
 *
 * Üçü de elle çizilmiş; resim dosyası kullanılmamasının sebebi tema. Bu
 * çizimler tema renklerinden besleniyor ve karanlık temada kendiliğinden
 * doğru görünüyor — sabit bir PNG için iki ayrı dosya tutmak ve ikisini de
 * güncel kalmaya zorlamak gerekirdi.
 *
 * [drift] düzlemin sayfaya göre ne kadar geride kaldığını söylüyor: büyük
 * değer daha uzak. [fade] ise uzak düzlemin daha geç sönmesini sağlıyor —
 * yakındaki nesneler kadraja girip çıkarken uzaktaki manzara kalıcıdır.
 */
@Composable
private fun IntroPlane(
    scene: IntroScene,
    plane: ScenePlane,
    phase: State<Float>,
    offset: Float,
    drift: Float,
    fade: Float
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = KasaTheme.colors.ink
    val soft = KasaTheme.colors.ink3

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offset * size.width * drift
                alpha = (1f - abs(offset) * fade).coerceIn(0f, 1f)
            }
    ) {
        // Faz çizim aşamasında okunuyor. Bestede okunsaydı tanıtım ekranı
        // açık durduğu sürece — yani kullanıcının okuduğu bütün süre boyunca
        // — kare başına yeniden bestelenirdi; hem de kaydırma tam o sırada
        // oluyor.
        val t = phase.value
        when (scene) {
            IntroScene.VAULT -> drawVaultScene(t, plane, accent, ink, soft)
            IntroScene.TYPES -> drawTypesScene(t, plane, accent, ink, soft)
            IntroScene.OFFLINE -> drawOfflineScene(t, plane, accent, ink, soft)
        }
    }
}

/** Bir sahnenin iki derinliği. Uzak olan yavaş kayıyor, yakın olan hızlı. */
private enum class ScenePlane { FAR, NEAR }

/**
 * Birinci sayfa: kayıtlar kadranın içine akıyor.
 *
 * Anlatılan şey "kasa tek bir dosya": dışarıdaki noktalar merkeze doğru
 * çekiliyor ve orada kayboluyor. Kadran uygulamanın kendi işareti, yani
 * kullanıcı bu şekli birazdan simgede tekrar görecek.
 */
private fun DrawScope.drawVaultScene(
    t: Float,
    plane: ScenePlane,
    accent: Color,
    ink: Color,
    soft: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.21f

    if (plane == ScenePlane.FAR) {
        // Uzak: halkalar. Sahnenin durduğu yeri tarif ediyorlar ve yakın
        // düzlemden yavaş kaydıkları için mesafe onlardan okunuyor.
        //
        // Halkalar artık nefes de alıyor: her biri kendi gecikmesiyle
        // genişleyip daralıyor, yani sahne kaydırılmadığında da yaşıyor.
        repeat(3) { ring ->
            val swell = sin((t + ring * RING_LAG) * 2f * PI.toFloat())
            drawCircle(
                color = soft.copy(alpha = 0.14f - ring * 0.035f),
                radius = radius * (1.9f + ring * 0.72f) * (1f + RING_BREATH * swell),
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
        return
    }

    // Merkeze akan kayıtlar
    repeat(SATELLITES) { index ->
        val seed = index / SATELLITES.toFloat()
        val progress = ((t + seed) % 1f)
        // Sona doğru hızlanıyor: kasaya "çekiliyor" hissi.
        val eased = progress * progress
        val angle = (seed * 360f + index * 47f) * (PI / 180f).toFloat()
        val distance = radius * (3.3f - eased * 2.1f)
        val dotCenter = Offset(
            center.x + cos(angle) * distance,
            center.y + sin(angle) * distance
        )
        // Kayıt merkeze yaklaştıkça uzuyor: hızlanan bir nesnenin bıraktığı
        // iz. Sabit boyda bir dikdörtgen, hız değişimini yalnızca konumdan
        // okutuyordu.
        val stretch = 1f + eased * SATELLITE_STRETCH
        drawRoundRect(
            color = accent.copy(alpha = (1f - eased) * 0.55f),
            topLeft = dotCenter - Offset(radius * 0.16f * stretch, radius * 0.11f),
            size = Size(radius * 0.32f * stretch, radius * 0.22f),
            cornerRadius = CornerRadius(radius * 0.08f)
        )
    }

    // Kadran: uygulamanın simgesiyle aynı geometri, yavaşça dönüyor
    rotate(degrees = t * 360f * 0.25f, pivot = center) {
        drawCircle(
            color = accent,
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.28f)
        )
        repeat(3) { spoke ->
            rotate(degrees = 30f + spoke * 120f, pivot = center) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(center.x - radius * 0.12f, center.y - radius * 0.82f),
                    size = Size(radius * 0.24f, radius * 0.46f),
                    cornerRadius = CornerRadius(radius * 0.12f)
                )
            }
        }
        drawCircle(accent, radius * 0.38f, center)
        drawCircle(ink.copy(alpha = 0.001f), radius * 0.14f, center)
    }
}

/**
 * İkinci sayfa: türler yelpaze gibi açılıyor.
 *
 * Anlatılan şey "her tür kendi alanında": kartlar üst üste değil, hafifçe
 * açılmış duruyor ve her birinin kendi rengi var. Yelpaze nefes alıyor —
 * açılıp kapanıyor — çünkü durağan bir yığın "arşiv", hareketli bir yelpaze
 * "seçilebilir" anlamına geliyor.
 */
private fun DrawScope.drawTypesScene(
    t: Float,
    plane: ScenePlane,
    accent: Color,
    ink: Color,
    soft: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val cardWidth = size.minDimension * 0.46f
    val cardHeight = cardWidth * 0.63f
    val breathe = sin(t * 2f * PI.toFloat()) * 0.5f + 0.5f

    val tints = listOf(
        accent.copy(alpha = 0.28f),
        accent.copy(alpha = 0.46f),
        accent.copy(alpha = 0.72f),
        accent
    )

    if (plane == ScenePlane.FAR) {
        // Uzak düzlem yığının zeminini taşıyor: gölge çizgisi kartların bir
        // yere oturduğunu söyleyen tek şey ve yakın düzlemden yavaş kaydığı
        // için zemin gibi davranıyor.
        drawRoundRect(
            color = ink.copy(alpha = 0.06f),
            topLeft = Offset(center.x - cardWidth * 0.62f, center.y + cardHeight * 0.86f),
            size = Size(cardWidth * 1.24f, cardHeight * 0.08f),
            cornerRadius = CornerRadius(cardHeight * 0.04f)
        )
        drawCircle(soft.copy(alpha = 0.001f), 1f, center)
    }

    // Yelpazenin kartları iki düzleme bölünüyor: arkadakiler uzakta,
    // öndeki yakında. Kaydırırken aradaki hız farkı yelpazeyi gerçekten
    // açıyor — tek düzlemde çizildiğinde yığın, üst üste konmuş düz
    // dikdörtgenlerden ibaretti.
    val front = tints.lastIndex
    tints.forEachIndexed { index, color ->
        val isFront = index == front
        if (isFront != (plane == ScenePlane.NEAR)) return@forEachIndexed

        val depth = index - (tints.size - 1) / 2f
        val spread = 1f + breathe * 0.55f
        translate(
            left = depth * cardWidth * 0.16f * spread,
            top = -depth * cardHeight * 0.2f * spread
        ) {
            rotate(degrees = depth * 7f * spread, pivot = center) {
                drawRoundRect(
                    color = color,
                    topLeft = center - Offset(cardWidth / 2f, cardHeight / 2f),
                    size = Size(cardWidth, cardHeight),
                    cornerRadius = CornerRadius(cardHeight * 0.18f)
                )
                // En öndeki kartta içerik ipucu: yonga ve iki satır
                if (isFront) {
                    val pad = cardWidth * 0.11f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = center - Offset(cardWidth / 2f - pad, cardHeight / 2f - pad),
                        size = Size(cardWidth * 0.2f, cardHeight * 0.2f),
                        cornerRadius = CornerRadius(cardWidth * 0.03f)
                    )
                    repeat(2) { line ->
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.7f - line * 0.28f),
                            topLeft = Offset(
                                center.x - cardWidth / 2f + pad,
                                center.y + cardHeight * (0.02f + line * 0.18f)
                            ),
                            size = Size(cardWidth * (0.62f - line * 0.26f), cardHeight * 0.09f),
                            cornerRadius = CornerRadius(cardHeight * 0.045f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Üçüncü sayfa: giden bir şey yok.
 *
 * Telefonun içinden dışarı doğru çıkan noktalar bir duvara çarpıp geri
 * dönüyor. Anlatım olumsuz bir şeyi (veri gönderilmiyor) gösteriyor ve bunu
 * göstermenin tek dürüst yolu hareketin **durdurulduğunu** göstermek — boş
 * bir ekran "hiçbir şey gitmiyor" demiyor, yalnızca hiçbir şey söylemiyor.
 */
private fun DrawScope.drawOfflineScene(
    t: Float,
    plane: ScenePlane,
    accent: Color,
    ink: Color,
    soft: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val phoneWidth = size.minDimension * 0.34f
    val phoneHeight = phoneWidth * 1.9f
    val wall = phoneWidth * 1.32f

    if (plane == ScenePlane.FAR) {
        // Uzak: duvar. Telefonun iki yanında dikey kesikli hat.
        //
        // Duvarın uzak düzlemde olması anlatımın da parçası: sınır telefonun
        // bir özelliği değil, telefonun içinde bulunduğu dünyanın sınırı.
        listOf(-1f, 1f).forEach { side ->
            var y = center.y - phoneHeight * 0.62f
            val end = center.y + phoneHeight * 0.62f
            val dash = phoneHeight * 0.07f
            while (y < end) {
                drawRoundRect(
                    color = soft.copy(alpha = 0.3f),
                    topLeft = Offset(center.x + side * wall - 1.2.dp.toPx(), y),
                    size = Size(2.4.dp.toPx(), dash),
                    cornerRadius = CornerRadius(1.2.dp.toPx())
                )
                y += dash * 2f
            }
        }
        return
    }

    // Duvara çarpıp geri dönen noktalar
    repeat(BOUNCERS) { index ->
        val seed = index / BOUNCERS.toFloat()
        val progress = ((t * 1.3f + seed) % 1f)
        // Gidiş-dönüş: yarıya kadar dışarı, sonra geri.
        val travel = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
        val side = if (index % 2 == 0) 1f else -1f
        val y = center.y + (seed - 0.5f) * phoneHeight * 0.9f
        val x = center.x + side * (phoneWidth * 0.5f + travel * (wall - phoneWidth * 0.5f))
        val hitting = travel > 0.92f
        drawCircle(
            color = if (hitting) accent.copy(alpha = 0.9f) else accent.copy(alpha = 0.4f),
            radius = phoneWidth * (if (hitting) 0.075f else 0.05f),
            center = Offset(x, y)
        )
        // Çarpma anında duvarda bir halka: nokta geri dönüyor değil,
        // **durduruluyor**. Sahnenin anlattığı şey bu ve önceden yalnızca
        // noktanın büyümesinden okunuyordu.
        if (hitting) {
            val bloom = (travel - 0.92f) / 0.08f
            drawCircle(
                color = accent.copy(alpha = 0.35f * (1f - bloom)),
                radius = phoneWidth * (0.075f + 0.16f * bloom),
                center = Offset(center.x + side * wall, y),
                style = Stroke(width = 1.6.dp.toPx())
            )
        }
    }

    // Telefon
    drawRoundRect(
        color = ink.copy(alpha = 0.9f),
        topLeft = center - Offset(phoneWidth / 2f, phoneHeight / 2f),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = CornerRadius(phoneWidth * 0.19f)
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.22f),
        topLeft = center - Offset(phoneWidth / 2f - phoneWidth * 0.07f, phoneHeight / 2f - phoneWidth * 0.07f),
        size = Size(phoneWidth * 0.86f, phoneHeight - phoneWidth * 0.14f),
        cornerRadius = CornerRadius(phoneWidth * 0.13f)
    )

    // İçindeki kalkan
    val shieldWidth = phoneWidth * 0.46f
    val shieldTop = center.y - shieldWidth * 0.6f
    drawRoundRect(
        color = accent,
        topLeft = Offset(center.x - shieldWidth / 2f, shieldTop),
        size = Size(shieldWidth, shieldWidth * 0.78f),
        cornerRadius = CornerRadius(shieldWidth * 0.16f)
    )
    drawCircle(
        color = accent,
        radius = shieldWidth * 0.3f,
        center = Offset(center.x, shieldTop + shieldWidth * 0.78f)
    )
}

/**
 * Düzlemlerin sayfaya göre kayma oranları.
 *
 * Büyük değer daha uzak: sayfadan yavaş kayan şey geride duruyor. Negatif
 * değer sayfadan hızlı, yani en önde. Üçünün arası eşit değil — uzak ile
 * yakın arasındaki fark, yakın ile yazı arasındakinden büyük, çünkü
 * derinlik algısı orana değil farka bakıyor ve eşit aralık üç ayrı düzlemi
 * tek bir eğime çeviriyordu.
 */
private const val PARALLAX_FAR = 0.62f
private const val PARALLAX_NEAR = 0.30f
private const val PARALLAX_TEXT = -0.22f

/** Halkaların nefes payı ve aralarındaki gecikme. */
private const val RING_BREATH = 0.035f
private const val RING_LAG = 0.18f

/** Kaydın merkeze yaklaşırken ne kadar uzayacağı. */
private const val SATELLITE_STRETCH = 0.9f

/** Uydu sayısı: az olsa seyrek, çok olsa gürültülü görünüyor. */
private const val SATELLITES = 7
private const val BOUNCERS = 6
private const val SCENE_CYCLE_MILLIS = 5200

/**
 * Tanıtım çiziminin en geniş hâli.
 *
 * Telefonda bu sınıra hiç ulaşılmıyor (ekran zaten dar); iş gördüğü yer geniş
 * pencereler.
 */
private val INTRO_ART_MAX = 340.dp
