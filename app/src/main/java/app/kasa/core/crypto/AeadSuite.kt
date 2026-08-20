package app.kasa.core.crypto

import android.util.Log
import kotlin.system.measureNanoTime

/**
 * Kasanın kullanabildiği kimlik doğrulamalı şifreleme paketleri.
 *
 * Dosya başlığında yalnızca kimlik baytı durur; hangi paketle yazıldığı
 * dosyanın kendisinden okunur. Böylece cihaz değişse, varsayılan değişse ya da
 * ileride bir paket emekliye ayrılsa bile eski kasa açılmaya devam eder.
 */
enum class AeadSuite(val id: Byte, val nonceBytes: Int, val label: String) {

    /**
     * Varsayılan. ARMv8 AES komutları olan her cihazda (Android 16 çalıştıran
     * tüm cihazlar bu kapsamda) donanım hızlandırmalı ve sabit zamanlı.
     */
    AES_256_GCM(1, Crypto.GCM_NONCE_BYTES, "AES-256-GCM"),

    /**
     * AES hızlandırması olmayan ya da zayıf olan cihazlar için. Yazılımda
     * ChaCha20 tasarımı gereği sabit zamanlıdır — AES'in yazılım uygulamaları
     * ise tablo aramaları yüzünden önbellek zamanlaması sızdırabilir.
     */
    XCHACHA20_POLY1305(2, XChaCha20Poly1305.NONCE_BYTES, "XChaCha20-Poly1305");

    val available: Boolean
        get() = when (this) {
            AES_256_GCM -> true
            XCHACHA20_POLY1305 -> XChaCha20Poly1305.available
        }

    /** Dönen dizi: nonce + şifreli metin + etiket. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray {
        val nonce = Crypto.randomBytes(nonceBytes)
        val body = when (this) {
            AES_256_GCM -> Crypto.aesGcmSeal(key, nonce, plain, aad)
            XCHACHA20_POLY1305 -> XChaCha20Poly1305.seal(key, nonce, plain, aad)
        }
        return nonce + body
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray?): ByteArray {
        require(sealed.size > nonceBytes + 16) { "Şifreli veri çok kısa" }
        val nonce = sealed.copyOfRange(0, nonceBytes)
        val body = sealed.copyOfRange(nonceBytes, sealed.size)
        return when (this) {
            AES_256_GCM -> Crypto.aesGcmOpen(key, nonce, body, aad)
            XCHACHA20_POLY1305 -> XChaCha20Poly1305.open(key, nonce, body, aad)
        }
    }

    companion object {
        val DEFAULT = AES_256_GCM

        fun fromId(id: Byte): AeadSuite =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Bilinmeyen şifreleme paketi: $id")

        /**
         * İki paketi de ölçer ve bu cihazda hızlı olanı seçer.
         *
         * Ölçüm gerçek bir tercih üretiyor: AES komut kümesi olan cihazlarda
         * AES-GCM açık ara önde, olmayanlarda ChaCha öne geçiyor. Sabit bir
         * varsayılan seçmek yerine ölçmek, her iki durumda da doğru kararı
         * veriyor ve seçim dosya başlığına yazıldığı için kalıcı oluyor.
         *
         * @param payloadBytes ölçümde kullanılacak örnek boyut; tipik bir kasa
         *        büyüklüğü seçilmeli, çok küçük veri kurulum maliyetini ölçer.
         */
        fun fastest(payloadBytes: Int = 256 * 1024): AeadSuite {
            val candidates = entries.filter { it.available }
            if (candidates.size < 2) return candidates.firstOrNull() ?: DEFAULT

            val key = Crypto.randomBytes(Crypto.KEY_BYTES)
            val payload = Crypto.randomBytes(payloadBytes)
            return try {
                val timings = candidates.associateWith { suite ->
                    runCatching {
                        // Bir tur ısınma: ilk çağrı sağlayıcı yüklemesini de ölçer.
                        suite.open(key, suite.seal(key, payload, null), null)
                        measureNanoTime {
                            repeat(3) { suite.open(key, suite.seal(key, payload, null), null) }
                        }
                    }.getOrNull()
                }.filterValues { it != null }.mapValues { it.value!! }

                timings.minByOrNull { it.value }?.key ?: DEFAULT
            } catch (t: Throwable) {
                Log.w("AeadSuite", "Şifre paketi ölçümü başarısız, varsayılana dönülüyor")
                DEFAULT
            } finally {
                key.fill(0)
                payload.fill(0)
            }
        }
    }
}
