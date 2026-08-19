package app.kasa.data.repo

import android.content.Context
import app.kasa.core.crypto.SecretBytes
import app.kasa.core.security.SecureClipboard
import app.kasa.data.SettingsStore
import app.kasa.data.VaultStore
import app.kasa.data.model.Category
import app.kasa.data.model.PasswordHistoryEntry
import app.kasa.data.model.VaultData
import app.kasa.data.model.VaultItem
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
            vaultKey?.wipe()
            vaultKey = result.vaultKey
            _data.value = try {
                store.readVault(result.vaultKey)
            } catch (t: Throwable) {
                VaultData()
            }
            _lockState.value = LockState.Unlocked
            UnlockOutcome.Success
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

    suspend fun delete(id: String): Boolean = mutate { current ->
        current.copy(items = current.items.filterNot { it.id == id })
    }

    suspend fun restore(item: VaultItem): Boolean = mutate { current ->
        current.copy(items = current.items + item)
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

    fun filter(category: Category?, query: String): List<VaultItem> {
        val items = _data.value.items
        val byCategory = if (category == null) items else items.filter { it.category == category }
        if (query.isBlank()) return byCategory.sortedBy { it.name.lowercase(TR) }
        val q = query.trim().lowercase(TR)
        return byCategory.filter { item ->
            item.name.lowercase(TR).contains(q) ||
                item.username.lowercase(TR).contains(q) ||
                item.url.lowercase(TR).contains(q) ||
                item.tags.any { it.lowercase(TR).contains(q) }
        }.sortedBy { it.name.lowercase(TR) }
    }

    fun recents(limit: Int = 8): List<VaultItem> =
        _data.value.items
            .filter { it.lastUsedAt > 0 || it.favorite }
            .sortedWith(compareByDescending<VaultItem> { it.favorite }.thenByDescending { it.lastUsedAt })
            .take(limit)

    /** Otomatik doldurma için: paket adı ve alan adına göre eşleşen kayıtlar. */
    fun matchesFor(packageName: String?, webDomain: String?): List<VaultItem> {
        val items = _data.value.items.filter { it.category == Category.LOGIN || it.category == Category.OTP }
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

    private companion object {
        const val MAX_HISTORY = 10
        const val MAX_GENERATOR_HISTORY = 30
        val TR: java.util.Locale = java.util.Locale("tr", "TR")
    }
}
