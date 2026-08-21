package app.kasa.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.kasa.ui.components.HeaderCollapse
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
    val report = state.report
    val score = report?.score ?: 0
    val animatedScore by animateFloatAsState(score / 100f, label = "score")

    val scoreTone = when {
        score >= 80 -> KasaTheme.colors.strengthStrong
        score >= 50 -> KasaTheme.colors.strengthMid
        else -> KasaTheme.colors.strengthWeak
    }

    val listState = rememberLazyListState()

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
                            Text(
                                buildAnnotatedString {
                                    withStyle(
                                        KasaTheme.text.score.toSpanStyle()
                                            .copy(color = KasaTheme.colors.ink)
                                    ) {
                                        // Tarama sürerken bilinmeyen bir sayı
                                        // göstermek yerine hiç göstermemek.
                                        append(if (state.scanning) "—" else score.toString())
                                    }
                                    if (!state.scanning) {
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
                Spacer(Modifier.height(16.dp))
                KasaButton(
                    text = stringResource(if (state.scanning) R.string.sec_scanning else R.string.sec_scan),
                    onClick = viewModel::scan,
                    enabled = !state.scanning,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "online-note") {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .glassSurface(RoundedCornerShape(KasaRadius.m), MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        stringResource(R.string.sec_online_check),
                        style = MaterialTheme.typography.titleSmall,
                        color = KasaTheme.colors.ink
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.sec_online_check_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink3
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
                    subtitle = stringResource(R.string.sec_no_findings_sub)
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
                    onAction = { onOpenCollection(finding.type.asSmartFolder()) }
                )
            }
        }
    }
}

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
    onAction: () -> Unit
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
        Modifier
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
    SecurityAnalyzer.FindingType.RENEW_DUE -> SmartFolder.OLD
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
