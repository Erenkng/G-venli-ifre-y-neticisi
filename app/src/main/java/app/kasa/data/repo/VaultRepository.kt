package app.kasa.data.repo

import android.content.Context
import app.kasa.core.crypto.AeadSuite
import app.kasa.core.crypto.Kdf
import app.kasa.core.crypto.KdfCalibration
import app.kasa.core.crypto.KeystoreKeys
import app.kasa.core.crypto.SecretBytes
import app.kasa.core.crypto.SecretText
import app.kasa.core.security.SecureClipboard
import app.kasa.data.SettingsStore
import app.kasa.data.VaultStore
import app.kasa.data.VaultTooNewException
import app.kasa.core.crypto.Crypto
import app.kasa.data.model.Attachment
import app.kasa.data.model.Category
import app.kasa.data.model.CategorySchema
import app.kasa.data.model.Folder
import app.kasa.data.model.Passkey
import app.kasa.data.model.PasswordHistoryEntry
import app.kasa.data.model.SmartFolder
import app.kasa.data.model.VaultData
import app.kasa.data.model.VaultFilter
import app.kasa.data.model.VaultItem
import app.kasa.core.util.PasswordGenerator
import app.kasa.core.util.PasswordStrength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * Kasanın çalışma zamanındaki tek doğruluk kaynağı.
 *
 * Kasa anahtarı yalnızca burada, yalnızca kilit açıkken ve yalnızca
 * [SecretBytes] içinde durur. [lock] çağrıldığı anda anahtar sıfırlanır,
 * çözülmüş kayıtlar bellekten düşer ve pano temizlenir — yani ekran
 * kapandıktan sonra bellekte okunacak bir parola kalmaz.
 */
