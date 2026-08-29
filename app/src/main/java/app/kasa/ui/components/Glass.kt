package app.kasa.ui.components

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaTheme

/**
 * Android 17'nin buzlu cam yüzeyleri.
 *
 * ### Tasarım dili
 *
 * Android 16 ve 17 boyunca sistem arayüzü derinliği **gölgeyle değil
 * bulanıklıkla** anlatmaya geçti: ses paneli, güç menüsü, hızlı ayarlar,
 * başlatıcı menüleri — hepsi arkasındakini bulanıklaştıran, üzerine renk
 * çalan yarı saydam bir levhanın üstünde duruyor. Google'ın gerekçesi şu:
 * bulanıklık "bir derinlik hissi yaratıyor, böylece hareket hafif hissediyor
 * ve arka planda kullandığın uygulamalardan haberdar kalıyorsun."
 *
 * Gölge "bu yükseltilmiş" diyor. Bulanıklık "arkasında bir şey **var** ve
 * hâlâ orada" diyor. Aradaki fark bu tasarım dilinin tamamı.
 *
 * ### Apple'ın Liquid Glass'ından farkı
 *
 * Liquid Glass arkasındakini **kırıyor**: yüzey bir mercek gibi davranıyor,
 * kenarlarında renk ayrışması ve büyütme var. Android'in yüzeyi buzlu cam:
 * bulanıklaştırıyor ve renk çalıyor, **çarpıtmıyor**. Buraya kırılma ya da
 * "sıvı" parlama eklemek, ötekinin sözlüğünü konuşmak olurdu.
 *
 * ### İki mekanizma
 *
 * Uygulamanın kendi penceresi içindeki yüzeyler (gezinme çubuğu) ekranın
 * kaydedilmiş bir kopyasını bulanıklaştırıyor — o yol `Navigation.kt` içinde
 * ve dokunulmadı.
 *
 * Ayrı pencerede açılan yüzeyler (pencereler, alt sayfalar) o kopyayı
 * göremiyor: başka bir yüzeye çiziliyorlar. Onlar için sistemin kendi
 * mekanizması var — `FLAG_BLUR_BEHIND` — ve sistem güç menüsünü de tam olarak
 * bununla bulanıklaştırıyor. Arkasındaki her şeyi bulanıklaştırıyor:
 * uygulamanın kendisini de, altındaki başka uygulamayı da.
 */

/**
 * Bu pencerenin arkasını bulanıklaştırır.
 *
 * ### Neden kapatılabilir olduğunu varsaymak zorundayız
 *
 * Pencere bulanıklığı bir **ayrıcalık değil, istek**: sistem onu pil
 * tasarrufunda, düşük güçlü cihazlarda ve geliştirici seçeneklerinden kapatır.
 * `isCrossWindowBlurEnabled` o anki durumu söylüyor ve durum çalışma
 * sırasında değişebiliyor.
 *
 * Bu yüzden bulanıklık **tek başına** okunabilirliği taşımıyor: kapalıyken
 * yüzeyin kendi rengi ve gölgesi zaten yeterli kontrastı veriyor, bulanıklık
 * yalnızca derinlik ekliyor. Tersi kurulsaydı — okunabilirlik bulanıklığa
 * bağlı olsaydı — pil tasarrufuna geçen kullanıcının penceresi okunamaz
 * hâle gelirdi.
 *
 * @param radius bulanıklık yarıçapı; 0 ise bayrak hiç eklenmiyor
 * @param dimAmount arkadaki içeriğin ne kadar karartılacağı
 */
@Composable
fun DialogBlurBehind(radius: Dp = DIALOG_BLUR, dimAmount: Float = 0.32f) {
    val view = LocalView.current
    val density = LocalDensity.current
    val context = LocalContext.current

    DisposableEffect(view, radius, dimAmount) {
        // Compose penceresi bir DialogWindowProvider üzerinden geliyor;
        // değilse bu bileşen ana pencerede çağrılmış demektir ve orada
        // pencere bayrağı ayarlamak bütün uygulamayı bulanıklaştırırdı.
        val provider = view.parent as? DialogWindowProvider
            ?: return@DisposableEffect onDispose { }
        val window = provider.window

        val blurEnabled = runCatching {
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        }.getOrDefault(false)

        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(
                // Bulanıklık yoksa karartma biraz daha güçlü: derinliği
                // taşıyan tek şey o kalıyor.
                if (blurEnabled) dimAmount else (dimAmount + 0.14f).coerceAtMost(0.6f)
            )
            if (blurEnabled && radius > 0.dp) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = with(density) { radius.roundToPx() }
                }
            }
        }
        onDispose { }
    }
}

