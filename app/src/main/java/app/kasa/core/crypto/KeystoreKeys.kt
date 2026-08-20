package app.kasa.core.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android donanım anahtar deposundaki (Keystore/StrongBox) anahtarları yönetir.
 *
 * Buradaki anahtarlar hiçbir zaman uygulama belleğine inmez: işletim sistemi
 * onları güvenli ortamda tutar, uygulama yalnızca "şununla şifrele" diyebilir.
 * Cihaz köklense bile StrongBox'taki anahtar dışarı çıkarılamaz.
 *
 * İki anahtar kullanılır:
 *  - [ALIAS_BIOMETRIC]: yalnızca canlı biyometrik doğrulamadan sonra kullanılabilir.
 *    Kasa anahtarını sarmalar. Kullanıcı yeni parmak izi kaydettiğinde anahtar
 *    otomatik geçersizleşir (setInvalidatedByBiometricEnrollment), yani birinin
 *    telefona kendi parmağını eklemesi kasayı açmaya yetmez.
 *  - [ALIAS_DEVICE]: doğrulama istemez, yalnızca gizli olmayan ama sızmaması
 *    gereken yerel meta veriyi (tarama sonuçları, sayaçlar) diske şifreli yazar.
 */
object KeystoreKeys {

    private const val PROVIDER = "AndroidKeyStore"
    const val ALIAS_BIOMETRIC = "kasa.kek.biometric.v1"
    const val ALIAS_DEVICE_CREDENTIAL = "kasa.kek.devicecred.v1"
    const val ALIAS_DEVICE = "kasa.kek.device.v1"
    const val ALIAS_PIN = "kasa.kek.pin.v1"

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    /**
     * Kasa anahtarını sarmalayan Keystore anahtarının hangi doğrulamayı
     * istediği.
     *
     * İki ayrı takma ad gerekiyor çünkü bir Keystore anahtarının doğrulama
     * koşulları üretim anında sabitlenir; sonradan gevşetilemez. Kullanıcı
     * seçimi değiştirdiğinde eski anahtar silinip yenisi üretiliyor.
     */
    enum class AuthClass(val id: Byte, val alias: String) {

        /**
         * Yalnızca Class 3 biyometri. En dar kapı: telefonun ekran kilidi
         * parolasını bilen biri bu anahtarı açamaz.
         */
        BIOMETRIC_ONLY(1, ALIAS_BIOMETRIC),

        /**
         * Biyometri **ya da** telefonun kendi PIN/desen/parolası.
         *
         * Eşiği bilerek düşürüyor: parmak izi okuyucusu bozuk ya da hiç
         * biyometri kaydetmemiş bir kullanıcı için tek seçenek, her açılışta
         * 20 karakterlik ana parolayı yazmak olurdu — ki pratikte olan şey,
         * kullanıcının ana parolayı kısaltması olur. Ekran kilidi kimlik
         * bilgisini kabul etmek, o gerçek riski ortadan kaldırıyor.
         *
         * Karşılığında: telefonun kilit ekranı parolasını bilen biri kasayı da
         * açabilir. Bu yüzden varsayılan değil, açık bir tercih.
         */
        DEVICE_CREDENTIAL(2, ALIAS_DEVICE_CREDENTIAL);

        companion object {
            fun fromId(id: Byte): AuthClass = entries.firstOrNull { it.id == id } ?: BIOMETRIC_ONLY
        }
    }

    fun hasBiometricKey(): Boolean =
        AuthClass.entries.any { runCatching { keyStore.containsAlias(it.alias) }.getOrDefault(false) }

    fun hasBiometricKey(authClass: AuthClass): Boolean =
        runCatching { keyStore.containsAlias(authClass.alias) }.getOrDefault(false)

    fun deleteBiometricKey() {
        AuthClass.entries.forEach { runCatching { keyStore.deleteEntry(it.alias) } }
    }

    fun deleteAll() {
        deleteBiometricKey()
        runCatching { keyStore.deleteEntry(ALIAS_DEVICE) }
        runCatching { keyStore.deleteEntry(ALIAS_PIN) }
    }

    fun deletePinKey() {
        runCatching { keyStore.deleteEntry(ALIAS_PIN) }
    }

    fun hasPinKey(): Boolean = runCatching { keyStore.containsAlias(ALIAS_PIN) }.getOrDefault(false)