class VaultRepository(
    private val context: Context,
    private val store: VaultStore,
    private val settings: SettingsStore,
    private val scope: CoroutineScope
) {

    sealed interface LockState {
        /** Henüz kasa kurulmamış. */
        data object NeedsSetup : LockState
        data object Locked : LockState
        data object Unlocked : LockState
    }

    private val mutex = Mutex()

    private val _lockState = MutableStateFlow<LockState>(
        if (store.vaultExists()) LockState.Locked else LockState.NeedsSetup
    )
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private val _data = MutableStateFlow(VaultData())
    val data: StateFlow<VaultData> = _data.asStateFlow()

    private var vaultKey: SecretBytes? = null

    /**
     * Açık oturumun hangi kasa bölmesinde olduğu.
     *
     * Zorlama parolasıyla açıldıysa yem bölmesindeyiz: yazmalar oraya gider,
     * gerçek kasa dokunulmadan kalır. Arayüz bu ayrımı **göstermiyor** —
     * gösterse, omzunun üstünden bakan biri hangi kasada olduğunu görürdü.
     */
    @Volatile
    private var activeSection: Int = VaultStore.SECTION_REAL

    /**
     * Yem kasada mıyız?
     *
     * Yalnızca güvenlik ayarlarını kilitlemek için kullanılıyor, ekranda
     * gösterilmiyor. Yem oturumda biyometri/PIN kurulmasına izin verilseydi,
     * kullanıcı ertesi gün parmağıyla yem kasayı açar ve gerçek kayıtlarının
     * silindiğini sanırdı.
     */
    val inDuressSession: Boolean get() = activeSection == VaultStore.SECTION_DECOY

    /** Kurulum sonrası bir kereliğine gösterilecek kurtarma kodu. */
    private val _pendingRecoveryCode = MutableStateFlow<String?>(null)
    val pendingRecoveryCode: StateFlow<String?> = _pendingRecoveryCode.asStateFlow()

    val isUnlocked: Boolean get() = vaultKey != null && _lockState.value is LockState.Unlocked

    fun refreshLockState() {
        if (vaultKey == null) {
            _lockState.value = if (store.vaultExists()) LockState.Locked else LockState.NeedsSetup
        }
    }

    // ------------------------------------------------------------- kurulum

    /**
     * Sıfırdan kasa kurar.
     *
     * İlk iş cihazı ölçmek: anahtar türetme maliyeti ve şifreleme paketi sabit
     * değil, bu telefonda ölçülüp bulunuyor ve kasa başlığına yazılıyor. Sabit
     * bir parametre amiral gemisinde gereksiz zayıf, dört yıllık orta segment
     * bir telefonda kullanılamaz yavaş kalırdı; ikisi de yanlış cevap.
     *
     * Ölçüm birkaç saniye sürüyor, bu yüzden [onProgress] ilerlemeyi bildiriyor.
     */
    suspend fun createVault(
        masterPassword: CharArray,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val calibration = KdfCalibration.calibrate(context) { onProgress(it * 0.85f) }
                val cipherSuite = AeadSuite.fastest()
                onProgress(0.9f)
                SecretBytes.ofUtf8(masterPassword).use { secret ->
                    val created = store.createVault(secret, calibration.params, cipherSuite)
                    vaultKey = created.vaultKey
                    _data.value = store.readVault(created.vaultKey)
                    _lockState.value = LockState.Unlocked
                    _pendingRecoveryCode.value = created.recoveryCode
                    onProgress(1f)
                    created.recoveryCode
                }
            }.also { masterPassword.fill('\u0000') }
        }
    }

    fun consumeRecoveryCode() {
        _pendingRecoveryCode.value = null
    }

    // ---------------------------------------------------------- kilit açma

    sealed interface UnlockOutcome {
        data object Success : UnlockOutcome
        data object WrongSecret : UnlockOutcome
        class Blocked(val remainingMillis: Long) : UnlockOutcome
        data object Wiped : UnlockOutcome
        /** Kasa dosyası bu sürümden yeni; açmayı reddettik. */
        data object TooNew : UnlockOutcome
        class Error(val cause: Throwable) : UnlockOutcome
    }

    suspend fun unlockWithPassword(password: CharArray, wipeAfterAttempts: Int): UnlockOutcome =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    SecretBytes.ofUtf8(password).use { secret ->
                        adopt(store.unlockWithPassword(secret, wipeAfterAttempts))
                    }
                } finally {
                    password.fill('\u0000')
                }
            }
        }

    suspend fun unlockWithRecovery(code: String, wipeAfterAttempts: Int): UnlockOutcome =
        withContext(Dispatchers.IO) {
            mutex.withLock { adopt(store.unlockWithRecovery(code, wipeAfterAttempts)) }
        }

    fun biometricCipher(): Cipher? = store.biometricDecryptCipher()

    suspend fun unlockWithBiometric(cipher: Cipher): UnlockOutcome = withContext(Dispatchers.IO) {
        mutex.withLock { adopt(store.unlockWithBiometric(cipher)) }
    }

    private fun adopt(result: VaultStore.UnlockResult): UnlockOutcome = when (result) {
        is VaultStore.UnlockResult.Success -> {
            // Anahtar doğru ama kasa okunamıyorsa kilidi AÇMIYORUZ.
            //
            // Eskiden burada boş bir VaultData'ya düşülüyordu; kullanıcı açılmış
            // ama boş bir kasa görüyor, ilk kayıt eklediğinde de o boş kasa
            // diske yazılıp gerçek kayıtların üstüne biniyordu. Okuma hatası
            // veri kaybının değil, açılmamanın sebebi olmalı.
            val loaded = runCatching { store.readVault(result.vaultKey, result.section) }
            loaded.fold(
                onSuccess = { data ->
                    vaultKey?.wipe()
                    vaultKey = result.vaultKey
                    activeSection = result.section
                    // Sürüm 3 öncesi kasalarda yem bölmesi yoktu; ilk açılışta
                    // ekleniyor ki her kasa aynı şekle sahip olsun.
                    runCatching { store.ensureDuressSlot(result.vaultKey, result.section) }
                    _data.value = data
                    _lockState.value = LockState.Unlocked
                    // Kasa açılır açılmaz bakım: süresi dolmuş çöp kutusu
                    // kayıtları ve sahipsiz ek dosyaları temizlenir.
                    scope.launch { runMaintenance() }
                    UnlockOutcome.Success
                },
                onFailure = { cause ->
                    result.vaultKey.wipe()
                    if (cause is VaultTooNewException) UnlockOutcome.TooNew
                    else UnlockOutcome.Error(cause)
                }
            )
        }
        VaultStore.UnlockResult.WrongSecret -> UnlockOutcome.WrongSecret
        is VaultStore.UnlockResult.Blocked -> UnlockOutcome.Blocked(result.remainingMillis)
        VaultStore.UnlockResult.Wiped -> {
            hardReset()
            UnlockOutcome.Wiped
        }
        is VaultStore.UnlockResult.Failure -> UnlockOutcome.Error(result.cause)
    }

    // ------------------------------------------------------------- kilitleme

    /** Anahtarı ve çözülmüş veriyi bellekten siler. */
    fun lock() {
        vaultKey?.wipe()
        vaultKey = null
        activeSection = VaultStore.SECTION_REAL
        shredSecrets(_data.value)
        _data.value = VaultData()
        _pendingRecoveryCode.value = null
        _lockState.value = if (store.vaultExists()) LockState.Locked else LockState.NeedsSetup
        SecureClipboard.clearNow(context)
        SecureClipboard.cancelScheduledClear(context)
    }

    private fun hardReset() {
        vaultKey?.wipe()
        vaultKey = null
        activeSection = VaultStore.SECTION_REAL
        shredSecrets(_data.value)
        _data.value = VaultData()
        _pendingRecoveryCode.value = null
        _lockState.value = LockState.NeedsSetup
        scope.launch { settings.clear() }
    }

    /**
     * Açık kasadaki her gizli metni sıfırlar.
     *
     * Kilit kapandığı anda bellekte okunabilir tek bir parola kalmaması bu
     * çağrıya bağlı. [SecretText] örnekleri kayıt kopyaları arasında
     * paylaşıldığı için bir kez silmek yeter; çöp toplayıcının ne zaman
     * çalışacağını beklemek zorunda değiliz.
     *
     * Silinen değer okunmaya çalışılırsa boş dizge döner — kilitlenme anında
     * hâlâ çizilmekte olan bir ekranın çökmemesi için bilinçli bir seçim.
     */
    private fun shredSecrets(data: VaultData) {
        data.items.forEach { item ->
            item.password.wipe()
            item.history.forEach { it.password.wipe() }
            item.passkeys.forEach { it.privateKey.wipe() }
        }
        data.generatorHistory.forEach { it.wipe() }
    }

    // ------------------------------------------------------------ biyometri

    fun biometricEncryptCipherOrNull(
        authClass: KeystoreKeys.AuthClass = KeystoreKeys.AuthClass.BIOMETRIC_ONLY
    ): Cipher? = runCatching {
        KeystoreKeys.biometricEncryptCipher(context, authClass)
    }.getOrNull()

    suspend fun enableBiometric(
        cipher: Cipher,
        authClass: KeystoreKeys.AuthClass = KeystoreKeys.AuthClass.BIOMETRIC_ONLY
    ): Boolean = withContext(Dispatchers.IO) {
        if (inDuressSession) return@withContext false
        val key = vaultKey ?: return@withContext false
        runCatching { store.enableBiometric(cipher, key, authClass) }.isSuccess
    }

    suspend fun disableBiometric() = withContext(Dispatchers.IO) { store.disableBiometric() }

    fun biometricEnrolled(): Boolean = store.biometricEnrolled()

    /** Kurulu sarmalayıcı hangi doğrulamayı istiyor? Ayarlarda gösteriliyor. */
    fun biometricAuthClass(): KeystoreKeys.AuthClass? = store.biometricAuthClass()

    // ---------------------------------------------------------- hızlı PIN

    fun pinEnabled(): Boolean = store.pinEnabled()

    fun pinLength(): Int = store.pinLength()

    fun pinAttemptsLeft(): Int = store.pinAttemptsLeft()

    /**
     * PIN katmanını kurar. Kasa açık olmalı: sarmalanan şey kasa anahtarının
     * kendisi, ana parola değil — yani PIN'i bilen biri ana parolayı öğrenmiyor.
     *
     * Maliyet ana parolanınkiyle aynı ([VaultStore.currentKdfParams]); taze
     * tuzla. Ayrı bir "PIN için daha ucuz" ayarı bilerek yok: PIN zaten kısa,
     * bir de türetmeyi ucuzlatmak iki zayıflığı üst üste koymak olurdu.
     */
    suspend fun enablePin(pin: CharArray, pinLength: Int): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = vaultKey
            try {
                if (key == null || inDuressSession) return@withLock false
                SecretBytes.ofUtf8(pin).use { secret ->
                    val params = (store.currentKdfParams() ?: Kdf.defaultParams()).withFreshSalt()
                    runCatching { store.enablePin(secret, key, pinLength, params) }.isSuccess
                }
            } finally {
                pin.fill('\u0000')
            }
        }
    }

    suspend fun unlockWithPin(pin: CharArray): UnlockOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                SecretBytes.ofUtf8(pin).use { secret -> adopt(store.unlockWithPin(secret)) }
            } finally {
                pin.fill('\u0000')
            }
        }
    }

    suspend fun disablePin() = withContext(Dispatchers.IO) { store.disablePin() }

    // -------------------------------------------------------------- kayıtlar

    suspend fun upsert(item: VaultItem): Boolean = mutate { current ->
        val existing = current.items.firstOrNull { it.id == item.id }
        val now = System.currentTimeMillis()

        val merged = if (existing == null) {
            item.copy(createdAt = now, updatedAt = now, passwordChangedAt = now)
        } else {
            val passwordChanged = existing.password != item.password && item.password.isNotBlank()
            item.copy(
                createdAt = existing.createdAt,
                updatedAt = now,
                passwordChangedAt = if (passwordChanged) now else existing.passwordChangedAt,
                lastUsedAt = existing.lastUsedAt,
                // Ekler ve çöp kutusu durumu düzenleyici formundan gelmez;
                // ayrı işlemlerle yönetilir. Formun taşıdığı eski kopya bunları
                // ezmemeli, yoksa düzenleme sırasında eklenen dosya kaybolur.
                attachments = existing.attachments,
                passkeys = existing.passkeys,
                deletedAt = existing.deletedAt,
                history = if (passwordChanged && existing.password.isNotBlank()) {
                    (listOf(PasswordHistoryEntry(existing.password, existing.passwordChangedAt)) + existing.history)
                        .take(MAX_HISTORY)
                } else existing.history,
                // Parola değiştiyse eski sızıntı sonucu artık geçerli değil.
                breachCount = if (passwordChanged) 0 else item.breachCount,
                breachCheckedAt = if (passwordChanged) 0L else item.breachCheckedAt
            )
        }

        val items = if (existing == null) current.items + merged
        else current.items.map { if (it.id == merged.id) merged else it }
        current.copy(items = items)
    }

    /**
     * Kaydı çöp kutusuna taşır.
     *
     * Kalıcı silme yalnızca [purge] ile ya da 30 gün dolduğunda olur. Kalıcı
     * silmeyi varsayılan yapmak, yanlışlıkla silinen tek bir kaydın geri
     * dönüşünü imkânsız kılıyordu; bildirim şeridindeki "geri al" uygulama
     * kapanınca kayboluyordu.
     */
    suspend fun moveToTrash(id: String): Boolean = mutate { current ->
        val now = System.currentTimeMillis()
        current.copy(items = current.items.map {
            if (it.id == id && !it.inTrash) it.copy(deletedAt = now, updatedAt = now) else it
        })
    }

    suspend fun restoreFromTrash(id: String): Boolean = mutate { current ->
        current.copy(items = current.items.map {
            if (it.id == id) it.copy(deletedAt = 0L, updatedAt = System.currentTimeMillis()) else it
        })
    }

    /** Kaydı ve eklerini geri dönüşsüz siler. */
    suspend fun purge(id: String): Boolean {
        val item = byId(id)
        val ok = mutate { current -> current.copy(items = current.items.filterNot { it.id == id }) }
        if (ok) item?.attachments?.forEach { store.deleteAttachment(it.id) }
        return ok
    }

    suspend fun emptyTrash(): Boolean {
        val trashed = _data.value.trashedItems
        val ok = mutate { current -> current.copy(items = current.items.filterNot { it.inTrash }) }
        if (ok) trashed.flatMap { it.attachments }.forEach { store.deleteAttachment(it.id) }
        return ok
    }

    /**
     * Çöp kutusunda [TRASH_RETENTION_DAYS] günü dolmuş kayıtları siler ve
     * kasada adı geçmeyen ek dosyalarını temizler.
     */
    suspend fun runMaintenance() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000
        val expired = _data.value.items.filter { it.inTrash && it.deletedAt < cutoff }
        if (expired.isNotEmpty()) {
            val ids = expired.map { it.id }.toSet()
            mutate { current -> current.copy(items = current.items.filterNot { it.id in ids }) }
            expired.flatMap { it.attachments }.forEach { store.deleteAttachment(it.id) }
        }
        withContext(Dispatchers.IO) {
            store.pruneOrphanAttachments(_data.value.items.flatMap { it.attachments }.map { it.id }.toSet())
        }
    }

    suspend fun toggleFavorite(id: String): Boolean = mutate { current ->
        current.copy(items = current.items.map {
            if (it.id == id) it.copy(favorite = !it.favorite, updatedAt = System.currentTimeMillis()) else it
        })
    }

    /** Kayıt görüntülendiğinde "son kullanılan" şeridini besler. */
    suspend fun touch(id: String): Boolean = mutate { current ->
        current.copy(items = current.items.map {
            if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        })
    }

    suspend fun replaceAll(items: List<VaultItem>): Boolean = mutate { it.copy(items = items) }

    // ------------------------------------------------------------- klasörler

    suspend fun createFolder(name: String, parentId: String? = null): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val folder = Folder(name = trimmed, parentId = parentId)
        return if (mutate { it.copy(folders = it.folders + folder) }) folder.id else null
    }

    suspend fun renameFolder(id: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        return mutate { current ->
            current.copy(folders = current.folders.map { if (it.id == id) it.copy(name = trimmed) else it })
        }
    }

    /**
     * Klasörü siler; içindeki kayıtlar silinmez, klasörsüz kalır.
     *
     * Klasör silmenin kayıt silmesi, kullanıcının beklemediği en pahalı
     * sürprizlerden biri olurdu.
     */
    suspend fun deleteFolder(id: String): Boolean = mutate { current ->
        val childIds = current.folders.filter { it.parentId == id }.map { it.id }.toSet() + id
        current.copy(
            folders = current.folders.filterNot { it.id in childIds },
            items = current.items.map { if (it.folderId in childIds) it.copy(folderId = null) else it }
        )
    }

    suspend fun moveToFolder(itemId: String, folderId: String?): Boolean = mutate { current ->
        current.copy(items = current.items.map {
            if (it.id == itemId) it.copy(folderId = folderId, updatedAt = System.currentTimeMillis()) else it
        })
    }

    // ----------------------------------------------------------------- ekler

    /** @return eklenen ek, sınır aşıldıysa ya da yazma başarısızsa `null`. */
    suspend fun addAttachment(itemId: String, name: String, mime: String, content: ByteArray): Attachment? =
        withContext(Dispatchers.IO) {
            if (content.size > MAX_ATTACHMENT_BYTES) return@withContext null
            val key = Crypto.randomBytes(Crypto.KEY_BYTES)
            val attachment = Attachment(
                name = name.take(120),
                mime = mime,
                size = content.size.toLong(),
                key = Crypto.hex(key)
            )
            val written = runCatching { store.writeAttachment(attachment.id, key, content) }.isSuccess
            key.fill(0)
            if (!written) return@withContext null

            val ok = mutate { current ->
                current.copy(items = current.items.map {
                    if (it.id == itemId) it.copy(
                        attachments = it.attachments + attachment,
                        updatedAt = System.currentTimeMillis()
                    ) else it
                })
            }
            if (ok) attachment else {
                store.deleteAttachment(attachment.id)
                null
            }
        }

    suspend fun removeAttachment(itemId: String, attachmentId: String): Boolean {
        val ok = mutate { current ->
            current.copy(items = current.items.map {
                if (it.id == itemId) it.copy(
                    attachments = it.attachments.filterNot { a -> a.id == attachmentId },
                    updatedAt = System.currentTimeMillis()
                ) else it
            })
        }
        if (ok) withContext(Dispatchers.IO) { store.deleteAttachment(attachmentId) }
        return ok
    }

    suspend fun readAttachment(attachment: Attachment): ByteArray? = withContext(Dispatchers.IO) {
        val key = runCatching { hexToBytes(attachment.key) }.getOrNull() ?: return@withContext null
        val content = store.readAttachment(attachment.id, key)
        key.fill(0)
        content
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    suspend fun addGeneratorHistory(value: String): Boolean = mutate { current ->
        val entry = SecretText.of(value)
        val merged = (listOf(entry) + current.generatorHistory).distinct().take(MAX_GENERATOR_HISTORY)
        // Listeden düşen üretilmiş parolalar bellekte asılı kalmasın.
        current.generatorHistory.filterNot { it in merged }.forEach { it.wipe() }
        current.copy(generatorHistory = merged)
    }

    suspend fun clearGeneratorHistory(): Boolean = mutate { it.copy(generatorHistory = emptyList()) }

    suspend fun recordScan(updated: List<VaultItem>, scannedAt: Long): Boolean = mutate { current ->
        current.copy(items = updated, lastScanAt = scannedAt)
    }

    /** İçe aktarma: aynı kimlikli kayıtlar yeni kimlikle eklenir, hiçbir şey ezilmez. */
    suspend fun merge(incoming: List<VaultItem>): Int {
        var added = 0
        mutate { current ->
            val existingIds = current.items.map { it.id }.toSet()
            val existingSignature = current.items.map { it.name to it.username }.toSet()
            val toAdd = incoming.mapNotNull { item ->
                if ((item.name to item.username) in existingSignature) null
                else if (item.id in existingIds) item.copy(id = VaultItem.randomId())
                else item
            }
            added = toAdd.size
            current.copy(items = current.items + toAdd)
        }
        return added
    }

    private suspend fun mutate(block: (VaultData) -> VaultData): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = vaultKey ?: return@withContext false
            val next = block(_data.value)
            runCatching {
                store.writeVault(key, next, activeSection)
                _data.value = next
            }.isSuccess
        }
    }

    // ------------------------------------------------------- zorlama parolası

    /**
     * Zorlama parolasını kurar ve yem kasayı örnek kayıtlarla doldurur.
     *
     * Yem boş bırakılmıyor: bir zorlayıcıya açılan boş kasa, kasanın yem
     * olduğunu söyleyen en açık işaret olurdu. Üretilen kayıtlar sıradan
     * hesaplar ve rastgele parolalar taşıyor; kullanıcı zorlama parolasıyla
     * girip bunları kendi istediği gibi düzenleyebilir — düzenlemesi de
     * tavsiye edilir, çünkü kendi hayatına benzeyen bir yem en inandırıcısı.
     */
    suspend fun setDuressPassword(duressPassword: CharArray): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (inDuressSession || vaultKey == null) return@withLock false
                val params = (store.currentKdfParams() ?: Kdf.defaultParams()).withFreshSalt()
                SecretBytes.ofUtf8(duressPassword).use { secret ->
                    store.setDuressPassword(secret, params, decoyVault())
                }
            } finally {
                duressPassword.fill('\u0000')
            }
        }
    }

    suspend fun clearDuressPassword(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (inDuressSession || vaultKey == null) return@withLock false
            store.clearDuressPassword()
        }
    }

    /**
     * Yem kasanın başlangıç içeriği.
     *
     * Parolalar gerçekten rastgele: "123456" gibi bir şey koymak, yemin yem
     * olduğunu ilk bakışta ele verirdi. Tarihler de geriye yayılıyor, hepsi
     * aynı dakikada oluşturulmuş bir kasa inandırıcı olmaz.
     */
    private fun decoyVault(): VaultData {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val seeds = listOf(
            Triple("Alışveriş", "posta@example.com", "example-shop.com"),
            Triple("Haber sitesi", "okuyucu", "news.example.net"),
            Triple("Forum", "kullanici41", "forum.example.org"),
            Triple("Spor salonu", "posta@example.com", "gym.example.com"),
            Triple("Yemek siparişi", "posta@example.com", "food.example.com")
        )
        val items = seeds.mapIndexed { index, (name, user, host) ->
            val age = (30L + index * 47L) * day
            VaultItem(
                name = name,
                category = Category.LOGIN,
                username = user,
                password = SecretText.of(
                    PasswordGenerator.generate(PasswordGenerator.Options(length = 16)).value
                ),
                url = host,
                createdAt = now - age,
                updatedAt = now - age / 2,
                passwordChangedAt = now - age,
                lastUsedAt = now - (index + 1) * day
            )
        }
        return VaultData(items = items, createdAt = now - 400 * day)
    }

    // --------------------------------------------------- ana parola / dosya

    suspend fun changeMasterPassword(current: CharArray, new: CharArray): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (inDuressSession) {
                    current.fill('\u0000')
                    new.fill('\u0000')
                    return@withLock false
                }
                try {
                    SecretBytes.ofUtf8(current).use { old ->
                        SecretBytes.ofUtf8(new).use { fresh ->
                            store.changeMasterPassword(old, fresh)
                        }
                    }
                } finally {
                    current.fill('\u0000')
                    new.fill('\u0000')
                }
            }
        }

    suspend fun regenerateRecoveryKey(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (inDuressSession) return@withContext null
            val key = vaultKey ?: return@withContext null
            runCatching { store.regenerateRecoveryKey(key) }.getOrNull()
        }
    }


    // --------------------------------------------------- kriptografi bakımı

    /**
     * Bu kasanın diskteki kriptografik ayarları. Ayarlar ekranı gösteriyor.
     *
     * Kilit açık olmasa da okunabilir: bu değerler gizli değil, dosya
     * başlığında düz duruyorlar ve başlık kimlik doğrulamalı verinin parçası
     * olduğu için kurcalanamıyorlar.
     */
    class CryptoProfile(val kdf: Kdf.Params?, val suite: AeadSuite) {
        val kdfName: String
            get() = when (kdf?.algorithm) {
                Kdf.ALG_ARGON2ID -> "Argon2id"
                Kdf.ALG_PBKDF2_SHA512 -> "PBKDF2-SHA512"
                else -> "?"
            }

        /** "64 MiB · 3 tur" ya da PBKDF2 için "600.000 tur". */
        val costLabel: String
            get() = when {
                kdf == null -> ""
                kdf.algorithm == Kdf.ALG_ARGON2ID -> "${kdf.memoryKib / 1024} MiB · ${kdf.iterations}"
                else -> kdf.iterations.toString()
            }
    }

    fun cryptoProfile(): CryptoProfile =
        CryptoProfile(store.currentKdfParams(), store.peekSuiteOnDisk() ?: store.suite)

    /**
     * Anahtar türetme maliyetini bu cihazda yeniden ölçer ve ana parola
     * sarmalayıcısını yeni maliyetle yazar.
     *
     * Neden gerekiyor: kasa eski, yavaş bir telefonda kurulup yenisine
     * taşındığında maliyet olduğu yerde kalıyor — yeni cihaz aynı işi çok daha
     * hızlı yapıyor, yani çevrimdışı saldırgan da öyle. Ölçümü tekrarlamak
     * korumayı cihazın bugünkü gücüne geri bağlıyor.
     *
     * Yalnızca ana parola sarmalayıcısına dokunuyor. Kurtarma sarmalayıcısı
     * kurtarma koduyla açıldığı için buradan yeniden yazılamaz; onu tazelemek
     * isteyen kullanıcı kurtarma anahtarını yeniler.
     */
    suspend fun recalibrateKdf(
        masterPassword: CharArray,
        onProgress: (Float) -> Unit = {}
    ): KdfCalibration.Result? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val calibration = KdfCalibration.calibrate(context, onProgress = onProgress)
                val ok = SecretBytes.ofUtf8(masterPassword).use { secret ->
                    SecretBytes.ofUtf8(masterPassword).use { same ->
                        store.changeMasterPassword(secret, same, calibration.params)
                    }
                }
                if (ok) calibration else null
            } catch (t: Throwable) {
                null
            } finally {
                masterPassword.fill(0.toChar())
            }
        }
    }

    /**
     * Kasa anahtarının kendisini yeniler: yeni anahtar, baştan şifrelenmiş
     * kasa, yeniden yazılmış sarmalayıcılar.
     *
     * Ana parola değişimi bunu yapmıyordu — yalnızca sarmalayıcıyı tazeliyor,
     * kasa anahtarı kurulumdan beri aynı kalıyordu. Eski bir yedek dosyası ya
     * da eski bir cihaz görüntüsü ele geçmişse fark burada ortaya çıkıyor:
     * parola değişimi o kopyayı korumasız bırakır, rotasyon işe yaramaz kılar.
     *
     * Dönen değer yeni kurtarma kodu; eski kod artık geçersiz. Biyometrik
     * sarmalayıcı da silinir, kullanıcı yeniden kurmak zorunda.
     */
    suspend fun rotateVaultKey(masterPassword: CharArray): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (inDuressSession) {
                masterPassword.fill('\u0000')
                return@withLock null
            }
            try {
                val rotation = SecretBytes.ofUtf8(masterPassword).use { secret ->
                    store.rotateVaultKey(secret)
                } ?: return@withLock null

                // Açık kasa yeni anahtarla devam ediyor; eski anahtar siliniyor.
                vaultKey?.wipe()
                vaultKey = rotation.vaultKey
                _data.value = store.readVault(rotation.vaultKey)
                _lockState.value = LockState.Unlocked
                rotation.recoveryCode
            } catch (t: Throwable) {
                null
            } finally {
                masterPassword.fill(0.toChar())
            }
        }
    }

    // ------------------------------------------------------------- passkey

    /** Bir alan adı için kasadaki passkey'ler, sahibi olan kayıtla birlikte. */
    fun passkeysFor(rpId: String): List<Pair<VaultItem, Passkey>> {
        val wanted = rpId.lowercase()
        return _data.value.liveItems.flatMap { item ->
            item.passkeys
                .filter { it.rpId.equals(wanted, ignoreCase = true) }
                .map { item to it }
        }.sortedByDescending { it.second.lastUsedAt }
    }

    fun findPasskey(credentialId: String): Pair<VaultItem, Passkey>? =
        _data.value.liveItems.firstNotNullOfOrNull { item ->
            item.passkeys.firstOrNull { it.credentialId == credentialId }?.let { item to it }
        }

    /**
     * Yeni passkey'i kasaya yazar.
     *
     * Aynı alan adı için bir kayıt zaten varsa passkey oraya iliştiriliyor;
     * yoksa alan adının adını taşıyan yeni bir kayıt açılıyor. Amaç, aynı site
     * için parola ve passkey'in ayrı iki satıra dağılmaması.
     */
    suspend fun addPasskey(passkey: Passkey): Boolean = mutate { current ->
        val host = passkey.rpId.lowercase()
        val owner = current.items.firstOrNull { !it.inTrash && it.host() == host }
            ?: current.items.firstOrNull { !it.inTrash && it.name.equals(passkey.rpName, ignoreCase = true) }

        if (owner != null) {
            val updated = owner.copy(
                passkeys = owner.passkeys.filterNot { it.credentialId == passkey.credentialId } + passkey,
                updatedAt = System.currentTimeMillis()
            )
            current.copy(items = current.items.map { if (it.id == updated.id) updated else it })
        } else {
            val created = VaultItem(
                name = passkey.rpName.ifBlank { passkey.rpId },
                category = Category.LOGIN,
                username = passkey.userName,
                url = passkey.rpId,
                passkeys = listOf(passkey)
            )
            current.copy(items = current.items + created)
        }
    }

    /** Passkey kullanıldı: kayıt "son kullanılanlar" listesinde öne çıksın. */
    suspend fun touchPasskey(credentialId: String): Boolean = mutate { current ->
        val now = System.currentTimeMillis()
        current.copy(
            items = current.items.map { item ->
                if (item.passkeys.none { it.credentialId == credentialId }) item
                else item.copy(
                    lastUsedAt = now,
                    passkeys = item.passkeys.map {
                        if (it.credentialId == credentialId) it.copy(lastUsedAt = now) else it
                    }
                )
            }
        )
    }

    suspend fun removePasskey(itemId: String, credentialId: String): Boolean = mutate { current ->
        current.copy(
            items = current.items.map { item ->
                if (item.id != itemId) item
                else {
                    item.passkeys.firstOrNull { it.credentialId == credentialId }?.privateKey?.wipe()
                    item.copy(
                        passkeys = item.passkeys.filterNot { it.credentialId == credentialId },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        )
    }

    /** Kasadaki tüm passkey'ler; ayarlar ve kurallı görünüm için. */
    fun allPasskeys(): List<Pair<VaultItem, Passkey>> =
        _data.value.liveItems.flatMap { item -> item.passkeys.map { item to it } }

    suspend fun exportVault(exportPassword: CharArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            SecretBytes.ofUtf8(exportPassword).use { secret ->
                store.exportEncrypted(_data.value, secret)
            }
        } catch (t: Throwable) {
            null
        } finally {
            exportPassword.fill('\u0000')
        }
    }

    suspend fun importVault(blob: ByteArray, password: CharArray): Int? = withContext(Dispatchers.IO) {
        val decoded: VaultData? = try {
            SecretBytes.ofUtf8(password).use { secret -> store.importEncrypted(blob, secret) }
        } finally {
            password.fill('\u0000')
        }
        if (decoded == null) null else merge(decoded.items)
    }

    suspend fun wipeEverything() = withContext(Dispatchers.IO) {
        store.wipe()
        settings.clear()
        hardReset()
    }

    fun masterKeyChangedAt(): Long = store.masterKeyChangedAt()

    fun failedAttempts(): Int = store.readAttempts().failed

    // ------------------------------------------------------------ sorgular

    fun byId(id: String): VaultItem? = _data.value.items.firstOrNull { it.id == id }

    fun folderById(id: String?): Folder? =
        id?.let { wanted -> _data.value.folders.firstOrNull { it.id == wanted } }

    /**
     * Etkin görünüm + kategori + arama metnine göre kayıtları süzer.
     *
     * Çöp kutusu ayrı bir görünüm: başka hiçbir süzgeçte silinmiş kayıt
     * görünmez, çöp kutusunda ise yalnızca onlar görünür.
     */
    fun filter(
        category: Category?,
        query: String,
        view: VaultFilter = VaultFilter.All
    ): List<VaultItem> {
        val base = if (view.isTrash) _data.value.trashedItems else _data.value.liveItems
        val scoped = when (view) {
            VaultFilter.All -> base
            is VaultFilter.InFolder -> base.filter { it.folderId == view.folderId }
            is VaultFilter.Smart -> base.filter { matchesSmart(it, view.kind) }
        }
        val byCategory = if (category == null) scoped else scoped.filter { it.category == category }
        if (query.isBlank()) return byCategory.sortedBy { it.name.lowercase(TR) }

        val q = query.trim().lowercase(TR)
        return byCategory.filter { item -> matchesQuery(item, q) }
            .sortedBy { it.name.lowercase(TR) }
    }

    private fun matchesQuery(item: VaultItem, q: String): Boolean =
        item.name.lowercase(TR).contains(q) ||
            item.username.lowercase(TR).contains(q) ||
            item.url.lowercase(TR).contains(q) ||
            item.tags.any { it.lowercase(TR).contains(q) } ||
            folderById(item.folderId)?.name?.lowercase(TR)?.contains(q) == true ||
            CategorySchema.searchableValues(item).any { it.lowercase(TR).contains(q) }

    /** Kurallı klasörlerin kuralları. Tek yerde durmaları önemli: güvenlik
     *  ekranındaki bulgular ve buradaki görünümler aynı tanımı kullanmalı. */
    private fun matchesSmart(item: VaultItem, kind: SmartFolder): Boolean = when (kind) {
        SmartFolder.FAVORITES -> item.favorite
        SmartFolder.PASSKEYS -> item.hasPasskey
        SmartFolder.LEAKED -> item.breached
        SmartFolder.REUSED -> item.password.isNotBlank() && reusedPasswords().contains(item.password)
        SmartFolder.WEAK -> item.primarySecret.isNotBlank() &&
            PasswordStrength.evaluate(item.primarySecret).tone == PasswordStrength.Tone.WEAK
        SmartFolder.OLD -> item.password.isNotBlank() &&
            System.currentTimeMillis() - item.passwordChangedAt > OLD_PASSWORD_MILLIS
        SmartFolder.NO_2FA -> item.category == Category.LOGIN &&
            item.password.isNotBlank() && item.totpSecret.isBlank()
        SmartFolder.TRASH -> item.inTrash
    }

    private fun reusedPasswords(): Set<SecretText> =
        _data.value.liveItems
            .filter { it.password.isNotBlank() }
            .groupingBy { it.password }
            .eachCount()
            .filterValues { it > 1 }
            .keys

    /** Kurallı klasörlerin rozet sayıları. */
    fun smartCounts(): Map<SmartFolder, Int> {
        val live = _data.value.liveItems
        return SmartFolder.entries.associateWith { kind ->
            if (kind == SmartFolder.TRASH) _data.value.trashedItems.size
            else live.count { matchesSmart(it, kind) }
        }
    }

    fun folderCounts(): Map<String, Int> =
        _data.value.liveItems.mapNotNull { it.folderId }.groupingBy { it }.eachCount()

    fun recents(limit: Int = 8): List<VaultItem> =
        _data.value.liveItems
            .filter { it.lastUsedAt > 0 || it.favorite }
            .sortedWith(compareByDescending<VaultItem> { it.favorite }.thenByDescending { it.lastUsedAt })
            .take(limit)

    /** Otomatik doldurma için: paket adı ve alan adına göre eşleşen kayıtlar. */
    fun matchesFor(packageName: String?, webDomain: String?): List<VaultItem> {
        val items = _data.value.liveItems
            .filter { it.category == Category.LOGIN || it.category == Category.OTP }
        val domain = webDomain?.lowercase()?.removePrefix("www.")
        val appToken = packageName?.substringAfterLast('.')?.lowercase()

        return items.filter { item ->
            val host = item.host()
            when {
                domain != null && host != null &&
                    (host == domain || host.endsWith(".$domain") || domain.endsWith(".$host")) -> true
                domain != null && item.name.lowercase(TR).let { domain.contains(it) || it.contains(domain.substringBefore('.')) } -> true
                appToken != null && appToken.length >= 3 &&
                    (item.name.lowercase(TR).contains(appToken) || host?.contains(appToken) == true) -> true
                packageName != null && item.tags.any { it.equals(packageName, ignoreCase = true) } -> true
                else -> false
            }
        }.sortedByDescending { it.lastUsedAt }
    }

    companion object {
        const val MAX_HISTORY = 10
        const val MAX_GENERATOR_HISTORY = 30

        /** Çöp kutusunda bekleme süresi. */
        const val TRASH_RETENTION_DAYS = 30

        /** Tek ek için üst sınır: 10 MiB. */
        const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

        /** "Bir yıldan eski" eşiği; güvenlik tarayıcısıyla aynı değer. */
        const val OLD_PASSWORD_MILLIS = 365L * 24 * 60 * 60 * 1000

        val TR: java.util.Locale = java.util.Locale("tr", "TR")
    }
}
