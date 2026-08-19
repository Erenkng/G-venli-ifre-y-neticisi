package app.kasa.data

import android.content.Context
import app.kasa.core.crypto.Base32
import app.kasa.core.crypto.Crypto
import app.kasa.core.crypto.Kdf
import app.kasa.core.crypto.KeystoreKeys
import app.kasa.core.crypto.RecoveryKey
import app.kasa.core.crypto.SecretBytes
import app.kasa.data.model.VaultData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

/**
 * Kasanın diskteki tüm hâli.
 *
 * Katmanlar:
 *
 * ```
 *  ana parola --Argon2id--> KEK ----+
 *  kurtarma anahtarı --Argon2id--> KEK2 --+--> AES-256-GCM ile sarmalanmış KASA ANAHTARI
 *  biyometri --Keystore/StrongBox--> KEK3 -+
 *                                            |
 *                                            v
 *                              AES-256-GCM(kasa JSON'u)
 * ```
 *
 * Kasa anahtarı yalnızca bir kez üretilir; ana parola değiştiğinde tüm kasa
 * yeniden şifrelenmez, sadece sarmalayıcı yenilenir. Bu hem hızlıdır hem de
 * değişiklik sırasında veri kaybı penceresi bırakmaz.
 *
 * Üç ayrı sarmalayıcı dosyası vardır; biri silinince (örneğin kullanıcı yeni
 * parmak izi kaydettiğinde biyometrik sarmalayıcı) diğerleri etkilenmez.
 */
class VaultStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "kasa").apply { if (!exists()) mkdirs() }

    private val masterKeyFile get() = File(dir, "master.key")
    private val recoveryKeyFile get() = File(dir, "recovery.key")
    private val biometricKeyFile get() = File(dir, "biometric.key")
    private val vaultFile get() = File(dir, "vault.bin")
    private val attemptsFile get() = File(dir, "attempts.bin")

    /** Ek dosyaları: her biri kendi anahtarıyla ayrı ayrı şifreli. */
    private val attachmentDir get() = File(dir, "att").apply { if (!exists()) mkdirs() }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // ------------------------------------------------------------------ durum

    fun vaultExists(): Boolean = masterKeyFile.exists() && vaultFile.exists()

    fun biometricEnrolled(): Boolean = biometricKeyFile.exists() && KeystoreKeys.hasBiometricKey()

    fun hasRecoveryKey(): Boolean = recoveryKeyFile.exists()

    /** Ana parolanın en son ne zaman değiştiğini verir (sarmalayıcının yaşı). */
    fun masterKeyChangedAt(): Long = masterKeyFile.lastModified()

    // ------------------------------------------------------------- oluşturma

    class NewVault(val vaultKey: SecretBytes, val recoveryCode: String)

    /**
     * Sıfırdan kasa kurar. Ağırdır (Argon2id), arka planda çağır.
     * Dönen kurtarma kodu bir daha üretilemez.
     */
    fun createVault(masterPassword: SecretBytes): NewVault {
        check(!vaultExists()) { "Kasa zaten var" }
        val vaultKey = SecretBytes.random(Crypto.KEY_BYTES)

        writeWrappedKey(masterKeyFile, MAGIC_MASTER, masterPassword, vaultKey, Kdf.defaultParams())

        val recoveryCode = RecoveryKey.generate()
        RecoveryKey.toSecret(recoveryCode)!!.use { secret ->
            writeWrappedKey(recoveryKeyFile, MAGIC_RECOVERY, secret, vaultKey, Kdf.defaultParams())
        }

        writeVault(vaultKey, VaultData())
        resetAttempts()
        return NewVault(vaultKey, recoveryCode)
    }

    // ------------------------------------------------------------ kilit açma

    sealed interface UnlockResult {
        class Success(val vaultKey: SecretBytes) : UnlockResult
        data object WrongSecret : UnlockResult
        class Blocked(val remainingMillis: Long) : UnlockResult
        data object Wiped : UnlockResult
        class Failure(val cause: Throwable) : UnlockResult
    }

    /**
     * Ana parolayla açar. Yanlış denemeleri sayar; [wipeAfterAttempts] sıfırdan
     * büyükse ve sayaç aşılırsa kasa geri dönüşsüz silinir.
     */
    fun unlockWithPassword(password: SecretBytes, wipeAfterAttempts: Int = 0): UnlockResult {
        val state = readAttempts()
        val now = System.currentTimeMillis()
        if (state.blockedUntil > now) return UnlockResult.Blocked(state.blockedUntil - now)

        return try {
            val key = openWrappedKey(masterKeyFile, MAGIC_MASTER, password)
            resetAttempts()
            UnlockResult.Success(key)
        } catch (e: AEADBadTagException) {
            onFailedAttempt(state, wipeAfterAttempts)
        } catch (e: javax.crypto.BadPaddingException) {
            onFailedAttempt(state, wipeAfterAttempts)
        } catch (t: Throwable) {
            UnlockResult.Failure(t)
        }
    }

    /** Kurtarma anahtarıyla açar. Deneme sayacı burada da işler. */
    fun unlockWithRecovery(code: String, wipeAfterAttempts: Int = 0): UnlockResult {
        val secret = RecoveryKey.toSecret(code) ?: return UnlockResult.WrongSecret
        return secret.use { unlockWithRecoverySecret(it, wipeAfterAttempts) }
    }

    private fun unlockWithRecoverySecret(secret: SecretBytes, wipeAfterAttempts: Int): UnlockResult {
        if (!recoveryKeyFile.exists()) return UnlockResult.WrongSecret
        val state = readAttempts()
        val now = System.currentTimeMillis()
        if (state.blockedUntil > now) return UnlockResult.Blocked(state.blockedUntil - now)
        return try {
            val key = openWrappedKey(recoveryKeyFile, MAGIC_RECOVERY, secret)
            resetAttempts()
            UnlockResult.Success(key)
        } catch (e: AEADBadTagException) {
            onFailedAttempt(state, wipeAfterAttempts)
        } catch (t: Throwable) {
            UnlockResult.Failure(t)
        }
    }

    /**
     * Biyometrik sarmalayıcı için çözme şifreleyicisi hazırlar.
     * BiometricPrompt'a verilecek nesne budur; `null` ise biyometrik yol yok.
     */
    fun biometricDecryptCipher(): Cipher? {
        if (!biometricKeyFile.exists()) return null
        val blob = runCatching { biometricKeyFile.readBytes() }.getOrNull() ?: return null
        val input = DataInputStream(ByteArrayInputStream(blob))
        return runCatching {
            val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC_BIOMETRIC))
            input.readByte()
            val ivLen = input.readUnsignedByte()
            val iv = ByteArray(ivLen).also { input.readFully(it) }
            KeystoreKeys.biometricDecryptCipher(iv)
        }.getOrNull()
    }

    /** Doğrulanmış şifreleyiciyle sarmalanmış kasa anahtarını çözer. */
    fun unlockWithBiometric(cipher: Cipher): UnlockResult = try {
        val blob = biometricKeyFile.readBytes()
        val input = DataInputStream(ByteArrayInputStream(blob))
        val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(magic.contentEquals(MAGIC_BIOMETRIC)) { "Bozuk biyometrik sarmalayıcı" }
        input.readByte()
        val ivLen = input.readUnsignedByte()
        input.skipBytes(ivLen)
        val len = input.readInt()
        val wrapped = ByteArray(len).also { input.readFully(it) }
        val key = cipher.doFinal(wrapped)
        resetAttempts()
        UnlockResult.Success(SecretBytes(key))
    } catch (t: Throwable) {
        // Sarmalayıcı çözülemiyorsa artık işe yaramaz; kullanıcıyı ana parolaya yolla.
        disableBiometric()
        UnlockResult.Failure(t)
    }

    /** Biyometrik sarmalayıcıyı yazar. [cipher] doğrulanmış şifreleme şifreleyicisidir. */
    fun enableBiometric(cipher: Cipher, vaultKey: SecretBytes) {
        val wrapped = cipher.doFinal(vaultKey.raw())
        val iv = cipher.iv
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.write(MAGIC_BIOMETRIC)
            d.writeByte(FORMAT_VERSION)
            d.writeByte(iv.size)
            d.write(iv)
            d.writeInt(wrapped.size)
            d.write(wrapped)
        }
        writeAtomically(biometricKeyFile, out.toByteArray())
    }

    fun disableBiometric() {
        secureDelete(biometricKeyFile)
        KeystoreKeys.deleteBiometricKey()
    }

    // --------------------------------------------------------- ana parola değişimi

    /**
     * Ana parolayı değiştirir. Kasa anahtarı aynı kalır, yalnızca sarmalayıcı
     * yeni tuz ve yeni KEK ile baştan yazılır.
     */
    fun changeMasterPassword(current: SecretBytes, new: SecretBytes): Boolean {
        val key = try {
            openWrappedKey(masterKeyFile, MAGIC_MASTER, current)
        } catch (t: Throwable) {
            return false
        }
        key.use {
            writeWrappedKey(masterKeyFile, MAGIC_MASTER, new, it, Kdf.defaultParams())
        }
        resetAttempts()
        return true
    }

    /** Kurtarma anahtarını yeniler ve yeni kodu döndürür. */
    fun regenerateRecoveryKey(vaultKey: SecretBytes): String {
        val code = RecoveryKey.generate()
        RecoveryKey.toSecret(code)!!.use { secret ->
            writeWrappedKey(recoveryKeyFile, MAGIC_RECOVERY, secret, vaultKey, Kdf.defaultParams())
        }
        return code
    }

    // ------------------------------------------------------------ kasa içeriği

    fun readVault(vaultKey: SecretBytes): VaultData {
        if (!vaultFile.exists()) return VaultData()
        val blob = vaultFile.readBytes()
        val input = DataInputStream(ByteArrayInputStream(blob))
        val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(magic.contentEquals(MAGIC_VAULT)) { "Bu bir Kasa dosyası değil" }
        val version = input.readByte().toInt()
        require(version == FORMAT_VERSION) { "Desteklenmeyen kasa sürümü: $version" }
        val len = input.readInt()
        val sealed = ByteArray(len).also { input.readFully(it) }
        val aad = blob.copyOfRange(0, MAGIC_LEN + 1)
        val plain = Crypto.open(vaultKey.raw(), sealed, aad)
        return try {
            // Önce ham JSON'a bak: şema sürümünü öğrenip gerekiyorsa yükselt.
            // Doğrudan decode etmek, bizden yeni bir dosyayı sessizce budardı.
            val root = json.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject
            val migrated = VaultMigrations.migrate(root)
            json.decodeFromJsonElement(VaultData.serializer(), migrated)
        } finally {
            plain.fill(0)
        }
    }

    fun writeVault(vaultKey: SecretBytes, data: VaultData) {
        val plain = json.encodeToString(VaultData.serializer(), data).toByteArray(Charsets.UTF_8)
        try {
            val header = ByteArrayOutputStream().apply {
                write(MAGIC_VAULT)
                write(FORMAT_VERSION)
            }.toByteArray()
            val sealed = Crypto.seal(vaultKey.raw(), plain, header)
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { d ->
                d.write(header)
                d.writeInt(sealed.size)
                d.write(sealed)
            }
            writeAtomically(vaultFile, out.toByteArray())
        } finally {
            plain.fill(0)
        }
    }

    // -------------------------------------------------------- dışa/içe aktarma

    /**
     * Taşınabilir, kendi başına şifreli dosya üretir. Ana parolayla değil,
     * kullanıcının seçtiği ayrı bir dışa aktarma parolasıyla korunur; böylece
     * yedek dosyası ana parolayı ifşa etmez.
     */
    fun exportEncrypted(data: VaultData, exportPassword: SecretBytes): ByteArray {
        val params = Kdf.defaultParams(forExport = true)
        val plain = json.encodeToString(VaultData.serializer(), data).toByteArray(Charsets.UTF_8)
        try {
            val header = ByteArrayOutputStream()
            DataOutputStream(header).use { d ->
                d.write(MAGIC_EXPORT)
                d.writeByte(FORMAT_VERSION)
                params.writeTo(d)
            }
            val headerBytes = header.toByteArray()
            return Kdf.derive(exportPassword, params).use { kek ->
                val sealed = Crypto.seal(kek.raw(), plain, headerBytes)
                val out = ByteArrayOutputStream()
                DataOutputStream(out).use { d ->
                    d.write(headerBytes)
                    d.writeInt(sealed.size)
                    d.write(sealed)
                }
                out.toByteArray()
            }
        } finally {
            plain.fill(0)
        }
    }

    /** Dışa aktarılmış dosyayı çözer. Parola yanlışsa `null` döner. */
    fun importEncrypted(blob: ByteArray, exportPassword: SecretBytes): VaultData? {
        return try {
            val stream = ByteArrayInputStream(blob)
            val input = DataInputStream(stream)
            val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC_EXPORT)) { "Bu bir .kasa dosyası değil" }
            input.readByte()
            val params = Kdf.Params.readFrom(input)
            val headerLen = blob.size - stream.available()
            val headerBytes = blob.copyOfRange(0, headerLen)
            val len = input.readInt()
            val sealed = ByteArray(len).also { input.readFully(it) }
            Kdf.derive(exportPassword, params).use { kek ->
                val plain = Crypto.open(kek.raw(), sealed, headerBytes)
                try {
                    val root = json.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject
                    json.decodeFromJsonElement(VaultData.serializer(), VaultMigrations.migrate(root))
                } finally {
                    plain.fill(0)
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------- ekler

    /**
     * Eki diske yazar. [key] o eke özel 32 baytlık anahtardır; kasa anahtarı
     * değil. Dosya biçimi kasa dosyasıyla aynı: sihirli sayı + AES-GCM.
     */
    fun writeAttachment(id: String, key: ByteArray, content: ByteArray) {
        require(key.size == Crypto.KEY_BYTES) { "Ek anahtarı 32 bayt olmalı" }
        val header = ByteArrayOutputStream().apply {
            write(MAGIC_ATTACHMENT)
            write(FORMAT_VERSION)
        }.toByteArray()
        val sealed = Crypto.seal(key, content, header)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.write(header)
            d.writeInt(sealed.size)
            d.write(sealed)
        }
        writeAtomically(File(attachmentDir, "$id.bin"), out.toByteArray())
    }

    /** Eki çözer. Dosya yoksa ya da anahtar yanlışsa `null`. */
    fun readAttachment(id: String, key: ByteArray): ByteArray? = try {
        val blob = File(attachmentDir, "$id.bin").readBytes()
        val input = DataInputStream(ByteArrayInputStream(blob))
        val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(magic.contentEquals(MAGIC_ATTACHMENT)) { "Bu bir Kasa eki değil" }
        input.readByte()
        val len = input.readInt()
        val sealed = ByteArray(len).also { input.readFully(it) }
        Crypto.open(key, sealed, blob.copyOfRange(0, MAGIC_LEN + 1))
    } catch (t: Throwable) {
        null
    }

    fun deleteAttachment(id: String) {
        secureDelete(File(attachmentDir, "$id.bin"))
    }

    /**
     * Kasada artık adı geçmeyen ek dosyalarını siler.
     *
     * Kayıt silinirken ekleri de silinir, ama içe aktarma ya da yarım kalmış
     * bir yazma sonrası sahipsiz dosya kalabilir; bunlar diskte şifreli
     * çöp olarak birikmemeli.
     */
    fun pruneOrphanAttachments(known: Set<String>) {
        runCatching {
            attachmentDir.listFiles()?.forEach { file ->
                val id = file.name.removeSuffix(".bin")
                if (id !in known) secureDelete(file)
            }
        }
    }

    // ------------------------------------------------------------- deneme sayacı

    class Attempts(val failed: Int, val blockedUntil: Long)

    /**
     * Yanlış deneme sayacı cihaz anahtarıyla şifreli tutulur; dosyayı silerek
     * sayacı sıfırlamak isteyen biri, silme işlemini de en baştan kilitli
     * sayacı yeniden kurmak zorunda kalır.
     */
    fun readAttempts(): Attempts {
        if (!attemptsFile.exists()) return Attempts(0, 0)
        val raw = runCatching { attemptsFile.readBytes() }.getOrNull() ?: return Attempts(0, 0)
        val plain = KeystoreKeys.deviceOpen(raw) ?: return Attempts(0, 0)
        return runCatching {
            val input = DataInputStream(ByteArrayInputStream(plain))
            Attempts(input.readInt(), input.readLong())
        }.getOrDefault(Attempts(0, 0))
    }

    private fun writeAttempts(attempts: Attempts) {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(attempts.failed)
            d.writeLong(attempts.blockedUntil)
        }
        runCatching { writeAtomically(attemptsFile, KeystoreKeys.deviceSeal(out.toByteArray())) }
    }

    fun resetAttempts() {
        writeAttempts(Attempts(0, 0))
    }

    private fun onFailedAttempt(state: Attempts, wipeAfterAttempts: Int): UnlockResult {
        val failed = state.failed + 1
        if (wipeAfterAttempts in 1..failed) {
            wipe()
            return UnlockResult.Wiped
        }
        // Üstel bekleme: 3. denemeden sonra 5 sn, sonra 15, 45, 135... en çok 30 dk.
        val blockMillis = if (failed < 3) 0L else {
            val seconds = (5.0 * Math.pow(3.0, (failed - 3).toDouble())).toLong()
            minOf(seconds, 1800L) * 1000L
        }
        writeAttempts(Attempts(failed, if (blockMillis > 0) System.currentTimeMillis() + blockMillis else 0))
        return if (blockMillis > 0) UnlockResult.Blocked(blockMillis) else UnlockResult.WrongSecret
    }

    // --------------------------------------------------------------- silme

    /** Kasayı ve tüm anahtarları geri dönüşsüz siler. */
    fun wipe() {
        listOf(masterKeyFile, recoveryKeyFile, biometricKeyFile, vaultFile, attemptsFile)
            .forEach { secureDelete(it) }
        runCatching { attachmentDir.listFiles()?.forEach { secureDelete(it) } }
        runCatching { attachmentDir.delete() }
        KeystoreKeys.deleteAll()
        runCatching { dir.listFiles()?.forEach { if (it.isFile) secureDelete(it) } }
    }

    // ------------------------------------------------------------- yardımcılar

    private fun writeWrappedKey(
        file: File,
        magic: ByteArray,
        secret: SecretBytes,
        vaultKey: SecretBytes,
        params: Kdf.Params
    ) {
        val header = ByteArrayOutputStream()
        DataOutputStream(header).use { d ->
            d.write(magic)
            d.writeByte(FORMAT_VERSION)
            params.writeTo(d)
        }
        val headerBytes = header.toByteArray()
        Kdf.derive(secret, params).use { kek ->
            val wrapped = Crypto.seal(kek.raw(), vaultKey.raw(), headerBytes)
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { d ->
                d.write(headerBytes)
                d.writeInt(wrapped.size)
                d.write(wrapped)
            }
            writeAtomically(file, out.toByteArray())
        }
    }

    private fun openWrappedKey(file: File, magic: ByteArray, secret: SecretBytes): SecretBytes {
        val blob = file.readBytes()
        val stream = ByteArrayInputStream(blob)
        val input = DataInputStream(stream)
        val fileMagic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(fileMagic.contentEquals(magic)) { "Bozuk anahtar dosyası" }
        input.readByte()
        val params = Kdf.Params.readFrom(input)
        val headerLen = blob.size - stream.available()
        val headerBytes = blob.copyOfRange(0, headerLen)
        val len = input.readInt()
        val wrapped = ByteArray(len).also { input.readFully(it) }
        return Kdf.derive(secret, params).use { kek ->
            SecretBytes(Crypto.open(kek.raw(), wrapped, headerBytes))
        }
    }

    /**
     * Yarım yazılmış kasa dosyası kalmasın diye önce geçici dosyaya yazıp
     * `fsync` sonrası yeniden adlandırırız.
     */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Kasa dosyası yazılamadı" }
        }
    }

    /**
     * Dosyayı silmeden önce üzerine rastgele veri yazar. Flash bellekte
     * (aşınma dengeleme yüzünden) bu tek başına yeterli bir garanti değildir;
     * asıl güvence dosyanın zaten şifreli olması ve anahtarının Keystore'dan
     * silinmesidir. Yine de kolay kurtarmayı engeller.
     */
    private fun secureDelete(file: File) {
        if (!file.exists()) return
        runCatching {
            val size = file.length().toInt().coerceAtLeast(1)
            FileOutputStream(file).use { out ->
                out.write(Crypto.randomBytes(size))
                out.flush()
                out.fd.sync()
            }
        }
        file.delete()
    }

    companion object {
        private const val MAGIC_LEN = 8
        private const val FORMAT_VERSION = 1

        private val MAGIC_MASTER = "KASAMST1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_RECOVERY = "KASAREC1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_BIOMETRIC = "KASABIO1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_VAULT = "KASAVLT1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_ATTACHMENT = "KASAATT1".toByteArray(Charsets.US_ASCII)
        val MAGIC_EXPORT = "KASAEXP1".toByteArray(Charsets.US_ASCII)

        const val EXPORT_EXTENSION = "kasa"

        /** Kullanıcının seçtiği dosyanın gerçekten .kasa olup olmadığını hızlıca söyler. */
        fun looksLikeExport(head: ByteArray): Boolean =
            head.size >= MAGIC_LEN && head.copyOfRange(0, MAGIC_LEN).contentEquals(MAGIC_EXPORT)

        /** TOTP anahtarının biçimsel geçerliliği. */
        fun isValidTotpSecret(secret: String): Boolean {
            val bytes = Base32.decodeRfc4648(secret) ?: return false
            return bytes.size >= 10
        }
    }
}
