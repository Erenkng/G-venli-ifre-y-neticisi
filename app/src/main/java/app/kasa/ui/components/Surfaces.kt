package app.kasa.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasa.core.util.PasswordStrength
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

private val SpatialSpring = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
private val SpatialDpSpring = spring<Dp>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

/** Gruplanmış listede bir öğenin konumu; köşe yarıçapını bu belirler. */
enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }

fun groupPositionOf(index: Int, size: Int): GroupPosition = when {
    size == 1 -> GroupPosition.ONLY
    index == 0 -> GroupPosition.FIRST
    index == size - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

private fun groupShape(position: GroupPosition, tight: Dp, loose: Dp): Shape = when (position) {
    GroupPosition.ONLY -> RoundedCornerShape(loose)
    GroupPosition.FIRST -> RoundedCornerShape(topStart = loose, topEnd = loose, bottomStart = tight, bottomEnd = tight)
    GroupPosition.LAST -> RoundedCornerShape(topStart = tight, topEnd = tight, bottomStart = loose, bottomEnd = loose)
    GroupPosition.MIDDLE -> RoundedCornerShape(tight)
}

/**
 * Gruplanmış liste satırı.
 *
 * Grubun ilk ve son öğesinin dış köşeleri yuvarlak, iç köşeleri keskindir;
 * basılınca satır küçülür ve tüm köşeleri yuvarlanır — satır gruptan
 * "kopup" öne çıkmış gibi olur. Tasarımın en çok tekrarlanan hareketi bu.
 */
@Composable
fun KasaTile(
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, SpatialSpring, label = "tileScale")
    // Yarıçap animasyonları [animatedCorner] üzerinden: yaylı hareket hedefi
    // aşıyor ve gölgenin kullandığı platform Outline'ı negatif yarıçapta
    // istisna atıyor.
    val loose = animatedCorner(KasaRadius.l, SpatialDpSpring, label = "tileLoose")
    val tight = animatedCorner(if (pressed) KasaRadius.l else KasaRadius.xs, SpatialDpSpring, label = "tileTight")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(groupShape(position, tight, loose))
            .background(if (pressed) KasaTheme.colors.tilePressed else KasaTheme.colors.tile)
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
    }
}

/** Kayıt rozeti: baş harf ya da simge. Basılınca kareden daireye döner. */
@Composable
fun KasaBadge(
    text: String? = null,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 16.dp,
    content: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content()
        else Text(
            text = text.orEmpty(),
            color = foreground,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

/** Parola gücü noktası: listenin sağ ucundaki küçük renkli işaret. */
@Composable
fun StrengthDot(tone: PasswordStrength.Tone, modifier: Modifier = Modifier) {
    val colors = KasaTheme.colors
    val color = when (tone) {
        PasswordStrength.Tone.WEAK -> colors.strengthWeak
        PasswordStrength.Tone.MID -> colors.strengthMid
        PasswordStrength.Tone.STRONG -> colors.strengthStrong
    }
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(KasaTheme.colors.tile),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(color)
        )
    }
}

/** Bölüm etiketi: büyük harf başlık + isteğe bağlı sayaç + ince ayırıcı çizgi. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = text.uppercase(java.util.Locale("tr", "TR")),
            style = KasaTheme.text.sectionLabel,
            color = KasaTheme.colors.ink3
        )
        if (count != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$count", style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink2)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** Yüzeyi kalkık kart. [tinted] açıkken tasarımın jade-kehribar geçişini alır. */
@Composable
fun KasaCard(
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = KasaTheme.colors
    val background = if (tinted) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            0f to (if (colors.isDark) Color(0xFF13302B) else Color(0xFFE9F8F2)),
            0.55f to colors.card,
            1f to (if (colors.isDark) Color(0xFF2E2413) else Color(0xFFFFF6E6))
        )
    } else {
        androidx.compose.ui.graphics.SolidColor(colors.card)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(KasaRadius.xl), clip = false)
            .clip(RoundedCornerShape(KasaRadius.xl))
            .background(background)
            .border(1.dp, if (colors.isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.9f), RoundedCornerShape(KasaRadius.xl))
            .padding(padding),
        content = content
    )
}

/** "Son kullanılan" şeridindeki küçük kart. */
@Composable
fun RecentCard(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Rozet dışarıdan veriliyor: son kullanılanlar şeridinde de kartlar kart
     * yüzüyle, diğer türler kendi simgeleriyle görünsün diye. Rozeti burada
     * kurmak, kart görselini bu dosyaya taşımak anlamına gelirdi.
     */
    badge: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, SpatialSpring, label = "recentScale")
    val radius = animatedCorner(if (pressed) KasaRadius.l else KasaRadius.m, SpatialDpSpring, label = "recentRadius")

    Column(
        modifier = modifier
            .width(104.dp)
            .scale(scale)
            .shadow(1.dp, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(KasaTheme.colors.tile)
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
    ) {
        badge()
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = KasaTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Boş durum bloğu. */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = KasaTheme.colors.ink2
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = KasaTheme.colors.ink3,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
