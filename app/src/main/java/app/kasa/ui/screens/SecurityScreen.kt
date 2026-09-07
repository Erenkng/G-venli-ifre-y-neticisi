package app.kasa.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.kasa.ui.components.HeaderCollapse
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import app.kasa.ui.components.staggeredReveal
import app.kasa.ui.components.REVEAL_WINDOW_MILLIS
import kotlinx.coroutines.delay
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.components.headerHandoff
import app.kasa.ui.components.glassSurface
import app.kasa.ui.components.ScoreRing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import app.kasa.data.model.ScoreEntry
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.SecurityAnalyzer
import app.kasa.data.model.Category
import app.kasa.data.model.SmartFolder
import app.kasa.ui.SecurityViewModel
import app.kasa.ui.components.EmptyState
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaCard
import androidx.compose.material.icons.rounded.CloudOff
import app.kasa.ui.components.KasaSwitch
import app.kasa.ui.components.KasaChip
import app.kasa.ui.components.ScanShape
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Güvenlik merkezi: kasa puanı, tarama ve bulgular.
 *
 * Puan tek başına bir sayı değil; her bulgu neyi ne kadar düşürdüğünü
 * gösteren bir eylem önerisiyle geliyor. "72/100" demek kullanıcıya bir şey
 * anlatmaz, "üç parolan sızıntıda, sırayla değiştir" anlatır.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    settings: SettingsStore.Settings,
    onOpenCollection: (SmartFolder) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Başlığın ne kadar yukarı çıktığı (0..1).
     *
     * Üstteki cam çubuk bu ekranın kardeşinde çiziliyor ve kaydırma durumu
     * burada duruyor; oran yukarı bildiriliyor. Çubuğun kendisi buraya
     * konulsaydı, ekranın kaydedilmiş kopyasının içine düşerdi ve kendi
     * bulanıklığını bulanıklaştırırdı.
     */
    onHeaderCollapse: (Float) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vaultData by viewModel.items.collectAsStateWithLifecycle()
    val vaultItems = vaultData.liveItems
    val history = vaultData.scoreHistory
    val report = state.report
    val score = report?.score ?: 0
    val animatedScore by animateFloatAsState(score / 100f, label = "score")

    val scoreTone = when {
        score >= 80 -> KasaTheme.colors.strengthStrong
        score >= 50 -> KasaTheme.colors.strengthMid
        else -> KasaTheme.colors.strengthWeak
    }

    val listState = rememberLazyListState()

    // Bulguların sıralı beliriş penceresi.
    //
    // Tarama bittiğinde liste tek karede doluyordu: kullanıcı "tarıyor"dan
    // "on dört bulgu"ya hiçbir ara olmadan geçiyor ve neyin ne zaman geldiğini
    // göremiyordu. Sırayla gelince olan şey tek bir olay — tarama sonucunu
    // yazıyor. Pencere her taramada yeniden açılıyor, çünkü her tarama yeni
    // bir sonuç.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.lastScanAt, state.scanning) {
        if (state.scanning) return@LaunchedEffect
        revealed = false
        delay(REVEAL_WINDOW_MILLIS)
        revealed = true
    }

    HeaderCollapse(listState, onHeaderCollapse)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = listContentPadding(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item(key = "hero") {
            HeroHeader(
                // Büyük başlık cam çubuğa devrederken küçülüp yukarı
                // çekiliyor: ikisi tek bir geçişin iki yarısı.
                modifier = Modifier.headerHandoff(listState),
                title = stringResource(R.string.sec_title),
                subtitle = if (state.lastScanAt > 0)
                    stringResource(R.string.sec_last_scan, relativeTime(state.lastScanAt))
                else stringResource(R.string.sec_never_scanned)
            )
        }

        item(key = "score") {
            // ── puan kartı ────────────────────────────────────────────────
            //
            // Önceden puan sol tarafta bir rakam, sağda ilgisiz bir tarama
            // şekli ve altta ayrı bir çubuktu: aynı bilgi üç ayrı yerde, üçü
            // de birbirine bakmadan. Şimdi rakam ölçeğin **içinde** duruyor ve
            // ekranın konusu ne olduğu tek bakışta anlaşılıyor.
            KasaCard(tinted = true) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ScoreRing(
                        progress = animatedScore,
                        color = scoreTone,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        scanning = state.scanning,
                        modifier = Modifier.size(184.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Puan tek karede belirmiyor.
                            //
                            // Halka tarama boyunca dönüyor ve bittiğinde
                            // ortasındaki tire bir anda sayıya dönüşüyordu:
                            // halkanın yaptığı bütün hazırlık, sayının
                            // gelişinde hiçbir karşılık bulmuyordu. Sayı artık
                            // alttan yükselerek geliyor, tire yukarı çıkıyor —
                            // yön "bir sonuç geldi" diyor.
                            val scoreIn: FiniteAnimationSpec<Float> = KasaMotion.enter()
                            val scoreOut: FiniteAnimationSpec<Float> = KasaMotion.exit()
                            val scoreSlide: FiniteAnimationSpec<IntOffset> = KasaMotion.medium()
                            AnimatedContent(
                                targetState = if (state.scanning) null else score,
                                transitionSpec = {
                                    (fadeIn(scoreIn) +
                                        slideInVertically(scoreSlide) { it / 2 })
                                        .togetherWith(
                                            fadeOut(scoreOut) +
                                                slideOutVertically(scoreSlide) { -it / 2 }
                                        )
                                },
                                label = "securityScore"
                            ) { shown ->
                                Text(
                                    buildAnnotatedString {
                                        withStyle(
                                            KasaTheme.text.score.toSpanStyle()
                                                .copy(color = KasaTheme.colors.ink)
                                        ) {
                                            // Tarama sürerken bilinmeyen bir
                                            // sayı göstermek yerine hiç
                                            // göstermemek.
                                            append(shown?.toString() ?: "—")
                                        }
                                        if (shown != null) {
                                            withStyle(
                                                MaterialTheme.typography.titleMedium.toSpanStyle()
                                                    .copy(color = KasaTheme.colors.ink3)
                                            ) {
                                                append("/100")
                                            }
                                        }
                                    },
                                    style = KasaTheme.text.score
                                )
                            }
                            Text(
                                stringResource(R.string.sec_score),
                                style = KasaTheme.text.sectionLabel,
                                color = KasaTheme.colors.ink3
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (report == null || report.affectedCount == 0)
                        stringResource(R.string.sec_note_clean)
                    else stringResource(R.string.sec_note, report.affectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                // ── puanın dökümü ─────────────────────────────────────
                //
                // Sayı tek başına bir yargıydı: 62 neden 62, hangi bulgu ne
                // kadar indiriyor, görünmüyordu. Hesap belirli olduğu için
                // saklamanın gerekçesi de yok. Gösterilince puan bir liste
                // oluyor ve kullanıcı hangi kolun sayıyı en çok oynatacağını
                // görüyor.
                if (report != null && report.scoredCount > 0 && !state.scanning) {
                    Spacer(Modifier.height(14.dp))
                    ScoreBreakdown(report = report)
                }

                Spacer(Modifier.height(16.dp))
                KasaButton(
                    text = stringResource(if (state.scanning) R.string.sec_scanning else R.string.sec_scan),
                    onClick = viewModel::scan,
                    enabled = !state.scanning,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── eğilim ────────────────────────────────────────────────────────
        //
        // İki noktadan azı bir eğilim değil; tek bir puanı çizgi olarak
        // göstermek, olmayan bir bilgiyi varmış gibi sunmak olurdu.
        if (history.size > 1) {
            item(key = "trend") {
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.sec_trend))
                Spacer(Modifier.height(8.dp))
                ScoreTrend(history = history, color = scoreTone)
            }
        }

        item(key = "online-note") {
            // ── çevrimiçi denetim ─────────────────────────────────────────
            //
            // Bu kart k-anonimliğin nasıl çalıştığını anlatıyordu ve **açık mı
            // kapalı mı** olduğunu söylemiyordu; ekranın aldığı `settings`
            // parametresi hiçbir yerde okunmuyordu. Denetimi kapatmış bir
            // kullanıcı, çalışmayan bir özelliğin açıklamasını okuyor ve puanı
            // sızıntı verisi olmadan hesaplanmış olmasına rağmen aynı
            // görünüyordu.
            //
            // `onlineCheckRan` da hesaplanıp hiçbir yere yazılmıyordu: uygulama
            // son taramanın gerçekten ağa çıkıp çıkmadığını biliyor ama
            // söylemiyordu. Ağ hatası ile "hiç denenmedi" arasındaki fark,
            // sızıntı bulgusunun ne kadar güvenilir olduğunu belirleyen şey.
            Spacer(Modifier.height(14.dp))
            val online = settings.onlineBreachCheck
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassSurface(RoundedCornerShape(KasaRadius.m), MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (online) Icons.Rounded.Shield else Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = if (online) MaterialTheme.colorScheme.primary else KasaTheme.colors.ink3,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sec_online_check),
                            style = MaterialTheme.typography.titleSmall,
                            color = KasaTheme.colors.ink
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(
                                if (online) R.string.sec_online_check_sub
                                else R.string.sec_online_check_off
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = KasaTheme.colors.ink3
                        )
                    }
                    KasaSwitch(checked = online, onCheckedChange = viewModel::setOnlineBreachCheck)
                }

                // Son taramanın ağa gerçekten çıkıp çıkmadığı.
                if (online && report != null && !report.onlineCheckRan) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.sec_online_check_not_run),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.badgeMidFg
                    )
                }
            }
        }

        // ── kasa istatistikleri ───────────────────────────────────────────
        //
        // Güvenlik ekranı şimdiye kadar yalnızca **yanlış** olanı gösteriyordu:
        // sızmış, zayıf, eski. Bir kasanın neye benzediği ise hiçbir yerde
        // yoktu. İstatistik kartı bunu veriyor ve bulgusuz kasada ekranın boş
        // kalmamasını da sağlıyor — "hiçbir bulgu yok" tek başına bir ekranı
        // doldurmuyor.
        item(key = "stats-label") {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.sec_stats))
        }
        item(key = "stats") {
            VaultStatsCard(items = vaultItems)
        }

        val findings = report?.findings.orEmpty()

        // ── bulgu şeridi ──────────────────────────────────────────────────
        //
        // Bulgular listesi uzun olabiliyor ve kullanıcının ilk sorusu "neyim
        // var" değil, "en çok neyim var". Şerit türleri sayılarıyla yan yana
        // koyuyor; dokununca doğrudan o kuralın listesine gidiyor, yani hem
        // özet hem gezinme.
        if (findings.isNotEmpty()) {
            item(key = "severity") {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    findings.forEach { finding ->
                        KasaChip(
                            text = stringResource(
                                findingChipLabel(finding.type),
                                finding.count
                            ),
                            onClick = { onOpenCollection(finding.type.asSmartFolder()) }
                        )
                    }
                }
            }
        }

        item(key = "findings-label") {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.sec_findings), count = findings.size)
        }

        if (findings.isEmpty()) {
            item(key = "clean") {
                EmptyState(
                    title = stringResource(R.string.sec_no_findings_title),
                    subtitle = stringResource(R.string.sec_no_findings_sub),
                    // Temiz bir kasa bir eksiklik değil, varılan yer: işaret
                    // ötekilerin aksine güç renginde.
                    icon = Icons.Rounded.VerifiedUser
                )
            }
        } else {
            items(findings.size) { index ->
                val finding = findings[index]
                FindingRow(
                    finding = finding,
                    position = groupPositionOf(index, findings.size),
                    // Bulgu artık bir sayı değil: dokununca o kurala uyan
                    // kayıtların listesine götürüyor.
                    onAction = { onOpenCollection(finding.type.asSmartFolder()) },
                    modifier = Modifier.staggeredReveal(step = index, play = !revealed)
                )
            }
        }
    }
}