    /** Anahtar gerçekten güvenli donanımda mı tutuluyor? Ayarlar ekranında gösterilir. */
    fun isHardwareBacked(context: Context): Boolean = try {
        val key = loadOrCreateDeviceKey()
        val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, PROVIDER)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        info.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
    } catch (t: Throwable) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    fun hasStrongBox(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    // ---------------------------------------------------------------- biyometrik

    private fun createBiometricKey(context: Context, authClass: AuthClass): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            authClass.alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setRandomizedEncryptionRequired(true)

        when (authClass) {
            AuthClass.BIOMETRIC_ONLY -> {
                // Yeni bir parmak izi/yüz kaydedilirse anahtar geçersizleşsin:
                // telefona kendi parmağını ekleyen biri kasayı açamasın.
                builder.setInvalidatedByBiometricEnrollment(true)
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            AuthClass.DEVICE_CREDENTIAL -> {
                // Burada kayıt geçersizleştirmesi açılmıyor. Açılsaydı, yeni bir
                // parmak izi eklemek ekran kilidi parolasıyla açılan yolu da
                // kapatırdı — kullanıcının bu seçeneği açma sebebi tam olarak
                // biyometrinin çalışmaması olduğu hâlde.
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            }
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)

        if (hasStrongBox(context)) {
            try {
                builder.setIsStrongBoxBacked(true)
                generator.init(builder.build())
                return generator.generateKey()
            } catch (e: StrongBoxUnavailableException) {
                Log.i("KeystoreKeys", "StrongBox yok, TEE'ye düşülüyor")
                builder.setIsStrongBoxBacked(false)
            } catch (e: Exception) {
                builder.setIsStrongBoxBacked(false)
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun biometricKey(authClass: AuthClass): SecretKey? =
        runCatching { keyStore.getKey(authClass.alias, null) as? SecretKey }.getOrNull()

    /**
     * Biyometrik sarmalama için şifreleme şifreleyicisi üretir.
     * Dönen [Cipher] BiometricPrompt.CryptoObject içine konmalıdır.
     */
    fun biometricEncryptCipher(
        context: Context,
        authClass: AuthClass = AuthClass.BIOMETRIC_ONLY
    ): Cipher {
        // Kullanıcı sınıfı değiştirdiyse öteki takma ad artık geçersiz: onu
        // bırakmak, kapatıldığı sanılan bir yolu açık bırakmak olurdu.
        AuthClass.entries.filter { it != authClass }
            .forEach { runCatching { keyStore.deleteEntry(it.alias) } }

        val key = biometricKey(authClass) ?: createBiometricKey(context, authClass)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * Sarmalanmış anahtarı açmak için çözme şifreleyicisi üretir.
     * Biyometri kaydı değiştiyse anahtar geçersizdir; bu durumda `null` döner ve
     * çağıran ana parolaya geri düşer.
     */
    fun biometricDecryptCipher(
        iv: ByteArray,
        authClass: AuthClass = AuthClass.BIOMETRIC_ONLY
    ): Cipher? = try {
        val key = biometricKey(authClass) ?: return null
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(Crypto.GCM_TAG_BITS, iv))
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        Log.i("KeystoreKeys", "Biyometrik anahtar geçersizleşti (yeni kayıt)")
        deleteBiometricKey()
        null
    } catch (t: Throwable) {
        null
    }

    // ------------------------------------------------------------------ PIN

    /**
     * PIN katmanının donanım demiri.
     *
     * Dört haneli bir PIN'in kendi başına on bin olasılığı var; Argon2 bunu
     * çevrimdışı bir saldırgana karşı anlamlı ölçüde zorlaştırmaz. PIN'i
     * güvenli kılan iki şey var ve ikisi de burada:
     *
     *  1. **Bu anahtar cihazdan çıkmaz.** PIN sarmalayıcısı önce PIN'den
     *     türetilen anahtarla, sonra bu Keystore anahtarıyla kapatılıyor.
     *     Dosyayı telefondan kopyalayan biri, Keystore katmanını açamadığı
     *     için PIN denemesine hiç başlayamıyor — saldırı yalnızca cihaz
     *     üzerinde mümkün.
     *  2. **Deneme sayacı.** Cihaz üzerindeki deneme de sayılıyor; sınır
     *     aşıldığında sarmalayıcı ve bu anahtar birlikte siliniyor ve ana
     *     parola isteniyor.
     *
     * Kullanıcı doğrulaması istenmiyor: PIN'in kendisi zaten o doğrulama.
     */
    private fun loadOrCreatePinKey(context: Context): SecretKey {
        (keyStore.getKey(ALIAS_PIN, null) as? SecretKey)?.let { return it }

        val builder = KeyGenParameterSpec.Builder(
            ALIAS_PIN,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        if (hasStrongBox(context)) {
            try {
                builder.setIsStrongBoxBacked(true)
                generator.init(builder.build())
                return generator.generateKey()
            } catch (e: Exception) {
                builder.setIsStrongBoxBacked(false)
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    fun pinSeal(context: Context, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreatePinKey(context))
        return cipher.iv + cipher.doFinal(plain)
    }

    fun pinOpen(context: Context, sealed: ByteArray): ByteArray? = try {
        val key = keyStore.getKey(ALIAS_PIN, null) as? SecretKey
        if (key == null) null else {
            val iv = sealed.copyOfRange(0, 12)
            val body = sealed.copyOfRange(12, sealed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(Crypto.GCM_TAG_BITS, iv))
            cipher.doFinal(body)
        }
    } catch (t: Throwable) {
        null
    }

    // ------------------------------------------------------------------- cihaz

    private fun loadOrCreateDeviceKey(): SecretKey {
        (keyStore.getKey(ALIAS_DEVICE, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS_DEVICE,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    /** Meta veriyi cihaz anahtarıyla şifreler: nonce + şifreli metin. */
    fun deviceSeal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateDeviceKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun deviceOpen(sealed: ByteArray): ByteArray? = try {
        val iv = sealed.copyOfRange(0, 12)
        val body = sealed.copyOfRange(12, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, loadOrCreateDeviceKey(), GCMParameterSpec(Crypto.GCM_TAG_BITS, iv))
        cipher.doFinal(body)
    } catch (t: Throwable) {
        null
    }
}
