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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
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
 * Listenin ortak TOTP saati.
 *
 * ### Neden tek bir saat
 *
 * [TotpDisplay] kendi döngüsünü kuruyor ve bu, tek bir kaydın ayrıntı
 * sayfasında doğru. Listede aynı şeyi yapmak, ekranda TOTP taşıyan her satır
 * için saniyede iki kez uyanan ayrı bir coroutine ve ayrı bir animasyon
 * demekti — kaydırırken en pahalı olduğu anda.
 *
 * Saat bir kez kuruluyor ve saniyede bir ilerliyor. Değeri **çizim
 * aşamasında** okunuyor (halka) ya da [rememberTotpCode] üzerinden
 * `derivedStateOf` ile süzülüyor: kod otuz saniyede bir değiştiği için satır
 * da otuz saniyede bir bestelenmiş oluyor, saniyede bir değil.
 */
@Composable
fun rememberTotpClock(): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.longValue = System.currentTimeMillis()
            // Saniye sınırına hizalanıyor: sabit 1000 ms'lik uykular zamanla
            // kayıyor ve geri sayım bir saniyeyi atlayıp iki birden düşüyordu.
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return now
}

/**
 * Ortak saatten türetilen kod.
 *
 * `derivedStateOf` yalnızca **sayaç** değiştiğinde haber veriyor; sayaç
 * `zaman / periyot` olduğu için bu, kodun gerçekten değiştiği an demek.
 * Saati doğrudan okumak satırı saniyede bir yeniden besteleyecekti.
 */
@Composable
fun rememberTotpCode(
    clock: State<Long>,
    secret: String,
    digits: Int,
    period: Int,
    algorithm: String
): String {
    val slot by remember(clock, period) {
        derivedStateOf { clock.value / (period.coerceAtLeast(1) * 1000L) }
    }
    return remember(secret, digits, period, algorithm, slot) {
        Totp.code(secret, digits, period, algorithm).orEmpty()
    }
}

/**
 * Liste satırındaki TOTP kodu: rakamlar ve kalan süre halkası.
 *
 * ### Neden listede
 *
 * İki adımlı doğrulama kodu uygulamadaki en zamana duyarlı şey — otuz
 * saniyede ölüyor ve kullanıcı tam o an başka bir uygulamada giriş yapmaya
 * çalışıyor. Kodu görmek için kaydı bulup açmak gerekiyordu; yani en aceleci
 * anda en uzun yol.
 *
 * ### Neden kapatılabilir
 *
 * Kod ekranda durunca omuz üstünden okunabilir hâle geliyor. Tek başına bir
 * işe yaramıyor (parolası olmayan biri onunla hiçbir yere giremez) ama yine de
 * kullanıcının kararı olmalı; ayar [app.kasa.data.SettingsStore.Settings.totpInList].
 */
@Composable
fun RowTotpCode(
    clock: State<Long>,
    secret: String,
    digits: Int,
    period: Int,
    algorithm: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val code = rememberTotpCode(clock, secret, digits, period, algorithm)
    if (code.isEmpty()) return

    val safePeriod = period.coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    val weak = KasaTheme.colors.strengthWeak

    Row(
        modifier = modifier.clickableNoRipple { onCopy(code) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Rakamların rengi kalan süreyle değişmiyor.
        //
        // Değişseydi metnin rengi saniyede bir yeniden bestelenirdi ve
        // kazandıracağı şey halkanın zaten söylediğinin tekrarı olurdu.
        Text(
            text = Totp.pretty(code),
            style = KasaTheme.text.mono,
            color = KasaTheme.colors.ink
        )
        Canvas(Modifier.size(16.dp)) {
            // Saat çizim aşamasında okunuyor: halka saniyede bir yeniden
            // çiziliyor ama hiçbir şey yeniden bestelenmiyor.
            val remaining = safePeriod - (clock.value / 1000L % safePeriod)
            val fraction = remaining.toFloat() / safePeriod
            val color = if (remaining <= EXPIRY_SECONDS) weak else accent
            val stroke = 2.dp.toPx()
            val inset = stroke / 2f
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = color.copy(alpha = 0.22f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = box,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** Halkanın kırmızıya döndüğü eşik: kodu kopyalamak yerine beklemek gerek. */
private const val EXPIRY_SECONDS = 5

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
