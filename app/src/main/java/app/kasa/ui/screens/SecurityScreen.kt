package app.kasa.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.data.repo.SecurityAnalyzer
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
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    settings: SettingsStore.Settings,
    onOpenCollection: (SmartFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report = state.report
    val score = report?.score ?: 0
    val animatedScore by animateFloatAsState(score / 100f, label = "score")

    val scoreTone = when {
        score >= 80 -> KasaTheme.colors.strengthStrong
        score >= 50 -> KasaTheme.colors.strengthMid
        else -> KasaTheme.colors.strengthWeak
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBarSpacing()),
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
            KasaCard(tinted = true) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sec_score),
                            style = MaterialTheme.typography.titleLarge,
                            color = KasaTheme.colors.ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(KasaTheme.text.score.toSpanStyle().copy(color = KasaTheme.colors.ink)) {
                                    append(score.toString())
                                }
                                withStyle(
                                    MaterialTheme.typography.titleLarge.toSpanStyle()
                                        .copy(color = KasaTheme.colors.ink3)
                                ) {
                                    append("/100")
                                }
                            },
                            style = KasaTheme.text.score
                        )
                    }
                    ScanShape(
                        scanning = state.scanning,
                        color = if (state.scanning) KasaTheme.colors.badgeMidBg else KasaTheme.colors.badgeStrongBg,
                        modifier = Modifier.size(120.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                WavyProgress(
                    progress = if (state.scanning) state.progress else animatedScore,
                    color = scoreTone,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (report == null || report.affectedCount == 0)
                        stringResource(R.string.sec_note_clean)
                    else stringResource(R.string.sec_note, report.affectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink2
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
                    .clip(RoundedCornerShape(KasaRadius.m))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
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

        val findings = report?.findings.orEmpty()
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
    }

    val title = stringResource(
        when (finding.type) {
            SecurityAnalyzer.FindingType.LEAKED -> R.string.finding_leaked_title
            SecurityAnalyzer.FindingType.REUSED -> R.string.finding_reused_title
            SecurityAnalyzer.FindingType.WEAK -> R.string.finding_weak_title
            SecurityAnalyzer.FindingType.OLD -> R.string.finding_old_title
            SecurityAnalyzer.FindingType.NO_2FA -> R.string.finding_no2fa_title
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
        }
    )
    val actionLabel = stringResource(
        when (finding.type) {
            SecurityAnalyzer.FindingType.LEAKED, SecurityAnalyzer.FindingType.WEAK -> R.string.finding_action_fix
            SecurityAnalyzer.FindingType.OLD -> R.string.finding_action_remind
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
            .clip(shape)
            .background(KasaTheme.colors.tile)
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
    SecurityAnalyzer.FindingType.NO_2FA -> SmartFolder.NO_2FA
}
