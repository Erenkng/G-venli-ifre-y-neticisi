package app.kasa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaTheme
import app.kasa.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ══════════════════════════════ dalgalı ilerleme ══════════════════════════════

/**
 * Material 3 Expressive "wavy progress": dolu kısım dalgalanır, boş kısım düz
 * bir çizgidir, sonunda küçük bir durak noktası bulunur.
 *
 * Dalganın genliği çizginin başında sıfırdan büyür; bu, ilerleme %0'a yakınken
 * çirkin bir kıvrımla başlamasını engelliyor.
 */
@Composable
fun WavyProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = KasaTheme.colors.strengthStrong,
    trackColor: Color = KasaTheme.colors.ink3.copy(alpha = 0.28f),
    height: Dp = 20.dp,
    strokeWidth: Dp = 4.5.dp,
    animated: Boolean = true
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "wavy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val seed = remember { (0..600).random() / 100f }
    val livePhase = if (animated && !reduced) phase + seed else seed

    Canvas(modifier = modifier.height(height)) {
        drawWavy(
            progress = progress.coerceIn(0f, 1f),
            phase = livePhase,
            color = color,
            trackColor = trackColor,
            strokeWidthPx = strokeWidth.toPx()
        )
    }
}

private fun DrawScope.drawWavy(
    progress: Float,
    phase: Float,
    color: Color,
    trackColor: Color,
    strokeWidthPx: Float
) {
    val centerY = size.height / 2f
    val amplitude = size.height * 0.16f
    val waveLength = 22.dp.toPx()
    val start = strokeWidthPx / 2f
    val end = (start + (size.width - strokeWidthPx) * progress).coerceAtLeast(start + 1f)
    val gap = 8.dp.toPx()

    val path = Path().apply {
        moveTo(start, centerY)
        var x = start
        while (x <= end) {
            val ramp = min(1f, (x - start) / (24.dp.toPx()))
            val y = centerY + sin(x / waveLength + phase) * amplitude * ramp
            lineTo(x, y)
            x += 2f
        }
    }
    drawPath(path, color, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

    val trackStart = min(size.width - strokeWidthPx / 2f, end + gap)
    if (trackStart < size.width - strokeWidthPx / 2f) {
        drawLine(
            color = trackColor,
            start = Offset(trackStart, centerY),
            end = Offset(size.width - strokeWidthPx / 2f, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
    }
    drawCircle(color, radius = strokeWidthPx * 0.72f, center = Offset(size.width - strokeWidthPx / 2f, centerY))
}

// ═══════════════════════════════ şekil motoru ═════════════════════════════════

/**
 * Tasarımdaki `shapePath` fonksiyonunun Compose karşılığı.
 *
 * Yıldız benzeri bir çokgen üretir; [spike] tepe ile vadi arasındaki farkı,
 * [round] köşelerin yumuşaklığını belirler. Parola güçlendikçe [spike] küçülür
 * ve [round] büyür: dikenli bir yıldızdan yumuşak bir çakıl taşına dönüşür.
 * Bu, gücü sayı yerine biçimle anlatmanın doğrudan yolu.
 */
fun buildMorphPath(
    centerX: Float,
    centerY: Float,
    radius: Float,
    points: Int,
    spike: Float,
    round: Float,
    rotation: Float
): Path {
    val total = points * 2
    val vertices = Array(total) { i ->
        val angle = rotation + i * PI.toFloat() / points
        val r = radius * if (i % 2 == 0) 1f else (1f - spike)
        Offset(centerX + cos(angle) * r, centerY + sin(angle) * r)
    }

    fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    val path = Path()
    for (i in 0 until total) {
        val current = vertices[i]
        val previous = vertices[(i - 1 + total) % total]
        val next = vertices[(i + 1) % total]
        val from = lerp(current, previous, round)
        val to = lerp(current, next, round)
        if (i == 0) path.moveTo(from.x, from.y) else path.lineTo(from.x, from.y)
        path.quadraticBezierTo(current.x, current.y, to.x, to.y)
    }
    path.close()
    return path
}

/**
 * Parola gücüne göre biçim değiştiren kadran.
 *
 * @param strength 0..1
 */
@Composable
fun MorphDial(
    strength: Float,
    color: Color,
    modifier: Modifier = Modifier,
    points: Int = 7,
    spin: Boolean = true
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "morph")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val angle = if (spin && !reduced) rotation * (1.2f - 0.85f * strength) else 0.4f

    // Şeklin kendisi net, uçları dağılıyor.
    //
    // Düz dolgu, dönen bir biçimi kâğıttan kesilmiş gibi gösteriyordu: kenarı
    // keskin olduğu için hareket de mekanik okunuyordu. Kenarı dışa doğru
    // çözmek, biçime hacim ve hız hissi veriyor — dönen şey artık bir nesne
    // değil, bir alan.
    //
    // Bulanıklık gerçek bir BlurEffect ile değil, iç içe geçmiş üç halkayla
    // yapılıyor: şekil her karede yeniden hesaplandığı için katman
    // bulanıklaştırma her karede yeni bir arabellek demek olurdu. Aynı yolu
    // büyüterek ve saydamlığını düşürerek çizmek, aynı görünümü tek geçişte
    // veriyor.
    val glowLayers = if (reduced) 0 else GLOW_LAYERS

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.88f

        fun path(scale: Float) = buildMorphPath(
            centerX = center.x,
            centerY = center.y,
            radius = radius * scale,
            points = points,
            spike = 0.30f * (1f - strength),
            round = 0.14f + 0.36f * strength,
            rotation = angle
        )

        // Dıştan içe: en dıştaki halka en saydam.
        for (layer in glowLayers downTo 1) {
            val t = layer.toFloat() / glowLayers
            drawPath(
                path = path(1f + GLOW_SPREAD * t),
                color = color.copy(alpha = 0.16f * (1f - t) + 0.04f)
            )
        }
        drawPath(path(1f), color)
    }
}

/** Kenarın çözüldüğü halka sayısı. Üçün üstü fark edilmiyor, altı sert kalıyor. */
private const val GLOW_LAYERS = 3

/** En dış halkanın yarıçapı ne kadar aşacağı. */
private const val GLOW_SPREAD = 0.14f

/**
 * Tarama göstergesi: sürekli biçim değiştiren yükleyici. [scanning] açıkken
 * daha hızlı ve daha dikenli döner, kapalıyken sakin bir çakıl taşı gibi durur.
 */
@Composable
fun ScanShape(
    scanning: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "scan")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (scanning) 2400 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanT"
    )
    val time = if (reduced) 0.6f else t

    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) / 2f * 0.86f
        val spike = if (scanning) 0.22f + 0.10f * sin(time * 3f) else 0.05f
        val path = buildMorphPath(
            centerX = size.width / 2f,
            centerY = size.height / 2f,
            radius = radius,
            points = 6,
            spike = spike,
            round = 0.34f + 0.12f * sin(time * 1.7f),
            rotation = time * if (scanning) 1.6f else 0.25f
        )
        drawPath(path, color)
    }
}
