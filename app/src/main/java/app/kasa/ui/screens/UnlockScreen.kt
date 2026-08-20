package app.kasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.ui.AuthViewModel
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.components.KasaPinField
import app.kasa.ui.components.KasaTextField
import app.kasa.ui.components.MorphDial
import app.kasa.ui.theme.KasaTheme

/**
 * Kilit ekranı.
 *
 * Biyometrik istem, ekran açılır açılmaz kendiliğinden gösterilir — tipik
 * kullanımda kullanıcının hiçbir şeye dokunmadan kasaya girmesi hedefleniyor.
 * İptal ederse ana parola alanı zaten hazır bekliyor.
 *
 * Yanlış denemelerde ekran, ne olduğunu saklamıyor: kalan deneme hakkı ve
 * bekleme süresi açıkça yazıyor. Gizlemek saldırganı yavaşlatmaz, yalnızca
 * gerçek kullanıcıyı paniğe sokar.
 */
@Composable
fun UnlockScreen(
    viewModel: AuthViewModel,
    settings: SettingsStore.Settings,
    modifier: Modifier = Modifier
) {
    val state by viewModel.unlock.collectAsStateWithLifecycle()
    val gate = LocalBiometricGate.current
    val context = LocalContext.current

    var password by remember { mutableStateOf("") }
    var recoveryInput by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var biometricTried by remember { mutableStateOf(false) }

    /**
     * PIN kuruluysa açılış PIN ile başlıyor: günde on kez yazılacak olan bu.
     * Ana parola bir dokunuş uzakta ve her zaman çalışıyor.
     */
    val pinLength = viewModel.pinLength
    var pinMode by remember { mutableStateOf(pinLength > 0) }
    var pin by remember { mutableStateOf("") }

    val blocked = state.cooldownMillis > 0
    val attemptsLeft = viewModel.attemptsLeft(settings.wipeAfterAttempts)

    // Açılışta bir kez biyometrik istem.
    LaunchedEffect(settings.biometricUnlock) {
        if (!biometricTried && settings.biometricUnlock && gate?.available == true) {
            biometricTried = true
            val cipher = viewModel.biometricCipher()
            if (cipher != null) {
                gate.authenticate(
                    title = context.getString(R.string.biometric_prompt_title),
                    subtitle = context.getString(R.string.biometric_prompt_sub),
                    negativeButton = context.getString(R.string.biometric_prompt_negative),
                    cipher = cipher,
                    onSuccess = viewModel::unlockWithBiometric
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MorphDial(
            strength = if (blocked) 0.05f else 0.9f,
            color = if (blocked) KasaTheme.colors.badgeWeakBg else KasaTheme.colors.badgeStrongBg,
            modifier = Modifier.size(132.dp),
            spin = true
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.lock_title),
            style = KasaTheme.text.sheetTitle,
            color = KasaTheme.colors.ink
        )
        Spacer(Modifier.height(28.dp))

        if (pinMode && !state.recoveryMode) {
            KasaPinField(
                value = pin,
                onValueChange = {
                    if (it.length <= pinLength) pin = it
                    if (state.error != null) viewModel.clearError()
                    // Son hane girildiğinde kendiliğinden dene: dört haneli bir
                    // PIN'den sonra ayrıca bir düğmeye basmak gereksiz sürtünme.
                    if (it.length == pinLength) {
                        val chars = it.toCharArray()
                        pin = ""
                        viewModel.unlockWithPin(chars)
                    }
                },
                label = stringResource(R.string.pin_unlock_title),
                isError = state.error != null,
                supportingText = state.error?.let { stringResource(it) }
            )
            Spacer(Modifier.height(20.dp))
            KasaButton(
                text = stringResource(R.string.pin_use_master),
                onClick = {
                    pinMode = false
                    pin = ""
                    viewModel.clearError()
                },
                tone = ButtonTone.TONAL,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (state.recoveryMode) {
            KasaTextField(
                value = recoveryInput,
                onValueChange = { recoveryInput = it },
                label = stringResource(R.string.lock_use_recovery),
                placeholder = stringResource(R.string.lock_recovery_hint),
                textStyle = KasaTheme.text.mono,
                imeAction = ImeAction.Done,
                isError = state.error != null,
                supportingText = state.error?.let { stringResource(it) }
            )
            Spacer(Modifier.height(20.dp))
            KasaButton(
                text = stringResource(if (state.busy) R.string.lock_unlocking else R.string.lock_unlock),
                onClick = { viewModel.unlockWithRecovery(recoveryInput) },
                enabled = !state.busy && !blocked && recoveryInput.length >= 20,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            KasaPasswordField(
                value = password,
                onValueChange = {
                    password = it
                    if (state.error != null) viewModel.clearError()
                },
                label = stringResource(R.string.lock_master),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                imeAction = ImeAction.Done,
                isError = state.error != null,
                supportingText = state.error?.let { stringResource(it) }
            )
            Spacer(Modifier.height(20.dp))
            KasaButton(
                text = stringResource(if (state.busy) R.string.lock_unlocking else R.string.lock_unlock),
                onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    viewModel.unlockWithPassword(chars)
                },
                enabled = !state.busy && !blocked && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (blocked) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.lock_cooldown, formatCooldown(state.cooldownMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        if (attemptsLeft != null && attemptsLeft <= 3 && state.failedAttempts > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (attemptsLeft <= 1) stringResource(R.string.lock_wipe_warning)
                else stringResource(R.string.lock_attempts_left, attemptsLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(18.dp))

        if (settings.biometricUnlock && gate?.available == true && !state.recoveryMode) {
            KasaButton(
                text = stringResource(R.string.lock_use_biometric),
                onClick = {
                    val cipher = viewModel.biometricCipher()
                    if (cipher != null) {
                        gate.authenticate(
                            title = context.getString(R.string.biometric_prompt_title),
                            subtitle = context.getString(R.string.biometric_prompt_sub),
                            negativeButton = context.getString(R.string.biometric_prompt_negative),
                            cipher = cipher,
                            onSuccess = viewModel::unlockWithBiometric
                        )
                    }
                },
                tone = ButtonTone.TONAL,
                enabled = !blocked,
                leading = {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        KasaButton(
            text = stringResource(
                if (state.recoveryMode) R.string.lock_master else R.string.lock_use_recovery
            ),
            onClick = viewModel::toggleRecoveryMode,
            tone = ButtonTone.TEXT,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatCooldown(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "$minutes:${seconds.toString().padStart(2, '0')}" else "$seconds"
}
