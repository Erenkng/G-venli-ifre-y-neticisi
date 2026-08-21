package app.kasa.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.kasa.data.GeneratorMode
import app.kasa.ui.components.KasaChip
import app.kasa.ui.components.clickableNoRipple
import app.kasa.ui.theme.KasaRadius
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


/**
 * Üretici ekranı.
 *
 * Ekranın ortasındaki kadran parolanın gücünü **biçimle** anlatıyor: zayıf
 * parolada dikenli ve hızlı dönen bir yıldız, güçlü parolada yavaş dönen
 * yumuşak bir çakıl. Yüzde ya da renk çubuğundan farklı olarak bu, bakmadan
 * da fark edilen bir sinyal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    settings: SettingsStore.Settings,
    onUseForNewEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val mode = state.settings.generatorMode
    // Uzunluk kaydırıcısı yalnızca "kaç birim" sorusu anlamlı olan kiplerde
    // var. Kullanıcı adı ve onaltılık anahtar kendi uzunluklarını kendileri
    // belirliyor; oraya kaydırıcı koymak, hiçbir şeyi değiştirmeyen bir
    // denetim göstermek olurdu.
    val hasLengthSlider = mode == GeneratorMode.PASSWORD ||
        mode == GeneratorMode.PASSPHRASE ||
        mode == GeneratorMode.PRONOUNCEABLE ||
        mode == GeneratorMode.PIN
    val strength by animateFloatAsState(
        targetValue = state.strength,
        animationSpec = KasaMotion.large(),
        label = "genStrength"
    )

    // Kadranın rengi ve üstündeki yazının rengi birlikte seçilir; karanlık
    // modda zemin koyulaştığı için yazı da açık tona geçmeli.
    // ── kadranın rengi ────────────────────────────────────────────────────
    //
    // Önceden kap renkleri (badgeStrongBg vb.) kullanılıyordu ve onlar
    // **üzerine yazı gelsin diye** seçilmiş tonlar: karanlık temada
    // #00504A gibi, neredeyse siyah bir jade. Kadranın üzerinde artık yazı
    // yok — biçimin kendisi ekranın konusu — ve o tonlar orada koyu bir leke
    // gibi duruyordu.
    //
    // Güç renkleri (strength*) iki temada da canlı: açıkta #0E8A6E,
    // karanlıkta #3FD9B4. Kadran bunlardan besleniyor ve içindeki degradeyi
    // MorphDial kendi üretiyor.
    val dialColor = when {
        state.strength > 0.5f -> KasaTheme.colors.strengthStrong
        state.strength > 0.28f -> KasaTheme.colors.strengthMid
        else -> KasaTheme.colors.strengthWeak
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
            // ── neden değer kadranın içinde değil ──────────────────────────
            //
            // Değer kadranın ortasında duruyordu ve iki şeyi birden
            // bozuyordu. Uzun bir sonuç — sözcük dizisi, 64 karakterlik
            // parola, altı satırlık kurtarma kodu — dönen biçimin içine
            // sığmıyor ve kırpılıyordu. Kopyalama ve yeniden üretme de
            // ekranın başka yerlerindeydi, yani "üret, bak, kopyala"
            // döngüsünün üç adımı üç ayrı yere dağılmıştı.
            //
            // Kadran şimdi yalnızca **gücü** anlatıyor: ne kadar dikenli ve
            // ne kadar hızlı döndüğü. Değer altında, kendi kabında, yanında
            // iki eylemiyle duruyor.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 190.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                MorphDial(
                    strength = strength,
                    color = dialColor,
                    modifier = Modifier.size(190.dp)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(state.label),
                        style = KasaTheme.text.sectionLabel,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.gen_entropy, state.entropyBits.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }
        }

        item(key = "value") {
            KasaCard(modifier = Modifier.padding(top = 12.dp), padding = 18.dp) {
                Text(
                    text = state.value.ifBlank { "·········" },
                    style = KasaTheme.text.generatedPassword,
                    color = KasaTheme.colors.ink,
                    // Kurtarma kodu seti çok satırlı; ötekiler tek satır.
                    // Sabit bir satır sınırı, kodların çoğunu gizlerdi.
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KasaButton(
                        text = stringResource(R.string.copy),
                        onClick = { viewModel.copy(settings.clipboardClearSeconds) },
                        height = 46.dp,
                        modifier = Modifier.weight(1f)
                    )
                    KasaButton(
                        text = stringResource(R.string.gen_regenerate),
                        onClick = viewModel::regenerate,
                        tone = ButtonTone.TONAL,
                        height = 46.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "panel") {
            KasaCard(
                modifier = Modifier.padding(top = 18.dp),
                padding = 20.dp
            ) {
                // Altı kip tek bir düğme grubuna sığmıyor; sarmalanan bir
                // yonga şeridi hem sığdırıyor hem de yeni kip eklendiğinde
                // düzeni bozmuyor.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeneratorMode.entries.forEach { option ->
                        KasaChip(
                            text = stringResource(generatorModeLabel(option)),
                            selected = mode == option,
                            onClick = { viewModel.setMode(option) }
                        )
                    }
                }

                if (hasLengthSlider) {
                    // Entropi hedefi açıkken uzunluk kullanıcının değil hedefin
                    // sonucu; kaydırıcıyı etkin bırakmak, dokunulduğunda hiçbir
                    // şey değiştirmeyen bir denetim olurdu.
                    val lengthLocked = mode == GeneratorMode.PASSWORD &&
                        state.settings.generatorEntropyTarget > 0

                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            stringResource(generatorAmountLabel(mode)),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (lengthLocked) KasaTheme.colors.ink3 else KasaTheme.colors.ink
                        )
                        Text(
                            text = generatorAmount(state.settings, mode).toString(),
                            style = KasaTheme.text.mono,
                            color = if (lengthLocked) KasaTheme.colors.ink3
                            else MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!lengthLocked) {
                        ExpressiveSlider(
                            value = generatorAmount(state.settings, mode),
                            range = generatorAmountRange(mode),
                            onValueChange = {
                                when (mode) {
                                    GeneratorMode.PASSPHRASE -> viewModel.setWordCount(it)
                                    GeneratorMode.PRONOUNCEABLE -> viewModel.setSyllables(it)
                                    GeneratorMode.PIN -> viewModel.setPinLength(it)
                                    else -> viewModel.setLength(it)
                                }
                            },
                            onStep = { viewModel.haptic(Haptics.Kind.TICK) },
                            onDragEnd = { viewModel.haptic(Haptics.Kind.TOGGLE) }
                        )
                    } else {
                        Text(
                            stringResource(R.string.gen_length_from_target),
                            style = MaterialTheme.typography.bodySmall,
                            color = KasaTheme.colors.ink3
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (mode == GeneratorMode.PRONOUNCEABLE) {
                    ToggleRow(
                        title = stringResource(R.string.gen_append_digits),
                        subtitle = stringResource(R.string.gen_append_digits_sub),
                        checked = state.settings.generatorDigits,
                        onCheckedChange = viewModel::setDigits,
                        first = true
                    )
                } else if (mode == GeneratorMode.PASSPHRASE) {
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
                } else if (mode == GeneratorMode.HEX) {
                    // Onaltılık anahtarda tek anlamlı seçenek bit sayısı.
                    // Karakter kümesi diye bir şey yok: hex, tanımı gereği
                    // 0-9a-f.
                    SectionLabel(stringResource(R.string.gen_hex_bits))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PasswordGenerator.HEX_BIT_CHOICES.forEach { bits ->
                            KasaChip(
                                text = "$bits bit",
                                selected = state.settings.generatorHexBits == bits,
                                onClick = { viewModel.setHexBits(bits) }
                            )
                        }
                    }
                } else if (mode == GeneratorMode.UUID || mode == GeneratorMode.RECOVERY) {
                    // İkisinin de ayarı yok: UUID'nin biçimi RFC'de sabit,
                    // kurtarma kodlarının alfabesi ve öbeklemesi okunabilirlik
                    // için seçildi. Ayar sunmak, değiştirilmemesi gereken bir
                    // şeyi değiştirilebilir göstermek olurdu.
                    Text(
                        stringResource(
                            if (mode == GeneratorMode.UUID) R.string.gen_uuid_hint
                            else R.string.gen_recovery_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink3
                    )
                } else if (mode == GeneratorMode.USERNAME || mode == GeneratorMode.PIN) {
                    // İkisinin de ayarı yok: kullanıcı adı sözlükten geliyor,
                    // PIN'in tek değişkeni uzunluk ve o yukarıda.
                    Text(
                        stringResource(
                            if (mode == GeneratorMode.PIN) R.string.gen_pin_hint
                            else R.string.gen_username_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink3
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

                    // Entropi hedefi: "20 karakter" bir güç ölçüsü değil.
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(stringResource(R.string.gen_entropy_target))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PasswordGenerator.ENTROPY_TARGETS.forEach { target ->
                            KasaChip(
                                text = if (target == 0) stringResource(R.string.gen_entropy_off)
                                else "$target bit",
                                selected = state.settings.generatorEntropyTarget == target,
                                onClick = { viewModel.setEntropyTarget(target) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.gen_batch),
                    subtitle = stringResource(R.string.gen_batch_sub),
                    checked = state.settings.generatorBatch,
                    onCheckedChange = viewModel::setBatch,
                    first = true
                )
            }
        }

        // ── toplu üretim seçenekleri ───────────────────────────────────────
        //
        // Seçenekler kadranın altında değil panelin altında: kadran "şu anki
        // değer"i gösteriyor ve seçenekler onu değiştiren bir denetim.
        if (state.alternatives.size > 1) {
            item(key = "batch") {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.gen_batch), count = state.alternatives.size)
            }
            items(state.alternatives.size, key = { "alt-" + it }) { index ->
                val option = state.alternatives[index]
                val selected = option == state.value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(KasaRadius.m))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickableNoRipple(role = Role.RadioButton) {
                            viewModel.selectAlternative(option)
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option,
                        style = KasaTheme.text.mono,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else KasaTheme.colors.ink2,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
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

/** Kip yongasının adı. */
private fun generatorModeLabel(mode: GeneratorMode): Int = when (mode) {
    GeneratorMode.PASSWORD -> R.string.gen_mode_password
    GeneratorMode.PASSPHRASE -> R.string.gen_mode_passphrase
    GeneratorMode.PRONOUNCEABLE -> R.string.gen_mode_pronounceable
    GeneratorMode.PIN -> R.string.gen_mode_pin
    GeneratorMode.USERNAME -> R.string.gen_mode_username
    GeneratorMode.HEX -> R.string.gen_mode_hex
    GeneratorMode.UUID -> R.string.gen_mode_uuid
    GeneratorMode.RECOVERY -> R.string.gen_mode_recovery
}

