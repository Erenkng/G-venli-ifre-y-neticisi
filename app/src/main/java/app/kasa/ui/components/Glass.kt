package app.kasa.ui.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
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
