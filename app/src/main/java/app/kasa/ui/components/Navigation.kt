package app.kasa.ui.components

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Bir gezinme hedefi.
 *
 * Simge alanı yok: çubuk kelimelerle geziniyor, gerekçesi [KasaNavBar]
 * üzerinde yazılı. Kullanılmayan bir alan bırakmak sonraki okuyucuya
 * "burada bir simge çiziliyor" demek olurdu.
 */
data class NavDestination(
    val key: String,
    val label: String
)

/**
 * Alt gezinme çubuğu: tipografik ray.
 *
 * ### Neden simge ve hap yok
 *
 * Burada dört kutu vardı: her biri simge + etiket, seçilinin arkasında
 * yatayda yaylanarak açılan bir hap. İyi işlenmişti ve tam olarak her
 * Android uygulamasında duran şeydi — uygulamanın kimlik iddiasının en zayıf
 * olduğu yer, en çok görünen yerdi.
 *
 * Yeni çubuk listeyle aynı tezi sürdürüyor: ayırmayı kutu değil **tipografi**
 * yapıyor. Hedefler bir satırda duran kelimeler. Seçili olan tam ağırlık ve
 * genişlikte (Flex 620 / wdth 116), ötekiler ince ve dar (Flex 300 / wdth
 * 92) ve kısık. Seçim bir hapın kayması değil, **kelimenin kendisinin öne
 * çıkması**: biri ağırlaşırken komşuları geri çekiliyor. Değişken eksenle
 * gezinen bir çubuk neredeyse hiçbir uygulamada yok ve bunu yapabilmesinin
 * sebebi pakette zaten Roboto Flex'in bulunması.
 *
 * Tek krom parçası etkin kelimenin altındaki ince ray: yaylanarak kelimeden
 * kelimeye kayıyor, bir deftere konmuş ayraç gibi.
 *
 * Simgelerin kalkmasının bir bedeli var ve açıkça söylenmeli: simge bir
 * kelimeden daha hızlı tanınıyor, özellikle uygulamayı yeni kullanan biri
 * için. Karşılığında dört kısa kelime belirsiz değil, dokunma alanı
 * küçülmüyor (her kelime kendi dilimini baştan sona kaplıyor) ve ekran
 * okuyucu için görünür metin, simgeden daha iyi bir kaynak.
 *
 * Çubuk kısaldı: listeye ~30dp daha yer kalıyor.
 *
 * ### Çubuğun altında ne oluyor
 *
 * Çubuk içeriğin **üzerinde** duruyor, yanında değil: liste sonuna kadar
 * kayıyor ve çubuğun altına giriyor. Girdiği yerde üç katman var:
 *
 *  1. **Arka plan bulanıklığı** — [backdrop], ekrandaki içeriğin kaydedilmiş
 *     bir kopyası. Çubuk o kopyanın yalnızca kendi altına düşen parçasını
 *     bulanıklaştırıp çiziyor. Buzlu cam etkisini veren şey bu; içeriğin
 *     hareket ettiği görünüyor ama okunmuyor.
 *  2. **Yumuşak degrade** — bulanık görüntünün üstünde, yukarıda saydam
 *     başlayıp aşağı indikçe koyulaşan bir örtü. Tam opak değil (0,96):
 *     "hafif opak" istenen his bu, ve altındaki hareket seçilmeye devam ediyor.
 *  3. **Sistem çubuğu alanı** — arka plan sistem gezinti çubuğunun altına
 *     kadar iniyor, yalnızca **içerik** iç boşlukla yukarı alınıyor. Eskiden
 *     iç boşluk çubuğun tamamına uygulandığı için o şerit boyasız kalıyordu.
 *
 * Bulanıklık kullanılamazsa (kaydedilmiş kopya yoksa) degrade tek başına
 * çalışmayı sürdürüyor; görüntü sadeleşiyor, bozulmuyor.
 */
