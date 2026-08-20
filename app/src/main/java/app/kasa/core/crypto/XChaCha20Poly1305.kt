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

    /**
     * Bu paket bu cihazda güvenle kullanılabilir mi?
     *
     * Yalnızca "sağlayıcı var mı" diye bakmıyor. Buradaki HChaCha20 el yazması
     * ve el yazması kriptografinin sessizce yanlış olması, gürültülü şekilde
     * bozulmasından çok daha kötü: yanlış bir alt anahtar, açılamayan bir kasa
     * demek ve bu ancak kullanıcı parolasını doğru girdiği hâlde kasası
     * açılmadığında anlaşılır.
     *
     * Bu yüzden paket sunulmadan önce iki şey doğrulanıyor:
     *
     *  1. **Bilinen cevap testi.** RFC 8439 türevi XChaCha taslağındaki
     *     HChaCha20 vektörü hesaplanıp beklenen alt anahtarla karşılaştırılıyor.
     *     Bu, dört-tur işlevinden bayt sırasına kadar her adımı sınıyor.
     *  2. **Gidiş-dönüş testi.** Gerçek bir mühürleme/açma turu yapılıp düz
     *     metnin aynen döndüğü ve kurcalanmış etiketin reddedildiği
     *     doğrulanıyor — yani platformun sağlayıcısı da sınanıyor.
     *
     * Biri bile tutmazsa paket hiç sunulmuyor ve kasa AES-256-GCM ile yazılıyor.
     */
    val available: Boolean by lazy { runCatching { selfTest() }.getOrDefault(false) }

    /** RFC taslağındaki HChaCha20 vektörünün girdisi ve beklenen alt anahtarı. */
    private val KAT_KEY = byteArrayOf(
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f
    )

    private val KAT_NONCE = byteArrayOf(
        0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x4a,
        0x00, 0x00, 0x00, 0x00, 0x31, 0x41, 0x59, 0x27
    )

    private val KAT_SUBKEY = byteArrayOf(
        0x82.toByte(), 0x41, 0x3b, 0x42, 0x27, 0xb2.toByte(), 0x7b, 0xfe.toByte(),
        0xd3.toByte(), 0x0e, 0x42, 0x50, 0x8a.toByte(), 0x87.toByte(), 0x7d, 0x73,
        0xa0.toByte(), 0xf9.toByte(), 0xe4.toByte(), 0xd5.toByte(),
        0x8a.toByte(), 0x74, 0xa8.toByte(), 0x53,
        0xc1.toByte(), 0x2e, 0xc4.toByte(), 0x13, 0x26, 0xd3.toByte(), 0xec.toByte(), 0xdc.toByte()
    )

    private fun selfTest(): Boolean {
        val derived = hChaCha20(KAT_KEY, KAT_NONCE)
        val kdfOk = try {
            Crypto.constantTimeEquals(derived, KAT_SUBKEY)
        } finally {
            derived.fill(0)
        }
        if (!kdfOk) return false

        val key = Crypto.randomBytes(KEY_BYTES)
        val nonce = Crypto.randomBytes(NONCE_BYTES)
        val plain = Crypto.randomBytes(96)
        val aad = Crypto.randomBytes(16)
        return try {
            val sealed = seal(key, nonce, plain, aad)
            if (!Crypto.constantTimeEquals(open(key, nonce, sealed, aad), plain)) return false
            // Kurcalanan etiket reddedilmeli; edilmiyorsa bütünlük yok demektir.
            sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
            runCatching { open(key, nonce, sealed, aad) }.isFailure
        } catch (t: Throwable) {
            false
        } finally {
            key.fill(0)
            plain.fill(0)
        }
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