/**
 * Puanın dökümü: taban ve inen cezalar.
 *
 * Yüzde çubuğu ya da pasta değil, düz bir liste. Cezaların büyüklüğü zaten
 * sayının kendisinde yazılı ve görsel bir ölçek, altı satırın hepsini
 * kıyaslanabilir kılmak yerine en büyüğüne bakmayı kolaylaştırırdı — oysa
 * kullanıcının merak ettiği "hangisi ne kadar", hepsi birlikte.
 */
@Composable
private fun ScoreBreakdown(report: SecurityAnalyzer.Report) {
    Column(Modifier.fillMaxWidth()) {
        BreakdownRow(
            label = stringResource(R.string.sec_score_base),
            value = "+" + report.basePoints,
            emphasis = true
        )
        report.deductions.forEach { deduction ->
            // Etiket akıllı klasör adından geliyor: onlar zaten sayısız,
            // düz isimler ("Sızmış", "Zayıf") ve bulgu satırlarıyla aynı
            // sözcükleri kullanmak, iki listeyi birbirine bağlıyor.
            BreakdownRow(
                label = smartFolderLabel(deduction.type.asSmartFolder()),
                value = "−" + deduction.points
            )
        }
        Spacer(Modifier.height(6.dp))
        // Puanın hangi kayıtlar üzerinden hesaplandığı.
        //
        // "Kasanın puanı" ile "kasanın ölçülebilir kısmının puanı" aynı şey
        // değil ve fark, not ya da kart ağırlıklı bir kasada büyük.
        Text(
            stringResource(R.string.sec_score_scope, report.scoredCount),
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink3
        )
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (emphasis) KasaTheme.colors.ink2 else KasaTheme.colors.ink3
        )
        Text(
            value,
            style = KasaTheme.text.mono.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (emphasis) KasaTheme.colors.ink2 else KasaTheme.colors.ink3
        )
    }
}

