package app.kasa.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import kotlin.math.roundToInt

/** Basıldığında küçülüp köşeleri değişen yay (spring) hareketi. */
private val SpatialSpring = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = Spring.StiffnessMediumLow
)

private val SpatialDpSpring = spring<Dp>(
    dampingRatio = 0.55f,
    stiffness = Spring.StiffnessMediumLow
)

enum class ButtonTone { FILLED, TONAL, OUTLINED, TEXT }

/**
 * Material 3 Expressive düğmesi.
 *
 * Basılınca hem ölçek küçülür hem de köşe yarıçapı tam yuvarlaktan kareye
 * yaklaşır. Bu ikili hareket, düğmenin dokunuşa fiziksel olarak yanıt verdiği
 * hissini veriyor; yalnız renk değiştiren bir düğmede bu his yok.
 */
@Composable
fun KasaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.FILLED,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    restingRadius: Dp = KasaRadius.full,
    pressedRadius: Dp = KasaRadius.s,
    leading: @Composable (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, SpatialSpring, label = "btnScale")
    val radius by animateDpAsState(if (pressed) pressedRadius else restingRadius, SpatialDpSpring, label = "btnRadius")

    val background = when (tone) {
        ButtonTone.FILLED -> MaterialTheme.colorScheme.primary
        ButtonTone.TONAL -> MaterialTheme.colorScheme.secondaryContainer
        ButtonTone.OUTLINED, ButtonTone.TEXT -> Color.Transparent
    }
    val foreground = when (tone) {
        ButtonTone.FILLED -> MaterialTheme.colorScheme.onPrimary
        ButtonTone.TONAL -> MaterialTheme.colorScheme.onSecondaryContainer
        ButtonTone.OUTLINED, ButtonTone.TEXT -> MaterialTheme.colorScheme.primary
    }
    val alpha = if (enabled) 1f else 0.38f

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(radius))
            .background(background.copy(alpha = background.alpha * alpha))
            .then(
                if (tone == ButtonTone.OUTLINED)
                    Modifier.border(1.5.dp, KasaTheme.colors.ink3.copy(alpha = 0.5f * alpha), RoundedCornerShape(radius))
                else Modifier
            )
            .clickableNoRipple(enabled = enabled, interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            leading?.invoke()
            Text(
                text = text,
                color = foreground.copy(alpha = alpha),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Bölünmüş düğme: solda ana eylem, sağda ikincil eylem. İki parça arasındaki
 * 3dp'lik boşluk ve asimetrik köşeler, ikisinin tek bir bütün olduğunu ama
 * ayrı ayrı basılabildiğini anlatıyor.
 */
@Composable
fun SplitButton(
    text: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    secondaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.weight(1f)) {
            KasaButtonShaped(
                onClick = onPrimary,
                shapeResting = RoundedCornerShape(
                    topStart = KasaRadius.full, bottomStart = KasaRadius.full,
                    topEnd = KasaRadius.s, bottomEnd = KasaRadius.s
                ),
                shapePressed = RoundedCornerShape(
                    topStart = KasaRadius.full, bottomStart = KasaRadius.full,
                    topEnd = 24.dp, bottomEnd = 24.dp
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    leading?.invoke()
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
        KasaButtonShaped(
            onClick = onSecondary,
            shapeResting = RoundedCornerShape(
                topStart = KasaRadius.s, bottomStart = KasaRadius.s,
                topEnd = KasaRadius.full, bottomEnd = KasaRadius.full
            ),
            shapePressed = RoundedCornerShape(
                topStart = 24.dp, bottomStart = 24.dp,
                topEnd = KasaRadius.full, bottomEnd = KasaRadius.full
            ),
            modifier = Modifier.width(72.dp),
            content = secondaryContent
        )
    }
}

@Composable
private fun KasaButtonShaped(
    onClick: () -> Unit,
    shapeResting: Shape,
    shapePressed: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, SpatialSpring, label = "splitScale")

    Box(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .clip(if (pressed) shapePressed else shapeResting)
            .background(MaterialTheme.colorScheme.primary)
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

/**
 * Düğme grubu (segmented). Tasarımdaki ayrıntı: bir düğmeye basıldığında
 * komşuları yatayda hafifçe sıkışır — gruptaki elemanların birbirine bağlı
 * olduğunu gösteren küçük ama akılda kalıcı bir hareket.
 */
@Composable
fun <T> KasaButtonGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    // Tasarımdaki `.group` yatayda kaydırılabilir; kategori sayısı arttıkça
    // sığmayan öğeler kesilmek yerine kaydırılıyor.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val neighbourPressed = pressedIndex != null && pressedIndex != index
            val squish by animateFloatAsState(if (neighbourPressed) 0.9f else 1f, SpatialSpring, label = "squish")

            val shape = when {
                isSelected -> RoundedCornerShape(KasaRadius.full)
                index == 0 -> RoundedCornerShape(
                    topStart = KasaRadius.full, bottomStart = KasaRadius.full,
                    topEnd = KasaRadius.s, bottomEnd = KasaRadius.s
                )
                index == options.lastIndex -> RoundedCornerShape(
                    topStart = KasaRadius.s, bottomStart = KasaRadius.s,
                    topEnd = KasaRadius.full, bottomEnd = KasaRadius.full
                )
                else -> RoundedCornerShape(KasaRadius.s)
            }

            Box(
                modifier = Modifier
                    .height(46.dp)
                    .scale(scaleX = squish, scaleY = 1f)
                    .clip(shape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .pointerInput(index, options.size) {
                        detectTapGestures(
                            onPress = {
                                pressedIndex = index
                                tryAwaitRelease()
                                pressedIndex = null
                            },
                            onTap = { onSelect(option) }
                        )
                    }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else KasaTheme.colors.ink2,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * M3 Expressive anahtar. Kapalıyken küçük ve soluk bir nokta, açıkken büyüyüp
 * beyazlaşan bir tutamak; basılı tutulduğunda tutamak yanlara doğru uzuyor.
 */
@Composable
fun KasaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val thumbSize by animateDpAsState(
        when {
            pressed -> 26.dp
            checked -> 22.dp
            else -> 16.dp
        },
        SpatialDpSpring, label = "thumbSize"
    )
    val offsetX by animateDpAsState(
        when {
            checked && pressed -> 22.dp
            checked -> 26.dp
            else -> 6.dp
        },
        SpatialDpSpring, label = "thumbOffset"
    )
    val alpha = if (enabled) 1f else 0.38f
    val trackColor = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = if (checked) MaterialTheme.colorScheme.primary else KasaTheme.colors.ink3

    Box(
        modifier = modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(trackColor.copy(alpha = trackColor.alpha * alpha))
            .border(2.dp, borderColor.copy(alpha = 0.7f * alpha), RoundedCornerShape(KasaRadius.full))
            .clickableNoRipple(
                enabled = enabled,
                interactionSource = interaction,
                role = Role.Switch
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = offsetX)
                .size(thumbSize)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(
                    (if (checked) Color.White else KasaTheme.colors.ink3).copy(alpha = alpha)
                )
        )
    }
}

/**
 * Material 3 Expressive kaydırıcı: kalın bir ray, üzerinde ince dikey bir
 * tutamak. Sürüklerken tutamak incelip uzar, ray üzerindeki noktalar adım
 * hissini verir.
 */
@Composable
fun ExpressiveSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDragEnd: () -> Unit = {}
) {
    val density = LocalDensity.current
    var width by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }

    val fraction = ((value - range.first).toFloat() / (range.last - range.first).toFloat()).coerceIn(0f, 1f)
    val thumbHeight by animateDpAsState(if (dragging) 52.dp else 44.dp, SpatialDpSpring, label = "sliderThumbH")
    val thumbWidth by animateDpAsState(if (dragging) 3.dp else 6.dp, SpatialDpSpring, label = "sliderThumbW")

    fun valueFor(x: Float): Int {
        val f = (x / width).coerceIn(0f, 1f)
        return (range.first + f * (range.last - range.first)).roundToInt().coerceIn(range.first, range.last)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(range, width) {
                detectTapGestures(onTap = { onValueChange(valueFor(it.x)) })
            }
            .pointerInput(range, width) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; onValueChange(valueFor(it.x)) },
                    onDragEnd = { dragging = false; onDragEnd() },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    onValueChange(valueFor(change.position.x))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // ray
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .onSizeChangedPx { width = it }
        ) {
            // adım noktaları
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(KasaRadius.full))
                            .background(KasaTheme.colors.ink3.copy(alpha = 0.45f))
                    )
                }
            }
        }
        // dolu kısım
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                .height(16.dp)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(MaterialTheme.colorScheme.primary)
        )
        // tutamak
        Box(
            modifier = Modifier
                .offset(x = with(density) { (width * fraction).toDp() } - thumbWidth / 2)
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(MaterialTheme.colorScheme.primary)
                .border(4.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(KasaRadius.full))
        )
    }
}

