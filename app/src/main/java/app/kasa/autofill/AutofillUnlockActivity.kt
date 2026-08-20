package app.kasa.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.kasa.KasaApplication
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.VaultRepository
import app.kasa.ui.BiometricGate
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Otomatik doldurma akışının kimlik doğrulama penceresi.
 *
 * Sistem, kilitli kasadan gelen "doğrulama gerekiyor" veri kümesine
 * dokunulduğunda bu Activity'yi açar. Kasa açıldıktan sonra gerçek
 * [FillResponse] `EXTRA_AUTHENTICATION_RESULT` ile geri verilir ve sistem
 * alanları doldurur.
 *
 * Bu Activity saydam bir pencere olarak açılır ve son uygulamalar listesine
 * girmez (`excludeFromRecents`); ekranı da `FLAG_SECURE` ile korunur, çünkü
 * burada ana parola yazılıyor.
 */
class AutofillUnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val container = KasaApplication.container(this)
        val repository = container.vaultRepository

        // Zaten açıksa doğrudan yanıtla.
        if (repository.isUnlocked) {
            finishWithResponse()
            return
        }

        setContent {
            val settings by container.settingsStore.settings
                .collectAsState(initial = SettingsStore.Settings())

            KasaTheme(
                themeMode = settings.theme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack
            ) {
                UnlockDialog(
                    repository = repository,
                    biometricEnabled = settings.biometricUnlock,
                    onUnlocked = { finishWithResponse() },
                    onCancel = { setResult(Activity.RESULT_CANCELED); finish() }
                )
            }
        }
    }

    /** Kasa açıldı: eşleşen kayıtlardan gerçek veri kümelerini kur ve dön. */
    private fun finishWithResponse() {
        val structure = intent.getParcelableExtra<android.app.assist.AssistStructure>(
            AutofillManager.EXTRA_ASSIST_STRUCTURE
        )
        if (structure == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val parsed = StructureParser(structure).parse()
        val repository = KasaApplication.container(this).vaultRepository
        val matches = repository.matchesFor(parsed.packageName, parsed.webDomain)
            .ifEmpty { repository.data.value.items.take(8) }

        val builder = FillResponse.Builder()
        var added = 0
        matches.forEach { item ->
            val dataset = buildDataset(parsed, item) ?: return@forEach
            builder.addDataset(dataset)
            added++
        }

        if (added == 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build())
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun buildDataset(parsed: StructureParser.Result, item: VaultItem): Dataset? {
        if (parsed.usernameId == null && parsed.passwordId == null) return null
        val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.autofill_title, item.name)
            setTextViewText(R.id.autofill_subtitle, item.username.ifBlank { item.host().orEmpty() })
        }
        return Dataset.Builder().apply {
            parsed.usernameId?.let { setValue(it, AutofillValue.forText(item.username), presentation) }
            parsed.passwordId?.let { setValue(it, AutofillValue.forText(item.password.reveal()), presentation) }
        }.build()
    }

    @Composable
    private fun UnlockDialog(
        repository: VaultRepository,
        biometricEnabled: Boolean,
        onUnlocked: () -> Unit,
        onCancel: () -> Unit
    ) {
        var password by remember { mutableStateOf("") }
        var revealed by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        var biometricTried by remember { mutableStateOf(false) }

        val gate = remember { BiometricGate(this) }

        LaunchedEffect(biometricEnabled) {
            if (!biometricTried && biometricEnabled && gate.available) {
                biometricTried = true
                val cipher = repository.biometricCipher()
                if (cipher != null) {
                    gate.authenticate(
                        title = getString(R.string.af_unlock_title),
                        subtitle = getString(R.string.af_unlock_prompt),
                        negativeButton = getString(R.string.biometric_prompt_negative),
                        cipher = cipher,
                        onSuccess = { authenticated ->
                            lifecycleScope.launch {
                                val outcome = repository.unlockWithBiometric(authenticated)
                                if (outcome is VaultRepository.UnlockOutcome.Success) onUnlocked()
                            }
                        }
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(24.dp)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KasaRadius.xl))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(24.dp)
            ) {
                Text(
                    stringResource(R.string.af_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.af_unlock_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink2
                )
                Spacer(Modifier.height(18.dp))
                KasaPasswordField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    label = stringResource(R.string.lock_master),
                    revealed = revealed,
                    onRevealToggle = { revealed = !revealed },
                    imeAction = ImeAction.Done,
                    isError = error,
                    supportingText = if (error) stringResource(R.string.lock_wrong) else null
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    KasaButton(
                        text = stringResource(R.string.cancel),
                        onClick = onCancel,
                        tone = ButtonTone.TONAL,
                        height = 46.dp
                    )
                    KasaButton(
                        text = stringResource(if (busy) R.string.lock_unlocking else R.string.lock_unlock),
                        onClick = {
                            val chars = password.toCharArray()
                            password = ""
                            busy = true
                            lifecycleScope.launch {
                                val wipeAfter = KasaApplication.container(this@AutofillUnlockActivity)
                                    .settingsStore.settings.first().wipeAfterAttempts
                                val outcome = repository.unlockWithPassword(chars, wipeAfter)
                                busy = false
                                if (outcome is VaultRepository.UnlockOutcome.Success) onUnlocked()
                                else error = true
                            }
                        },
                        enabled = !busy && password.isNotEmpty(),
                        height = 46.dp
                    )
                }
            }
        }
    }
}
