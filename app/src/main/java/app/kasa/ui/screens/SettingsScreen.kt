package app.kasa.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.BuildConfig
import app.kasa.R
import app.kasa.data.ThemeMode
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.SettingsViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaBadge
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaCard
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaTheme

/**
 * Ayarlar ekranı.
 *
 * Sıralama bilinçli: önce her gün dokunulan görünüm ve cihaz anahtarları,
 * sonra güvenlik eşikleri, en sonda geri dönüşü olmayan kasa işlemleri.
 * Yıkıcı işlem (kasayı sil) en altta ve tek başına duruyor.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recoveryCode by viewModel.recoveryCode.collectAsStateWithLifecycle()
    val changeResult by viewModel.changeResult.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val gate = LocalBiometricGate.current

    var showChangeMaster by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingImportPassword by remember { mutableStateOf<CharArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && password != null) viewModel.exportTo(uri, password)
        else password?.fill('\u0000')
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val password = pendingImportPassword
        pendingImportPassword = null
        if (uri != null && password != null) viewModel.importFrom(uri, password)
        else password?.fill('\u0000')
    }

    val actions = listOf(
        VaultAction(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.set_change_master),
            subtitle = stringResource(R.string.set_change_master_sub, relativeTime(viewModel.masterKeyChangedAt())),
            background = KasaTheme.colors.badgeStrongBg,
            foreground = KasaTheme.colors.badgeStrongFg
        ) { showChangeMaster = true },
        VaultAction(
            icon = Icons.Rounded.Key,
            title = stringResource(R.string.set_recovery),
            subtitle = stringResource(R.string.set_recovery_sub),
            background = KasaTheme.colors.badgeMidBg,
            foreground = KasaTheme.colors.badgeMidFg
        ) { viewModel.regenerateRecoveryKey() },
        VaultAction(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.set_export),
            subtitle = stringResource(R.string.set_export_sub),
            background = KasaTheme.colors.badgeBlueBg,
            foreground = KasaTheme.colors.badgeBlueFg
        ) { showExport = true },
        VaultAction(
            icon = Icons.Rounded.Upload,
            title = stringResource(R.string.set_import),
            subtitle = stringResource(R.string.set_import_sub),
            background = KasaTheme.colors.badgeBlueBg,
            foreground = KasaTheme.colors.badgeBlueFg
        ) { showImport = true },
        VaultAction(
            icon = Icons.Rounded.DeleteForever,
            title = stringResource(R.string.set_wipe),
            subtitle = stringResource(R.string.set_wipe_sub),
            background = KasaTheme.colors.badgeWeakBg,
            foreground = KasaTheme.colors.badgeWeakFg
        ) { showWipe = true }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item(key = "hero") {
            HeroHeader(
                title = stringResource(R.string.set_title),
                subtitle = stringResource(
                    R.string.set_sub,
                    if (viewModel.argon2Available) "Argon2id" else "PBKDF2-SHA512"
                )
            )
        }

        // ── görünüm ───────────────────────────────────────────────────────
        item(key = "appearance") {
            KasaCard {
                Text(
                    stringResource(R.string.set_group_appearance),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(14.dp))
                KasaButtonGroup(
                    options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                    selected = settings.theme,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.set_theme_system
                                ThemeMode.LIGHT -> R.string.set_theme_light
                                ThemeMode.DARK -> R.string.set_theme_dark
                            }
                        )
                    },
                    onSelect = viewModel::setTheme,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_dynamic),
                    subtitle = stringResource(R.string.set_dynamic_sub),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    first = true
                )
                ToggleRow(
                    title = stringResource(R.string.set_amoled),
                    subtitle = stringResource(R.string.set_amoled_sub),
                    checked = settings.pureBlack,
                    onCheckedChange = viewModel::setPureBlack
                )
            }
        }

        // ── cihaz ─────────────────────────────────────────────────────────
        item(key = "device") {
            Spacer(Modifier.height(14.dp))
            KasaCard {
                Text(
                    stringResource(R.string.set_group_device),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_haptic),
                    subtitle = stringResource(R.string.set_haptic_sub),
                    checked = settings.haptics,
                    onCheckedChange = viewModel::setHaptics,
                    first = true
                )
                ToggleRow(
                    title = stringResource(R.string.set_bio),
                    subtitle = stringResource(
                        if (gate?.available == false) R.string.set_bio_unavailable else R.string.set_bio_sub
                    ),
                    checked = settings.biometricUnlock,
                    enabled = gate?.available == true,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            viewModel.disableBiometric()
                        } else {
                            val cipher = viewModel.biometricAvailableCipher()
                            if (cipher != null && gate != null) {
                                gate.authenticate(
                                    title = context.getString(R.string.onb_biometric_title),
                                    subtitle = context.getString(R.string.onb_biometric_sub),
                                    negativeButton = context.getString(R.string.cancel),
                                    cipher = cipher,
                                    onSuccess = viewModel::onBiometricEnrolled
                                )
                            }
                        }
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.set_fill),
                    subtitle = stringResource(R.string.set_fill_sub),
                    checked = isAutofillEnabled(context),
                    onCheckedChange = { openAutofillSettings(context) }
                )
                ToggleRow(
                    title = stringResource(R.string.set_screenshot),
                    subtitle = stringResource(R.string.set_screenshot_sub),
                    checked = settings.blockScreenshots,
                    onCheckedChange = viewModel::setBlockScreenshots
                )
            }
        }

        // ── güvenlik ──────────────────────────────────────────────────────
        item(key = "security") {
            Spacer(Modifier.height(14.dp))
            KasaCard {
                Text(
                    stringResource(R.string.set_group_security),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    stringResource(R.string.set_autolock),
                    style = MaterialTheme.typography.titleSmall,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_autolock_sub, durationLabel(settings.autoLockSeconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(10.dp))
                KasaButtonGroup(
                    options = listOf(0, 30, 60, 300),
                    selected = settings.autoLockSeconds,
                    label = { durationLabel(it) },
                    onSelect = viewModel::setAutoLockSeconds,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.set_clip, settings.clipboardClearSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_clip_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(10.dp))
                KasaButtonGroup(
                    options = listOf(0, 15, 30, 60),
                    selected = settings.clipboardClearSeconds,
                    label = { if (it == 0) stringResource(R.string.dur_never) else stringResource(R.string.dur_seconds_short, it) },
                    onSelect = viewModel::setClipboardSeconds,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.set_wipe_attempts, settings.wipeAfterAttempts),
                    style = MaterialTheme.typography.titleSmall,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_wipe_attempts_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(10.dp))
                KasaButtonGroup(
                    options = listOf(0, 5, 10, 20),
                    selected = settings.wipeAfterAttempts,
                    label = { if (it == 0) stringResource(R.string.dur_never) else it.toString() },
                    onSelect = viewModel::setWipeAfterAttempts,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_online),
                    subtitle = stringResource(R.string.set_online_sub),
                    checked = settings.onlineBreachCheck,
                    onCheckedChange = viewModel::setOnlineBreachCheck,
                    first = true
                )

                Spacer(Modifier.height(10.dp))
                KasaButton(
                    text = stringResource(R.string.set_lock_now),
                    onClick = viewModel::lockNow,
                    tone = ButtonTone.TONAL,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── kasa işlemleri ────────────────────────────────────────────────
        item(key = "vault-label") {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.set_group_vault))
        }

        items(actions.size) { index ->
            val action = actions[index]
            ActionTile(action, groupPositionOf(index, actions.size))
        }

        // ── hakkında ──────────────────────────────────────────────────────
        item(key = "about") {
            Spacer(Modifier.height(26.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.set_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.set_crypto_line,
                        if (viewModel.argon2Available) "Argon2id" else "PBKDF2-SHA512",
                        stringResource(
                            if (viewModel.hardwareBackedKey) R.string.set_hardware_key
                            else R.string.set_software_key
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // ── pencereler ────────────────────────────────────────────────────────

    if (showChangeMaster) {
        ChangeMasterDialog(
            busy = busy,
            error = changeResult is SettingsViewModel.ChangeResult.WrongCurrent,
            onConfirm = { current, new -> viewModel.changeMasterPassword(current, new) },
            onDismiss = {
                showChangeMaster = false
                viewModel.clearChangeResult()
            }
        )
    }

    LaunchedEffect(changeResult) {
        if (changeResult is SettingsViewModel.ChangeResult.Success) {
            showChangeMaster = false
            viewModel.clearChangeResult()
        }
    }

    recoveryCode?.let { code ->
        RecoveryCodeDialog(
            code = code,
            onCopy = { viewModel.copyRecoveryCode(code, settings.clipboardClearSeconds) },
            onDismiss = viewModel::dismissRecoveryCode
        )
    }

    if (showExport) {
        PasswordPromptDialog(
            title = stringResource(R.string.exp_title),
            description = stringResource(R.string.exp_sub),
            label = stringResource(R.string.exp_password),
            confirmText = stringResource(R.string.exp_create),
            requireConfirmation = true,
            onConfirm = { password ->
                showExport = false
                pendingExportPassword = password
                viewModel.suppressAutoLockForPicker()
                exportLauncher.launch("kasa-yedek.kasa")
            },
            onDismiss = { showExport = false }
        )
    }

    if (showImport) {
        PasswordPromptDialog(
            title = stringResource(R.string.imp_title),
            description = stringResource(R.string.imp_sub),
            label = stringResource(R.string.imp_password),
            confirmText = stringResource(R.string.imp_pick),
            onConfirm = { password ->
                showImport = false
                pendingImportPassword = password
                viewModel.suppressAutoLockForPicker()
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showImport = false }
        )
    }

    if (showWipe) {
        TypeToConfirmDialog(
            title = stringResource(R.string.wipe_title),
            body = stringResource(R.string.wipe_body),
            hint = stringResource(R.string.wipe_confirm_hint),
            word = stringResource(R.string.wipe_confirm_word),
            confirmText = stringResource(R.string.set_wipe),
            onConfirm = {
                showWipe = false
                viewModel.wipeVault { }
            },
            onDismiss = { showWipe = false }
        )
    }
}

private class VaultAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val background: Color,
    val foreground: Color,
    val onClick: () -> Unit
)

@Composable
private fun ActionTile(action: VaultAction, position: GroupPosition) {
    KasaTile(position = position, onClick = action.onClick) {
        KasaBadge(background = action.background, foreground = action.foreground) {
            Icon(action.icon, contentDescription = null, tint = action.foreground, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(action.title, style = KasaTheme.text.tileName, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink3)
        }
    }
}

@Composable
private fun ChangeMasterDialog(
    busy: Boolean,
    error: Boolean,
    onConfirm: (CharArray, CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val mismatch = confirm.isNotEmpty() && new != confirm
    val valid = current.isNotEmpty() && new.length >= 12 && new == confirm && !busy

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.chg_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(16.dp))
            app.kasa.ui.components.KasaPasswordField(
                value = current,
                onValueChange = { current = it },
                label = stringResource(R.string.chg_current),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                isError = error,
                supportingText = if (error) stringResource(R.string.chg_wrong_current) else null
            )
            Spacer(Modifier.height(8.dp))
            app.kasa.ui.components.KasaPasswordField(
                value = new,
                onValueChange = { new = it },
                label = stringResource(R.string.chg_new),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed }
            )
            Spacer(Modifier.height(8.dp))
            app.kasa.ui.components.KasaPasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.chg_new_again),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                isError = mismatch,
                supportingText = if (mismatch) stringResource(R.string.onb_mismatch) else null
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                KasaButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
                KasaButton(
                    text = stringResource(if (busy) R.string.chg_working else R.string.save),
                    onClick = { onConfirm(current.toCharArray(), new.toCharArray()) },
                    enabled = valid,
                    height = 46.dp
                )
            }
        }
    }
}

@Composable
private fun RecoveryCodeDialog(code: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.onb_recovery_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.onb_recovery_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(18.dp)
            ) {
                Text(
                    code,
                    style = KasaTheme.text.mono,
                    color = KasaTheme.colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                KasaButton(
                    text = stringResource(R.string.copy),
                    onClick = onCopy,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
                KasaButton(
                    text = stringResource(R.string.onb_recovery_saved),
                    onClick = onDismiss,
                    height = 46.dp
                )
            }
        }
    }
}

/** Kasa, sistemde etkin otomatik doldurma servisi mi? */
private fun isAutofillEnabled(context: android.content.Context): Boolean = try {
    val manager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
    manager?.hasEnabledAutofillServices() == true
} catch (t: Throwable) {
    false
}

private fun openAutofillSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
