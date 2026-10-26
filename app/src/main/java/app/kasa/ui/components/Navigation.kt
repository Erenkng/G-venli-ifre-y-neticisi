package app.kasa.ui.components

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

data class NavDestination(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Havada asılı duran pill biçiminde alt gezinme çubuğu.
 *
 * ### Buraya nasıl gelindi
 *
 * İki tur önce burada dört kutu vardı (simge + etiket + seçilinin arkasında
 * açılan hap) — iyi işlenmiş ama her Android uygulamasıyla aynı. Sonra
 * kelimelerle gezinen, kenardan kenara uzanan tipografik bir raya çevrildi:
 * tez tutarlıydı, cihazda kötü çıktı. İki ders:
 *
 *  - Simgeyi kaldırmak dokunma hedefini de küçültüyor. Kelime kendi
 *    diliminin ortasında duran ince bir şerit; parmak ise ekranın altında
 *    kabaca nişan alıyor.
 *  - Kenardan kenara uzanan bir çubuk, ekranın altını **kapatıyor**. Havada
 *    duran bir pill ise altından içerik geçiyor ve çubuk bir nesne gibi
 *    okunuyor.
 *
 * Şimdiki hâli: kenarlardan boşluklu, tamamen yuvarlatılmış, yükseltilmiş
 * bir [Surface]. Simgeler geri geldi ve her hedef 64dp yüksekliğinde bir
 * dokunma alanı kaplıyor.
 *
 * ### Camın nereye gittiği
 *
 * Kenardan kenara giden çubuk iki kademeli bir blur bandı taşıyordu ve o
 * bandın varlık sebebi "çubuğun altına giren içerik okunmasın, ama
 * hareketi görünsün"dü. Havada duran bir pillin altına içerik girmiyor —
 * yanından geçiyor. Bu yüzden pill opak bir yüzey; blur makinesi durum
 * çubuğu örtüsünde ve açılan sayfalarda kullanılmaya devam ediyor, yani
 * cam dili kalkmadı, ait olduğu yere çekildi.
 */
@Composable
fun KasaNavBar(
    destinations: List<NavDestination>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
    contentBehind: Boolean = true
) {
    // İmza korunuyor: çağıran taraf hâlâ kaydedilmiş kopyayı ve "altında
    // içerik var mı" bilgisini veriyor. Pill ikisini de kullanmıyor ama
    // imzayı daraltmak, blur bandına dönmek istendiğinde çağıranı da
    // değiştirmeyi gerektirirdi.
    @Suppress("UNUSED_PARAMETER") val unusedBackdrop = backdrop
    @Suppress("UNUSED_PARAMETER") val unusedContentBehind = contentBehind

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(KasaRadius.full),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = KasaTheme.colors.ink,
            // Yükseltme pillin havada durmasını söyleyen şey: gölge olmadan
            // ekranın altına yapıştırılmış bir şerit gibi okunuyor.
            shadowElevation = 10.dp,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                destinations.forEach { destination ->
                    NavItem(
                        destination = destination,
                        selected = destination.key == selected,
                        onClick = { onSelect(destination.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Gezinme yüzeyinin altındaki bulanık arka plan.
 *
 * ### Bulanıklık kademeli
 *
 * Tek bir bulanıklık yarıçapını saydamlıkla söndürmek yetmiyor. Yarı saydam
 * bir noktada ekranda **iki görüntü üst üste** duruyor: net içerik ve 24dp
 * bulanıklaştırılmış kopyası. Göz bunu yumuşak bir geçiş olarak değil, hayalet
 * bir çift görüntü olarak okuyor. Buzlu camın gerçekte yaptığı şey saydamlığını
 * değil **kalınlığını** değiştirmek.
 *
 * Bu yüzden iki geçiş var. İçeriğe yakın kenarda ince bir bulanıklık
 * ([BLUR_NEAR]), yüzeyin derinlerinde kalın olan ([BLUR_FAR]); maskeleri
 * birbirinin içine geçiyor, yani bulanıklık kademe kademe artıyor. İkisi de
 * kenardan sıfırla başlıyor — biri sıfırdan başlamasaydı gidermeye çalıştığımız
 * keskin çizgi geri gelirdi.
 *
 * ### Neden her geçiş kendi katmanında
 *
 * Bulanık görüntü maskelenmek zorunda ve maske ancak kendi çevrimdışı
 * katmanında [BlendMode.DstIn] ile uygulanabiliyor; aynı katmanda çizilseydi
 * maske gezinme simgelerini de silerdi. İki geçiş üst üste sıradan biçimde
 * (SrcOver) besteleniyor.
 *
 * @param gradientStart maskenin içerik tarafındaki ucu (bulanıklığın olmadığı)
 * @param gradientEnd maskenin yüzey tarafındaki ucu (bulanıklığın tam olduğu)
 */
@Composable
private fun Backdrop(
    backdrop: GraphicsLayer?,
    modifier: Modifier,
    gradientStart: (Size) -> Offset,
    gradientEnd: (Size) -> Offset,
    strength: Float = 1f,
    radiusNear: Dp = BLUR_NEAR,
    radiusFar: Dp = BLUR_FAR
) {
    if (backdrop == null) return
    if (strength <= 0.01f) return

    Box(modifier) {
        // Sıra önemli: kalın olan altta, ince olan üstünde. Üstteki kenara
        // yakın yerde opak olduğu için orada ince bulanıklık görünüyor;
        // saydamlaştığı yerde alttaki kalın bulanıklık ortaya çıkıyor.
        BlurBand(backdrop, Modifier.matchParentSize(), radiusFar, FAR_STOPS, gradientStart, gradientEnd, strength)
        BlurBand(backdrop, Modifier.matchParentSize(), radiusNear, NEAR_STOPS, gradientStart, gradientEnd, strength)
    }
}

/**
 * Tek bir bulanıklık geçişi: kaydedilmiş ekran kopyasının bu yüzeyin altına
 * düşen parçası, [radius] kadar bulanıklaştırılıp [stops] maskesiyle çiziliyor.
 *
 * ### Konum kökten alınıyor
 *
 * [backdrop] ekranın tamamını, kök düzenin (0,0) noktasından başlayarak
 * tutuyor. Bu yüzey ise ekranın bir kenarında duruyor; kopyanın doğru parçasını
 * göstermek için **kökteki** konumu kadar ters yöne kaydırmak gerekiyor.
 *
 * Burada eskiden `positionInParent` kullanılıyordu ve bu sessiz bir kusurdu:
 * yüzey kendi kabını tamamen doldurduğu için o değer her zaman (0,0) çıkıyor,
 * kaydırma hiç olmuyor ve gezinme çubuğunun altında **ekranın sol üst köşesi**
 * bulanıklaştırılmış olarak duruyordu. Yanlış olduğu belli olmuyordu, çünkü
 * bulanık bir görüntünün hangi bölgeye ait olduğu bakışla anlaşılmıyor.
 */
@Composable
private fun BlurBand(
    backdrop: GraphicsLayer,
    modifier: Modifier,
    radius: Dp,
    stops: List<Pair<Float, Float>>,
    gradientStart: (Size) -> Offset,
    gradientEnd: (Size) -> Offset,
    /**
     * Bulanıklığın gücü (0-1).
     *
     * Altında hiçbir şey yokken cam da olmamalı: boş bir listede çubuğun
     * altında bulanıklaştırılacak bir içerik yok ve tam güçte bir buzlu cam
     * orada yalnızca zeminin kendisini bulandırıyor, yani gereksiz bir katman
     * çiziliyor. Değer animasyonlu geldiği için kullanıcı kaydırmaya
     * başladığında cam da yavaşça yerleşiyor.
     */
    strength: Float
) {
    val blurLayer = rememberGraphicsLayer()
    val blurRadius = with(LocalDensity.current) { radius.toPx() } * strength.coerceIn(0f, 1f)
    val colorStops = remember(stops, strength) {
        stops.map { (position, alpha) -> position to Color.Black.copy(alpha = alpha * strength) }
            .toTypedArray()
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                // Katman kaydı besteleme sırasında serbest bırakılmış olabilir;
                // o durumda bu geçiş sessizce atlanıyor, ekran kaybolmuyor.
                runCatching {
                    blurLayer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                    blurLayer.clip = true
                    blurLayer.record {
                        translate(left = -origin.x, top = -origin.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)

                    drawRect(
                        brush = Brush.linearGradient(
                            *colorStops,
                            start = gradientStart(size),
                            end = gradientEnd(size)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    )
}

/**
 * Durum çubuğunun altındaki ince cam.
 *
 * ### Neden gerekli
 *
 * İçerik kenardan kenara çizildiği için durum çubuğunun altından geçiyor ve
 * saat, pil, sinyal o an oradan geçen şeyin rengine kalıyor. Beyaz bir kart
 * geçerken saat okunuyor, koyu bir kart yüzü geçerken kayboluyor. Sistem
 * yazısının rengi tek bir tema kararı; altından geçen içerik ise her karede
 * değişiyor. İkisi hiçbir zaman uyuşmuyor.
 *
 * Bulanıklık bu ilişkiyi kesiyor: altındaki içerik ne olursa olsun ortalanmış,
 * düşük kontrastlı bir yüzeye dönüşüyor ve sistem yazısı her zaman onun
 * üstünde okunuyor.
 *
 * ### Neden gezinme çubuğundakinden ince
 *
 * Alttaki cam bir yüzey — üzerinde düğmeler duruyor ve kalınlığı o yüzeyi
 * gerçek kılıyor. Buradaki ise yalnızca bir okunabilirlik önlemi: kalın bir
 * cam, ekranın üstünde içerik için ayrılmış alanı yiyor ve kullanıcı ondan
 * hiçbir şey kazanmıyor. Yarıçaplar da geçiş mesafesi de bilerek küçük.
 *
 * ### Maskenin yönü ters
 *
 * Gezinme çubuğunda opak kenar altta; burada üstte. Aynı [BlurBand] iki yönde
 * de çalışıyor çünkü degrade uçlarını çağıran veriyor — maskenin kendisini
 * ikinci kez yazmak, ikisinin zamanla ayrışmasına açık kapı bırakırdı.
 */
@Composable
fun KasaStatusBarScrim(
    backdrop: GraphicsLayer?,
    modifier: Modifier = Modifier,
    /**
     * Camın gücü (0-1).
     *
     * Üstte başlık çubuğu belirdiğinde bu sıfıra iniyor: ikisi aynı bölgeyi
     * kaplıyor ve üst üste gelen iki bulanıklık, tek başına hiçbirinin
     * vermediği koyu bir leke üretiyor. Aynı işi iki katman yapmamalı;
     * çubuk varken okunabilirliği o taşıyor.
     */
    strength: Float = 1f
) {
    val colors = KasaTheme.colors
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Sistem çubuğu hiç yoksa (tam ekran bir kip) çizilecek bir şey de yok.
    if (inset <= 0.dp) return

    val level = strength.coerceIn(0f, 1f)
    if (level <= 0.01f) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(inset + STATUS_FADE_RUNWAY)
            .background(
                Brush.verticalGradient(
                    0.00f to colors.navScrim.copy(alpha = 0.86f * level),
                    0.55f to colors.navScrim.copy(alpha = 0.52f * level),
                    1.00f to colors.navScrim.copy(alpha = 0f)
                )
            )
    ) {
        Backdrop(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize(),
            // Opak uç üstte: degrade aşağıdan yukarı okunuyor.
            gradientStart = { Offset(0f, it.height) },
            gradientEnd = { Offset(0f, 0f) },
            strength = level,
            radiusNear = STATUS_BLUR_NEAR,
            radiusFar = STATUS_BLUR_FAR
        )
    }
}

/** Durum çubuğu camının içerik tarafındaki solma mesafesi. */
private val STATUS_FADE_RUNWAY = 16.dp
private val STATUS_BLUR_NEAR = 4.dp
private val STATUS_BLUR_FAR = 13.dp

/** İçeriğe yakın kenardaki ince bulanıklık. */
private val BLUR_NEAR = 9.dp

/** Yüzeyin derinlerindeki kalın bulanıklık. */
private val BLUR_FAR = 30.dp

/**
 * Kalın geçişin maskesi: kenarda yok, ortada zayıf, sonda tam.
 * (konum, saydamlık) çiftleri; konum kenardan yüzeyin ucuna doğru.
 */
private val FAR_STOPS = listOf(
    0.00f to 0.00f,
    0.38f to 0.18f,
    0.72f to 0.86f,
    1.00f to 1.00f
)

/**
 * İnce geçişin maskesi: kenarda yok, hemen ardından tam, sonra sönüyor —
 * çünkü orada yerini kalın geçişe bırakıyor.
 */
private val NEAR_STOPS = listOf(
    0.00f to 0.00f,
    0.20f to 0.92f,
    0.58f to 0.80f,
    1.00f to 0.00f
)

/**
 * Gezinme yüzeyinin üst kenarında bulanıklığın belirmesi için ayrılan boşluk.
 *
 * Solma bu mesafeye yayılıyor: kısa tutulduğunda geçiş yine sert görünüyor,
 * uzattıkça yumuşuyor ama içerik gereksiz yer kaybediyor.
 */
private val FADE_RUNWAY = 34.dp

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Seçili hedefin arkasındaki kap pillin içinde bir pill: yuvarlak
    // biçim dışarıdaki kabuğu tekrarlıyor, köşeli bir vurgu onun içinde
    // yabancı durur.
    val fill by animateFloatAsState(
        if (selected) 1f else 0f,
        KasaMotion.medium(),
        label = "navFill"
    )
    val press by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        KasaMotion.small(),
        label = "navPress"
    )

    val container = MaterialTheme.colorScheme.secondaryContainer

    Column(
        modifier = modifier
            // 64dp: Material'ın dokunma hedefi alt sınırı 48dp ve bu onun
            // üstünde. Kelimelerle gezinen önceki hâlde hedef, metnin
            // kapladığı ince şeritti; parmak ekranın altında kabaca nişan
            // alıyor ve ince şerit o nişanı tutmuyor.
            .height(64.dp)
            .clickableNoRipple(interactionSource = interaction, role = Role.Tab, onClick = onClick)
            .background(
                color = container.copy(alpha = fill),
                shape = RoundedCornerShape(KasaRadius.full)
            )
            .scale(press),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else KasaTheme.colors.ink3,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = destination.label,
            style = if (selected) KasaTheme.text.navActive else KasaTheme.text.navLabel,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else KasaTheme.colors.ink3,
            maxLines = 1,
            softWrap = false
        )
    }
}

data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Kendi içinden açılan eylem düğmesi.
 *
 * ### Önceki hâl ve neden değişti
 *
 * Düğmenin üstünde yüzen bir pil yığını vardı: yedi ayrı yuvarlak kapsül,
 * sırayla belirip kayboluyordu. Altlarındaki listeyle yarıştıkları için
 * arkaya bir karartma gerekiyordu, karartma da her dokunuşu bütün ekranı
 * ilgilendiren bir olaya çeviriyordu. İki sorun aynı kökten geliyordu:
 * menü düğmenin **yanında** açılıyordu, düğmeden değil.
 *
 * Şimdi tek bir yüzey var ve o yüzey düğmenin kendisi. Düğme yerinde
 * duruyor, arkasındaki kart onun altından yukarı ve sola doğru büyüyor;
 * eylemler o kartın içinde, büyüyen pencere açtıkça görünür oluyor. Hareketin
 * tamamı düğmenin sınırları içinde başlıyor, yani gözün takip edeceği tek bir
 * nesne var.
 *
 * Karartma da bu yüzden kalktı: kart donuk ve yükseltilmiş, altındaki satırla
 * yarışmıyor. Örtünün varlık sebebi menünün okunurluğuydu, okunurluğu kartın
 * kendisi sağlıyor.
 *
 * ### İçerik neden sabit ölçüde
 *
 * Kap büyürken içerik de onunla birlikte ölçülse, yazılar animasyon boyunca
 * sıkışıp yeniden satırlanırdı — kapsayıcı dönüşümlerinin klasik hatası.
 * Sütun [androidx.compose.foundation.layout.requiredWidth] ile gelen
 * kısıtlamaları yok sayıyor: hep son ölçüsünde duruyor, kap yalnızca üstünü
 * açıyor. Sütun alta çapalı, yani açığa çıkan ilk şey düğmeye en yakın eylem.
 */
@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
    icon: ImageVector
) {
    val rotation by animateFloatAsState(
        if (expanded) 135f else 0f,
        KasaMotion.medium(),
        label = "fabRotation"
    )
    // Kart ile düğme **aynı** yarıçapı kullanıyor. Ayrı değerler verilseydi
    // paylaştıkları sağ alt köşede biri diğerinin eğrisini taşardı; kapalıyken
    // de ikisi tam olarak üst üste oturmazdı.
    //
    // Hedef küçük tutuluyor (22→28dp): eskiden hedef KasaRadius.full (999dp)
    // idi ve yay 999'dan 22'ye inerken hedefi aşıp negatif yarıçap üretiyordu;
    // gölgenin kullandığı platform Outline'ı negatif yarıçapta istisna atıyor
    // ve menü **kapanırken** uygulama çöküyordu.
    val radius = animatedCorner(
        if (expanded) 28.dp else 22.dp,
        animationSpec = KasaMotion.medium(),
        label = "fabRadius"
    )

    val contentWidth = MENU_WIDTH
    val contentHeight = FAB_SIZE + MENU_PAD + actions.size * MENU_ROW
    // Ölçüler **durum nesnesi** olarak alınıyor ve `Modifier.size` yerine
    // ölçüm sırasında okunuyor. `size(width, height)` değeri beste aşamasında
    // istiyor, yani kart büyüdüğü her karede FabMenu'nün bütün iskeleti —
    // yedi satır, yazıları, simgeleri — yeniden bestelenirdi. Böyle yalnızca
    // yeniden ölçüm oluyor.
    val width = animateDpAsState(
        if (expanded) contentWidth else FAB_SIZE,
        KasaMotion.medium(),
        label = "fabWidth"
    )
    val height = animateDpAsState(
        if (expanded) contentHeight else FAB_SIZE,
        KasaMotion.medium(),
        label = "fabHeight"
    )
    // Kapalıyken kartın gölgesi yok: düğmenin gölgesiyle aynı sınırlarda iki
    // gölge üst üste binip tek bir koyu halka yapıyordu.
    val cardShadow by animateDpAsState(
        if (expanded) KasaElevation.three else 0.dp,
        KasaMotion.effect(),
        label = "fabCardShadow"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.9f else 1f, KasaMotion.small(), label = "fabScale")

    Box(
        modifier = modifier
            .animatedSize(width, height)
            .shadow(cardShadow, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = Modifier
                .requiredWidth(contentWidth)
                .requiredHeight(contentHeight)
                .padding(horizontal = MENU_PAD, vertical = MENU_PAD),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            actions.forEachIndexed { index, action ->
                // Düğmeye en yakın eylem önce beliriyor: yedisi aynı anda
                // gelseydi tek bir blok olarak okunur, menünün hangi uçtan
                // açıldığı kaybolurdu.
                val step = actions.size - 1 - index
                FabMenuItem(
                    action = action,
                    appear = animateFloatAsState(
                        if (expanded) 1f else 0f,
                        KasaMotion.stagger(step, if (expanded) KasaMotion.ENTER_MILLIS else KasaMotion.EXIT_MILLIS),
                        label = "fabItemAppear"
                    ),
                    enabled = expanded
                )
            }
            // Düğmenin solunda kalan şerit: menünün ne yaptığını söyleyen
            // sessiz bir başlık. Boş bırakılsaydı kartın alt kenarı sebepsiz
            // bir boşlukla biterdi.
            Row(
                modifier = Modifier.fillMaxWidth().height(FAB_SIZE - MENU_PAD),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_new_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp).graphicsLayer {
                        // Başlık kartla birlikte açılıyor: pencere yalnızca
                        // düğme genişliğindeyken sıfır, tam açıldığında bir.
                        alpha = ((width.value - FAB_SIZE) / (contentWidth - FAB_SIZE)).coerceIn(0f, 1f)
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .size(FAB_SIZE)
                .pressScale(scale)
                .shadow(KasaElevation.two, RoundedCornerShape(radius), clip = false)
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.primary)
                .clickableNoRipple(
                    interactionSource = interaction,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.add_new)
                ) {
                    onExpandedChange(!expanded)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.add_new),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp).rotate(rotation)
            )
        }
    }
}

/**
 * Menüdeki bir eylem.
 *
 * Beliriş oranı **durum nesnesi** olarak geliyor ve yalnızca `graphicsLayer`
 * içinde — yani çizim sırasında — okunuyor. `by` ile okunsaydı satırın
 * iskeleti animasyon boyunca her karede yeniden bestelenirdi ve yedi satır
 * birlikte bunu yapardı.
 */
@Composable
private fun FabMenuItem(
    action: FabAction,
    appear: State<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateFloatAsState(if (pressed) 1f else 0f, KasaMotion.effect(), label = "fabItemTint")
    val press = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MENU_ROW)
            .graphicsLayer {
                val value = appear.value
                alpha = value
                // Küçük bir kayma: pencere zaten açılıyor, satırın ayrıca uzun
                // yol almasına gerek yok. Bu kadarı satırın yerine "oturduğu"
                // hissini veriyor.
                translationY = (1f - value) * MENU_SLIDE.toPx()
            }
            .clip(RoundedCornerShape(KasaRadius.m))
            .drawBehind { drawRect(press.copy(alpha = 0.10f * tint)) }
            .clickableNoRipple(
                enabled = enabled,
                interactionSource = interaction,
                role = Role.Button,
                onClick = action.onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * Ölçüsünü çizim/ölçüm aşamasında okunan bir durumdan alan kap.
 *
 * `Modifier.size(Dp, Dp)` değeri beste aşamasında istiyor; animasyonlu bir
 * değerle kullanıldığında her kare yeni bir beste demek. Burada değer ölçüm
 * lambdasının içinde okunuyor, yani değişiklik yalnızca yeniden ölçüm
 * tetikliyor — beste hiç çalışmıyor.
 *
 * İçerik sabit kısıtlamayla ölçülüyor ve kap kendi bildirdiği ölçüde kalıyor;
 * içeriği daha büyükse taşan kısmı zincirdeki `clip` kesiyor.
 */
private fun Modifier.animatedSize(width: State<Dp>, height: State<Dp>): Modifier =
    layout { measurable, _ ->
        val w = width.value.roundToPx().coerceAtLeast(0)
        val h = height.value.roundToPx().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(w, h) { placeable.place(0, 0) }
    }

/** Açılmış kartın genişliği. En uzun etiket ("Banka hesabı ekle" değil, menüdeki kısa hâli) buna sığıyor. */
private val MENU_WIDTH = 236.dp

/** Menüdeki bir eylemin yüksekliği. */
private val MENU_ROW = 52.dp

/** Kartın kenar boşluğu; düğmenin kart kenarına olan uzaklığı da bu. */
private val MENU_PAD = 8.dp

/** Satırın yerine oturmak için aldığı yol. */
private val MENU_SLIDE = 10.dp


/** Ana eylem düğmesinin çapı. Köşe animasyonunun hedefi buradan türetiliyor. */
private val FAB_SIZE = 66.dp

/** Gölge boyu için tasarım ölçekleri. */
object KasaElevation {
    val one: Dp = 2.dp
    val two: Dp = 6.dp
    val three: Dp = 16.dp
}