/**
 * Buzlu cam levha.
 *
 * ### Neden renk katmanı şart
 *
 * Yalnızca bulanıklaştırılmış bir arka plan, altındaki içerik alacalıysa
 * çamura dönüyor: bulanıklık renkleri karıştırıyor ama ortalama parlaklığı
 * korumuyor, dolayısıyla üstüne gelen metnin kontrastı önceden kestirilemez
 * oluyor. Üzerine çalınan renk, zemini **öngörülebilir** kılıyor; asıl işi
 * güzellik değil, okunabilirliği garanti altına almak.
 *
 * Renk üstten alta hafifçe açılıyor. Düz bir renk katmanı, bulanıklığın
 * yarattığı derinliği tekrar düzleştiriyordu: cam bir yüzeyin üstü ile altı
 * asla aynı miktarda ışık almıyor.
 *
 * ### Kenar çizgisi
 *
 * Çok ince ve çok soluk bir kenar. Yarı saydam bir levhanın nerede bittiği,
 * arkasındaki içerik açık renkliyse belirsizleşiyor; kenar çizgisi sınırı
 * geri veriyor ve gölgenin yapamadığı şeyi yapıyor — çünkü gölge de aynı
 * açık zeminde kayboluyor.
 */
@Composable
fun GlassPlate(
    shape: Shape,
    modifier: Modifier = Modifier,
    /** Levhanın rengi; verilmezse temanın en alt yüzey rolü. */
    tint: Color = KasaTheme.colors.card,
    /** Ne kadar geçirgen: 1 tamamen opak, 0.7 civarı camsı. */
    opacity: Float = GLASS_OPACITY,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = KasaTheme.colors
    val brush = remember(tint, opacity, colors.isDark) {
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = (opacity + 0.06f).coerceAtMost(1f)),
                tint.copy(alpha = opacity),
                tint.copy(alpha = (opacity - 0.04f).coerceAtLeast(0f))
            )
        )
    }
    val edge = remember(colors.isDark) {
        if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(GLASS_EDGE, edge, shape),
        content = content
    )
}

/**
 * Tam ekran bulanık örtü.
 *
 * Arama gibi ekranı kaplayan yüzeylerin altında duruyor: altındaki liste
 * görünür kalıyor ama okunmuyor, yani "geri dönebilirsin" bilgisi
 * kayboluyor. Düz bir örtü bunu da götürürdü.
 */
@Composable
fun GlassScrim(
    modifier: Modifier = Modifier,
    tint: Color = KasaTheme.colors.card,
    opacity: Float = SCRIM_OPACITY
) {
    Box(
        modifier
            .fillMaxSize()
            .background(tint.copy(alpha = opacity))
    )
}

/**
 * Alt sayfa için pencere bulanıklığı.
 *
 * Karartma sıfır: Material'ın kendi örtüsü (scrim) zaten sayfanın arkasını
 * karartıyor ve ikinci bir karartma eklemek altındaki ekranı tamamen
 * yutuyordu — o zaman da bulanıklığın gösterecek bir şeyi kalmıyor.
 *
 * Yarıçap pencerelerinkinden düşük. Alt sayfa ekranın bir kısmını kaplıyor ve
 * kenarından görünen içerik kullanıcının nereye döneceğini söylüyor; çok güçlü
 * bir bulanıklık o bilgiyi de siliyor.
 */
@Composable
fun SheetBlurBehind() {
    DialogBlurBehind(radius = SHEET_BLUR, dimAmount = 0f)
}

private val SHEET_BLUR = 26.dp

/** Pencere arkası bulanıklık yarıçapı: sistem güç menüsüne yakın. */
private val DIALOG_BLUR = 38.dp