/**
 * Puanın zaman içindeki seyri.
 *
 * ### Neden eksen yok
 *
 * Okunacak şey mutlak değer değil **yön**: yukarı mı gidiyor, aşağı mı. Puan
 * zaten kartın ortasında büyük büyük yazılı. Eksen ve ızgara, üç santimetrelik
 * bir çizgiye kendisinden fazla yer kaplayan bir çerçeve eklerdi.
 *
 * Ölçek 0-100'e değil **verinin kendi aralığına** oturuyor: 71'den 78'e çıkan
 * bir kasa, 0-100 ölçeğinde düz bir çizgi olarak görünür ve o düzlük yanlış
 * bir haber olurdu.
 */
@Composable
private fun ScoreTrend(history: List<ScoreEntry>, color: Color) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val points = remember(history) { history.map { it.score } }
    val low = remember(points) { points.min() }
    val high = remember(points) { points.max() }

    KasaCard {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(TREND_HEIGHT)
        ) {
            if (points.size < 2) return@Canvas
            // Tamamen düz bir dizide bölme sıfıra düşer; o durumda çizgi
            // ortadan geçiyor, ki söylediği şey de bu: değişmemiş.
            val span = (high - low).coerceAtLeast(1)
            val stepX = size.width / (points.size - 1)
            val path = Path()
            points.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height - (value - low).toFloat() / span * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawLine(
                color = track,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Son nokta vurgulu: gözün "şu an buradayım" diye tutunduğu yer.
            val lastX = size.width
            val lastY = size.height - (points.last() - low).toFloat() / span * size.height
            drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                relativeTime(history.first().at),
                style = MaterialTheme.typography.labelSmall,
                color = KasaTheme.colors.ink3
            )
            Text(
                stringResource(R.string.sec_trend_range, low, high),
                style = MaterialTheme.typography.labelSmall,
                color = KasaTheme.colors.ink3
            )
        }
    }
}

