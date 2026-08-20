package app.kasa.core.crypto

import android.util.Log
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Anahtar türetme: ana parola -> 32 baytlık anahtar şifreleme anahtarı (KEK).
 *
 * Birincil seçim Argon2id'dir. Bellek-zor bir fonksiyon olduğu için GPU ve ASIC
 * üzerinde paralel deneme yapmanın maliyetini gerçekten yükseltir; PBKDF2 gibi
 * yalnız işlemci-zor fonksiyonlar bu saldırganlara neredeyse bedavadır.
 *
 * Yerel kitaplık yüklenemezse (çok eski ya da alışılmadık bir ABI) sessizce
 * güvensiz bir yola düşmek yerine PBKDF2-HMAC-SHA512 / 600.000 tura geçilir --
 * OWASP'ın 2023 tavsiyesi. Hangi yolun kullanıldığı dosya başlığına yazılır,
 * böylece cihaz değişse bile kasa açılabilir.
 */
object Kdf {

    const val ALG_ARGON2ID: Byte = 1
    const val ALG_PBKDF2_SHA512: Byte = 2

    /** Telefonda kabul edilebilir gecikmeyle (yaklaşık 0,6-1,2 sn) en yüksek maliyet. */
    const val ARGON2_MEMORY_KIB = 65_536      // 64 MiB
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_PARALLELISM = 2

    /** Dışa aktarılan dosyalar için daha ağır parametreler: gecikme orada sorun değil. */
    const val ARGON2_MEMORY_KIB_EXPORT = 131_072  // 128 MiB
    const val ARGON2_ITERATIONS_EXPORT = 4

    const val PBKDF2_ITERATIONS = 600_000

    const val SALT_BYTES = 16

    private val argon2: Argon2Kt? by lazy {
        try {
            Argon2Kt()
        } catch (t: Throwable) {
            Log.w("Kdf", "Argon2 yerel kitaplığı yüklenemedi, PBKDF2'ye düşülüyor")
            null
        }
    }

    val argon2Available: Boolean get() = argon2 != null

    class Params(
        val algorithm: Byte,
        val salt: ByteArray,
        val iterations: Int,
        val memoryKib: Int,
        val parallelism: Int
    ) {
        fun writeTo(out: DataOutputStream) {
            out.writeByte(algorithm.toInt())
            out.writeInt(iterations)
            out.writeInt(memoryKib)
            out.writeByte(parallelism)
            out.writeByte(salt.size)
            out.write(salt)
        }

        /**
         * Aynı maliyet, yeni tuz.
         *
         * Her sarmalayıcı kendi tuzunu taşımalı: aynı tuzu paylaşan iki
         * sarmalayıcı, saldırgana tek bir Argon2 hesabıyla ikisini birden
         * deneme imkânı verirdi.
         */
        fun withFreshSalt(): Params =
            Params(algorithm, Crypto.randomBytes(SALT_BYTES), iterations, memoryKib, parallelism)

        override fun equals(other: Any?): Boolean =
            other is Params && algorithm == other.algorithm && iterations == other.iterations &&
                memoryKib == other.memoryKib && parallelism == other.parallelism &&
                salt.contentEquals(other.salt)

        override fun hashCode(): Int =
            algorithm.hashCode() * 31 + iterations * 31 + memoryKib * 31 + salt.contentHashCode()

        companion object {
            fun readFrom(input: DataInputStream): Params {
                val alg = input.readByte()
                val iterations = input.readInt()
                val memoryKib = input.readInt()
                val parallelism = input.readUnsignedByte()
                val saltLen = input.readUnsignedByte()
                require(saltLen in 8..64) { "Geçersiz tuz uzunluğu" }
                val salt = ByteArray(saltLen)
                input.readFully(salt)
                return Params(alg, salt, iterations, memoryKib, parallelism)
            }
        }
    }

    /** Bu cihazda kullanılacak varsayılan parametreleri yeni bir tuzla üretir. */
    fun defaultParams(forExport: Boolean = false): Params =
        if (argon2Available) {
            Params(
                algorithm = ALG_ARGON2ID,
                salt = Crypto.randomBytes(SALT_BYTES),
                iterations = if (forExport) ARGON2_ITERATIONS_EXPORT else ARGON2_ITERATIONS,
                memoryKib = if (forExport) ARGON2_MEMORY_KIB_EXPORT else ARGON2_MEMORY_KIB,
                parallelism = ARGON2_PARALLELISM
            )
        } else {
            Params(
                algorithm = ALG_PBKDF2_SHA512,
                salt = Crypto.randomBytes(SALT_BYTES),
                iterations = PBKDF2_ITERATIONS,
                memoryKib = 0,
                parallelism = 1
            )
        }

    /**
     * Parolayı [params] ile 32 baytlık anahtara dönüştürür.
     * Ağırdır; her zaman arka plan iş parçacığından çağır.
     */
    fun derive(password: SecretBytes, params: Params): SecretBytes = when (params.algorithm) {
        ALG_ARGON2ID -> deriveArgon2(password, params)
        ALG_PBKDF2_SHA512 -> derivePbkdf2(password, params)
        else -> throw IllegalArgumentException("Bilinmeyen anahtar türetme algoritması: ${params.algorithm}")
    }

    private fun deriveArgon2(password: SecretBytes, params: Params): SecretBytes {
        // Kasa Argon2 ile oluşturulmuşsa ve bu cihazda kitaplık yoksa sessizce
        // zayıf bir yola düşmek yerine açıkça hata veriyoruz.
        val engine = argon2
            ?: throw IllegalStateException("Bu kasa Argon2id ile korunuyor, bu cihazda desteklenmiyor")
        val result = engine.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.raw(),
            salt = params.salt,
            tCostInIterations = params.iterations,
            mCostInKibibyte = params.memoryKib,
            parallelism = params.parallelism,
            hashLengthInBytes = Crypto.KEY_BYTES
        )
        return SecretBytes(result.rawHashAsByteArray())
    }

    private fun derivePbkdf2(password: SecretBytes, params: Params): SecretBytes {
        // PBKDF2 API'si char[] ister; UTF-8 baytlarını Latin-1 gibi eşleyerek
        // bilgi kaybı olmadan char dizisine taşıyoruz.
        val raw = password.raw()
        val chars = CharArray(raw.size) { (raw[it].toInt() and 0xFF).toChar() }
        try {
            val spec = PBEKeySpec(chars, params.salt, params.iterations, Crypto.KEY_BYTES * 8)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val key = factory.generateSecret(spec).encoded
            spec.clearPassword()
            return SecretBytes(key)
        } finally {
            chars.fill('\u0000')
        }
    }
}