/** Levhanın kenar çizgisi; kalınlaşırsa cam değil çerçeve olur. */
private val GLASS_EDGE = 0.8.dp

/**
 * Levhanın örtücülüğü.
 *
 * Bundan düşüğü metni okunmaz yapıyor, yükseği camı opak bir yüzeye
 * çeviriyor ve bulanıklığa ödenen bedel boşa gidiyor.
 */
private const val GLASS_OPACITY = 0.82f
private const val SCRIM_OPACITY = 0.72f

/**
 * Cam yüzey malzemesi.
 *
 * ### Neden düz renk yetmiyor
 *
 * Uygulamanın zemini üç radyal duraklı, günün saatine göre kayan bir gradyan.
 * Üzerindeki her satır, her kart, her yonga o zemini **tamamen** kapatıyordu:
 * arkada özenle kurulmuş bir renk geçişi vardı ve kullanıcı onun yalnızca
 * kenar boşluklarını görüyordu. Zemin bir arka plan resmi değil, derinliğin
 * kendisi; yüzeylerin ondan haberdar olması gerekiyor.
 *
 * Yüzeyi geçirgen yapmak bunu çözüyor: listenin üst tarafındaki satır zeminin
 * jade ucundan, alt tarafındaki mavi ucundan bir tutam alıyor. Fark çok küçük
 * ve tek tek bakınca görünmüyor — ama liste kayarken yüzeylerin zeminin
 * üstünde **gezindiği** hissediliyor, çünkü gerçekten öyle oluyor.
 *
 * ### Dört özellik
 *
 * Bir cam yüzeyi cam yapan şey saydamlık değil, dördünün birlikte olması:
 *
 * 1. **Geçirgenlik** — arkasındaki zeminin rengini geçiriyor.
 * 2. **Ton geçişi** — üstü altından biraz daha aydınlık. Düz bir renk
 *    katmanı, geçirgenliğin kurduğu derinliği tekrar düzleştiriyor; gerçek bir
 *    levhanın üstü ile altı asla aynı ışığı almıyor.
 * 3. **Kenar ışığı** — üst kenar parlak, alt kenar sönük. Işığın yukarıdan
 *    geldiğini söyleyen tek işaret bu ve yüzeyin kalınlığı buradan okunuyor.
 * 4. **Sınır** — yarı saydam bir levhanın nerede bittiği, arkasındaki zemin
 *    açık renkliyse belirsizleşiyor. Kenar ışığı aynı zamanda o sınırı
 *    veriyor; gölge veremiyor, çünkü gölge de aynı açık zeminde kayboluyor.
 *
 * ### Okunabilirlik geçirgenliğe bağlı değil
 *
 * Örtücülük bilerek yüksek: metnin kontrastı zeminin o anki rengine
 * bırakılmıyor. Geçirgenlik derinlik ekliyor, kontrast taşımıyor — tersi
 * kurulsaydı sabah açık, gece koyu bir zeminde aynı yazı iki farklı
 * okunabilirlikte olurdu.
 *
 * @param shape yüzeyin biçimi; kırpma ve sınır aynı biçimi kullanıyor
 * @param tint yüzeyin kendi rengi (`colors.tile`, `colors.card`, …)
 * @param opacity örtücülük; küçük yazı taşıyan yüzeylerde düşürülmemeli
 */
fun Modifier.glassSurface(
    shape: Shape,
    tint: Color,
    opacity: Float = SURFACE_OPACITY,
    /**
     * Kenar ışığının gücü.
     *
     * Küçük yüzeylerde (yonga, rozet) düşürülüyor: aynı kalınlıktaki bir
     * ışık, küçük bir yüzeyin alanının görünür bir kısmını kaplıyor ve levha
     * değil çerçeve gibi duruyor.
     */
    edge: Float = 1f
): Modifier = composed {
    val colors = KasaTheme.colors
    val fill = remember(tint, opacity) {
        Brush.verticalGradient(
            0f to tint.copy(alpha = (opacity + 0.07f).coerceIn(0f, 1f)),
            0.55f to tint.copy(alpha = opacity.coerceIn(0f, 1f)),
            1f to tint.copy(alpha = (opacity - 0.05f).coerceIn(0f, 1f))
        )
    }
    val rim = remember(colors.isDark, edge) {
        // Karanlıkta ışık beyaz kalıyor ama çok kısılıyor: koyu bir yüzeyde
        // aynı beyaz, aydınlıktakinin birkaç katı kadar göze çarpıyor.
        val top = if (colors.isDark) 0.14f else 0.80f
        val bottom = if (colors.isDark) 0.02f else 0.10f
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = top * edge),
            0.5f to Color.White.copy(alpha = (top * 0.32f) * edge),
            1f to Color.White.copy(alpha = bottom * edge)
        )
    }

    this
        .clip(shape)
        .background(fill)
        .border(SURFACE_EDGE, rim, shape)
}

