package app.kasa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.core.util.Haptics
import app.kasa.core.util.PasswordGenerator
import app.kasa.data.SettingsStore
import app.kasa.ui.GeneratorViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.ExpressiveSlider
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaCard
import app.kasa.ui.components.KasaSwitch
import app.kasa.ui.components.MorphDial
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.SplitButton
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaTheme

private enum class GeneratorMode { PASSWORD, PASSPHRASE, PRONOUNCEABLE }

/**
 * Üretici ekranı.
 *
 * Ekranın ortasındaki kadran parolanın gücünü **biçimle** anlatıyor: zayıf
 * parolada dikenli ve hızlı dönen bir yıldız, güçlü parolada yavaş dönen
 * yumuşak bir çakıl. Yüzde ya da renk çubuğundan farklı olarak bu, bakmadan
 * da fark edilen bir sinyal.
 */
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    settings: SettingsStore.Settings,
    onUseForNewEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val passphrase = state.settings.generatorPassphrase
    val pronounceable = state.settings.generatorPronounceable
    val mode = when {
        pronounceable -> GeneratorMode.PRONOUNCEABLE
        passphrase -> GeneratorMode.PASSPHRASE
        else -> GeneratorMode.PASSWORD
    }
    val strength by animateFloatAsState(
        targetValue = state.strength,
        animationSpec = KasaMotion.large(),
        label = "genStrength"
    )

    // Kadranın rengi ve üstündeki yazının rengi birlikte seçilir; karanlık
    // modda zemin koyulaştığı için yazı da açık tona geçmeli.
    val dialColor = when {
        state.strength > 0.5f -> KasaTheme.colors.badgeStrongBg
        state.strength > 0.28f -> KasaTheme.colors.badgeMidBg
        else -> KasaTheme.colors.badgeWeakBg
    }
    val dialTextColor = when {
        state.strength > 0.5f -> KasaTheme.colors.badgeStrongFg
        state.strength > 0.28f -> KasaTheme.colors.badgeMidFg
        else -> KasaTheme.colors.badgeWeakFg
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = listContentPadding()
    ) {
        item(key = "hero") {
            HeroHeader(
                title = stringResource(R.string.gen_title),
                subtitle = stringResource(R.string.gen_sub)
            )
        }

        item(key = "dial") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 236.dp)
                    .padding(top = 6.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                MorphDial(
                    strength = strength,
                    color = dialColor,
                    modifier = Modifier.size(236.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.value.ifBlank { "·········" },
                        style = KasaTheme.text.generatedPassword,
                        color = dialTextColor,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(state.label),
                        style = KasaTheme.text.sectionLabel,
                        color = dialTextColor.copy(alpha = 0.78f)
                    )
                }
            }
        }

        item(key = "actions") {
            SplitButton(
                text = stringResource(R.string.gen_regen),
                onPrimary = {
                    viewModel.haptic(Haptics.Kind.MEDIUM)
                    viewModel.regenerate()
                },
                onSecondary = { viewModel.copy(settings.clipboardClearSeconds) },
                leading = {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                secondaryContent = {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.gen_copy),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }

        item(key = "meta") {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, start = 6.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.gen_entropy, state.entropyBits.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                val crack = viewModel.crackTime()
                Text(
                    stringResource(
                        R.string.gen_crack,
                        crack.arg?.let { stringResource(crack.textRes, it) } ?: stringResource(crack.textRes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
            }
        }

        item(key = "panel") {
            KasaCard(
                modifier = Modifier.padding(top = 18.dp),
                padding = 20.dp
            ) {
                KasaButtonGroup(
                    options = GeneratorMode.entries.toList(),
                    selected = mode,
                    label = {
                        stringResource(
                            when (it) {
                                GeneratorMode.PASSWORD -> R.string.gen_mode_password
                                GeneratorMode.PASSPHRASE -> R.string.gen_mode_passphrase
                                GeneratorMode.PRONOUNCEABLE -> R.string.gen_mode_pronounceable
                            }
                        )
                    },
                    onSelect = { selected ->
                        when (selected) {
                            GeneratorMode.PASSWORD -> {
                                viewModel.setPassphrase(false)
                                viewModel.setPronounceable(false)
                            }
                            GeneratorMode.PASSPHRASE -> viewModel.setPassphrase(true)
                            GeneratorMode.PRONOUNCEABLE -> viewModel.setPronounceable(true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        stringResource(
                            when (mode) {
                                GeneratorMode.PASSPHRASE -> R.string.gen_words
                                GeneratorMode.PRONOUNCEABLE -> R.string.gen_syllables
                                GeneratorMode.PASSWORD -> R.string.gen_length
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = KasaTheme.colors.ink
                    )
                    Text(
                        text = when (mode) {
                            GeneratorMode.PASSPHRASE -> state.settings.generatorWordCount
                            GeneratorMode.PRONOUNCEABLE -> state.settings.generatorSyllables
                            GeneratorMode.PASSWORD -> state.settings.generatorLength
                        }.toString(),
                        style = KasaTheme.text.mono,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                ExpressiveSlider(
                    value = when (mode) {
                        GeneratorMode.PASSPHRASE -> state.settings.generatorWordCount
                        GeneratorMode.PRONOUNCEABLE -> state.settings.generatorSyllables
                        GeneratorMode.PASSWORD -> state.settings.generatorLength
                    },
                    range = when (mode) {
                        GeneratorMode.PASSPHRASE ->
                            PasswordGenerator.MIN_WORDS..PasswordGenerator.MAX_WORDS
                        GeneratorMode.PRONOUNCEABLE ->
                            PasswordGenerator.MIN_SYLLABLES..PasswordGenerator.MAX_SYLLABLES
                        GeneratorMode.PASSWORD ->
                            PasswordGenerator.MIN_LENGTH..PasswordGenerator.MAX_LENGTH
                    },
                    onValueChange = {
                        when (mode) {
                            GeneratorMode.PASSPHRASE -> viewModel.setWordCount(it)
                            GeneratorMode.PRONOUNCEABLE -> viewModel.setSyllables(it)
                            GeneratorMode.PASSWORD -> viewModel.setLength(it)
                        }
                    },
                    onDragEnd = { viewModel.haptic(Haptics.Kind.TICK) }
                )

                Spacer(Modifier.height(10.dp))

                if (pronounceable) {
                    ToggleRow(
                        title = stringResource(R.string.gen_append_digits),
                        subtitle = stringResource(R.string.gen_append_digits_sub),
                        checked = state.settings.generatorDigits,
                        onCheckedChange = viewModel::setDigits,
                        first = true
                    )
                } else if (passphrase) {
                    ToggleRow(
                        title = stringResource(R.string.gen_capitalize),
                        checked = state.settings.generatorCapitalize,
                        onCheckedChange = viewModel::setCapitalize,
                        first = true
                    )
                    ToggleRow(
                        title = stringResource(R.string.gen_num),
                        subtitle = stringResource(R.string.gen_num_sub),
                        checked = state.settings.generatorDigits,
                        onCheckedChange = viewModel::setDigits
                    )
                    SeparatorRow(
                        selected = state.settings.generatorSeparator,
                        onSelect = viewModel::setSeparator
                    )
                } else {
                    ToggleRow(
                        title = stringResource(R.string.gen_upper),
                        subtitle = stringResource(R.string.gen_upper_sub),
                        checked = state.settings.generatorUpper,
                        onCheckedChange = viewModel::setUpper,
                        first = true
                    )
                    ToggleRow(
                        title = stringResource(R.string.gen_num),
                        subtitle = stringResource(R.string.gen_num_sub),
                        checked = state.settings.generatorDigits,
                        onCheckedChange = viewModel::setDigits
                    )
                    ToggleRow(
                        title = stringResource(R.string.gen_sym),
                        subtitle = stringResource(R.string.gen_sym_sub),
                        checked = state.settings.generatorSymbols,
                        onCheckedChange = viewModel::setSymbols
                    )
                    ToggleRow(
                        title = stringResource(R.string.gen_clear),
                        subtitle = stringResource(R.string.gen_clear_sub),
                        checked = state.settings.generatorAvoidLookalikes,
                        onCheckedChange = viewModel::setAvoidLookalikes
                    )
                }
            }
        }

        item(key = "use") {
            KasaButton(
                text = stringResource(R.string.add_login),
                onClick = { onUseForNewEntry(state.value) },
                tone = ButtonTone.TONAL,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }

        if (history.isNotEmpty()) {
            item(key = "history-label") {
                Spacer(Modifier.height(20.dp))
                SectionLabel(stringResource(R.string.gen_history), count = history.size)
            }
            items(history.size) { index ->
                val value = history[index]
                Text(
                    text = value,
                    style = KasaTheme.text.mono.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    color = KasaTheme.colors.ink2,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
            item(key = "history-clear") {
                KasaButton(
                    text = stringResource(R.string.delete),
                    onClick = viewModel::clearHistory,
                    tone = ButtonTone.OUTLINED,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

/** Ayar satırı: başlık, açıklama ve anahtar. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    first: Boolean = false,
    enabled: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        if (!first) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) KasaTheme.colors.ink else KasaTheme.colors.ink3
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink3
                    )
                }
            }
            KasaSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun SeparatorRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("-", ".", "_", " ")
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.gen_separator),
            style = MaterialTheme.typography.titleSmall,
            color = KasaTheme.colors.ink,
            modifier = Modifier.weight(1f)
        )
        KasaButtonGroup(
            options = options,
            selected = selected,
            label = { if (it == " ") "␣" else it },
            onSelect = onSelect
        )
    }
}