/** Eğilim çizgisinin yüksekliği: yön okunacak kadar, yer kaplamayacak kadar. */
private val TREND_HEIGHT = 56.dp

/**
 * Kasanın sayılarla özeti.
 *
 * Seçilen dört ölçü, kullanıcının kasası hakkında gerçekten merak ettiği
 * şeyler: kaç kayıt var, kaçında iki adımlı doğrulama var, ortalama parola ne
 * kadar uzun ve en eski parola kaç yaşında. "Kaç klasör var" gibi bir sayı
 * doğru ama işe yaramaz olurdu.
 */
@Composable
private fun VaultStatsCard(items: List<VaultItem>) {
    val logins = remember(items) { items.filter { it.category == Category.LOGIN } }
    val withTotp = remember(items) { items.count { it.totpSecret.isNotBlank() } }
    val averageLength = remember(logins) {
        val lengths = logins.map { it.password.length }.filter { it > 0 }
        if (lengths.isEmpty()) 0 else lengths.sum() / lengths.size
    }
    val oldestDays = remember(logins) {
        val oldest = logins.filter { it.password.isNotBlank() }.minOfOrNull { it.passwordChangedAt }
        if (oldest == null) 0
        else ((System.currentTimeMillis() - oldest) / SecurityAnalyzer.DAY_MILLIS).toInt().coerceAtLeast(0)
    }

    KasaCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatCell(items.size.toString(), stringResource(R.string.stat_entries))
            StatCell(withTotp.toString(), stringResource(R.string.stat_with_2fa))
            StatCell(
                if (averageLength == 0) "—" else averageLength.toString(),
                stringResource(R.string.stat_avg_length)
            )
            StatCell(
                if (oldestDays == 0) "—" else oldestDays.toString(),
                stringResource(R.string.stat_oldest_days)
            )
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = KasaTheme.colors.ink
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = KasaTheme.colors.ink3,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FindingRow(
    finding: SecurityAnalyzer.Finding,
    position: GroupPosition,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KasaTheme.colors
    val (icon, background, foreground) = when (finding.type) {
        SecurityAnalyzer.FindingType.LEAKED ->
            Triple(Icons.Rounded.Warning, colors.badgeWeakBg, colors.badgeWeakFg)
        SecurityAnalyzer.FindingType.REUSED ->
            Triple(Icons.Rounded.Repeat, colors.badgeMidBg, colors.badgeMidFg)
        SecurityAnalyzer.FindingType.WEAK ->
            Triple(Icons.Rounded.Warning, colors.badgeMidBg, colors.badgeMidFg)
        SecurityAnalyzer.FindingType.OLD ->
            Triple(Icons.Rounded.History, colors.badgeBlueBg, colors.badgeBlueFg)
        SecurityAnalyzer.FindingType.NO_2FA ->
            Triple(Icons.Rounded.Shield, colors.badgeBlueBg, colors.badgeBlueFg)
        SecurityAnalyzer.FindingType.RENEW_DUE ->
            Triple(Icons.Rounded.Autorenew, colors.badgeMidBg, colors.badgeMidFg)
    }

    val title = stringResource(
        when (finding.type) {
            SecurityAnalyzer.FindingType.LEAKED -> R.string.finding_leaked_title
            SecurityAnalyzer.FindingType.REUSED -> R.string.finding_reused_title
            SecurityAnalyzer.FindingType.WEAK -> R.string.finding_weak_title
            SecurityAnalyzer.FindingType.OLD -> R.string.finding_old_title
            SecurityAnalyzer.FindingType.NO_2FA -> R.string.finding_no2fa_title
            SecurityAnalyzer.FindingType.RENEW_DUE -> R.string.finding_renew_title
        },
        finding.count
    )
    val description = stringResource(
        when (finding.type) {
            SecurityAnalyzer.FindingType.LEAKED -> R.string.finding_leaked_desc
            SecurityAnalyzer.FindingType.REUSED -> R.string.finding_reused_desc
            SecurityAnalyzer.FindingType.WEAK -> R.string.finding_weak_desc
            SecurityAnalyzer.FindingType.OLD -> R.string.finding_old_desc
            SecurityAnalyzer.FindingType.NO_2FA -> R.string.finding_no2fa_desc
            SecurityAnalyzer.FindingType.RENEW_DUE -> R.string.finding_renew_desc
        }
    )
    val actionLabel = stringResource(
        when (finding.type) {
            SecurityAnalyzer.FindingType.LEAKED, SecurityAnalyzer.FindingType.WEAK -> R.string.finding_action_fix
            SecurityAnalyzer.FindingType.OLD, SecurityAnalyzer.FindingType.RENEW_DUE ->
                R.string.finding_action_remind
            else -> R.string.finding_action_view
        }
    )

    val shape = when (position) {
        GroupPosition.ONLY -> RoundedCornerShape(KasaRadius.l)
        GroupPosition.FIRST -> RoundedCornerShape(
            topStart = KasaRadius.l, topEnd = KasaRadius.l,
            bottomStart = KasaRadius.xs, bottomEnd = KasaRadius.xs
        )
        GroupPosition.LAST -> RoundedCornerShape(
            topStart = KasaRadius.xs, topEnd = KasaRadius.xs,
            bottomStart = KasaRadius.l, bottomEnd = KasaRadius.l
        )
        GroupPosition.MIDDLE -> RoundedCornerShape(KasaRadius.xs)
    }

    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(shape, KasaTheme.colors.tile)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FindingIcon(icon, background, foreground)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink3)
            Spacer(Modifier.height(10.dp))
            KasaChip(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun FindingIcon(icon: ImageVector, background: Color, foreground: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
    }
}

/** Bulgu türünün karşılık geldiği kurallı klasör. */
private fun SecurityAnalyzer.FindingType.asSmartFolder(): SmartFolder = when (this) {
    SecurityAnalyzer.FindingType.LEAKED -> SmartFolder.LEAKED
    SecurityAnalyzer.FindingType.REUSED -> SmartFolder.REUSED
    SecurityAnalyzer.FindingType.WEAK -> SmartFolder.WEAK
    SecurityAnalyzer.FindingType.OLD -> SmartFolder.OLD
    // Yenileme zamanı gelenler ayrı bir koleksiyon değil: kullanıcı "eski
    // parolalar" görünümünde zaten onları da görüyor ve ayrı bir liste,
    // birbirini büyük ölçüde kapsayan iki görünüm demek olurdu.
    SecurityAnalyzer.FindingType.RENEW_DUE -> SmartFolder.RENEW_DUE
    SecurityAnalyzer.FindingType.NO_2FA -> SmartFolder.NO_2FA
}

/**
 * Bulgu şeridindeki kısa etiket.
 *
 * Uzun bulgu başlıklarının ("Bir yıldan eski parolalar") yan yana dizilmiş
 * hâli okunmuyor; şeritte tek sözcük ve sayı var, açıklaması aşağıdaki
 * satırda zaten duruyor.
 */
private fun findingChipLabel(type: SecurityAnalyzer.FindingType): Int = when (type) {
    SecurityAnalyzer.FindingType.LEAKED -> R.string.sec_chip_leaked
    SecurityAnalyzer.FindingType.REUSED -> R.string.sec_chip_reused
    SecurityAnalyzer.FindingType.WEAK -> R.string.sec_chip_weak
    SecurityAnalyzer.FindingType.OLD -> R.string.sec_chip_old
    SecurityAnalyzer.FindingType.NO_2FA -> R.string.sec_chip_no_2fa
    SecurityAnalyzer.FindingType.RENEW_DUE -> R.string.sec_chip_renew
}