/**
 * Kaydedilmiş ekran kopyasının bu alana düşen parçasını bulanıklaştırır.
 *
 * ### Neden kopya üzerinden
 *
 * Bir yüzeyin arkasındaki içeriği bulanıklaştırmanın doğrudan yolu yok:
 * çizim sırası tek yönlü, üstteki yüzey altındakine bakamıyor. Ekran bir kez
 * çizilirken aynı anda bir katmana kaydediliyor; bu bileşen o kaydı kendi
 * altına düşen parçası için yeniden çizip bulanıklaştırıyor. İçerik iki kez
 * **bestelenmiyor**, yalnızca bir kez daha kopyalanıyor.
 *
 * ### Konum kökten alınıyor
 *
 * Kopya kök düzenin (0,0) noktasından başlıyor; bu bileşen ekranın herhangi
 * bir yerinde olabilir. Doğru parçayı göstermek için kökteki konumu kadar
 * ters yöne kaydırmak gerekiyor — `positionInParent` burada sessizce yanlış
 * sonuç veriyor, çünkü bileşen kabını doldurduğunda o değer her zaman sıfır.
 *
 * ### Yarıçap sabit, güç saydamlıkta
 *
 * Açılış animasyonu boyunca yarıçapı büyütmek, her karede bulanıklığın
 * yeniden hesaplanması demek. Yarıçap sabit tutulup katmanın saydamlığı
 * animasyonlanıyor: aynı görüntü, kare başına tek bir çarpım.
 */