/** Küçük, ana hat çizgili eylem çipi. */
@Composable
fun KasaChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, SpatialSpring, label = "chipScale")

    Box(
        modifier = modifier
            .height(34.dp)
            .scale(scale)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceContainer
                else Color.Transparent
            )
            .border(1.5.dp, KasaTheme.colors.ink3.copy(alpha = 0.45f), RoundedCornerShape(KasaRadius.full))
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = KasaTheme.colors.ink2,
            maxLines = 1
        )
    }
}

/** Yuvarlak simge düğmesi; basılınca köşeleri kareye döner. */
@Composable
fun KasaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, SpatialSpring, label = "iconScale")
    val radius by animateDpAsState(if (pressed) 13.dp else KasaRadius.full, SpatialDpSpring, label = "iconRadius")

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(radius))
            .background(
                if (accent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .clickableNoRipple(
                interactionSource = interaction,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

/** Satır içi eylem çubuğu düğmesi (kayıt ayrıntısındaki kopyala/düzenle/sil). */
@Composable
fun RowScope.ToolbarAction(
    onClick: () -> Unit,
    danger: Boolean = false,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, SpatialSpring, label = "tbScale")

    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(if (pressed) Color.Black.copy(alpha = 0.07f) else Color.Transparent)
            .clickableNoRipple(
                interactionSource = interaction,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}
