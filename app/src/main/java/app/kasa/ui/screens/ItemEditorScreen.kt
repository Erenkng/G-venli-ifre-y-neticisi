package app.kasa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.core.util.PasswordGenerator
import app.kasa.core.util.PasswordStrength
import app.kasa.core.util.Totp
import app.kasa.data.VaultStore
import app.kasa.data.model.Category
import app.kasa.data.model.CustomField
import app.kasa.data.model.VaultItem
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaChip
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.components.KasaTextField
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.theme.KasaTheme

/**
 * Kayıt ekleme/düzenleme ekranı.
 *
 * Kategori üstte değiştirilebilir; alanlar buna göre yeniden düzenlenir ama
 * girilen değerler korunur, çünkü kullanıcı çoğu zaman "aslında bu bir kart
 * değil not" diye fikir değiştirir ve yazdıklarını kaybetmemeli.
 */
@Composable
fun ItemEditorScreen(
    initial: VaultItem,
    viewModel: VaultViewModel,
    onScanQr: ((String) -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var item by remember(initial.id) { mutableStateOf(initial) }
    var revealed by remember(initial.id) { mutableStateOf(initial.password.isEmpty()) }
    var nameTouched by remember(initial.id) { mutableStateOf(false) }

    val isNew = !viewModel.isExisting(initial.id)
    val nameError = nameTouched && item.name.isBlank()
    val strength = if (item.password.isBlank()) null else PasswordStrength.evaluate(item.password)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
    ) {
        // ── üst çubuk ─────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KasaIconButton(onClick = onClose, contentDescription = stringResource(R.string.close)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = KasaTheme.colors.ink2,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = stringResource(if (isNew) R.string.editor_new_title else R.string.editor_edit_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink,
                modifier = Modifier.weight(1f)
            )
            KasaButton(
                text = stringResource(R.string.save),
                onClick = {
                    nameTouched = true
                    if (item.name.isNotBlank()) viewModel.save(item.copy(name = item.name.trim()))
                },
                height = 44.dp
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            KasaButtonGroup(
                options = listOf(Category.LOGIN, Category.CARD, Category.NOTE, Category.OTP),
                selected = item.category,
                label = { categoryLabel(it) },
                onSelect = { item = item.copy(category = it) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            KasaTextField(
                value = item.name,
                onValueChange = { item = item.copy(name = it); nameTouched = true },
                label = stringResource(R.string.editor_name),
                isError = nameError,
                supportingText = if (nameError) stringResource(R.string.editor_name_required) else null
            )
            Spacer(Modifier.height(8.dp))

            when (item.category) {
                Category.LOGIN -> {
                    KasaTextField(
                        value = item.username,
                        onValueChange = { item = item.copy(username = it) },
                        label = stringResource(R.string.field_username),
                        keyboardType = KeyboardType.Email
                    )
                    Spacer(Modifier.height(8.dp))
                    PasswordEditor(
                        value = item.password,
                        onValueChange = { item = item.copy(password = it) },
                        revealed = revealed,
                        onToggleReveal = { revealed = !revealed },
                        strength = strength
                    )
                    Spacer(Modifier.height(8.dp))
                    KasaTextField(
                        value = item.url,
                        onValueChange = { item = item.copy(url = it) },
                        label = stringResource(R.string.field_url),
                        keyboardType = KeyboardType.Uri,
                        placeholder = stringResource(R.string.editor_url_hint)
                    )
                    Spacer(Modifier.height(8.dp))
                    TotpEditor(item = item, onChange = { item = it }, onScanQr = onScanQr)
                }

                Category.CARD -> {
                    KasaTextField(
                        value = item.cardNumber,
                        onValueChange = { item = item.copy(cardNumber = it) },
                        label = stringResource(R.string.field_card_number),
                        keyboardType = KeyboardType.Number,
                        textStyle = KasaTheme.text.mono
                    )
                    Spacer(Modifier.height(8.dp))
                    KasaTextField(
                        value = item.cardHolder,
                        onValueChange = { item = item.copy(cardHolder = it) },
                        label = stringResource(R.string.field_card_holder)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            KasaTextField(
                                value = item.cardExpiry,
                                onValueChange = { item = item.copy(cardExpiry = it) },
                                label = stringResource(R.string.field_card_expiry),
                                placeholder = stringResource(R.string.editor_expiry_hint),
                                keyboardType = KeyboardType.Number,
                                textStyle = KasaTheme.text.mono
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            KasaTextField(
                                value = item.cardCvv,
                                onValueChange = { item = item.copy(cardCvv = it) },
                                label = stringResource(R.string.field_card_cvv),
                                keyboardType = KeyboardType.NumberPassword,
                                textStyle = KasaTheme.text.mono
                            )
                        }
                    }
                }

                Category.NOTE -> {
                    KasaTextField(
                        value = item.notes,
                        onValueChange = { item = item.copy(notes = it) },
                        label = stringResource(R.string.field_notes),
                        singleLine = false,
                        minLines = 6,
                        imeAction = ImeAction.Default
                    )
                }

                Category.OTP -> {
                    KasaTextField(
                        value = item.username,
                        onValueChange = { item = item.copy(username = it) },
                        label = stringResource(R.string.field_username)
                    )
                    Spacer(Modifier.height(8.dp))
                    TotpEditor(item = item, onChange = { item = it }, onScanQr = onScanQr, expanded = true)
                }
            }

            if (item.category != Category.NOTE) {
                Spacer(Modifier.height(8.dp))
                KasaTextField(
                    value = item.notes,
                    onValueChange = { item = item.copy(notes = it) },
                    label = stringResource(R.string.field_notes),
                    singleLine = false,
                    minLines = 3,
                    imeAction = ImeAction.Default
                )
            }

            Spacer(Modifier.height(8.dp))
            KasaTextField(
                value = item.tags.joinToString(", "),
                onValueChange = { raw ->
                    item = item.copy(tags = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                },
                label = stringResource(R.string.field_tags),
                placeholder = stringResource(R.string.editor_tags_hint)
            )

            // ── özel alanlar ──────────────────────────────────────────────
            if (item.customFields.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.editor_custom_fields), count = item.customFields.size)
            }
            item.customFields.forEachIndexed { index, field ->
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        KasaTextField(
                            value = field.value,
                            onValueChange = { newValue ->
                                item = item.copy(
                                    customFields = item.customFields.toMutableList().also {
                                        it[index] = field.copy(value = newValue)
                                    }
                                )
                            },
                            label = field.key
                        )
                    }
                    KasaIconButton(
                        onClick = {
                            item = item.copy(customFields = item.customFields.filterIndexed { i, _ -> i != index })
                        },
                        contentDescription = stringResource(R.string.delete)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = KasaTheme.colors.ink3,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            AddCustomFieldRow { key, secret ->
                item = item.copy(customFields = item.customFields + CustomField(key, "", secret))
            }
        }
    }
}

@Composable
private fun PasswordEditor(
    value: String,
    onValueChange: (String) -> Unit,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    strength: PasswordStrength.Result?
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column {
        KasaPasswordField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.field_password),
            revealed = revealed,
            onRevealToggle = onToggleReveal,
            imeAction = ImeAction.Next,
            trailingExtra = {
                KasaIconButton(
                    onClick = {
                        onValueChange(
                            PasswordGenerator.generate(PasswordGenerator.Options()).value
                        )
                    },
                    size = 36.dp,
                    contentDescription = stringResource(R.string.editor_generate)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
        if (strength != null) {
            Spacer(Modifier.height(8.dp))
            WavyProgress(
                progress = strength.score,
                color = strengthColor(strength.tone),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.gen_entropy, strength.entropyBits.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun TotpEditor(
    item: VaultItem,
    onChange: (VaultItem) -> Unit,
    onScanQr: ((String) -> Unit) -> Unit,
    expanded: Boolean = false
) {
    val invalid = item.totpSecret.isNotBlank() && !VaultStore.isValidTotpSecret(item.totpSecret)

    Column {
        KasaTextField(
            value = item.totpSecret,
            onValueChange = { onChange(item.copy(totpSecret = it.uppercase().filter { c -> !c.isWhitespace() })) },
            label = stringResource(R.string.editor_totp_secret),
            textStyle = KasaTheme.text.mono,
            isError = invalid,
            supportingText = if (invalid) stringResource(R.string.editor_totp_invalid) else null,
            trailing = {
                KasaIconButton(
                    onClick = {
                        onScanQr { raw ->
                            val config = Totp.parseUri(raw)
                            if (config != null) {
                                onChange(
                                    item.copy(
                                        totpSecret = config.secret,
                                        totpDigits = config.digits,
                                        totpPeriod = config.period,
                                        totpAlgorithm = config.algorithm,
                                        name = item.name.ifBlank { config.issuer },
                                        username = item.username.ifBlank { config.account }
                                    )
                                )
                            } else {
                                onChange(item.copy(totpSecret = raw.uppercase()))
                            }
                        }
                    },
                    size = 36.dp,
                    accent = expanded,
                    contentDescription = stringResource(R.string.editor_scan_qr)
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        )
    }
}

@Composable
private fun AddCustomFieldRow(onAdd: (String, Boolean) -> Unit) {
    var key by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) {
            KasaTextField(
                value = key,
                onValueChange = { key = it },
                label = stringResource(R.string.editor_custom_field_name),
                placeholder = stringResource(R.string.editor_custom_hint)
            )
        }
        KasaChip(text = stringResource(R.string.editor_add_field), onClick = {
            if (key.isNotBlank()) {
                onAdd(key.trim(), true)
                key = ""
            }
        })
    }
}
