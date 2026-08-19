package app.kasa.data.repo

import android.content.Context
import app.kasa.core.crypto.SecretBytes
import app.kasa.core.security.SecureClipboard
import app.kasa.data.SettingsStore
import app.kasa.data.VaultStore
import app.kasa.data.VaultTooNewException
import app.kasa.core.crypto.Crypto
import app.kasa.data.model.Attachment
import app.kasa.data.model.Category
import app.kasa.data.model.CategorySchema
import app.kasa.data.model.Folder
import app.kasa.data.model.PasswordHistoryEntry
import app.kasa.data.model.SmartFolder
import app.kasa.data.model.VaultData
import app.kasa.data.model.VaultFilter
import app.kasa.data.model.VaultItem
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

    suspend fun createVault(masterPassword: CharArray): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                SecretBytes.ofUtf8(masterPassword).use { secret ->
                    val created = store.createVault(secret)
                    vaultKey = created.vaultKey
                    _data.value = store.readVault(created.vaultKey)
                    _lockState.value = LockState.Unlocked
                    _pendingRecoveryCode.value = created.recoveryCode
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
            val loaded = runCatching { store.readVault(result.vaultKey) }
            loaded.fold(
                onSuccess = { data ->
                    vaultKey?.wipe()
                    vaultKey = result.vaultKey
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
        _data.value = VaultData()
        _pendingRecoveryCode.value = null
        _lockState.value = if (store.vaultExists()) LockState.Locked else LockState.NeedsSetup
        SecureClipboard.clearNow(context)
        SecureClipboard.cancelScheduledClear(context)
    }

    private fun hardReset() {
        vaultKey?.wipe()
        vaultKey = null
        _data.value = VaultData()
        _pendingRecoveryCode.value = null
        _lockState.value = LockState.NeedsSetup
        scope.launch { settings.clear() }
    }

    // ------------------------------------------------------------ biyometri

    fun biometricEncryptCipherOrNull(): Cipher? = runCatching {
        app.kasa.core.crypto.KeystoreKeys.biometricEncryptCipher(context)
    }.getOrNull()

    suspend fun enableBiometric(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val key = vaultKey ?: return@withContext false
        runCatching { store.enableBiometric(cipher, key) }.isSuccess
    }

    suspend fun disableBiometric() = withContext(Dispatchers.IO) { store.disableBiometric() }

    fun biometricEnrolled(): Boolean = store.biometricEnrolled()

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
        current.copy(generatorHistory = (listOf(value) + current.generatorHistory).distinct().take(MAX_GENERATOR_HISTORY))
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
                store.writeVault(key, next)
                _data.value = next
            }.isSuccess
        }
    }

    // --------------------------------------------------- ana parola / dosya

    suspend fun changeMasterPassword(current: CharArray, new: CharArray): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
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
            val key = vaultKey ?: return@withContext null
            runCatching { store.regenerateRecoveryKey(key) }.getOrNull()
        }
    }

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

    private fun reusedPasswords(): Set<String> =
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
