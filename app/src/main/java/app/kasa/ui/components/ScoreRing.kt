package app.kasa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.LocalReducedMotion

/**
 * Kasa puanının halkası.
 *
 * ### Neden halka, çubuk değil
 *
 * Puan 0–100 arası **kapalı** bir ölçek: bir üst sınırı var ve kullanıcının
 * görmesi gereken şey "ne kadarı doldu". Doğrusal bir çubuk bunu gösteriyor
 * ama ekranda bir satır kaplıyor ve ekranın geri kalanıyla aynı ağırlıkta
 * duruyor. Oysa bu sayı ekranın konusu.
 *
 * Halka, sayının kendisini içine alıyor: rakam ve ölçek tek bir nesne oluyor
 * ve göz ikisini ayrı ayrı okumak zorunda kalmıyor. Kapalı bir ölçeğin
 * kapalılığı da halkanın kendisinden anlaşılıyor — çubuğun sağ ucunda böyle
 * bir ipucu yok.
 *
 * ### Boşluk üstte
 *
 * Yay saat 12'den değil, 135°'den başlıyor ve 270° sürüyor. Tam bir daire
 * "doldu" ile "boş" arasındaki farkı başlangıç noktası belirsiz olduğu için
 * zayıflatıyor; üstteki boşluk hem başlangıcı hem bitişi gösteriyor.
 *
 * ### Tarama sırasında
 *
 * Puan henüz bilinmiyor ve bir sayı göstermek yanlış olurdu. Halka o sırada
 * belirsiz bir kip alıyor: kısa bir yay çember boyunca dönüyor. Bilinmeyen bir
 * ilerlemeyi belirli bir yayla göstermek, kullanıcıya olmayan bir bilgi
 * vermek olurdu.
 */
@Composable
fun ScoreRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    scanning: Boolean = false,
    strokeWidth: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "scoreRing")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPIN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scoreSpin"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            if (scanning) {
                // Belirsiz kip: kısa bir yay yolun üzerinde dönüyor.
                drawArc(
                    color = color,
                    startAngle = if (reduced) START_ANGLE else START_ANGLE + spin,
                    sweepAngle = SWEEP_ANGLE * INDETERMINATE_FRACTION,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            } else if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }
        content()
    }
}

/** Saat 7-8 yönü: üstte açık bir boşluk kalıyor. */
private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f
private const val INDETERMINATE_FRACTION = 0.22f
private const val SPIN_MILLIS = 1400
