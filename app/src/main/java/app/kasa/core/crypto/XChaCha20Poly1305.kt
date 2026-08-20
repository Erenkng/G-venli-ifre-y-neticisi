package app.kasa.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * XChaCha20-Poly1305.
 *
 * Android'in sağlayıcısı IETF ChaCha20-Poly1305'i (12 baytlık nonce) sunuyor
 * ama XChaCha20'yi (24 baytlık nonce) sunmuyor. Aradaki fark küçük görünse de
 * pratikte belirleyici:
 *
 *  - 96 bitlik nonce rastgele seçildiğinde, aynı anahtarla ~2^32 şifreleme
 *    sonrası çakışma olasılığı ihmal edilebilir olmaktan çıkar. GCM'de olduğu
 *    gibi burada da nonce yinelenmesi şifrelemeyi tamamen çökertir.
 *  - 192 bitlik nonce ile rastgele seçim, sayaç tutmaya hiç gerek kalmadan
 *    güvenli. Kasa dosyası her kaydetmede baştan şifrelendiği için bu, sayaç
 *    saklamak zorunda kalmamak demek.
 *
 * Aradaki köprü standart HChaCha20 yapısı: nonce'un ilk 16 baytı anahtarla
 * birlikte bir alt anahtar türetir, kalan 8 bayt da IETF nonce'unun sonuna
 * yerleşir. Yani asıl AEAD işini yine platformun (donanım hızlandırmalı,
 * denetlenmiş) ChaCha20-Poly1305 kodu yapıyor; burada yazılan tek şey
 * anahtar türetme adımı.
 */
object XChaCha20Poly1305 {

    const val KEY_BYTES = 32
    const val NONCE_BYTES = 24
    const val TAG_BYTES = 16

    /** Platformun ChaCha20-Poly1305 sunup sunmadığı. */
    val available: Boolean by lazy {
        runCatching { chachaCipher() }.isSuccess
    }

    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray {
        require(key.size == KEY_BYTES) { "XChaCha20 anahtarı 32 bayt olmalı" }
        require(nonce.size == NONCE_BYTES) { "XChaCha20 nonce'u 24 bayt olmalı" }

        val subKey = hChaCha20(key, nonce.copyOfRange(0, 16))
        try {
            val cipher = chachaCipher()
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(subKey, "ChaCha20"),
                IvParameterSpec(ietfNonce(nonce))
            )
            aad?.let { cipher.updateAAD(it) }
            return cipher.doFinal(plain)
        } finally {
            subKey.fill(0)
        }
    }

    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray?): ByteArray {
        require(key.size == KEY_BYTES) { "XChaCha20 anahtarı 32 bayt olmalı" }
        require(nonce.size == NONCE_BYTES) { "XChaCha20 nonce'u 24 bayt olmalı" }

        val subKey = hChaCha20(key, nonce.copyOfRange(0, 16))
        try {
            val cipher = chachaCipher()
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(subKey, "ChaCha20"),
                IvParameterSpec(ietfNonce(nonce))
            )
            aad?.let { cipher.updateAAD(it) }
            return cipher.doFinal(sealed)
        } finally {
            subKey.fill(0)
        }
    }

    /** IETF nonce'u: dört sıfır bayt + XChaCha nonce'unun son 8 baytı. */
    private fun ietfNonce(nonce: ByteArray): ByteArray =
        ByteArray(12).also { System.arraycopy(nonce, 16, it, 4, 8) }

    private fun chachaCipher(): Cipher =
        runCatching { Cipher.getInstance("ChaCha20-Poly1305") }
            .getOrElse { Cipher.getInstance("ChaCha20/Poly1305/NoPadding") }

    // ─────────────────────────────── HChaCha20 ───────────────────────────────

    /**
     * HChaCha20: anahtar + 16 baytlık nonce → 32 baytlık alt anahtar.
     *
     * ChaCha20'nin çekirdeğiyle aynı 20 turu çalıştırır ama sonunda başlangıç
     * durumunu geri eklemez; çıktı olarak yalnızca ilk ve son dört sözcüğü
     * verir. Bu fark HChaCha20'yi tersine çevrilemez kılan şeydir.
     */
    private fun hChaCha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865  // "expa"
        state[1] = 0x3320646e  // "nd 3"
        state[2] = 0x79622d32  // "2-by"
        state[3] = 0x6b206574  // "te k"
        for (i in 0 until 8) state[4 + i] = littleEndianInt(key, i * 4)
        for (i in 0 until 4) state[12 + i] = littleEndianInt(nonce16, i * 4)

        repeat(10) {
            // sütunlar
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            // köşegenler
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }

        val out = ByteArray(32)
        for (i in 0 until 4) writeLittleEndian(out, i * 4, state[i])
        for (i in 0 until 4) writeLittleEndian(out, 16 + i * 4, state[12 + i])
        state.fill(0)
        return out
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }

    private fun littleEndianInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
