package app.kasa.ui.screens

import app.kasa.ui.components.SheetBlurBehind
import app.kasa.ui.components.sheetGlassColor
import app.kasa.ui.components.CategoryHeroBand
import androidx.compose.foundation.layout.heightIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.core.util.Haptics
import app.kasa.core.util.PasswordStrength
import app.kasa.data.SettingsStore
import app.kasa.data.model.Attachment
import app.kasa.data.model.Category
import app.kasa.data.model.CategorySchema
import app.kasa.data.model.FieldKind
import app.kasa.data.model.VaultItem
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.FieldBlock
import app.kasa.ui.components.maskFade
import app.kasa.ui.components.rememberMaskFade
import app.kasa.data.model.CardBrand
import app.kasa.ui.components.CardFace
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.ToolbarAction
import app.kasa.ui.components.TotpDisplay
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Kayıt ayrıntısı alt sayfası.
 *
 * Parola varsayılan olarak maskelidir ve açılınca **kopyalanmaz**, yalnızca
 * görünür olur; kopyalama ayrı bir düğmedir ve panoyu zamanlayıcıyla temizler.
 * Bu ayrım bilinçli: "gördüm" ile "panoya koydum" iki farklı risk düzeyidir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: VaultItem,
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var revealed by remember(item.id) { mutableStateOf(false) }

    /**
     * Kayıt bazlı ek kilit. İşaretli kayıtta alanlar doğrulama gelene kadar
     * hiç çizilmiyor — maskeleyip "göster"e basılabilir bırakmak değil, çünkü
     * o durumda korunan şey yalnızca bir dokunuş uzakta olurdu.
     */
    var itemUnlocked by remember(item.id) { mutableStateOf(!item.requireAuth) }
    val gate = LocalBiometricGate.current
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    var historyOpen by remember(item.id) { mutableStateOf(false) }
    var pendingExport by remember(item.id) { mutableStateOf<Attachment?>(null) }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val attachment = pendingExport
        pendingExport = null
        if (uri != null && attachment != null) viewModel.exportAttachment(attachment, uri)
    }

    val tone = toneOf(item)
    val strength = if (item.primarySecret.isBlank()) 1f
    else PasswordStrength.evaluate(item.primarySecret).score
    val reuse = viewModel.reuseCount(item)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Cam levha: altındaki ekran görünür kalıyor ama okunmuyor.
        // Pencerenin arkası SheetBlurBehind ile bulanıklaşıyor; ikisi
        // birlikte Android 17'nin alt sayfa yüzeyini kuruyor.
        containerColor = sheetGlassColor(),

        contentColor = KasaTheme.colors.ink,
        shape = RoundedCornerShape(topStart = KasaRadius.xl, topEnd = KasaRadius.xl),
        dragHandle = { SheetHandle() }
    ) {
        SheetBlurBehind()
        Column(
            Modifier
                // Sayfa telefonun üst kenarına dayanmıyor; gerekçesi
                // sheetMaxContentHeight üzerinde yazılı.
                .heightIn(max = sheetMaxContentHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // ── kart yüzü ─────────────────────────────────────────────────
            //
            // Kart kaydı açıldığında ilk görülen şey kartın kendisi: numara,
            // ad ve son kullanma tek karede, cüzdandan çıkarılmış gibi. Aynı
            // bilgiyi alt alta üç satır olarak listelemek teknik olarak
            // yeterliydi ama kartı tanımayı okumaya bağlıyordu.
            //
            // Kilitli kayıtta çizilmiyor: kart yüzü zaten numarayı taşıyor,
            // doğrulamadan önce göstermek ek kilidi anlamsız kılardı.
            // ── türe özel başlık ──────────────────────────────────────────
            //
            // Kart kendi yüzüyle açılıyordu ama geri kalan yedi tür aynı
            // görünüyordu: bir rozet, bir ad ve altında alan blokları. Kaydı
            // açan kişi ne açtığını okuyarak anlamak zorunda kalıyordu.
            //
            // Her tür artık kendi başlığıyla geliyor. Anlatılan şey bilgi
            // değil **tanıma**: kart karta, kimlik belgeye, 2FA dönen bir
            // koda benziyor. Bilginin kendisi zaten aşağıdaki alanlarda.
            //
            // Kilitli kayıtta hiçbiri çizilmiyor: başlıklar gizli değeri
            // taşıyabiliyor (kart numarası, kimlik seri numarası) ve
            // doğrulamadan önce göstermek ek kilidi anlamsız kılardı.
            if (itemUnlocked) {
                when (item.category) {
                    Category.CARD -> {
                        Spacer(Modifier.height(6.dp))
                        CardFace(
                            item = item,
                            revealed = revealed,
                            // Kart alanlarının hepsi hassas: pano süreli temizleniyor.
                            onCopy = { viewModel.copySecret(it, settings.clipboardClearSeconds) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Category.LOGIN -> {
                        Spacer(Modifier.height(6.dp))
                        LoginHero(item)
                        Spacer(Modifier.height(6.dp))
                    }

                    Category.IDENTITY -> {
                        Spacer(Modifier.height(6.dp))
                        IdentityHero(item)
                        Spacer(Modifier.height(6.dp))
                    }

                    // Kalan türler ortak bir şeride düşüyor: simgesi, rengi ve
                    // türe göre seçilen tek satırlık özeti. Her biri için ayrı
                    // bir başlık çizmek, dört ekranı dört ayrı yerden bakıma
                    // muhtaç bırakırdı ve kazandıracağı şey yalnızca farklı
                    // görünmekti.
                    else -> {
                        Spacer(Modifier.height(6.dp))
                        CategoryHero(item)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── başlık ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item.category != Category.CARD) {
                    EntryBadge(item = item, size = 60.dp, cornerRadius = 21.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = KasaTheme.text.sheetTitle,
                        color = KasaTheme.colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            append(categoryLabel(item.category))
                            viewModel.folderName(item.folderId)?.let { append(" · ").append(it) }
                            append(" · ").append(relativeTime(item.passwordChangedAt))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = KasaTheme.colors.ink3
                    )
                }
                KasaIconButton(
                    onClick = { viewModel.toggleFavorite(item.id) },
                    contentDescription = stringResource(R.string.detail_favorite)
                ) {
                    Icon(
                        if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        tint = if (item.favorite) MaterialTheme.colorScheme.tertiary else KasaTheme.colors.ink3,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── alanlar ───────────────────────────────────────────────────
            if (!itemUnlocked) {
                LockedItemNotice(
                    onUnlock = {
                        val target = gate
                        if (target == null) {
                            itemUnlocked = true
                        } else {
                            target.authenticatePresence(
                                title = context.getString(R.string.item_lock_prompt_title),
                                subtitle = context.getString(R.string.item_lock_prompt_sub, item.name),
                                onSuccess = { itemUnlocked = true }
                            )
                        }
                    }
                )
            } else {
                when (item.category) {
                    Category.CARD -> CardFields(item, revealed, { revealed = !revealed }, viewModel, settings)
                    Category.OTP -> OtpFields(item, viewModel, settings)
                    Category.LOGIN -> LoginFields(item, revealed, { revealed = !revealed }, viewModel, settings)
                    else -> SchemaDetailFields(item, revealed, { revealed = !revealed }, viewModel, settings)
                }
            }

            if (item.url.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                FieldBlock(label = stringResource(R.string.field_url)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            item.url,
                            style = MaterialTheme.typography.bodyLarge,
                            color = KasaTheme.colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        KasaIconButton(
                            onClick = { openUrl(context, item.url) },
                            contentDescription = stringResource(R.string.detail_open_site)
                        ) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = KasaTheme.colors.ink2,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (item.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                FieldBlock(label = stringResource(R.string.field_notes)) {
                    Text(item.notes, style = MaterialTheme.typography.bodyLarge, color = KasaTheme.colors.ink)
                }
            }

            item.customFields.forEach { field ->
                Spacer(Modifier.height(8.dp))
                FieldBlock(label = field.key) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (field.secret && !revealed) "•".repeat(field.value.length.coerceAtMost(18)) else field.value,
                            style = if (field.secret) KasaTheme.text.mono else MaterialTheme.typography.bodyLarge,
                            color = KasaTheme.colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        CopyButton(
                            onClick = {
                                if (field.secret) viewModel.copySecret(field.value, settings.clipboardClearSeconds)
                                else viewModel.copyPlain(field.value)
                            }
                        )
                    }
                }
            }

            // ── güç göstergesi ────────────────────────────────────────────
            if (item.primarySecret.isNotBlank() && item.category != Category.OTP) {
                Spacer(Modifier.height(8.dp))
                FieldBlock(label = stringResource(R.string.field_strength, toneLabel(tone))) {
                    WavyProgress(
                        progress = strength,
                        color = strengthColor(tone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            item.breached -> stringResource(R.string.detail_leak_warning)
                            reuse > 0 -> stringResource(R.string.detail_reused_warning, reuse)
                            tone == PasswordStrength.Tone.MID -> stringResource(R.string.detail_mid_warning)
                            tone == PasswordStrength.Tone.WEAK -> stringResource(R.string.detail_mid_warning)
                            else -> stringResource(R.string.detail_ok_note)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = KasaTheme.colors.ink2
                    )
                }
            }

            // Geçmiş yalnızca sayı olarak duruyordu ve eski parolaya ulaşmanın
            // yolu yoktu; saklamanın tek sebebi geri dönebilmek olduğuna göre
            // ulaşılamayan bir geçmiş hiçbir işe yaramıyordu. Gerekçesi
            // PasswordHistorySheet üzerinde yazılı.
            if (item.history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FieldBlock(
                    label = stringResource(R.string.detail_history),
                    onClick = { historyOpen = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.detail_history_count, item.history.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KasaTheme.colors.ink2,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = KasaTheme.colors.ink3,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ── ekler ─────────────────────────────────────────────────────
            if (item.attachments.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.att_title), count = item.attachments.size)
                Spacer(Modifier.height(6.dp))
                item.attachments.forEach { attachment ->
                    AttachmentRow(attachment = attachment) {
                        KasaIconButton(
                            onClick = {
                                pendingExport = attachment
                                viewModel.suppressAutoLockForPicker()
                                exportLauncher.launch(attachment.name)
                            },
                            size = 36.dp,
                            contentDescription = stringResource(R.string.att_export)
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = KasaTheme.colors.ink2,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // ── araç çubuğu ───────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolbarAction(
                    onClick = {
                        // Kilitli kayıtta kopyalama da kapalı: panoya koymak,
                        // görmekten daha geniş bir izin.
                        if (itemUnlocked) {
                            viewModel.copySecret(item.primarySecret, settings.clipboardClearSeconds)
                        }
                    },
                    contentDescription = stringResource(R.string.copy)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(21.dp)
                    )
                }
                if (item.inTrash) {
                    ToolbarAction(
                        onClick = { viewModel.restoreFromTrash(item) },
                        contentDescription = stringResource(R.string.trash_restore)
                    ) {
                        Icon(
                            Icons.Rounded.RestoreFromTrash,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                } else {
                    ToolbarAction(onClick = onEdit, contentDescription = stringResource(R.string.edit)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                ToolbarAction(
                    onClick = {
                        viewModel.haptic(Haptics.Kind.THRESHOLD)
                        confirmDelete = true
                    },
                    danger = true,
                    contentDescription = stringResource(
                        if (item.inTrash) R.string.trash_delete_forever else R.string.detail_delete_to_trash
                    )
                ) {
                    Icon(
                        if (item.inTrash) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            if (!item.inTrash) {
                Spacer(Modifier.height(10.dp))
                KasaButton(
                    text = stringResource(R.string.detail_change_password),
                    onClick = onEdit,
                    tone = ButtonTone.OUTLINED,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (historyOpen) {
        PasswordHistorySheet(
            item = item,
            onCopy = { viewModel.copySecret(it, settings.clipboardClearSeconds) },
            onRestore = { entry ->
                viewModel.restorePassword(item, entry)
                historyOpen = false
            },
            onDismiss = { historyOpen = false }
        )
    }

    if (confirmDelete) {
        if (item.inTrash) {
            ConfirmDialog(
                title = stringResource(R.string.trash_purge_confirm),
                body = stringResource(R.string.trash_purge_body, item.name),
                confirmText = stringResource(R.string.trash_delete_forever),
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    viewModel.purge(item)
                },
                onDismiss = { confirmDelete = false }
            )
        } else {
            // Silme artık geri dönüşü olan bir işlem; onay metni de bunu söylüyor.
            ConfirmDialog(
                title = stringResource(R.string.detail_delete_confirm),
                body = stringResource(R.string.trash_empty_sub),
                confirmText = stringResource(R.string.detail_delete_to_trash),
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    viewModel.moveToTrash(item)
                },
                onDismiss = { confirmDelete = false }
            )
        }
    }
}

@Composable
private fun LoginFields(
    item: VaultItem,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings
) {
    if (item.username.isNotBlank()) {
        FieldBlock(label = stringResource(R.string.field_username)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    item.username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KasaTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CopyButton(onClick = { viewModel.copyPlain(item.username) })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (item.password.isNotBlank()) {
        SecretFieldBlock(
            label = stringResource(R.string.field_password),
            value = item.password.reveal(),
            revealed = revealed,
            onToggleReveal = onToggleReveal,
            onCopy = { viewModel.copySecret(item.password.reveal(), settings.clipboardClearSeconds) }
        )
    }

    if (item.totpSecret.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        OtpFields(item, viewModel, settings)
    }
}

@Composable
private fun CardFields(
    item: VaultItem,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings
) {
    // Numara, ad ve son kullanma kart yüzünde zaten duruyor. Buradaki blok
    // onları tekrar yazmak için değil, **kopyalanabilir** kılmak için var:
    // kart yüzü bir görsel, alan ise bir eylem.
    SecretFieldBlock(
        label = stringResource(R.string.field_card_number),
        value = item.cardNumber,
        maskedText = item.maskedCard(),
        revealed = revealed,
        onToggleReveal = onToggleReveal,
        onCopy = { viewModel.copySecret(item.cardNumber, settings.clipboardClearSeconds) }
    )

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            SecretFieldBlock(
                label = stringResource(R.string.field_card_cvv),
                value = item.cardCvv,
                revealed = revealed,
                onToggleReveal = onToggleReveal,
                onCopy = { viewModel.copySecret(item.cardCvv, settings.clipboardClearSeconds) },
                compact = true
            )
        }
        Box(Modifier.weight(1f)) {
            FieldBlock(label = stringResource(R.string.field_card_brand)) {
                Text(
                    CardBrand.detect(item.cardNumber).displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KasaTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Luhn sağlaması yalnızca tutmadığında konuşuyor. Doğru numaranın yanına
    // "geçerli" yazmak gürültü; yanlış yazılmış bir numaranın aylar sonra
    // ödeme anında keşfedilmesi ise gerçek bir zarar.
    if (CardBrand.luhnValid(item.cardNumber) == false) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.card_luhn_warning),
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.strengthMid
        )
    }
}

@Composable
private fun OtpFields(item: VaultItem, viewModel: VaultViewModel, settings: SettingsStore.Settings) {
    var current by remember(item.id) { mutableStateOf("") }
    FieldBlock(label = stringResource(R.string.field_totp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotpDisplay(
                secret = item.totpSecret,
                digits = item.totpDigits,
                period = item.totpPeriod,
                algorithm = item.totpAlgorithm,
                onCodeChange = { current = it },
                modifier = Modifier.weight(1f)
            )
            CopyButton(
                accent = true,
                onClick = {
                    val code = current.ifBlank {
                        app.kasa.core.util.Totp.code(
                            item.totpSecret, item.totpDigits, item.totpPeriod, item.totpAlgorithm
                        ).orEmpty()
                    }
                    viewModel.copySecret(code, settings.clipboardClearSeconds)
                }
            )
        }
    }
}

/** Maskeli değer + göster/kopyala düğmeleri. */
@Composable
private fun SecretFieldBlock(
    label: String,
    value: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    maskedText: String? = null,
    compact: Boolean = false,
    multiline: Boolean = false
) {
    // Basılı tutarken göster, bırakınca gizle.
    //
    // Gizli değere bakmanın çoğu sebebi bir saniyelik: "sonu 47 miydi?".
    // Göster'e basıp okuyup tekrar basmak iki dokunuş ve arada parola ekranda
    // açık kalıyor — kullanıcı unutursa kasa kilitlenene kadar öyle kalıyor.
    // Basılı tutma parmağın kalktığı anda kapanıyor; açık kalması imkânsız.
    var peeking by remember { mutableStateOf(false) }
    val visible = revealed || peeking

    // Noktalardan harfe geçiş odak üzerinden yapılıyor: metin önce
    // bulanıklaşıyor, takas en bulanık karede oluyor, sonra netleşiyor. Tek
    // karelik sert bir takas, gözün yeni metni baştan okumasını gerektiriyordu.
    // Gerekçenin uzunu rememberMaskFade üzerinde yazılı.
    val fade = rememberMaskFade(visible)
    val shown = if (fade.showPlain) value else maskedText ?: "•".repeat(value.length.coerceAtMost(18))
    val color by animateColorAsState(
        if (visible) KasaTheme.colors.ink else KasaTheme.colors.ink2,
        label = "secretColor"
    )

    FieldBlock(label = label, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = shown,
                style = KasaTheme.text.mono,
                color = color,
                maxLines = when {
                    compact -> 1
                    multiline -> 8
                    else -> 2
                },
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .maskFade(fade)
                    .pointerInput(value) {
                        // awaitPointerEventScope: basış başladığında aç,
                        // bittiğinde (parmak kalkınca ya da hareket iptal
                        // olunca) kapat. detectTapGestures'ın onPress'i de
                        // aynısını yapıyor ama tıklama/uzun basma ayrımına
                        // giriyor; burada gereken tek şey temasın süresi.
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                peeking = true
                                waitForUpOrCancellation()
                                peeking = false
                            }
                        }
                    }
            )
            app.kasa.ui.components.RevealButton(revealed = visible, onClick = onToggleReveal)
            if (!compact) CopyButton(accent = true, onClick = onCopy)
        }
    }
}

@Composable
private fun CopyButton(onClick: () -> Unit, accent: Boolean = false) {
    val label = stringResource(R.string.copy)
    KasaIconButton(onClick = onClick, accent = accent, size = 40.dp, contentDescription = label) {
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            tint = if (accent) MaterialTheme.colorScheme.onPrimary else KasaTheme.colors.ink2,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val normalized = if (url.contains("://")) url else "https://$url"
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(normalized))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Şema tabanlı türlerin ayrıntı görünümü.
 *
 * Gizli alanlar maskeli başlar ve kendi kopyala düğmelerini taşır; gizli
 * olmayanlar düz metin olarak görünür. Hangi alanın gizli olduğu tek bir
 * yerde, [CategorySchema] içinde tanımlı.
 */
@Composable
private fun SchemaDetailFields(
    item: VaultItem,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings
) {
    val fields = CategorySchema.fieldsFor(item.category)
        .filter { item.extras[it.key].isNullOrBlank().not() }

    fields.forEachIndexed { index, def ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        val value = item.extras[def.key].orEmpty()

        if (CategorySchema.isSecret(def.kind)) {
            SecretFieldBlock(
                label = stringResource(def.labelRes),
                value = value,
                revealed = revealed,
                onToggleReveal = onToggleReveal,
                onCopy = { viewModel.copySecret(value, settings.clipboardClearSeconds) },
                multiline = def.kind == FieldKind.SECRET_MULTILINE
            )
        } else {
            FieldBlock(label = stringResource(def.labelRes)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        value,
                        style = if (def.kind == FieldKind.MULTILINE) KasaTheme.text.mono
                        else MaterialTheme.typography.bodyLarge,
                        color = KasaTheme.colors.ink,
                        maxLines = if (def.kind == FieldKind.MULTILINE) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    CopyButton(onClick = { viewModel.copyPlain(value) })
                }
            }
        }
    }
}

/**
 * İşaretli kaydın yerine çizilen kapı.
 *
 * Alanların yerine geçiyor, üstlerine binmiyor: gizlenen değerin ekranda
 * hiç oluşmaması, maskelenmiş olarak durmasından daha güvenli.
 */
@Composable
private fun LockedItemNotice(onUnlock: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = KasaTheme.colors.ink2,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.item_lock_title),
            style = MaterialTheme.typography.titleSmall,
            color = KasaTheme.colors.ink
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.item_lock_body),
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink3,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        KasaButton(
            text = stringResource(R.string.item_lock_verify),
            onClick = onUnlock,
            height = 44.dp
        )
    }
}

/**
 * Şema tabanlı türlerin ortak başlığı.
 *
 * Türün kendi rengi ve simgesi büyük, yanında kaydın adı ve o tür için
 * anlamlı olan tek satır: banka hesabında banka adı, Wi-Fi'da ağ adı,
 * SSH anahtarında sunucu, lisansta ürün. Alt satır [CategorySchema] tarafından
 * zaten "listede görünecek alan" olarak işaretlenmiş olandan geliyor — yani
 * burada ikinci bir seçim yapılmıyor, var olan karar kullanılıyor.
 */
@Composable
private fun CategoryHero(item: VaultItem) {
    val (background, foreground) = categoryTint(item.category)
    val subtitle = CategorySchema.subtitle(item)

    CategoryHeroBand(background = background, height = 104.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = categoryIcon(item.category),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(40.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    categoryLabel(item.category).uppercase(),
                    style = KasaTheme.text.fieldLabel,
                    color = foreground.copy(alpha = 0.7f)
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = foreground,
                    maxLines = 1
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = foreground.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
