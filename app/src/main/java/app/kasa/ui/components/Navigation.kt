package app.kasa.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

data class NavDestination(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Alt gezinme çubuğu.
 *
 * Seçili öğenin arkasındaki hap (pill) yatayda sıkışık başlayıp yaylanarak
 * açılır; simge hafifçe büyür.
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
    backdrop: GraphicsLayer? = null
) {
    val colors = KasaTheme.colors

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
            gradientEnd = { Offset(0f, it.height) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Yalnızca içerik yukarı alınıyor; arka plan sistem çubuğunun
                // altına kadar iniyor.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, top = FADE_RUNWAY, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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

/**
 * Yatay moddaki gezinme rayı.
 *
 * Telefon yan çevrildiğinde dikey alan zaten yarıya iniyor; oraya bir de alt
 * çubuk koymak, kalan içeriği okunamayacak kadar daraltıyor. Ray aynı işi
 * yatayda yapıyor ve dikey alanın tamamını içeriğe bırakıyor.
 *
 * Bulanıklık ve solma aynı: yalnızca yön değişiyor, soldan sağa.
 */
@Composable
fun KasaNavRail(
    destinations: List<NavDestination>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null
) {
    val colors = KasaTheme.colors

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            .background(
                Brush.horizontalGradient(
                    0.00f to colors.navScrim.copy(alpha = 0.94f),
                    0.55f to colors.navScrim.copy(alpha = 0.72f),
                    1.00f to colors.navScrim.copy(alpha = 0f)
                )
            )
    ) {
        Backdrop(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize(),
            // Ray solda: bulanıklık solda tam, sağa doğru siliniyor.
            gradientStart = { Offset(it.width, 0f) },
            gradientEnd = { Offset(0f, 0f) }
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 8.dp, end = FADE_RUNWAY, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            destinations.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination.key == selected,
                    onClick = { onSelect(destination.key) }
                )
            }
        }
    }
}

/**
 * Gezinme yüzeyinin altındaki bulanık arka plan.
 *
 * ### Neden ayrı bir katman
 *
 * Bulanıklığın kendisi maskelenmek zorunda. Önceki hâlinde bulanık görüntü
 * çubuğun tamamına eşit güçte çiziliyordu ve üst kenarında **keskin bir çizgi**
 * oluşuyordu: bir piksel üstü net, bir piksel altı tam bulanık. Degradeyi
 * bulanıklığın üstüne koymak bunu gizlemiyor, çünkü sorun rengin değil
 * bulanıklığın kendisinin sert başlaması.
 *
 * Çözüm, bulanık görüntüyü kendi çevrimdışı katmanında çizip üzerine
 * [BlendMode.DstIn] ile bir saydamlık degradesi geçirmek: bulanıklık artık
 * yavaşça beliriyor. Çevrimdışı katman şart — aynı katmanda çizilseydi maske
 * gezinme simgelerini de silerdi.
 *
 * @param gradientStart maskenin saydam (bulanıklığın olmadığı) ucu
 * @param gradientEnd maskenin opak (bulanıklığın tam olduğu) ucu
 */
@Composable
private fun Backdrop(
    backdrop: GraphicsLayer?,
    modifier: Modifier,
    gradientStart: (Size) -> Offset,
    gradientEnd: (Size) -> Offset
) {
    if (backdrop == null) return

    val blurLayer = rememberGraphicsLayer()
    val blurRadius = with(LocalDensity.current) { 24.dp.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInParent() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                runCatching {
                    blurLayer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                    blurLayer.clip = true
                    blurLayer.record {
                        translate(left = -origin.x, top = -origin.y) { drawLayer(backdrop) }
                    }
                    drawLayer(blurLayer)

                    // Bulanıklığı sert bir kenar bırakmadan sonlandıran maske.
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black,
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
 * Gezinme yüzeyinin üst kenarında bulanıklığın belirmesi için ayrılan boşluk.
 *
 * Solma bu mesafeye yayılıyor: kısa tutulduğunda geçiş yine sert görünüyor,
 * uzattıkça yumuşuyor ama içerik gereksiz yer kaybediyor.
 */
private val FADE_RUNWAY = 34.dp

/** Yatay moddaki rayın genişliği. */
private val RAIL_WIDTH = 92.dp

@Composable
private fun NavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pillScaleX by animateFloatAsState(
        if (selected) 1f else 0.42f,
        KasaMotion.medium(),
        label = "pillScale"
    )
    val pillAlpha by animateFloatAsState(if (selected) 1f else 0f, KasaMotion.effect(), label = "pillAlpha")
    val iconScale by animateFloatAsState(if (selected) 1.06f else 1f, KasaMotion.small(), label = "iconScale")
    val slotScale by animateFloatAsState(if (pressed) 0.9f else 1f, KasaMotion.small(), label = "slotScale")

    Column(
        modifier = modifier
            .clickableNoRipple(interactionSource = interaction, role = Role.Tab, onClick = onClick)
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 34.dp)
                .scale(slotScale),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 34.dp)
                    .scale(scaleX = pillScaleX, scaleY = 1f)
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = pillAlpha))
            )
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else KasaTheme.colors.ink3,
                modifier = Modifier.size(24.dp).scale(iconScale)
            )
        }
        Text(
            text = destination.label,
            style = KasaTheme.text.navLabel,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else KasaTheme.colors.ink3
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

/** Alt sayfa ve menüler açıkken içeriği karartan örtü. */
@Composable
fun Scrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scrimAlpha")
    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .background(Color(0xFF09201B).copy(alpha = 0.34f * alpha))
                .then(
                    if (visible) Modifier.clickableNoRipple(onClick = onDismiss) else Modifier
                )
        )
    }
}

/** Ana eylem düğmesinin çapı. Köşe animasyonunun hedefi buradan türetiliyor. */
private val FAB_SIZE = 66.dp

/** Gölge boyu için tasarım ölçekleri. */
object KasaElevation {
    val one: Dp = 2.dp
    val two: Dp = 6.dp
    val three: Dp = 16.dp
}