@Composable
fun BackdropBlur(
    backdrop: GraphicsLayer?,
    modifier: Modifier = Modifier,
    radius: Dp = BACKDROP_BLUR,
    strength: Float = 1f,
    /**
     * Alt kenarda bulanıklığın söndüğü bölge, yüksekliğin oranı olarak.
     *
     * Sert bir kenar, bulanık yüzeyi içeriğin üstüne yapıştırılmış bir şerit
     * gibi gösteriyor. Sönerek biten kenar, altındaki içeriğin devam ettiğini
     * söylüyor — ki gerçek olan da bu.
     *
     * Maske ayrı bir geçişte, [BlendMode.DstIn] ile uygulanıyor: aynı geçişte
     * çizilseydi kendi kendini maskelerdi.
     */
    fadeBottom: Float = 0f
) {
    if (backdrop == null || strength <= 0.01f) return

    val blurLayer = rememberGraphicsLayer()
    val blurRadius = with(LocalDensity.current) { radius.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = strength.coerceIn(0f, 1f)
            }
            .drawBehind {
                // Katman kaydı beste dağıtılırken serbest bırakılmış olabilir;
                // o durumda bu geçiş sessizce atlanıyor, ekran kaybolmuyor.
                runCatching {
                    blurLayer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                    blurLayer.clip = true
                    blurLayer.record {
                        translate(left = -origin.x, top = -origin.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)

                    if (fadeBottom > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                (1f - fadeBottom).coerceIn(0f, 1f) to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
            }
    )
}

/**
 * Menü açıkken ekranı buzlayan örtü.
 *
 * ### Neden karartma tek başına yetmiyordu
 *
 * Eski örtü düz bir koyu katmandı ve tek işi menünün altındaki her şeyi
 * eşit ölçüde soldurmaktı. Altındaki liste hâlâ **okunabiliyordu**: gözü
 * çeken şey menü değil, arkasındaki yarı görünür on iki satırdı.
 *
 * Bulanıklık ikisini birden yapıyor. Altındaki içerik tanınabilir kalıyor —
 * kullanıcı nereye döneceğini biliyor — ama okunamıyor, dolayısıyla dikkat
 * için yarışmıyor. Sistem güç menüsü de tam olarak bunu yapıyor ve bir
 * uygulamanın kendi menüsünün ondan farklı davranması için sebep yok.
 *
 * Karartma yine de duruyor, yalnızca daha hafif: bulanıklığın kapalı olduğu
 * durumda (kaydedilecek içerik yokken) ayrımı taşıyan tek şey o kalıyor.
 */
@Composable
fun GlassBackdropScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: GraphicsLayer?,
    modifier: Modifier = Modifier,
    /**
     * Bulanıklığın alttan ne kadarını boş bırakacağı.
     *
     * Kaydedilmiş kopya gezinme çubuğunu **içermiyor** — çubuk kayıttan sonra
     * çiziliyor. O kopyayı çubuğun üstüne tam güçte çizmek, çubuğun yerine
     * altındaki listenin bulanık hâlini koymak, yani çubuğu görünmez yapmak
     * demek. Bulanıklık çubuğun üstünde bitiyor; karartma bütün ekranı
     * kaplamaya devam ediyor, çünkü onun çubuğu gizlemek gibi bir etkisi yok.
     */
    blurBottomInset: Dp = 0.dp
) {
    // İki belirteç de koşulsuz okunuyor: KasaMotion sistem ayarını
    // CompositionLocal'dan aldığı için yalnızca beste içinde çağrılabiliyor ve
    // koşula bağlı çağırmak, açılış ile kapanış arasında beste ağacının
    // biçimini değiştirirdi.
    val enterSpec = KasaMotion.enter<Float>()
    val exitSpec = KasaMotion.exit<Float>()
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) enterSpec else exitSpec,
        label = "glassScrim"
    )

    if (progress > 0.01f) {
        Box(
            modifier.then(
                if (visible) Modifier.clickableNoRipple(onClick = onDismiss) else Modifier
            )
        ) {
            BackdropBlur(
                backdrop = backdrop,
                modifier = Modifier.matchParentSize().padding(bottom = blurBottomInset),
                strength = progress,
                // Alt kenar sert bitmesin: çubuğun kendi degradesi orada
                // saydam başlıyor ve sert bir kenar iki cam arasında bir
                // dikiş gibi görünüyordu.
                fadeBottom = SCRIM_FADE
            )
            Box(Modifier.matchParentSize().background(SCRIM_TINT.copy(alpha = SCRIM_DIM * progress)))
        }
    }
}

/** Örtü rengi: iki temada da koyu, çünkü işi ışığı azaltmak. */
private val SCRIM_TINT = Color(0xFF09201B)

/**
 * Örtünün karartması.
 *
 * Eskisinin (0.34) altında: ayrımın bir kısmını artık bulanıklık taşıyor ve
 * ikisi tam güçte üst üste gelince altındaki ekran tamamen yok oluyordu — o
 * zaman da bulanıklığın gösterecek bir şeyi kalmıyor.
 */
private const val SCRIM_DIM = 0.22f

/** Örtü bulanıklığının alt kenarındaki yumuşama bölgesi. */
private const val SCRIM_FADE = 0.06f

/**
 * Menü örtüsünün bulanıklık yarıçapı.
 *
 * Gezinme çubuğununkinden yüksek: orada bulanıklık ince bir şeridin altında
 * duruyor ve içeriğin devam ettiğini göstermesi gerekiyor. Burada bütün ekranı
 * kaplıyor ve işi okunabilirliği **bitirmek**.
 */
private val BACKDROP_BLUR = 30.dp

/**
 * İçerik yüzeylerinin örtücülüğü.
 *
 * Bundan düşüğü küçük yazının kontrastını zeminin o anki rengine bırakıyor;
 * yükseği zemini tamamen kapatıyor ve geçirgenliğe ödenen bedel boşa gidiyor.
 */
const val SURFACE_OPACITY = 0.88f

/** Yüzeyin kenar ışığı; kalınlaşırsa cam değil çerçeve olur. */
private val SURFACE_EDGE = 1.dp