@Composable
fun KasaNavBar(
    destinations: List<NavDestination>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
    /** Altında içerik var mı; camın gücünü bu belirliyor. */
    contentBehind: Boolean = true
) {
    val colors = KasaTheme.colors
    val strength by animateFloatAsState(
        if (contentBehind) 1f else 0f,
        KasaMotion.effect(),
        label = "navBlur"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.00f to colors.navScrim.copy(alpha = 0f),
                    0.30f to colors.navScrim.copy(alpha = 0.30f),
                    0.62f to colors.navScrim.copy(alpha = 0.78f),
                    1.00f to colors.navScrim.copy(alpha = 0.94f)
                )
            )
    ) {
        Backdrop(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize(),
            gradientStart = { Offset(0f, 0f) },
            gradientEnd = { Offset(0f, it.height) },
            strength = strength
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Yalnızca içerik yukarı alınıyor; arka plan sistem çubuğunun
                // altına kadar iniyor.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 10.dp, end = 10.dp, top = FADE_RUNWAY, bottom = 12.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val count = destinations.size.coerceAtLeast(1)
                val slot = maxWidth / count
                val index = destinations.indexOfFirst { it.key == selected }.coerceAtLeast(0)

                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        destinations.forEach { destination ->
                            NavItem(
                                destination = destination,
                                selected = destination.key == selected,
                                onClick = { onSelect(destination.key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))

                    // Ayraç. Konumu dilimden hesaplanıyor, ölçümden değil:
                    // dilimler eşit olduğu için kelimenin ortası zaten
                    // belirli ve bu, ölçüm geri bildirimi beklemeden ilk
                    // kareden doğru yerde durması demek.
                    val ruleWidth = RULE_WIDTH
                    val targetX = slot * index + (slot - ruleWidth) / 2
                    val x by animateDpAsState(targetX, KasaMotion.medium(), label = "navRule")
                    Box(
                        Modifier
                            // Kayma yerleşim aşamasında okunuyor: animasyonlu
                            // bir değeri beste aşamasında okumak her karede
                            // yeniden beste demek.
                            .offset { IntOffset(x.roundToPx(), 0) }
                            .width(ruleWidth)
                            .height(2.dp)
                            .clip(RoundedCornerShape(KasaRadius.full))
                            .background(MaterialTheme.colorScheme.primary)
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

    // Basınçta kelime hafifçe çekiliyor; hapsız bir çubukta dokunulduğunu
    // söyleyen tek şey bu.
    val press by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        KasaMotion.small(),
        label = "navPress"
    )
    val dim by animateFloatAsState(
        if (selected) 1f else 0.62f,
        KasaMotion.effect(),
        label = "navDim"
    )

    Box(
        modifier = modifier
            .clickableNoRipple(interactionSource = interaction, role = Role.Tab, onClick = onClick)
            .scale(press),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = destination.label,
            // Seçim ağırlık ve genişlikle söyleniyor; iki biçim arasındaki
            // fark gözle okunacak kadar büyük olmak zorunda, yoksa "hangisi
            // seçili" sorusu rengin tek başına cevaplamasına kalıyor.
            style = if (selected) KasaTheme.text.navActive else KasaTheme.text.navLabel,
            color = if (selected) MaterialTheme.colorScheme.primary
            else KasaTheme.colors.ink3.copy(alpha = dim),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}


data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Genişleyen eylem düğmesi.
 *
 * Açılırken alt öğeler alttan üste doğru sırayla belirir, kapanırken üstten
 * alta doğru kaybolur; bu tersine sıralama, menünün ana düğmeden çıkıp yine
 * ona döndüğü hissini veriyor. Ana düğme aynı anda 135 derece dönerek artıdan
 * çarpıya geçiyor.
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
    // Tam yuvarlak = düğmenin yarısı (66/2). Eskiden hedef KasaRadius.full
    // (999dp) idi ve yay 999'dan 22'ye inerken hedefi aşıp negatif yarıçap
    // üretiyordu; gölgenin kullandığı platform Outline'ı negatif yarıçapta
    // istisna atıyor ve menü **kapanırken** uygulama çöküyordu.
    val fabRadius = animatedCorner(
        if (expanded) FAB_SIZE / 2 else 22.dp,
        label = "fabRadius"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, KasaMotion.small(), label = "fabScale")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEachIndexed { index, action ->
            // Menü aşağıdan yukarı açılıyor: düğmeye en yakın eylem önce
            // beliriyor. Dördü aynı anda gelseydi tek bir blok olarak okunur,
            // sıra bilgisi kaybolurdu. Kapanışta gecikme yok — kullanıcı kararı
            // vermiş, menünün oyalanmaya hakkı yok.
            val step = actions.size - 1 - index
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(KasaMotion.stagger(step, KasaMotion.EXIT_MILLIS)) + scaleIn(
                    initialScale = 0.7f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f),
                    animationSpec = KasaMotion.stagger(step)
                ),
                exit = fadeOut(KasaMotion.exit()) + scaleOut(
                    targetScale = 0.7f,
                    animationSpec = KasaMotion.exit()
                )
            ) {
                FabMenuItem(action = action)
            }
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(FAB_SIZE)
                .scale(scale)
                .shadow(6.dp, RoundedCornerShape(fabRadius), clip = false)
                .clip(RoundedCornerShape(fabRadius))
                .background(if (expanded) KasaTheme.colors.ink else MaterialTheme.colorScheme.primary)
                .clickableNoRipple(interactionSource = interaction, role = Role.Button) {
                    onExpandedChange(!expanded)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (expanded) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp).rotate(rotation)
            )
        }
    }
}

@Composable
private fun FabMenuItem(action: FabAction, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "fabItemScale")

    Row(
        modifier = modifier
            .height(50.dp)
            .scale(scale)
            .shadow(4.dp, RoundedCornerShape(KasaRadius.full), clip = false)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Ana eylem düğmesinin çapı. Köşe animasyonunun hedefi buradan türetiliyor. */
private val FAB_SIZE = 66.dp

/** Etkin kelimenin altındaki ayracın genişliği. */
private val RULE_WIDTH = 22.dp

/** Gölge boyu için tasarım ölçekleri. */
object KasaElevation {
    val one: Dp = 2.dp
    val two: Dp = 6.dp
    val three: Dp = 16.dp
}
