package app.kasa.data

import android.content.Context
import app.kasa.core.crypto.AeadSuite
import app.kasa.core.crypto.Base32
import app.kasa.core.crypto.Crypto
import app.kasa.core.crypto.Kdf
import app.kasa.core.crypto.KeystoreKeys
import app.kasa.core.crypto.RecoveryKey
import app.kasa.core.crypto.SecretBytes
import app.kasa.data.model.VaultData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.FutureTask
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

    /**
     * Zorlama parolası sarmalayıcısı.
     *
     * **Her zaman var.** Kullanıcı bir zorlama parolası kurmadıysa da var ve
     * o zaman rastgele, kimsenin bilmediği bir gizle sarmalanmış oluyor.
     * Varlığı bir şey ele vermiyor; yokluğu ele verirdi.
     */
    private val duressKeyFile get() = File(dir, "duress.key")
    private val biometricKeyFile get() = File(dir, "biometric.key")
    private val vaultFile get() = File(dir, "vault.bin")
    private val attemptsFile get() = File(dir, "attempts.bin")

    /** Ek dosyaları: her biri kendi anahtarıyla ayrı ayrı şifreli. */
    private val attachmentDir get() = File(dir, "att").apply { if (!exists()) mkdirs() }

    /** Yarım kalmış anahtar rotasyonunun izi. */
    private val rotationMarker get() = File(dir, "rotation.pending")

    /** Hızlı PIN katmanı: sarmalayıcı ve kendi deneme sayacı. */
    private val pinKeyFile get() = File(dir, "pin.key")
    private val pinAttemptsFile get() = File(dir, "pin.attempts")

    /**
     * Bu kasanın şifreleme paketi. Dosya başlığından okunur; sonraki yazmalar
     * aynı paketi kullanır, yoksa kasa her kaydetmede biçim değiştirirdi.
     */
    @Volatile
    private var activeSuite: AeadSuite? = null

    val suite: AeadSuite get() = activeSuite ?: AeadSuite.DEFAULT

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    init {
        // Cihaz rotasyonun ortasında kapandıysa dosyalar yarım kalmış olabilir;
        // kasayı açmayı denemeden önce iş bitirilir.
        runCatching { completePendingRotation() }
        runCatching { activeSuite = peekSuiteOnDisk() }
    }

    // ------------------------------------------------------------------ durum

    fun vaultExists(): Boolean = masterKeyFile.exists() && vaultFile.exists()

    fun biometricEnrolled(): Boolean = biometricKeyFile.exists() && KeystoreKeys.hasBiometricKey()

    fun hasRecoveryKey(): Boolean = recoveryKeyFile.exists()

    /** Ana parolanın en son ne zaman değiştiğini verir (sarmalayıcının yaşı). */
    fun masterKeyChangedAt(): Long = masterKeyFile.lastModified()

    /**
     * Kasa dosyasının başlığındaki şifreleme paketini anahtar olmadan okur.
     *
     * Başlık kimlik doğrulamalı verinin (AAD) parçası olduğu için kurcalanamaz;
     * kilit açılmadan da güvenle gösterilebilir. Ayarlar ekranı bunu kullanıyor.
     */
    fun peekSuiteOnDisk(): AeadSuite? = runCatching {
        if (!vaultFile.exists()) return@runCatching null
        val head = ByteArray(MAGIC_LEN + 2)
        vaultFile.inputStream().use { stream ->
            if (stream.read(head) != head.size) return@runCatching null
        }
        if (!head.copyOfRange(0, MAGIC_LEN).contentEquals(MAGIC_VAULT)) return@runCatching null
        when (val version = head[MAGIC_LEN].toInt()) {
            FORMAT_VERSION_LEGACY -> AeadSuite.AES_256_GCM
            else -> if (version >= FORMAT_VERSION_SUITE) AeadSuite.fromId(head[MAGIC_LEN + 1]) else null
        }
    }.getOrNull()

    /**
     * Ana parola sarmalayıcısının başlığındaki anahtar türetme parametreleri.
     *
     * Ölçümle bulunan maliyet burada duruyor; parola değiştirirken ya da
     * kurtarma anahtarını yenilerken yeniden ölçmek yerine bu okunuyor.
     * Yeniden ölçüm saniyeler sürüyor ve sonucu da zaten aynı olurdu.
     */
    fun currentKdfParams(): Kdf.Params? = runCatching {
        val blob = masterKeyFile.readBytes()
        val input = DataInputStream(ByteArrayInputStream(blob))
        val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(magic.contentEquals(MAGIC_MASTER)) { "Bozuk anahtar dosyası" }
        val version = input.readByte().toInt()
        readSuite(input, version)
        Kdf.Params.readFrom(input)
    }.getOrNull()

    /** Sonraki yazmalarda kullanılacak parametreler: aynı maliyet, taze tuz. */
    private fun inheritedParams(): Kdf.Params =
        currentKdfParams()?.withFreshSalt() ?: Kdf.defaultParams()

    // ------------------------------------------------------------- oluşturma

    class NewVault(val vaultKey: SecretBytes, val recoveryCode: String)

    /**
     * Sıfırdan kasa kurar. Ağırdır (Argon2id), arka planda çağır.
     * Dönen kurtarma kodu bir daha üretilemez.
     */
    /**
     * @param params ölçümle bulunmuş anahtar türetme parametreleri.
     *        Verilmezse sabit varsayılanlara düşülür — ama kurulum akışı her
     *        zaman [KdfCalibration] sonucunu geçirmeli, sabit değer bu cihazda
     *        ya gereksiz zayıf ya da kullanılamaz yavaş olur.
     * @param cipherSuite ölçümle seçilmiş şifreleme paketi.
     */
    fun createVault(
        masterPassword: SecretBytes,
        params: Kdf.Params = Kdf.defaultParams(),
        cipherSuite: AeadSuite = AeadSuite.DEFAULT
    ): NewVault {
        check(!vaultExists()) { "Kasa zaten var" }
        activeSuite = cipherSuite
        val vaultKey = SecretBytes.random(Crypto.KEY_BYTES)

        writeWrappedKey(masterKeyFile, MAGIC_MASTER, masterPassword, vaultKey, params, cipherSuite)

        val recoveryCode = RecoveryKey.generate()
        RecoveryKey.toSecret(recoveryCode)!!.use { secret ->
            writeWrappedKey(
                recoveryKeyFile, MAGIC_RECOVERY, secret, vaultKey,
                params.withFreshSalt(), cipherSuite
            )
        }

        writeVaultTo(vaultFile, vaultKey, VaultData(), cipherSuite, SECTION_REAL, emptyList())
        // Zorlama sarmalayıcısı kurulumdan itibaren var: sonradan eklenseydi
        // dosyanın ortaya çıkış tarihi bile bir ipucu olurdu.
        writeUnopenableDuress(params, cipherSuite)
        resetAttempts()
        return NewVault(vaultKey, recoveryCode)
    }

    // ------------------------------------------------------------ kilit açma

    sealed interface UnlockResult {
        class Success(val vaultKey: SecretBytes, val section: Int = SECTION_REAL) : UnlockResult
        data object WrongSecret : UnlockResult
        class Blocked(val remainingMillis: Long) : UnlockResult
        data object Wiped : UnlockResult
        class Failure(val cause: Throwable) : UnlockResult
    }

    /**
     * Ana parolayla açar. Yanlış denemeleri sayar; [wipeAfterAttempts] sıfırdan
     * büyükse ve sayaç aşılırsa kasa geri dönüşsüz silinir.
     */
    /**
     * Ana parolayla açar. Yanlış denemeleri sayar; [wipeAfterAttempts] sıfırdan
     * büyükse ve sayaç aşılırsa kasa geri dönüşsüz silinir.
     *
     * ### Zorlama parolası
     *
     * İki sarmalayıcı da denenir: `master.key` gerçek kasayı, `duress.key` yem
     * kasayı açar. Hangisinin açıldığı yalnızca dönen [UnlockResult.Success]
     * içindeki bölme numarasından anlaşılır; arayüz ikisini ayırt etmez ve
     * ayırt etmemelidir.
     *
     * İkisi **eş zamanlı** türetilir. Sırayla denemek, yem parolanın iki kat
     * uzun sürmesi demekti; elinde kronometre olan bir zorlayıcı bundan yem
     * kasaya bakmakta olduğunu anlardı. Paralel çalıştırıldığında duvar saati
     * süresi iki durumda da aynı.
     */
    fun unlockWithPassword(password: SecretBytes, wipeAfterAttempts: Int = 0): UnlockResult {
        val state = readAttempts()
        val now = System.currentTimeMillis()
        if (state.blockedUntil > now) return UnlockResult.Blocked(state.blockedUntil - now)

        return try {
            val real = FutureTask { runCatching { openWrappedKey(masterKeyFile, MAGIC_MASTER, password) } }
            val decoy = FutureTask { runCatching { openWrappedKey(duressKeyFile, MAGIC_DURESS, password) } }
            Thread(real, "kasa-kek-1").start()
            Thread(decoy, "kasa-kek-2").start()

            val realKey = real.get().getOrNull()
            val decoyKey = decoy.get().getOrNull()

            when {
                realKey != null -> {
                    decoyKey?.wipe()
                    resetAttempts()
                    UnlockResult.Success(realKey, SECTION_REAL)
                }
                decoyKey != null -> {
                    resetAttempts()
                    UnlockResult.Success(decoyKey, SECTION_DECOY)
                }
                else -> onFailedAttempt(state, wipeAfterAttempts)
            }
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
            val version = input.readByte().toInt()
            val authClass = readAuthClass(input, version)
            val ivLen = input.readUnsignedByte()
            val iv = ByteArray(ivLen).also { input.readFully(it) }
            KeystoreKeys.biometricDecryptCipher(iv, authClass)
        }.getOrNull()
    }

    /**
     * Sarmalayıcının hangi doğrulama sınıfıyla kurulduğu.
     *
     * Dosyaya yazılıyor çünkü iki sınıfın Keystore takma adı ayrı: hangisiyle
     * kurulduğunu bilmeden doğru anahtar bulunamaz. Sürüm 1 dosyalarında bu
     * bayt yoktu; onlar her zaman yalnız-biyometriydi.
     */
    private fun readAuthClass(input: DataInputStream, version: Int): KeystoreKeys.AuthClass =
        if (version >= FORMAT_VERSION_SUITE) KeystoreKeys.AuthClass.fromId(input.readByte())
        else KeystoreKeys.AuthClass.BIOMETRIC_ONLY

    /** Kurulu biyometrik sarmalayıcının doğrulama sınıfı; yoksa `null`. */
    fun biometricAuthClass(): KeystoreKeys.AuthClass? = runCatching {
        if (!biometricKeyFile.exists()) return@runCatching null
        val head = ByteArray(MAGIC_LEN + 2)
        biometricKeyFile.inputStream().use { if (it.read(head) != head.size) return@runCatching null }
        if (!head.copyOfRange(0, MAGIC_LEN).contentEquals(MAGIC_BIOMETRIC)) return@runCatching null
        if (head[MAGIC_LEN].toInt() >= FORMAT_VERSION_SUITE) KeystoreKeys.AuthClass.fromId(head[MAGIC_LEN + 1])
        else KeystoreKeys.AuthClass.BIOMETRIC_ONLY
    }.getOrNull()

    /** Doğrulanmış şifreleyiciyle sarmalanmış kasa anahtarını çözer. */
    fun unlockWithBiometric(cipher: Cipher): UnlockResult = try {
        val blob = biometricKeyFile.readBytes()
        val input = DataInputStream(ByteArrayInputStream(blob))
        val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(magic.contentEquals(MAGIC_BIOMETRIC)) { "Bozuk biyometrik sarmalayıcı" }
        val version = input.readByte().toInt()
        readAuthClass(input, version)
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
    fun enableBiometric(
        cipher: Cipher,
        vaultKey: SecretBytes,
        authClass: KeystoreKeys.AuthClass = KeystoreKeys.AuthClass.BIOMETRIC_ONLY
    ) {
        val wrapped = cipher.doFinal(vaultKey.raw())
        val iv = cipher.iv
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.write(MAGIC_BIOMETRIC)
            d.writeByte(FORMAT_VERSION)
            d.writeByte(authClass.id.toInt())
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
    fun changeMasterPassword(
        current: SecretBytes,
        new: SecretBytes,
        params: Kdf.Params = inheritedParams()
    ): Boolean {
        val key = try {
            openWrappedKey(masterKeyFile, MAGIC_MASTER, current)
        } catch (t: Throwable) {
            return false
        }
        key.use {
            writeWrappedKey(masterKeyFile, MAGIC_MASTER, new, it, params)
        }
        resetAttempts()
        return true
    }

    /** Kurtarma anahtarını yeniler ve yeni kodu döndürür. */
    fun regenerateRecoveryKey(vaultKey: SecretBytes, params: Kdf.Params = inheritedParams()): String {
        val code = RecoveryKey.generate()
        RecoveryKey.toSecret(code)!!.use { secret ->
            writeWrappedKey(recoveryKeyFile, MAGIC_RECOVERY, secret, vaultKey, params)
        }
        return code
    }

    // ------------------------------------------------------- anahtar rotasyonu

    class Rotation(val vaultKey: SecretBytes, val recoveryCode: String)

    /**
     * Kasa anahtarının kendisini yeniler.
     *
     * Ana parola değişimi yalnızca sarmalayıcıyı yeniliyordu; kasa anahtarı
     * kurulumdan beri aynıydı. Cihaz kaybı ya da yedek dosyasının sızması gibi
     * durumlarda gereken şey bu değil: eski anahtarı ele geçirmiş biri, eski
     * kopyayı sonsuza dek açabilir. Rotasyon yeni bir anahtar üretip kasayı
     * baştan şifreler ve üç sarmalayıcıyı da yeniden yazar — eski kopya bir
     * daha hiçbir işe yaramaz.
     *
     * ### Yarıda kesilmeye karşı
     *
     * Tehlike, üç dosyanın birbiriyle tutarlı olmasında: yeni anahtarla yazılmış
     * bir kasa, eski anahtarı saran bir `master.key` ile birlikte kaldığında
     * kasa hiç açılamaz. Bu yüzden önce bütün yeni dosyalar `.new` uzantısıyla
     * yazılır, sonra bir işaret dosyası konur, ancak ondan sonra yeniden
     * adlandırma yapılır. Süreç arada ölürse [completePendingRotation] açılışta
     * kalanları tamamlar; işlem tekrarlanabilir olduğu için iki kez çalışması
     * da zarar vermez.
     *
     * Biyometrik sarmalayıcı silinir: içindeki eski kasa anahtarı artık geçersiz
     * ve Keystore anahtarı kullanıcı doğrulaması istediği için burada sessizce
     * yeniden yazılamaz. Kullanıcı biyometriyi bir kez daha kurmak zorunda.
     */
    fun rotateVaultKey(
        masterPassword: SecretBytes,
        params: Kdf.Params = inheritedParams(),
        cipherSuite: AeadSuite = suite
    ): Rotation? {
        val oldKey = try {
            openWrappedKey(masterKeyFile, MAGIC_MASTER, masterPassword)
        } catch (t: Throwable) {
            return null
        }

        return oldKey.use { current ->
            val opened = readVaultFrom(vaultFile, current)
            val newKey = SecretBytes.random(Crypto.KEY_BYTES)
            val recoveryCode = RecoveryKey.generate()

            // 1. Yeni hâli yan dosyalara yaz. Bu aşamada diskteki kasa hâlâ
            //    tutarlı ve eski anahtarla açılabilir durumda.
            activeSuite = cipherSuite
            // Yem bölmesi olduğu gibi taşınıyor: anahtarı bizde değil ve
            // rotasyonun onu bozması, zorlama parolasını sessizce çöpe atmak
            // olurdu.
            writeVaultTo(
                File(vaultFile.path + NEW_SUFFIX), newKey, opened.data, cipherSuite,
                opened.section, runCatching { readContainer(vaultFile).sections }.getOrNull()
            )
            writeWrappedKey(
                File(masterKeyFile.path + NEW_SUFFIX), MAGIC_MASTER,
                masterPassword, newKey, params, cipherSuite
            )
            RecoveryKey.toSecret(recoveryCode)!!.use { secret ->
                writeWrappedKey(
                    File(recoveryKeyFile.path + NEW_SUFFIX), MAGIC_RECOVERY,
                    secret, newKey, params.withFreshSalt(), cipherSuite
                )
            }

            // 2. İşaret koy: buradan sonrası tamamlanmak zorunda.
            rotationMarker.writeBytes(ByteArray(0))

            // 3. Devral.
            completePendingRotation()
            resetAttempts()
            Rotation(newKey, recoveryCode)
        }
    }

    /**
     * Yarım kalmış rotasyonu tamamlar. Açılışta çağrılır; işaret yoksa hiçbir
     * şey yapmaz.
     */
    fun completePendingRotation() {
        if (!rotationMarker.exists()) return
        listOf(vaultFile, masterKeyFile, recoveryKeyFile).forEach { target ->
            val staged = File(target.path + NEW_SUFFIX)
            if (staged.exists()) {
                target.delete()
                staged.renameTo(target)
            }
        }
        // Eski kasa anahtarını saran biyometrik sarmalayıcı artık geçersiz.
        secureDelete(biometricKeyFile)
        KeystoreKeys.deleteBiometricKey()
        rotationMarker.delete()
        activeSuite = runCatching { peekSuiteOnDisk() }.getOrNull()
    }

    // ---------------------------------------------------------- hızlı PIN

    /**
     * 4-6 haneli PIN ile kasa anahtarını sarmalar.
     *
     * ### Neden PIN
     *
     * 20 karakterlik bir ana parola günde on kez yazılmaz. Yazılması istenirse
     * kullanıcı onu kısaltır — yani "her seferinde ana parola" kuralı,
     * pratikte ana parolayı zayıflatan kuraldır. Biyometrisi olmayan ya da
     * çalışmayan cihazda tek makul yol PIN.
     *
     * ### PIN'i zayıf olmaktan çıkaran şey
     *
     * Dört hanenin on bin olasılığı var; Argon2 bunu çevrimdışı bir
     * saldırgana karşı kurtarmaz. Koruma iki yerden geliyor:
     *
     *  1. **Çift katman.** Kasa anahtarı önce PIN'den türetilen anahtarla,
     *     sonra Keystore'daki (çoğu cihazda StrongBox) bir anahtarla
     *     kapatılıyor. Dosyayı telefondan kopyalayan biri dış katmanı
     *     açamadığı için PIN denemesine hiç başlayamıyor.
     *  2. **Deneme sayacı.** Cihaz üzerindeki deneme sayılıyor;
     *     [PIN_MAX_ATTEMPTS] aşıldığında sarmalayıcı ve Keystore anahtarı
     *     birlikte siliniyor. Kasa kaybolmuyor — ana parola hâlâ açıyor;
     *     düşen yalnızca kısayol.
     *
     * @param params ana parolanınkiyle aynı maliyet, taze tuzla. Aynı tuzu
     *        paylaşmak, saldırgana tek hesapla iki sarmalayıcıyı birden
     *        deneme imkânı verirdi.
     */
    fun enablePin(pin: SecretBytes, vaultKey: SecretBytes, pinLength: Int, params: Kdf.Params) {
        require(pinLength in MIN_PIN_LENGTH..MAX_PIN_LENGTH) { "PIN uzunluğu geçersiz" }
        val cipherSuite = suite

        val header = ByteArrayOutputStream()
        DataOutputStream(header).use { d ->
            d.write(MAGIC_PIN)
            d.writeByte(FORMAT_VERSION)
            d.writeByte(cipherSuite.id.toInt())
            d.writeByte(pinLength)
            params.writeTo(d)
        }
        val headerBytes = header.toByteArray()

        Kdf.derive(pin, params).use { kek ->
            val inner = Crypto.seal(kek.raw(), vaultKey.raw(), headerBytes, cipherSuite)
            // Dış katman: cihazdan çıkarılamayan Keystore anahtarı.
            val outer = KeystoreKeys.pinSeal(context, inner)
            inner.fill(0)
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { d ->
                d.write(headerBytes)
                d.writeInt(outer.size)
                d.write(outer)
            }
            writeAtomically(pinKeyFile, out.toByteArray())
        }
        writePinAttempts(0)
    }

    fun pinEnabled(): Boolean = pinKeyFile.exists() && KeystoreKeys.hasPinKey()

    /** Kaç haneli PIN kurulmuş? Tuş takımını çizmek için gerekiyor. 0 = yok. */
    fun pinLength(): Int = runCatching {
        if (!pinEnabled()) return@runCatching 0
        val head = ByteArray(MAGIC_LEN + 3)
        pinKeyFile.inputStream().use { if (it.read(head) != head.size) return@runCatching 0 }
        if (!head.copyOfRange(0, MAGIC_LEN).contentEquals(MAGIC_PIN)) return@runCatching 0
        head[MAGIC_LEN + 2].toInt()
    }.getOrDefault(0)

    /**
     * PIN ile açar.
     *
     * Yanlış PIN'de sayaç artıyor; sınır aşıldığında PIN katmanı tamamen
     * düşüyor ve [UnlockResult.WrongSecret] yerine yine `WrongSecret` dönüyor
     * ama [pinEnabled] artık `false`. Çağıran bunu görüp ana parola ekranına
     * geçiyor.
     */
    fun unlockWithPin(pin: SecretBytes): UnlockResult {
        if (!pinEnabled()) return UnlockResult.WrongSecret
        return try {
            val blob = pinKeyFile.readBytes()
            val stream = ByteArrayInputStream(blob)
            val input = DataInputStream(stream)
            val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC_PIN)) { "Bozuk PIN sarmalayıcısı" }
            val version = input.readByte().toInt()
            val fileSuite = readSuite(input, version)
            input.readByte() // uzunluk: başlığın parçası, burada kullanılmıyor
            val params = Kdf.Params.readFrom(input)
            val headerLen = blob.size - stream.available()
            val headerBytes = blob.copyOfRange(0, headerLen)
            val len = input.readInt()
            val outer = ByteArray(len).also { input.readFully(it) }

            val inner = KeystoreKeys.pinOpen(context, outer)
                ?: return onFailedPin()

            val key = try {
                Kdf.derive(pin, params).use { kek ->
                    SecretBytes(Crypto.open(kek.raw(), inner, headerBytes, fileSuite))
                }
            } finally {
                inner.fill(0)
            }
            writePinAttempts(0)
            resetAttempts()
            UnlockResult.Success(key)
        } catch (e: AEADBadTagException) {
            onFailedPin()
        } catch (e: javax.crypto.BadPaddingException) {
            onFailedPin()
        } catch (t: Throwable) {
            UnlockResult.Failure(t)
        }
    }

    private fun onFailedPin(): UnlockResult {
        val failed = readPinAttempts() + 1
        if (failed >= PIN_MAX_ATTEMPTS) {
            disablePin()
        } else {
            writePinAttempts(failed)
        }
        return UnlockResult.WrongSecret
    }

    /** Kaç deneme hakkı kaldı? Kullanıcıya göstermek için. */
    fun pinAttemptsLeft(): Int = (PIN_MAX_ATTEMPTS - readPinAttempts()).coerceAtLeast(0)

    /**
     * PIN katmanını kaldırır.
     *
     * Keystore anahtarı da siliniyor: yalnızca dosyayı silmek, dosyanın bir
     * kopyasını almış birine dış katmanı açma imkânı bırakırdı.
     */
    fun disablePin() {
        secureDelete(pinKeyFile)
        secureDelete(pinAttemptsFile)
        KeystoreKeys.deletePinKey()
    }

    private fun readPinAttempts(): Int {
        if (!pinAttemptsFile.exists()) return 0
        val raw = runCatching { pinAttemptsFile.readBytes() }.getOrNull() ?: return 0
        val plain = KeystoreKeys.deviceOpen(raw) ?: return 0
        return runCatching { DataInputStream(ByteArrayInputStream(plain)).readInt() }.getOrDefault(0)
    }

    private fun writePinAttempts(value: Int) {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.writeInt(value) }
        runCatching { writeAtomically(pinAttemptsFile, KeystoreKeys.deviceSeal(out.toByteArray())) }
    }

    // ------------------------------------------------------- zorlama parolası

    /**
     * İkinci bir ana parola: girildiğinde gerçek kasa değil, yem kasa açılır.
     *
     * ### Ne söz veriyor
     *
     * Sınır kapısında ya da zorla açtırma durumunda verilecek bir parola.
     * Açılan kasa gerçek: içinde kayıtlar var, düzenlenebiliyor, bir şey
     * "sahte" görünmüyor. Gerçek kasa aynı dosyanın öteki bölmesinde duruyor
     * ve o bölmeyi çözecek anahtar hiçbir yerde yok.
     *
     * ### Ne söz vermiyor
     *
     * Kasa'nın iki bölmeli olduğu kaynak kodda yazıyor; bunu bilen bir
     * zorlayıcı "ikinci parolayı da ver" diyebilir. Hiçbir yazılım bunu
     * çözemez. Buradaki inkâr edilebilirlik şu dar ama gerçek iddiaya
     * dayanıyor: **dosyaya bakarak zorlama parolasının kurulu olup olmadığı
     * anlaşılamaz.** Sarmalayıcı her kasada var, boyutları aynı, sihirli
     * sayıları aynı; kurulu olmadığında rastgele bir gizle kapatılmış oluyor.
     * Yem bölmesi de her kasada dolu ve dolgu sayesinde gerçek bölmeyle
     * kıyaslanabilir boyutta.
     *
     * @param duressPassword yeni zorlama parolası. Ana paroladan farklı olmalı.
     * @return yem kasa için üretilen anahtar; çağıran yem içeriğini yazmak
     *         için kullanır.
     */
    fun setDuressPassword(duressPassword: SecretBytes, params: Kdf.Params, decoy: VaultData): Boolean =
        runCatching {
            val cipherSuite = suite
            val decoyKey = SecretBytes.random(Crypto.KEY_BYTES)
            decoyKey.use { key ->
                // Önce yem bölmesi, sonra sarmalayıcı: ters sırada yapılıp
                // arada kesilseydi açılabilir bir sarmalayıcı ile açılamayan
                // bir bölme kalırdı ve zorlama parolası sessizce çalışmazdı.
                val carried = runCatching { readContainer(vaultFile).sections }.getOrNull()
                writeVaultTo(vaultFile, key, decoy, cipherSuite, SECTION_DECOY, carried)
                writeWrappedKey(duressKeyFile, MAGIC_DURESS, duressPassword, key, params, cipherSuite)
            }
            true
        }.getOrDefault(false)

    /**
     * Zorlama parolasını kaldırır.
     *
     * Dosya silinmiyor — silmek, "bu kullanıcıda zorlama parolası yok" demenin
     * en açık yolu olurdu. Yerine, kimsenin bilmediği rastgele bir gizle
     * yeniden yazılıyor ve yem bölmesi boş bir kasayla değiştiriliyor. Sonuç,
     * hiç kurulmamış bir kasayla bayt düzeyinde aynı şekle sahip.
     */
    fun clearDuressPassword(): Boolean = runCatching {
        val cipherSuite = suite
        val params = currentKdfParams()?.withFreshSalt() ?: Kdf.defaultParams()
        val carried = runCatching { readContainer(vaultFile).sections }.getOrNull()
        SecretBytes.random(Crypto.KEY_BYTES).use { key ->
            writeVaultTo(vaultFile, key, VaultData(), cipherSuite, SECTION_DECOY, carried)
        }
        writeUnopenableDuress(params, cipherSuite)
        true
    }.getOrDefault(false)

    /**
     * Açılamayan bir zorlama sarmalayıcısı yazar.
     *
     * Sarmaladığı anahtar rastgele ve hemen siliniyor; onu açacak parola da
     * rastgele ve hiçbir yere yazılmıyor. Dosya tam olarak gerçek bir
     * sarmalayıcı gibi görünüyor ve tam olarak hiçbir işe yaramıyor — istenen
     * de bu.
     */
    private fun writeUnopenableDuress(params: Kdf.Params, cipherSuite: AeadSuite) {
        SecretBytes.random(32).use { nobodysSecret ->
            SecretBytes.random(Crypto.KEY_BYTES).use { orphanKey ->
                writeWrappedKey(
                    duressKeyFile, MAGIC_DURESS, nobodysSecret, orphanKey,
                    params.withFreshSalt(), cipherSuite
                )
            }
        }
    }

    /**
     * Eski kasaları iki bölmeli kaba taşır.
     *
     * Sürüm 3 öncesi kasalarda yem bölmesi ve zorlama sarmalayıcısı yoktu.
     * Bunları sonradan eklemek, "kullanıcı sürüm 3'e geçtikten sonra zorlama
     * parolası kurdu" gibi bir iz bırakmıyor: her yükseltmede ikisi de
     * oluşuyor, kurulu olsun olmasın.
     */
    fun ensureDuressSlot(vaultKey: SecretBytes, section: Int) {
        if (duressKeyFile.exists()) return
        runCatching {
            val data = readVaultFrom(vaultFile, vaultKey, section).data
            val cipherSuite = suite
            writeVaultTo(vaultFile, vaultKey, data, cipherSuite, section, emptyList())
            writeUnopenableDuress(currentKdfParams() ?: Kdf.defaultParams(), cipherSuite)
        }
    }

    // ------------------------------------------------------------ kasa içeriği

    /**
     * Kasa kabının çözülmüş hâli.
     *
     * Dosyada iki bölme var ve hangisinin açıldığı, sonraki yazmaların nereye
     * gideceğini belirliyor: yem parolayla açılmış bir oturumun yaptığı
     * değişiklik gerçek kasaya dokunmamalı.
     */
    class VaultOpen(val data: VaultData, val section: Int)

    fun readVault(vaultKey: SecretBytes, section: Int = SECTION_REAL): VaultData =
        readVaultFrom(vaultFile, vaultKey, section).data

    /**
     * Kabı okur ve [preferredSection] ile başlayarak anahtarın hangi bölmeyi
     * açtığını bulur.
     *
     * Deneme sırası kimliği ele vermiyor: AEAD etiketi doğrulanmadığı sürece
     * hiçbir bölme çözülmüş sayılmıyor ve yanlış anahtar her iki bölmede de
     * aynı hatayı veriyor.
     */
    private fun readVaultFrom(
        file: File,
        vaultKey: SecretBytes,
        preferredSection: Int = SECTION_REAL
    ): VaultOpen {
        if (!file.exists()) return VaultOpen(VaultData(), SECTION_REAL)
        val container = readContainer(file)
        activeSuite = container.suite

        val order = if (preferredSection == SECTION_DECOY) {
            listOf(SECTION_DECOY, SECTION_REAL)
        } else {
            listOf(SECTION_REAL, SECTION_DECOY)
        }

        var lastFailure: Throwable? = null
        for (index in order) {
            val sealed = container.sections.getOrNull(index) ?: continue
            val plain = try {
                Crypto.open(vaultKey.raw(), sealed, container.header, container.suite)
            } catch (t: Throwable) {
                lastFailure = t
                continue
            }
            val body = unpad(plain, container.version)
            return try {
                VaultOpen(decodeVault(body), index)
            } finally {
                body.fill(0)
                plain.fill(0)
            }
        }
        throw lastFailure ?: IllegalStateException("Kasa açılamadı")
    }

    /** Diskteki kabın ham hâli: başlık (AAD) ve bölmelerin şifreli gövdeleri. */
    private class Container(
        val version: Int,
        val suite: AeadSuite,
        val header: ByteArray,
        val sections: List<ByteArray>
    )

    private fun readContainer(file: File): Container {
        val blob = file.readBytes()
        val stream = ByteArrayInputStream(blob)
        val input = DataInputStream(stream)
        val magic = ByteArray(MAGIC_LEN).also { input.readFully(it) }
        require(magic.contentEquals(MAGIC_VAULT)) { "Bu bir Kasa dosyası değil" }

        val version = input.readByte().toInt()
        val suite = readSuite(input, version)
        // Sürüm 3 öncesinde tek bölme vardı ve sayaç baytı yoktu.
        val count = if (version >= FORMAT_VERSION) input.readUnsignedByte() else 1
        val headerLen = blob.size - stream.available()
        val header = blob.copyOfRange(0, headerLen)

        val sections = ArrayList<ByteArray>(count)
        repeat(count) {
            val len = input.readInt()
            sections.add(ByteArray(len).also { input.readFully(it) })
        }
        return Container(version, suite, header, sections)
    }

    fun writeVault(vaultKey: SecretBytes, data: VaultData, section: Int = SECTION_REAL) {
        writeVaultTo(vaultFile, vaultKey, data, activeSuite ?: AeadSuite.DEFAULT, section)
    }

    /**
     * Kasayı JSON baytlarına çevirir.
     *
     * Akışa yazılıyor, `String` üzerinden geçilmiyor: aksi hâlde kasanın
     * tamamı — içindeki bütün parolalarla — silinemeyen bir `String` olarak
     * yığında kalırdı. Dönen dizi çağıran tarafından sıfırlanıyor.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun encodeVault(data: VaultData): ByteArray {
        val out = ByteArrayOutputStream()
        json.encodeToStream(VaultData.serializer(), data, out)
        return out.toByteArray()
    }

    /**
     * Bir bölmeyi yeniden yazar, ötekini olduğu gibi taşır.
     *
     * Öteki bölmenin anahtarı elimizde yok — olması da gerekmiyor: şifreli
     * gövdesi baytı baytına kopyalanıyor. Yem parolayla açılmış bir oturumun
     * gerçek kasaya zarar verememesinin tek sebebi bu.
     *
     * Başlık (AAD) her iki bölme için ortak. Bu, saldırganın bölmeleri
     * birbiriyle ya da başka bir dosyayla değiştirmesini engelliyor: etiket
     * doğrulaması başlığa bağlı ve başlık dosyanın kendisinde.
     */
    private fun writeVaultTo(
        file: File,
        vaultKey: SecretBytes,
        data: VaultData,
        suite: AeadSuite,
        section: Int,
        existing: List<ByteArray>? = null
    ) {
        val header = ByteArrayOutputStream().apply {
            write(MAGIC_VAULT)
            write(FORMAT_VERSION)
            write(suite.id.toInt())
            write(SECTION_COUNT)
        }.toByteArray()

        val carried = existing ?: runCatching {
            if (vaultFile.exists()) readContainer(vaultFile).sections else emptyList()
        }.getOrDefault(emptyList())

        val plain = pad(encodeVault(data))
        val sealed = try {
            Crypto.seal(vaultKey.raw(), plain, header, suite)
        } finally {
            plain.fill(0)
        }

        val sections = (0 until SECTION_COUNT).map { index ->
            when {
                index == section -> sealed
                // Taşınacak bölme yoksa (yeni kasa ya da eski tek bölmeli
                // dosya) rastgele bir anahtarla boş bir yem üretiliyor. Yem
                // bölmesinin **her zaman** dolu olması şart: boş bırakmak,
                // "bu kullanıcıda yem yok" demenin en kısa yolu olurdu.
                index < carried.size && carried[index].isNotEmpty() -> carried[index]
                else -> freshDecoySection(header, suite)
            }
        }

        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.write(header)
            sections.forEach {
                d.writeInt(it.size)
                d.write(it)
            }
        }
        writeAtomically(file, out.toByteArray())
    }

    /**
     * Bir daha asla açılmayacak bir yem bölmesi.
     *
     * Anahtar üretilip hemen siliniyor; kimse — kullanıcı dâhil — bu bölmeyi
     * açamaz. Amaç açılabilmesi değil, **var olması**: kapta her zaman iki
     * dolu bölme bulunması, zorlama parolasının kurulu olup olmadığını dosyaya
     * bakarak anlamayı imkânsız kılıyor.
     */
    private fun freshDecoySection(header: ByteArray, suite: AeadSuite): ByteArray =
        SecretBytes.random(Crypto.KEY_BYTES).use { throwaway ->
            val plain = pad(encodeVault(VaultData()))
            try {
                Crypto.seal(throwaway.raw(), plain, header, suite)
            } finally {
                plain.fill(0)
            }
        }

    /**
     * Düz metni sabit bloklara tamamlar: `uzunluk(4) ‖ JSON ‖ sıfırlar`.
     *
     * Şifreli metnin boyutu, kaç kayıt olduğunu yaklaşık olarak ele veriyordu.
     * İki bölmeli kapta bu daha ağır bir sızıntı: bölmelerin göreli boyutu
     * hangisinin gerçek kasa olduğunu söylerdi ve inkâr edilebilirlik biterdi.
     */
    private fun pad(json: ByteArray): ByteArray {
        val total = 4 + json.size
        val padded = ((total + PAD_BLOCK - 1) / PAD_BLOCK) * PAD_BLOCK
        val out = ByteArray(padded)
        out[0] = (json.size ushr 24).toByte()
        out[1] = (json.size ushr 16).toByte()
        out[2] = (json.size ushr 8).toByte()
        out[3] = json.size.toByte()
        System.arraycopy(json, 0, out, 4, json.size)
        json.fill(0)
        return out
    }

    /** Sürüm 3 öncesi bölmelerde dolgu yoktu; düz metnin tamamı JSON'du. */
    private fun unpad(plain: ByteArray, version: Int): ByteArray {
        if (version < FORMAT_VERSION) return plain
        require(plain.size >= 4) { "Bozuk kasa bölmesi" }
        val length = ((plain[0].toInt() and 0xFF) shl 24) or
            ((plain[1].toInt() and 0xFF) shl 16) or
            ((plain[2].toInt() and 0xFF) shl 8) or
            (plain[3].toInt() and 0xFF)
        require(length in 0..(plain.size - 4)) { "Bozuk kasa bölmesi" }
        return plain.copyOfRange(4, 4 + length)
    }

    /**
     * Çözülmüş JSON'u nesneye çevirir.
     *
     * Hızlı yol, göç gerekmediğinde JSON ağacını ve tüm kasanın metin kopyasını
     * hiç oluşturmaz: doğrudan bayt akışından okur. Bu yalnızca hız değil,
     * bellek hijyeni meselesi — aksi hâlde kasanın tamamı, içindeki bütün
     * parolalarla birlikte, silinemeyen bir `String` olarak yığında kalırdı.
     */
    private fun decodeVault(plain: ByteArray): VaultData {
        val schema = peekSchema(plain)
        if (schema == VaultMigrations.CURRENT) {
            return ByteArrayInputStream(plain).use { stream ->
                @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
                json.decodeFromStream(VaultData.serializer(), stream)
            }
        }
        // Göç gerekiyor: ancak burada ağaç kurulur.
        val text = String(plain, Charsets.UTF_8)
        val root = json.parseToJsonElement(text).jsonObject
        return json.decodeFromJsonElement(VaultData.serializer(), VaultMigrations.migrate(root))
    }

    /**
     * Şema sürümünü JSON'u ayrıştırmadan, baytlar üzerinde arayarak okur.
     * Bulunamazsa en eski sürüm varsayılır ve tam ayrıştırmaya düşülür.
     */
    private fun peekSchema(plain: ByteArray): Int {
        val needle = "\"schema\":".toByteArray(Charsets.US_ASCII)
        val limit = minOf(plain.size, 512)
        outer@ for (start in 0..limit - needle.size) {
            for (i in needle.indices) if (plain[start + i] != needle[i]) continue@outer
            var index = start + needle.size
            while (index < plain.size && plain[index] == ' '.code.toByte()) index++
            var value = 0
            var digits = 0
            while (index < plain.size && plain[index] in '0'.code.toByte()..'9'.code.toByte()) {
                value = value * 10 + (plain[index] - '0'.code.toByte())
                index++
                digits++
            }
            return if (digits > 0) value else VaultMigrations.OLDEST_SUPPORTED
        }
        return VaultMigrations.OLDEST_SUPPORTED
    }

    // -------------------------------------------------------- dışa/içe aktarma

    /**
     * Taşınabilir, kendi başına şifreli dosya üretir. Ana parolayla değil,
     * kullanıcının seçtiği ayrı bir dışa aktarma parolasıyla korunur; böylece
     * yedek dosyası ana parolayı ifşa etmez.
     */
    fun exportEncrypted(
        data: VaultData,
        exportPassword: SecretBytes,
        params: Kdf.Params = Kdf.defaultParams(forExport = true),
        cipherSuite: AeadSuite = suite
    ): ByteArray {
        val plain = encodeVault(data)
        try {
            val header = ByteArrayOutputStream()
            DataOutputStream(header).use { d ->
                d.write(MAGIC_EXPORT)
                d.writeByte(FORMAT_VERSION)
                d.writeByte(cipherSuite.id.toInt())
                params.writeTo(d)
            }
            val headerBytes = header.toByteArray()
            return Kdf.derive(exportPassword, params).use { kek ->
                val sealed = Crypto.seal(kek.raw(), plain, headerBytes, cipherSuite)
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
            val version = input.readByte().toInt()
            val fileSuite = readSuite(input, version)
            val params = Kdf.Params.readFrom(input)
            val headerLen = blob.size - stream.available()
            val headerBytes = blob.copyOfRange(0, headerLen)
            val len = input.readInt()
            val sealed = ByteArray(len).also { input.readFully(it) }
            Kdf.derive(exportPassword, params).use { kek ->
                val plain = Crypto.open(kek.raw(), sealed, headerBytes, fileSuite)
                try {
                    decodeVault(plain)
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
     * değil. Dosya biçimi kasa dosyasıyla aynı: sihirli sayı + sürüm + paket
     * kimliği + kimlik doğrulamalı şifreli gövde.
     */
    fun writeAttachment(id: String, key: ByteArray, content: ByteArray) {
        require(key.size == Crypto.KEY_BYTES) { "Ek anahtarı 32 bayt olmalı" }
        val cipherSuite = suite
        val header = ByteArrayOutputStream().apply {
            write(MAGIC_ATTACHMENT)
            write(FORMAT_VERSION)
            write(cipherSuite.id.toInt())
        }.toByteArray()
        val sealed = Crypto.seal(key, content, header, cipherSuite)
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
        val version = input.readByte().toInt()
        val fileSuite = readSuite(input, version)
        val headerLen = MAGIC_LEN + 1 + if (version >= FORMAT_VERSION_SUITE) 1 else 0
        val len = input.readInt()
        val sealed = ByteArray(len).also { input.readFully(it) }
        Crypto.open(key, sealed, blob.copyOfRange(0, headerLen), fileSuite)
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
        listOf(
            masterKeyFile, recoveryKeyFile, biometricKeyFile, vaultFile,
            attemptsFile, pinKeyFile, pinAttemptsFile, duressKeyFile
        ).forEach { secureDelete(it) }
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
        params: Kdf.Params,
        suite: AeadSuite = activeSuite ?: AeadSuite.DEFAULT
    ) {
        val header = ByteArrayOutputStream()
        DataOutputStream(header).use { d ->
            d.write(magic)
            d.writeByte(FORMAT_VERSION)
            d.writeByte(suite.id.toInt())
            params.writeTo(d)
        }
        val headerBytes = header.toByteArray()
        Kdf.derive(secret, params).use { kek ->
            val wrapped = Crypto.seal(kek.raw(), vaultKey.raw(), headerBytes, suite)
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
        val version = input.readByte().toInt()
        val suite = readSuite(input, version)
        val params = Kdf.Params.readFrom(input)
        val headerLen = blob.size - stream.available()
        val headerBytes = blob.copyOfRange(0, headerLen)
        val len = input.readInt()
        val wrapped = ByteArray(len).also { input.readFully(it) }
        return Kdf.derive(secret, params).use { kek ->
            SecretBytes(Crypto.open(kek.raw(), wrapped, headerBytes, suite))
        }
    }

    /**
     * Dosya başlığından şifreleme paketini okur.
     *
     * Sürüm 1 dosyalarında paket baytı yoktu; onlar her zaman AES-256-GCM'di.
     * Kimliği dosyaya yazmanın karşılığı bu: varsayılan değişse bile eski
     * kasa hangi paketle yazıldığını kendisi söylüyor.
     */
    private fun readSuite(input: DataInputStream, version: Int): AeadSuite = when {
        version >= FORMAT_VERSION_SUITE -> AeadSuite.fromId(input.readByte())
        version == FORMAT_VERSION_LEGACY -> AeadSuite.AES_256_GCM
        else -> throw IllegalStateException("Desteklenmeyen dosya sürümü: $version")
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

        /** İki bölmeli kasa kabı: gerçek kasa ve yem kasa yan yana. */
        private const val FORMAT_VERSION = 3

        /** Şifreleme paketi kimliğinin eklendiği sürüm. */
        private const val FORMAT_VERSION_SUITE = 2

        /** Paket baytı olmayan ilk biçim; her zaman AES-256-GCM demekti. */
        private const val FORMAT_VERSION_LEGACY = 1

        /** Kasa kabındaki bölme sırası. Sıra sabit; hangisinin ne olduğu değil. */
        const val SECTION_REAL = 0
        const val SECTION_DECOY = 1
        private const val SECTION_COUNT = 2

        /**
         * Bölme içeriği bu katın katlarına tamamlanıyor.
         *
         * Dolgu olmadan dosya boyutu kaç kayıt olduğunu yaklaşık olarak ele
         * verirdi; iki bölmeli kapta bu daha da kötü, çünkü bölmelerin göreli
         * boyutu hangisinin "gerçek" olduğunu söylerdi.
         */
        private const val PAD_BLOCK = 4096

        /** Rotasyon sırasında hazırlanan yan dosyaların uzantısı. */
        private const val NEW_SUFFIX = ".new"

        private val MAGIC_MASTER = "KASAMST1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_RECOVERY = "KASAREC1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_BIOMETRIC = "KASABIO1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_VAULT = "KASAVLT1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_ATTACHMENT = "KASAATT1".toByteArray(Charsets.US_ASCII)
        private val MAGIC_PIN = "KASAPIN1".toByteArray(Charsets.US_ASCII)
        /**
         * Zorlama sarmalayıcısı, ana parola sarmalayıcısıyla **aynı** sihirli
         * sayıyı taşıyor. Bilerek: iki dosya bayt düzeyinde ayırt edilemez
         * olmalı. Farklı bir imza koymak, "bu dosya yem içindir" yazmakla aynı
         * şey olurdu.
         */
        private val MAGIC_DURESS = MAGIC_MASTER
        val MAGIC_EXPORT = "KASAEXP1".toByteArray(Charsets.US_ASCII)

        const val EXPORT_EXTENSION = "kasa"

        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6

        /**
         * PIN kaç yanlış denemeden sonra düşer.
         *
         * Beş, on binlik bir uzayda anlamlı bir tahmin şansı bırakmıyor
         * (%0,05) ama cebinde telefonu yanlışlıkla dokunmuş bir kullanıcıyı da
         * ana parolaya göndermiyor. Düşmek kasayı kaybetmek değil: ana parola
         * her zaman açıyor.
         */
        const val PIN_MAX_ATTEMPTS = 5

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
