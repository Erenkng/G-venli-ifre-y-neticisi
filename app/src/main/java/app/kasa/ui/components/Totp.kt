package app.kasa.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasa.core.util.Totp
import app.kasa.ui.theme.KasaTheme
import kotlinx.coroutines.delay

/**
 * Canlı TOTP kodu ve kalan süre halkası.
 *
 * Kod her saniye yeniden hesaplanır (hesap ucuz: tek bir HMAC). Halka son
 * 5 saniyede kırmızıya döner, böylece kullanıcı kodu kopyalamadan önce
 * "yeni kodu bekle" kararını renkten verebilir.
 */
@Composable
fun TotpDisplay(
    secret: String,
    digits: Int,
    period: Int,
    algorithm: String,
    modifier: Modifier = Modifier,
    onCodeChange: (String) -> Unit = {}
) {
    var code by remember(secret) { mutableStateOf(Totp.code(secret, digits, period, algorithm).orEmpty()) }
    var remaining by remember(secret) { mutableIntStateOf(Totp.secondsRemaining(period)) }

    LaunchedEffect(secret, digits, period, algorithm) {
        while (true) {
            val fresh = Totp.code(secret, digits, period, algorithm).orEmpty()
            if (fresh != code) {
                code = fresh
                onCodeChange(fresh)
            }
            remaining = Totp.secondsRemaining(period)
            delay(500)
        }
    }

    val expiring = remaining <= 5
    val ringColor = if (expiring) KasaTheme.colors.strengthWeak else MaterialTheme.colorScheme.primary
    val progress by animateFloatAsState(
        targetValue = remaining.toFloat() / period.toFloat(),
        animationSpec = tween(durationMillis = 480, easing = LinearEasing),
        label = "totpProgress"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (code.isEmpty()) "······" else Totp.pretty(code),
            style = KasaTheme.text.mono.copy(fontSize = 21.sp),
            color = if (expiring) KasaTheme.colors.strengthWeak else KasaTheme.colors.ink
        )
        CountdownRing(progress = progress, color = ringColor, label = remaining)
    }
}

@Composable
fun CountdownRing(
    progress: Float,
    color: Color,
    label: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(34.dp)) {
            val stroke = 3.dp.toPx()
            // Kutu hâlenin tamamına göre daraltılıyor; yalnızca çekirdek
            // çizgiye göre daraltılsaydı dış katman kırpılır ve sayaç halkası
            // kenarlarından kesik görünürdü.
            val inset = glowExtent(stroke, RING_GLOW_SPREAD)
            val diameter = size.minDimension - inset * 2
            val ring = Size(diameter, diameter)
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            drawArc(
                color = color.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = ring,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Sayacın kalan kısmı hâleyle: kod okunurken göz zaten oraya
            // bakıyor ve kalan sürenin azaldığı hâlenin sönmesinden de
            // okunuyor.
            drawGlowArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                topLeft = topLeft,
                arcSize = ring,
                width = stroke
            )
        }
        Text(
            text = label.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
