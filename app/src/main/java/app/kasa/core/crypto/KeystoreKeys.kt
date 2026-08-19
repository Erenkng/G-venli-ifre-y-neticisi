package app.kasa.core.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    const val ALIAS_DEVICE = "kasa.kek.device.v1"

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    fun hasBiometricKey(): Boolean = runCatching { keyStore.containsAlias(ALIAS_BIOMETRIC) }.getOrDefault(false)

    fun deleteBiometricKey() {
        runCatching { keyStore.deleteEntry(ALIAS_BIOMETRIC) }
    }

    fun deleteAll() {
        runCatching { keyStore.deleteEntry(ALIAS_BIOMETRIC) }
        runCatching { keyStore.deleteEntry(ALIAS_DEVICE) }
    }

    /** Anahtar gerçekten güvenli donanımda mı tutuluyor? Ayarlar ekranında gösterilir. */
    fun isHardwareBacked(context: Context): Boolean = try {
        val key = loadOrCreateDeviceKey()
        val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, PROVIDER)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
    } catch (t: Throwable) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    fun hasStrongBox(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    // ---------------------------------------------------------------- biyometrik

    private fun createBiometricKey(context: Context): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            ALIAS_BIOMETRIC,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Yeni bir parmak izi/yüz kaydedilirse anahtar geçersizleşsin.
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Her kullanımda taze doğrulama; yalnızca güçlü (Class 3) biyometri kabul.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
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

    private fun biometricKey(): SecretKey? =
        runCatching { keyStore.getKey(ALIAS_BIOMETRIC, null) as? SecretKey }.getOrNull()

    /**
     * Biyometrik sarmalama için şifreleme şifreleyicisi üretir.
     * Dönen [Cipher] BiometricPrompt.CryptoObject içine konmalıdır.
     */
    fun biometricEncryptCipher(context: Context): Cipher {
        val key = biometricKey() ?: createBiometricKey(context)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * Sarmalanmış anahtarı açmak için çözme şifreleyicisi üretir.
     * Biyometri kaydı değiştiyse anahtar geçersizdir; bu durumda `null` döner ve
     * çağıran ana parolaya geri düşer.
     */
    fun biometricDecryptCipher(iv: ByteArray): Cipher? = try {
        val key = biometricKey() ?: return null
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
