package app.kasa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.core.security.DeviceIntegrity
import app.kasa.core.util.Haptics
import app.kasa.core.util.PasswordStrength
import app.kasa.data.repo.VaultRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.crypto.Cipher

/**
 * Kurulum ve kilit açma.
 *
 * Ana parola `CharArray` olarak taşınır ve kullanıldığı anda sıfırlanır.
 * Compose'un metin alanı kaçınılmaz olarak bir `String` tutuyor, ama o kopya
 * ekran kapanınca kompozisyondan düşer; kripto katmanına hiçbir zaman `String`
 * girmez, dolayısıyla anahtar türetme yolunda bellekte asılı kalan bir kopya olmaz.
 */
class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.vaultRepository

    enum class Stage { SETUP, RECOVERY_SHOWN, BIOMETRIC_OFFER, DONE }

    data class SetupState(
        val stage: Stage = Stage.SETUP,
        val busy: Boolean = false,
        val error: Int? = null,
        val recoveryCode: String? = null,
        val strength: Float = 0f,
        /**
         * Kurulum ilerlemesi (0..1).
         *
         * Kurulum artık anında değil: cihazın anahtar türetme kapasitesi
         * ölçülüyor ve bu birkaç saniye sürüyor. Dönen bir çark yerine gerçek
         * ilerlemeyi göstermek, kullanıcının uygulamanın donduğunu sanmasını
         * engelliyor.
         */
        val progress: Float = 0f
    )

    data class UnlockState(
        val busy: Boolean = false,
        val error: Int? = null,
        val cooldownMillis: Long = 0L,
        val failedAttempts: Int = 0,
        val recoveryMode: Boolean = false,
        val wiped: Boolean = false
    )

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup.asStateFlow()

    private val _unlock = MutableStateFlow(UnlockState(failedAttempts = repository.failedAttempts()))
    val unlock: StateFlow<UnlockState> = _unlock.asStateFlow()

    val lockState: StateFlow<VaultRepository.LockState> = repository.lockState

    val integrity: DeviceIntegrity.Report by lazy { DeviceIntegrity.check(container.appContext) }

    // ------------------------------------------------------------- kurulum

    fun onMasterPasswordTyped(value: String) {
        _setup.value = _setup.value.copy(strength = PasswordStrength.evaluate(value).score, error = null)
    }

    fun createVault(password: CharArray, confirm: CharArray) {
        val error = when {
            password.size < MIN_MASTER_LENGTH -> app.kasa.R.string.onb_too_short
            !password.contentEquals(confirm) -> app.kasa.R.string.onb_mismatch
            PasswordStrength.evaluate(String(password)).entropyBits < MIN_MASTER_ENTROPY ->
                app.kasa.R.string.onb_too_weak
            else -> null
        }
        if (error != null) {
            confirm.fill('\u0000')
            password.fill('\u0000')
            container.haptics.play(Haptics.Kind.WARNING)
            _setup.value = _setup.value.copy(error = error)
            return
        }

        viewModelScope.launch {
            _setup.value = _setup.value.copy(busy = true, error = null, progress = 0f)
            val result = repository.createVault(password) { done ->
                _setup.value = _setup.value.copy(progress = done)
            }
            confirm.fill('\u0000')
            _setup.value = result.fold(
                onSuccess = { code ->
                    container.haptics.play(Haptics.Kind.SUCCESS)
                    SetupState(stage = Stage.RECOVERY_SHOWN, recoveryCode = code)
                },
                onFailure = {
                    container.haptics.play(Haptics.Kind.WARNING)
                    _setup.value.copy(busy = false, error = app.kasa.R.string.imp_failed)
                }
            )
        }
    }

    fun onRecoveryAcknowledged() {
        repository.consumeRecoveryCode()
        val canOfferBiometric = repository.biometricEncryptCipherOrNull() != null
        _setup.value = _setup.value.copy(
            stage = if (canOfferBiometric) Stage.BIOMETRIC_OFFER else Stage.DONE,
            recoveryCode = null
        )
        if (!canOfferBiometric) finishOnboarding()
    }

    /** Biyometrik sarmalayıcı kurmak için şifreleme şifreleyicisi. */
    fun biometricEncryptCipher(): Cipher? = repository.biometricEncryptCipherOrNull()

    fun enableBiometric(cipher: Cipher) {
        viewModelScope.launch {
            if (repository.enableBiometric(cipher)) {
                container.settingsStore.setBiometricUnlock(true)
                container.haptics.play(Haptics.Kind.SUCCESS)
            }
            _setup.value = _setup.value.copy(stage = Stage.DONE)
            finishOnboarding()
        }
    }

    fun skipBiometric() {
        _setup.value = _setup.value.copy(stage = Stage.DONE)
        finishOnboarding()
    }

    private fun finishOnboarding() {
        viewModelScope.launch { container.settingsStore.setOnboardingDone(true) }
    }

    // ---------------------------------------------------------- kilit açma

    fun unlockWithPassword(password: CharArray) {
        viewModelScope.launch {
            _unlock.value = _unlock.value.copy(busy = true, error = null)
            val wipeAfter = container.settingsStore.settings.first().wipeAfterAttempts
            handleOutcome(repository.unlockWithPassword(password, wipeAfter))
        }
    }

    /** Kurulu PIN kaç haneli? 0 ise PIN katmanı yok. */
    val pinLength: Int get() = repository.pinLength()

    /**
     * PIN ile açar.
     *
     * Yanlış PIN'de kalan hak gösteriliyor; sıfırlandığında katman düşüyor ve
     * kullanıcı ana parolaya yönlendiriliyor. Kasa kaybolmuyor — düşen yalnızca
     * kısayol.
     */
    fun unlockWithPin(pin: CharArray) {
        viewModelScope.launch {
            _unlock.value = _unlock.value.copy(busy = true, error = null)
            val outcome = repository.unlockWithPin(pin)
            if (outcome is VaultRepository.UnlockOutcome.WrongSecret) {
                container.haptics.play(Haptics.Kind.WARNING)
                val left = repository.pinAttemptsLeft()
                _unlock.value = _unlock.value.copy(
                    busy = false,
                    error = if (left <= 0) app.kasa.R.string.pin_dropped else app.kasa.R.string.pin_wrong
                )
            } else {
                handleOutcome(outcome)
            }
        }
    }

    fun unlockWithRecovery(code: String) {
        viewModelScope.launch {
            _unlock.value = _unlock.value.copy(busy = true, error = null)
            val wipeAfter = container.settingsStore.settings.first().wipeAfterAttempts
            handleOutcome(repository.unlockWithRecovery(code, wipeAfter))
        }
    }

    fun biometricCipher(): Cipher? = repository.biometricCipher()

    fun unlockWithBiometric(cipher: Cipher) {
        viewModelScope.launch {
            _unlock.value = _unlock.value.copy(busy = true, error = null)
            handleOutcome(repository.unlockWithBiometric(cipher))
        }
    }

    private suspend fun handleOutcome(outcome: VaultRepository.UnlockOutcome) {
        when (outcome) {
            VaultRepository.UnlockOutcome.Success -> {
                container.haptics.play(Haptics.Kind.SUCCESS)
                _unlock.value = UnlockState()
            }
            VaultRepository.UnlockOutcome.WrongSecret -> {
                container.haptics.play(Haptics.Kind.WARNING)
                _unlock.value = _unlock.value.copy(
                    busy = false,
                    error = app.kasa.R.string.lock_wrong,
                    failedAttempts = repository.failedAttempts()
                )
            }
            is VaultRepository.UnlockOutcome.Blocked -> {
                container.haptics.play(Haptics.Kind.WARNING)
                _unlock.value = _unlock.value.copy(
                    busy = false,
                    error = app.kasa.R.string.lock_wrong,
                    cooldownMillis = outcome.remainingMillis,
                    failedAttempts = repository.failedAttempts()
                )
                tickCooldown()
            }
            VaultRepository.UnlockOutcome.TooNew -> {
                container.haptics.play(Haptics.Kind.WARNING)
                _unlock.value = _unlock.value.copy(
                    busy = false,
                    error = app.kasa.R.string.vault_too_new
                )
            }
            VaultRepository.UnlockOutcome.Wiped -> {
                container.haptics.play(Haptics.Kind.WARNING)
                _unlock.value = UnlockState(wiped = true)
            }
            is VaultRepository.UnlockOutcome.Error -> {
                _unlock.value = _unlock.value.copy(busy = false, error = app.kasa.R.string.lock_wrong)
            }
        }
    }

    private fun tickCooldown() {
        viewModelScope.launch {
            while (_unlock.value.cooldownMillis > 0) {
                delay(1000)
                val remaining = (_unlock.value.cooldownMillis - 1000).coerceAtLeast(0)
                _unlock.value = _unlock.value.copy(cooldownMillis = remaining)
            }
        }
    }

    fun toggleRecoveryMode() {
        _unlock.value = _unlock.value.copy(recoveryMode = !_unlock.value.recoveryMode, error = null)
    }

    fun clearError() {
        _unlock.value = _unlock.value.copy(error = null)
    }

    fun consumeWiped() {
        _unlock.value = _unlock.value.copy(wiped = false)
    }

    /**
     * Kaç deneme hakkı kaldı? Yalnızca "N denemede sil" ayarı açıksa anlamlı.
     */
    fun attemptsLeft(wipeAfter: Int): Int? =
        if (wipeAfter <= 0) null else (wipeAfter - _unlock.value.failedAttempts).coerceAtLeast(0)

    fun markIntegrityWarningShown() {
        viewModelScope.launch { container.settingsStore.setIntegrityWarningShown(true) }
    }

    companion object {
        const val MIN_MASTER_LENGTH = 12

        /**
         * Ana parola için alt sınır. 60 bit, çevrimdışı bir saldırganın Argon2id
         * maliyetiyle birlikte pratikte deneyemeyeceği bir alan bırakıyor;
         * bunun altındaki bir ana parola, kasadaki 20 karakterlik parolaları
         * anlamsız kılar çünkü zinciri en zayıf halka belirler.
         */
        const val MIN_MASTER_ENTROPY = 60.0
    }
}
