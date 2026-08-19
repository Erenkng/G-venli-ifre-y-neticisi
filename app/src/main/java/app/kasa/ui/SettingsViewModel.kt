package app.kasa.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.R
import app.kasa.core.crypto.KeystoreKeys
import app.kasa.core.security.SecureClipboard
import app.kasa.core.util.Haptics
import app.kasa.data.SettingsStore
import app.kasa.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * Ayarlar ekranı ve ona bağlı ağır işlemler (ana parola değişimi,
 * dışa/içe aktarma, kasa silme).
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settingsStore = container.settingsStore
    private val repository = container.vaultRepository

    val settings: StateFlow<SettingsStore.Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.Settings())

    private val messages = Channel<UiMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** Ekranda gösterilen tek seferlik kurtarma kodu. */
    private val _recoveryCode = MutableStateFlow<String?>(null)
    val recoveryCode: StateFlow<String?> = _recoveryCode.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val hardwareBackedKey: Boolean by lazy { KeystoreKeys.isHardwareBacked(container.appContext) }
    val strongBox: Boolean by lazy { KeystoreKeys.hasStrongBox(container.appContext) }
    val argon2Available: Boolean get() = app.kasa.core.crypto.Kdf.argon2Available

    fun masterKeyChangedAt(): Long = repository.masterKeyChangedAt()

    // ------------------------------------------------------------- anahtarlar

    fun setTheme(mode: ThemeMode) = launchSetting { settingsStore.setTheme(mode) }
    fun setDynamicColor(value: Boolean) = launchSetting { settingsStore.setDynamicColor(value) }
    fun setPureBlack(value: Boolean) = launchSetting { settingsStore.setPureBlack(value) }
    fun setHaptics(value: Boolean) = launchSetting {
        settingsStore.setHaptics(value)
        container.haptics.enabled = value
        if (value) container.haptics.play(Haptics.Kind.SUCCESS)
    }

    fun setBlockScreenshots(value: Boolean) = launchSetting { settingsStore.setBlockScreenshots(value) }
    fun setClipboardSeconds(value: Int) = launchSetting { settingsStore.setClipboardClearSeconds(value) }
    fun setAutoLockSeconds(value: Int) = launchSetting { settingsStore.setAutoLockSeconds(value) }
    fun setWipeAfterAttempts(value: Int) = launchSetting { settingsStore.setWipeAfterAttempts(value) }
    fun setOnlineBreachCheck(value: Boolean) = launchSetting { settingsStore.setOnlineBreachCheck(value) }

    private fun launchSetting(block: suspend () -> Unit) {
        container.haptics.play(Haptics.Kind.TOGGLE)
        viewModelScope.launch { block() }
    }

    // ------------------------------------------------------------- biyometri

    fun biometricAvailableCipher(): Cipher? = repository.biometricEncryptCipherOrNull()

    fun onBiometricEnrolled(cipher: Cipher) {
        viewModelScope.launch {
            if (repository.enableBiometric(cipher)) {
                settingsStore.setBiometricUnlock(true)
                container.haptics.play(Haptics.Kind.SUCCESS)
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            repository.disableBiometric()
            settingsStore.setBiometricUnlock(false)
        }
    }

    // ------------------------------------------------------ ana parola değişimi

    sealed interface ChangeResult {
        data object Success : ChangeResult
        data object WrongCurrent : ChangeResult
        data object TooWeak : ChangeResult
    }

    private val _changeResult = MutableStateFlow<ChangeResult?>(null)
    val changeResult: StateFlow<ChangeResult?> = _changeResult.asStateFlow()

    fun clearChangeResult() {
        _changeResult.value = null
    }

    fun changeMasterPassword(current: CharArray, new: CharArray) {
        viewModelScope.launch {
            _busy.value = true
            val ok = repository.changeMasterPassword(current, new)
            _busy.value = false
            _changeResult.value = if (ok) ChangeResult.Success else ChangeResult.WrongCurrent
            if (ok) {
                // Ana parola değişti; eski biyometrik sarmalayıcı hâlâ geçerlidir
                // (kasa anahtarı aynı), ama kullanıcıya yeniden onaylatmak daha
                // temiz: eski parolayı bilen biri sarmalayıcıyı kuramaz.
                container.haptics.play(Haptics.Kind.SUCCESS)
                messages.send(UiMessage(R.string.chg_done))
            }
        }
    }

    // ---------------------------------------------------------- kurtarma anahtarı

    fun regenerateRecoveryKey() {
        viewModelScope.launch {
            _busy.value = true
            _recoveryCode.value = repository.regenerateRecoveryKey()
            _busy.value = false
        }
    }

    fun copyRecoveryCode(code: String, clearSeconds: Int) {
        SecureClipboard.copySensitive(container.appContext, code, clearSeconds)
        container.haptics.play(Haptics.Kind.SUCCESS)
        viewModelScope.launch { messages.send(UiMessage(R.string.copied_clip, listOf(clearSeconds))) }
    }

    fun dismissRecoveryCode() {
        _recoveryCode.value = null
    }

    // ------------------------------------------------------------ dışa aktarma

    fun exportTo(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _busy.value = true
            val count = repository.data.value.items.size
            val blob = repository.exportVault(password)
            val ok = blob != null && withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openOutputStream(uri)?.use { it.write(blob) } != null
                }.getOrDefault(false)
            }
            _busy.value = false
            messages.send(
                if (ok) UiMessage(R.string.exp_done, listOf(count))
                else UiMessage(R.string.imp_failed)
            )
        }
    }

    fun importFrom(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _busy.value = true
            val blob = withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            val added = if (blob == null) null else repository.importVault(blob, password)
            _busy.value = false
            messages.send(
                if (added == null) UiMessage(R.string.imp_failed)
                else UiMessage(R.string.imp_done, listOf(added))
            )
        }
    }

    // ---------------------------------------------------------------- silme

    fun wipeVault(onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            repository.wipeEverything()
            _busy.value = false
            container.haptics.play(Haptics.Kind.WARNING)
            messages.send(UiMessage(R.string.wipe_done))
            onDone()
        }
    }

    fun lockNow() {
        container.autoLocker.lockNow()
    }

    /**
     * Dosya seçici açılmadan hemen önce çağrılır: seçicinin uygulamayı arka
     * plana alması otomatik kilidi tetiklememeli, yoksa seçiciden dönüldüğünde
     * yazacak kasa kalmaz.
     */
    fun suppressAutoLockForPicker() {
        container.autoLocker.suppressNextBackground()
    }
}
