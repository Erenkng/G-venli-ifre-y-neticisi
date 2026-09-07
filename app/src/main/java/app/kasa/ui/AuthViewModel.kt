package app.kasa.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.core.security.DeviceIntegrity
import app.kasa.core.util.Haptics
import app.kasa.core.util.PasswordStrength
import app.kasa.data.SettingsStore
import app.kasa.data.repo.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * Kurulumun adımları.
     *
     * Sıra rastgele değil; iki tanesi bilerek araya girmiş durumda:
     *
     * - [RECOVERY_VERIFY], anahtarı gerçekten kaydedip kaydetmediğini
     *   **kanıtlatıyor**. Önceden burada bir onay kutusu vardı ve onay kutusu
     *   tam olarak "sonra bakarım"ın kendisidir: işaretlemenin bedeli sıfır.
     *
     * - [CONFIRM_MASTER], ana parolayı ikinci kez sordurmayı ilk ekrandan
     *   alıp buraya taşıyor. İki alan yan yanayken yapılan şey hatırlamak
     *   değil kopyalamaktı; araya kurtarma adımı girince test gerçek oluyor.
     */
    enum class Stage {
        SETUP,
        RECOVERY_SHOWN,
        RECOVERY_VERIFY,
        CONFIRM_MASTER,
        BIOMETRIC_OFFER,
        POSTURE,
        HANDOFF,
        DONE
    }

    /** Kurulumun başındaki çatal: boş kasa mı, yedekten geri yükleme mi. */
    enum class SetupIntent { FRESH, RESTORE }

    data class SetupState(
        val stage: Stage = Stage.SETUP,
        val busy: Boolean = false,
        val error: Int? = null,
        val recoveryCode: String? = null,
        val strength: Float = 0f,
        /**
         * Kullanıcının başta seçtiği yol. Yalnızca metinleri ve son adımın
         * hangi seçeneği öne çıkaracağını belirliyor — kasa her iki yolda da
         * aynı şekilde yaratılıyor, çünkü yedek dosyası açık kasaya
         * aktarılıyor ve kendi parolası ana paroladan ayrı.
         */
        val intent: SetupIntent = SetupIntent.FRESH,
        /**
         * Kurtarma anahtarının hangi gruplarının geri yazılacağı (0 tabanlı).
         *
         * Kurulum başına bir kez seçiliyor: her yanlış denemede yeni gruplar
         * sormak, kullanıcının koda bakmadan deneme yanılmayla ilerlemesine
         * kapı açardı.
         */
        val verifyIndices: List<Int> = emptyList(),
        val verifyError: Boolean = false,
        val confirmError: Boolean = false,
        /**
         * Ana parolayı üst üste kaç kez yanlış yazdı.
         *
         * Üçten sonra ekran, kasanın **şu an boş** olduğunu hatırlatıp baştan
         * başlamayı öneriyor. Bu öneri yalnızca burada verilebilir: kurulumun
         * sonrasında aynı durum kurtarma anahtarı meselesi olur.
         */
        val confirmAttempts: Int = 0,
        /** Kurtarma anahtarı bir dosyaya yazıldı mı. */
        val recoverySaved: Boolean = false,
        /** Son adımda içe aktarılan kayıt sayısı; `null` ise henüz denenmedi. */
        val imported: Int? = null,
        val importError: Int? = null,
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

    /** Tanıtımdan sonraki çatal. Metinleri ve son adımın sırasını belirliyor. */
    fun onIntentChosen(intent: SetupIntent) {
        _setup.value = _setup.value.copy(intent = intent)
    }

    /**
     * Kasayı yaratır.
     *
     * Yineleme alanı artık burada değil: parola ikinci kez [confirmMaster] ile,
     * kurtarma adımından **sonra** soruluyor. Bunun bedeli, yanlış yazılmış bir
     * parolayla kasa yaratılabilmesi; karşılığı ise yinelemenin gerçekten bir
     * hafıza sınavı olması. Bedel kurulum içinde kalıyor çünkü kasa o anda boş
     * ve [restartSetup] her şeyi silip baştan başlatabiliyor.
     */
    fun createVault(password: CharArray) {
        val error = when {
            password.size < MIN_MASTER_LENGTH -> app.kasa.R.string.onb_too_short
            PasswordStrength.evaluate(String(password)).entropyBits < MIN_MASTER_ENTROPY ->
                app.kasa.R.string.onb_too_weak
            else -> null
        }
        if (error != null) {
            password.fill('\u0000')
            container.haptics.play(Haptics.Kind.DENY)
            _setup.value = _setup.value.copy(error = error)
            return
        }

        viewModelScope.launch {
            _setup.value = _setup.value.copy(busy = true, error = null, progress = 0f)
            val result = repository.createVault(password) { done ->
                _setup.value = _setup.value.copy(progress = done)
            }
            _setup.value = result.fold(
                onSuccess = { code ->
                    container.haptics.play(Haptics.Kind.SEAL)
                    _setup.value.copy(
                        busy = false,
                        stage = Stage.RECOVERY_SHOWN,
                        recoveryCode = code,
                        verifyIndices = pickVerifyIndices(code)
                    )
                },
                onFailure = {
                    container.haptics.play(Haptics.Kind.WARNING)
                    _setup.value.copy(busy = false, error = app.kasa.R.string.imp_failed)
                }
            )
        }
    }

    /**
     * Doğrulamada sorulacak grupları seçer.
     *
     * İlk grup hiçbir zaman sorulmuyor: kod ekranda dururken göz onu
     * kaçınılmaz olarak okuyor ve en taze hatırlanan parça o oluyor. Sondan
     * ve ortadan sormak, koda gerçekten bakılmasını gerektiriyor.
     */
    private fun pickVerifyIndices(code: String): List<Int> {
        val groups = code.split('-').size
        if (groups <= VERIFY_GROUPS) return (0 until groups).toList()
        return (1 until groups).shuffled().take(VERIFY_GROUPS).sorted()
    }

    /**
     * Kurtarma anahtarını bir metin dosyasına yazar.
     *
     * Dosya yalnızca kodu değil, kodun ne olduğunu da içeriyor. Aylar sonra
     * indirilenler klasöründe bulunan 24 karakterlik bir dizi hiçbir şey
     * ifade etmiyor ve büyük ihtimalle siliniyor — yani açıklamayı yazmamak,
     * dosyayı işe yaramaz kılmanın en sessiz yolu olurdu.
     *
     * Panoya kopyalama bilerek sunulmuyor: pano bütün uygulamalara açık ve
     * kasa anahtarının orada bir saniye bile durmasının gerekçesi yok.
     */
    fun saveRecoveryCode(uri: Uri) {
        val code = _setup.value.recoveryCode ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(recoverySheet(code).toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            container.haptics.play(if (ok) Haptics.Kind.SEAL else Haptics.Kind.WARNING)
            _setup.value = _setup.value.copy(recoverySaved = ok)
        }
    }

    private fun recoverySheet(code: String): String = buildString {
        appendLine(container.appContext.getString(app.kasa.R.string.rec_sheet_title))
        appendLine()
        appendLine(code)
        appendLine()
        appendLine(container.appContext.getString(app.kasa.R.string.rec_sheet_body))
    }

    fun onRecoverySeen() {
        _setup.value = _setup.value.copy(stage = Stage.RECOVERY_VERIFY, verifyError = false)
    }

    /** Kurtarma adımına geri döner: kullanıcı koda yeniden bakmak istedi. */
    fun onRecoveryReview() {
        _setup.value = _setup.value.copy(stage = Stage.RECOVERY_SHOWN, verifyError = false)
    }

    /**
     * Geri yazılan grupları koddaki karşılıklarıyla karşılaştırır.
     *
     * Karşılaştırma büyük/küçük harf ve boşluk duyarsız: Crockford Base32
     * zaten büyük harfli ve kullanıcı kâğıttan okuyor, yani buradaki tek
     * gerçek soru "kod elinde mi".
     */
    fun onRecoveryVerified(typed: List<String>) {
        val code = _setup.value.recoveryCode ?: return
        val groups = code.split('-')
        val ok = _setup.value.verifyIndices.withIndex().all { (slot, group) ->
            typed.getOrNull(slot)?.trim()?.uppercase() == groups.getOrNull(group)?.uppercase()
        }
        if (!ok) {
            container.haptics.play(Haptics.Kind.DENY)
            _setup.value = _setup.value.copy(verifyError = true)
            return
        }
        container.haptics.play(Haptics.Kind.SEAL)
        repository.consumeRecoveryCode()
        _setup.value = _setup.value.copy(
            stage = Stage.CONFIRM_MASTER,
            recoveryCode = null,
            verifyError = false
        )
    }

    /**
     * Ana parolayı hafızadan yazdırır.
     *
     * Karşılaştırma bellekteki bir kopyayla değil, kasanın kendisiyle
     * yapılıyor: parola kurulumdan bu yana hiçbir yerde tutulmuyor ve
     * tutulmasının da gerekçesi yok. Bedeli anahtar türetmenin süresi,
     * karşılığı sınavın gerçek olması.
     */
    fun confirmMaster(password: CharArray) {
        viewModelScope.launch {
            _setup.value = _setup.value.copy(busy = true, confirmError = false)
            val ok = repository.verifyMasterPassword(password)
            _setup.value = if (ok) {
                container.haptics.play(Haptics.Kind.SEAL)
                val canOfferBiometric = repository.biometricEncryptCipherOrNull() != null
                _setup.value.copy(
                    busy = false,
                    confirmError = false,
                    confirmAttempts = 0,
                    stage = if (canOfferBiometric) Stage.BIOMETRIC_OFFER else Stage.POSTURE
                )
            } else {
                container.haptics.play(Haptics.Kind.WARNING)
                _setup.value.copy(
                    busy = false,
                    confirmError = true,
                    confirmAttempts = _setup.value.confirmAttempts + 1
                )
            }
        }
    }

    /**
     * Her şeyi siler ve kurulumu ilk adıma alır.
     *
     * Yalnızca [Stage.CONFIRM_MASTER] içinden, kullanıcı parolasını
     * hatırlamadığını kabul ettiğinde çağrılıyor. O noktada kasa boş — yani
     * silinen tek şey açılamayacak bir anahtar. Aynı durumun kurulum
     * sonrasındaki karşılığı bu değil; orada cevap kurtarma anahtarıdır.
     */
    fun restartSetup() {
        viewModelScope.launch {
            repository.wipeEverything()
            // Çatalın seçimi korunuyor: kullanıcı parolasını unuttu, yedeği
            // olup olmadığını değil.
            _setup.value = SetupState(intent = _setup.value.intent)
        }
    }

    /** Biyometrik sarmalayıcı kurmak için şifreleme şifreleyicisi. */
    fun biometricEncryptCipher(): Cipher? = repository.biometricEncryptCipherOrNull()

    fun enableBiometric(cipher: Cipher) {
        viewModelScope.launch {
            if (repository.enableBiometric(cipher)) {
                container.settingsStore.setBiometricUnlock(true)
                container.haptics.play(Haptics.Kind.SEAL)
            }
            _setup.value = _setup.value.copy(stage = Stage.POSTURE)
        }
    }

    fun skipBiometric() {
        _setup.value = _setup.value.copy(stage = Stage.POSTURE)
    }

    /** Sıkılık ön ayarını yazar ve son adıma geçer. */
    fun choosePosture(posture: SettingsStore.SecurityPosture) {
        viewModelScope.launch {
            container.settingsStore.applySecurityPosture(posture)
            container.haptics.play(Haptics.Kind.SEAL)
            _setup.value = _setup.value.copy(stage = Stage.HANDOFF)
        }
    }

    /**
     * Kurulumu kapatır.
     *
     * Bayrak yalnızca burada yazılıyor: son adım atlanabilir olsa da
     * atlanabilirliği kullanıcının kararı, kurulumun yarıda kesilmesi değil.
     * Uygulama daha önce kapatılırsa kurulum baştan başlıyor ve o da doğru,
     * çünkü [restartSetup] dışında kasa zaten yaratılmış olmuyor.
     */
    fun finishOnboarding() {
        viewModelScope.launch {
            container.settingsStore.setOnboardingDone(true)
            _setup.value = _setup.value.copy(stage = Stage.DONE)
        }
    }

    // ------------------------------------------------- kurulumun son adımı

    /**
     * Şifreli `.kasa` yedeğinden geri yükler.
     *
     * Yedeğin parolası ana paroladan ayrı: dışa aktarma anında seçilmişti ve
     * dosya buluta, e-postaya, USB belleğe gidebildiği için kendi koruması
     * olması gerekiyor. Kullanıcıya bunu söylemek [OnboardingScreen]'in işi;
     * buradaki iş ikisini karıştırmamak.
     *
     * Ayarlar'daki eşdeğeriyle aynı depo çağrısını kullanıyor, ama sonucu bir
     * ileti kanalına değil kurulum durumuna yazıyor: son adımda kullanıcının
     * görmesi gereken şey geçip giden bir çubuk değil, kaç kaydın geldiği.
     */
    fun restoreArchive(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _setup.value = _setup.value.copy(busy = true, importError = null)
            val blob = withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            val added = if (blob == null) {
                password.fill('\u0000')
                null
            } else {
                repository.importVault(blob, password)
            }
            container.haptics.play(if (added == null) Haptics.Kind.WARNING else Haptics.Kind.SEAL)
            _setup.value = _setup.value.copy(
                busy = false,
                imported = added,
                importError = if (added == null) app.kasa.R.string.imp_failed else null
            )
        }
    }

    /**
     * Başka bir yöneticinin CSV dışa aktarımından aktarır.
     *
     * Dosya parolaları **açık metin** taşıyor; dışa aktarmanın doğası bu.
     * Okunan tampon ve ayrıştırılan gizli metinler burada sıfırlanıyor,
     * kullanıcıya da dosyayı silmesi söyleniyor — telefonun indirilenler
     * klasöründe duran açık bir parola listesi, kasanın koruduğu her şeyi
     * anlamsız kılar.
     */
    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _setup.value = _setup.value.copy(busy = true, importError = null)
            val parsed = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    container.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null) null
                else try {
                    app.kasa.data.CsvImport.parse(String(bytes, Charsets.UTF_8))
                } finally {
                    bytes.fill(0)
                }
            }
            val added = if (parsed == null) null else repository.importItems(parsed.items)
            // Depo kendi kopyasını aldı; buradaki gizli metinler artık gereksiz.
            parsed?.items?.forEach { it.password.wipe() }

            container.haptics.play(if (added == null) Haptics.Kind.WARNING else Haptics.Kind.SEAL)
            _setup.value = _setup.value.copy(
                busy = false,
                imported = added,
                importError = if (added == null) app.kasa.R.string.csv_failed else null
            )
        }
    }

    /**
     * Kurulumu çoktan geçmiş kasalarda bayrağı yerine koyar.
     *
     * Bayrağı yazan tek yol ([finishOnboarding]) kurulumun ulaşılamayan
     * adımlarının içindeydi, yani bugüne kadar hiçbir kasada yazılmadı.
     * Kilit açıkken ve kurulum sürmezken çağrılıyor; ikinci kez çağrılması
     * zararsız çünkü aynı değeri yazıyor.
     */
    fun markOnboardingSettled() {
        viewModelScope.launch {
            if (!container.settingsStore.settings.first().onboardingDone) {
                container.settingsStore.setOnboardingDone(true)
            }
        }
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
                container.haptics.play(Haptics.Kind.UNLOCK)
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
                container.haptics.play(Haptics.Kind.BLOCKED)
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
                container.haptics.play(Haptics.Kind.ALARM)
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
        /**
         * Kurtarma anahtarının kaç grubu geri yazdırılıyor.
         *
         * Altı gruptan üçü. Hepsini yazdırmak kâğıttan kopyalamayı bir işe
         * dönüştürür ve insanlar işten kaçmak için ekran görüntüsü alır —
         * yani kodu tam da korunması gereken yere, galeriye koyar. Üçü,
         * koda bakmadan geçilemeyecek kadar çok.
         */
        const val VERIFY_GROUPS = 3

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