/** Kaydırıcının ne saydığı: karakter, sözcük, hece ya da hane. */
private fun generatorAmountLabel(mode: GeneratorMode): Int = when (mode) {
    GeneratorMode.PASSPHRASE -> R.string.gen_words
    GeneratorMode.PRONOUNCEABLE -> R.string.gen_syllables
    GeneratorMode.PIN -> R.string.gen_pin_length
    else -> R.string.gen_length
}

private fun generatorAmount(settings: SettingsStore.Settings, mode: GeneratorMode): Int = when (mode) {
    GeneratorMode.PASSPHRASE -> settings.generatorWordCount
    GeneratorMode.PRONOUNCEABLE -> settings.generatorSyllables
    GeneratorMode.PIN -> settings.generatorPinLength
    else -> settings.generatorLength
}

private fun generatorAmountRange(mode: GeneratorMode): IntRange = when (mode) {
    GeneratorMode.PASSPHRASE -> PasswordGenerator.MIN_WORDS..PasswordGenerator.MAX_WORDS
    GeneratorMode.PRONOUNCEABLE -> PasswordGenerator.MIN_SYLLABLES..PasswordGenerator.MAX_SYLLABLES
    GeneratorMode.PIN -> PasswordGenerator.MIN_PIN..PasswordGenerator.MAX_PIN
    else -> PasswordGenerator.MIN_LENGTH..PasswordGenerator.MAX_LENGTH
}
