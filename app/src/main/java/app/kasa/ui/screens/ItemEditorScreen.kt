package app.kasa.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.core.crypto.SecretText
import app.kasa.core.util.PasswordGenerator
import app.kasa.core.util.PasswordStrength
import app.kasa.core.util.Totp
import app.kasa.data.VaultStore
import app.kasa.data.model.Attachment
import app.kasa.data.model.Category
import app.kasa.data.model.CategorySchema
import app.kasa.data.model.CustomField
import app.kasa.data.model.FieldKind
import app.kasa.data.model.Folder
import app.kasa.data.model.VaultItem
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.readablePane
import app.kasa.ui.components.kasaBackdrop
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaChip
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.components.KasaTextField
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.TotpDisplay
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
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

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()

    // Ekler formda değil, doğrudan kasada tutuluyor: eklenen dosya anında
    // yazılıyor, formun eski kopyası onu ezmiyor.
    val stored = data.items.firstOrNull { it.id == item.id }

    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.attachFile(item.id, uri) }

    // Formun tamamı tek bir `item` durumunda duruyor, yani "değişti mi"
    // sorusu tek bir karşılaştırma. SecretText içerik eşitliği tanımladığı
    // için parola alanı da doğru cevaplıyor.
    val dirty = item != initial
    var confirmDiscard by remember(initial.id) { mutableStateOf(false) }

    // Kapatma isteğinin tek kapısı. Geri tuşu, geri hareketi ve çarpı
    // düğmesi buradan geçiyor; üçünün ayrı ayrı sorması, birinin sormayı
    // unutması demekti.
    val requestClose: () -> Unit = {
        if (dirty) confirmDiscard = true else onClose()
    }

    // Yalnızca kaydedilmemiş değişiklik varken kayıtlı. Boşken devre dışı
    // kalması önemli: etkin bir düz işleyici, MainScaffold'daki parmağa bağlı
    // geri hareketini tamamen bastırırdı — bu ekran daha sonra bestelendiği
    // için sırada o kazanıyor.
    BackHandler(enabled = dirty) { confirmDiscard = true }

    val isNew = !viewModel.isExisting(initial.id)
    val nameError = nameTouched && item.name.isBlank()
    val strength = if (item.password.isBlank()) null else PasswordStrength.evaluate(item.password.reveal())

    Column(
        modifier = modifier
            .fillMaxSize()
            // Uygulamanın kendi zemini. Eskiden düz `surface` boyanıyordu ve
            // düzenleyici uygulamanın tek gradyansız ekranıydı; dahası cam
            // metin girişlerinin arkasında gösterecek bir şey kalmıyordu.
            .kasaBackdrop()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            // Zemin ekranın tamamını kaplıyor, form ortalanıyor: geniş
            // pencerede alan bloklarının bir metreye yayılması, yazılanı
            // okumayı kolaylaştırmıyor.
            .readablePane()
    ) {
        // ── üst çubuk ─────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KasaIconButton(onClick = requestClose, contentDescription = stringResource(R.string.close)) {
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
            // Türe özel başlık: yazılan şey anında görünüyor.
            when (item.category) {
                Category.LOGIN -> LoginHero(item, Modifier.padding(bottom = 16.dp))
                Category.OTP -> OtpHero(
                    item = item,
                    onScanQr = onScanQr,
                    // Karekod bir `otpauth://` bağlantısı taşıyorsa hane
                    // sayısı ve periyot da oradan geliyor; yalnızca anahtarı
                    // almak, sekiz haneli kod üreten bir servisi sessizce
                    // altı haneye düşürüyordu.
                    onSecretScanned = { scanned ->
                        item = when (val read = Totp.read(scanned)) {
                            is Totp.Input.Uri -> item.withTotp(read.config)
                            is Totp.Input.Secret -> item.copy(totpSecret = read.text)
                            else -> item
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Category.IDENTITY -> IdentityHero(item, Modifier.padding(bottom = 16.dp))
                else -> Unit
            }

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
                        value = item.password.reveal(),
                        onValueChange = { item = item.copy(password = SecretText.of(it)) },
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

                Category.CARD -> CardEditorFields(item = item, onChange = { item = it })

                Category.OTP -> {
                    KasaTextField(
                        value = item.username,
                        onValueChange = { item = item.copy(username = it) },
                        label = stringResource(R.string.field_username)
                    )
                    Spacer(Modifier.height(8.dp))
                    TotpEditor(item = item, onChange = { item = it }, onScanQr = onScanQr, expanded = true)
                }

                // Şema tabanlı türler: alanlar CategorySchema'dan geliyor,
                // burada tür başına ayrı arayüz yazmak gerekmiyor.
                else -> SchemaFields(
                    item = item,
                    revealed = revealed,
                    onToggleReveal = { revealed = !revealed },
                    onChange = { item = it }
                )
            }

            Spacer(Modifier.height(8.dp))
            KasaTextField(
                value = item.notes,
                onValueChange = { item = item.copy(notes = it) },
                label = stringResource(R.string.field_notes),
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Default
            )

            Spacer(Modifier.height(8.dp))
            KasaTextField(
                value = item.tags.joinToString(", "),
                onValueChange = { raw ->
                    item = item.copy(tags = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                },
                label = stringResource(R.string.field_tags),
                placeholder = stringResource(R.string.editor_tags_hint)
            )

            Spacer(Modifier.height(8.dp))
            FolderPicker(
                folders = folders,
                selectedId = item.folderId,
                onSelect = { item = item.copy(folderId = it) },
                onCreate = { name -> viewModel.createFolder(name) }
            )

            // ── kayıt bazlı ek kilit ──────────────────────────────────────
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = stringResource(R.string.editor_require_auth),
                subtitle = stringResource(R.string.editor_require_auth_sub),
                checked = item.requireAuth,
                onCheckedChange = { item = item.copy(requireAuth = it) },
                first = true
            )

            // ── ekler ─────────────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            AttachmentEditor(
                item = stored ?: item,
                enabled = stored != null,
                onAdd = {
                    viewModel.suppressAutoLockForPicker()
                    attachLauncher.launch(arrayOf("*/*"))
                },
                onRemove = { attachmentId -> viewModel.removeAttachment(item.id, attachmentId) }
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

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            body = stringResource(
                if (isNew) R.string.editor_discard_new else R.string.editor_discard_body
            ),
            confirmText = stringResource(R.string.editor_discard_confirm),
            dismissText = stringResource(R.string.editor_discard_keep),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false }
        )
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

/**
 * İkinci faktör anahtarı.
 *
 * ### Yapıştırılan her biçim kabul ediliyor
 *
 * Servisler anahtarı üç ayrı biçimde veriyor ve hangisinin geleceğine
 * kullanıcı karar vermiyor: karekodun altındaki `otpauth://` bağlantısı,
 * dörtlü gruplara ayrılmış bir Base32 anahtarı, ya da onaltılık bir tohum.
 * Alan eskiden yalnızca boşluksuz Base32 kabul ediyordu; ötekini yapıştıran
 * kullanıcı kırmızı bir çerçeve görüyor ve kaydı hiç ekleyemiyordu.
 *
 * Bağlantı yapıştırıldığında yalnızca anahtar değil hane sayısı, periyot ve
 * özet algoritması da doluyor. Bunlar ayrı alanlar olsaydı kullanıcı bir
 * karekodun içindeki `digits=8`i elle taşımak zorunda kalırdı — ve
 * taşımadığında kodlar sessizce yanlış çıkardı.
 *
 * ### Neden kodu burada gösteriyor
 *
 * Anahtarın doğru olup olmadığı ancak ürettiği kod servisinkiyle
 * karşılaştırılınca anlaşılıyor. Önizleme olmadan kullanıcı kaydı kaydedip
 * ayrıntı sayfasını açmak, orada yanlışsa geri dönmek zorundaydı; yani
 * denemek için kaydetmek gerekiyordu.
 */
@Composable
private fun TotpEditor(
    item: VaultItem,
    onChange: (VaultItem) -> Unit,
    onScanQr: ((String) -> Unit) -> Unit,
    expanded: Boolean = false
) {
    // Toplu dışa aktarma bağlantısı geçerli bir şey — yalnızca başka bir şey.
    // "Geçersiz anahtar" demek, kullanıcıya elindekinin bozuk olduğunu
    // söylemek olurdu.
    var migration by remember { mutableStateOf(false) }
    val invalid = !migration && item.totpSecret.isNotBlank() &&
        !VaultStore.isValidTotpSecret(item.totpSecret)
    val valid = item.totpSecret.isNotBlank() && VaultStore.isValidTotpSecret(item.totpSecret)

    Column {
        KasaTextField(
            value = item.totpSecret,
            onValueChange = { raw ->
                val read = Totp.read(raw)
                migration = read is Totp.Input.Migration
                when (read) {
                    is Totp.Input.Uri -> onChange(item.withTotp(read.config))
                    is Totp.Input.Secret -> onChange(item.copy(totpSecret = read.text))
                    // Yarım yazılmış bir bağlantı da alanda durabilmeli:
                    // metin olduğu gibi giriyor, geçerliliği kırmızıyla
                    // söyleniyor ve tamamlandığı anda alanlara açılıyor.
                    else -> onChange(item.copy(totpSecret = raw.trim()))
                }
            },
            label = stringResource(R.string.editor_totp_secret),
            placeholder = stringResource(R.string.editor_totp_hint),
            textStyle = KasaTheme.text.mono,
            isError = invalid,
            supportingText = when {
                migration -> stringResource(R.string.editor_totp_migration)
                invalid -> stringResource(R.string.editor_totp_invalid)
                else -> null
            },
            trailing = {
                KasaIconButton(
                    onClick = {
                        onScanQr { raw ->
                            when (val read = Totp.read(raw)) {
                                is Totp.Input.Uri -> {
                                    migration = false
                                    onChange(item.withTotp(read.config))
                                }
                                is Totp.Input.Secret -> {
                                    migration = false
                                    onChange(item.copy(totpSecret = read.text))
                                }
                                Totp.Input.Migration -> migration = true
                                else -> Unit
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

        // Anahtar okunur okunmaz kod görünüyor: kullanıcı kaydetmeden önce
        // servisin gösterdiğiyle karşılaştırabiliyor.
        //
        // OTP türünde gösterilmiyor: orada kodu zaten başlıktaki kart
        // taşıyor ve aynı sayı iki kez yazılırdı.
        AnimatedVisibility(
            visible = valid && !expanded,
            enter = fadeIn(KasaMotion.enter()) + expandVertically(KasaMotion.medium()),
            exit = fadeOut(KasaMotion.exit()) + shrinkVertically(KasaMotion.medium())
        ) {
            Column(Modifier.padding(top = 10.dp, start = 6.dp, end = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.editor_totp_preview),
                        style = KasaTheme.text.sectionLabel,
                        color = KasaTheme.colors.ink3
                    )
                    // Varsayılan dışı bir ayar varsa görünür olmalı: aynı
                    // anahtardan sekiz haneli kod bekleyen bir servis, altı
                    // hane gösteren bir uygulamayla hiç eşleşmiyor.
                    if (item.totpDigits != 6 || item.totpPeriod != 30 || item.totpAlgorithm != "SHA1") {
                        Text(
                            stringResource(
                                R.string.editor_totp_params,
                                item.totpDigits,
                                item.totpPeriod,
                                item.totpAlgorithm
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = KasaTheme.colors.ink3
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                TotpDisplay(
                    secret = item.totpSecret,
                    digits = item.totpDigits,
                    period = item.totpPeriod,
                    algorithm = item.totpAlgorithm
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.editor_totp_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
            }
        }
    }
}

/**
 * Bağlantıdan gelen yapılandırmayı kayda uygular.
 *
 * Ad ve hesap yalnızca **boşsa** doluyor: kullanıcının yazdığı adı bir
 * karekodun etiketiyle değiştirmek, onun kararını geri almak olurdu.
 */
private fun VaultItem.withTotp(config: Totp.Config): VaultItem = copy(
    totpSecret = config.secret,
    totpDigits = config.digits,
    totpPeriod = config.period,
    totpAlgorithm = config.algorithm,
    name = name.ifBlank { config.issuer },
    username = username.ifBlank { config.account }
)

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

/**
 * Şema tabanlı türlerin alanları.
 *
 * Tek bir tanım listesinden hem klavye türü, hem maskeleme, hem çok satırlılık
 * çıkıyor. Yeni bir tür eklemek burada hiçbir değişiklik gerektirmiyor.
 */
@Composable
private fun SchemaFields(
    item: VaultItem,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onChange: (VaultItem) -> Unit
) {
    val fields = CategorySchema.fieldsFor(item.category)

    fields.forEachIndexed { index, def ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        val value = item.extras[def.key].orEmpty()
        val update: (String) -> Unit = { fresh ->
            onChange(item.copy(extras = item.extras.toMutableMap().apply { put(def.key, fresh) }))
        }

        when (def.kind) {
            FieldKind.SECRET, FieldKind.SECRET_MULTILINE -> KasaPasswordField(
                value = value,
                onValueChange = update,
                label = stringResource(def.labelRes),
                revealed = revealed,
                onRevealToggle = onToggleReveal,
                imeAction = ImeAction.Next
            )

            FieldKind.MULTILINE -> KasaTextField(
                value = value,
                onValueChange = update,
                label = stringResource(def.labelRes),
                singleLine = false,
                minLines = 4,
                imeAction = ImeAction.Default,
                textStyle = KasaTheme.text.mono
            )

            else -> KasaTextField(
                value = value,
                onValueChange = update,
                label = stringResource(def.labelRes),
                keyboardType = when (def.kind) {
                    FieldKind.NUMBER -> KeyboardType.Number
                    FieldKind.EMAIL -> KeyboardType.Email
                    else -> KeyboardType.Text
                },
                textStyle = if (def.kind == FieldKind.NUMBER) KasaTheme.text.mono else null
            )
        }
    }
}

/**
 * Klasör seçici.
 *
 * "Klasörsüz" ilk seçenek ve varsayılan: kullanıcıyı kayıt eklerken klasör
 * kurmaya zorlamak, klasörleri hiç kullanmayacak kişiye bedel yüklerdi.
 * Yeni klasör buradan da açılabiliyor, ayarlara gitmek gerekmiyor.
 */
@Composable
private fun FolderPicker(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.folder_field).uppercase(java.util.Locale("tr", "TR")),
            style = KasaTheme.text.fieldLabel,
            color = KasaTheme.colors.ink3,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        KasaButtonGroup(
            options = listOf<String?>(null) + folders.map { it.id },
            selected = selectedId,
            label = { id ->
                if (id == null) stringResource(R.string.folder_none)
                else folders.firstOrNull { it.id == id }?.name.orEmpty()
            },
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        if (creating) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    KasaTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = stringResource(R.string.folder_name),
                        imeAction = ImeAction.Done
                    )
                }
                KasaChip(
                    text = stringResource(R.string.folder_create),
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreate(newName)
                            newName = ""
                            creating = false
                        }
                    }
                )
            }
        } else {
            KasaChip(text = stringResource(R.string.folder_new), onClick = { creating = true })
        }
    }
}

/**
 * Ek listesi ve ekleme düğmesi.
 *
 * Kaydedilmemiş kayda ek eklenemiyor: ek diske hemen yazıldığı için, hiç
 * kaydedilmeyecek bir kaydın eki sahipsiz kalırdı. Kullanıcıya bunu
 * söylemek, sessizce devre dışı bir düğme göstermekten iyi.
 */
@Composable
private fun AttachmentEditor(
    item: VaultItem,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(
            stringResource(R.string.att_title),
            count = item.attachments.size.takeIf { it > 0 }
        )
        Spacer(Modifier.height(6.dp))

        item.attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                trailing = {
                    KasaIconButton(
                        onClick = { onRemove(attachment.id) },
                        size = 36.dp,
                        contentDescription = stringResource(R.string.att_remove)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = KasaTheme.colors.ink3,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(6.dp))
        }

        if (enabled) {
            KasaChip(text = stringResource(R.string.att_add), onClick = onAdd)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.att_note),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        } else {
            Text(
                stringResource(R.string.att_save_first),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

/** Ek satırı: ad, boyut ve sağda tek bir eylem. */
@Composable
fun AttachmentRow(
    attachment: Attachment,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.m))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = KasaTheme.colors.ink3,
            modifier = Modifier.size(18.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                attachment.name,
                style = MaterialTheme.typography.titleSmall,
                color = KasaTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatBytes(attachment.size),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
        }
        trailing()
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
