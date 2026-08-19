package app.kasa.core.crypto

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kasanın simetrik şifreleme katmanı.
 *
 * Seçimler ve gerekçeleri:
 *  - **AES-256-GCM**: bütünlük doğrulaması şifrelemenin içinde. Yanlış anahtarla
 *    açma denemesi sessizce çöp üretmez, `AEADBadTagException` ile patlar. Bu
 *    sayede "ana parola doğru mu" sorusuna ayrı bir doğrulayıcı alan koymadan
 *    yanıt verilir; ayrı doğrulayıcı, çevrimdışı saldırgana bedava sağlama sunardı.
 *  - **96 bitlik rastgele nonce**: GCM'in önerdiği boyut. Her yazımda yeniden
 *    üretilir; aynı anahtarla nonce yinelenmesi GCM'i tamamen kırdığı için
 *    sayaç yerine [SecureRandom] kullanılıp anahtar sık sık döndürülür.
 *  - **AAD**: başlık baytları ek doğrulanmış veri olarak bağlanır; saldırgan
 *    KDF parametrelerini (örneğin Argon2 maliyetini) düşürecek şekilde başlığı
 *    kurcalayamaz, çünkü etiket doğrulaması bozulur.
 */
object Crypto {

    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val KEY_BYTES = 32

    private val secureRandom: SecureRandom by lazy { SecureRandom() }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    fun randomInt(bound: Int): Int {
        require(bound > 0)
        // Modulo sapmasını ortadan kaldıran reddetme örneklemesi.
        val limit = Int.MAX_VALUE - (Int.MAX_VALUE % bound)
        while (true) {
            val v = secureRandom.nextInt() and Int.MAX_VALUE
            if (v < limit) return v % bound
        }
    }

    fun secretKey(key: ByteArray): SecretKey = SecretKeySpec(key, "AES")

    /**
     * [plain] verisini şifreler. Dönen dizi: nonce (12 bayt) + şifreli metin + etiket.
     */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 için 32 baytlık anahtar gerekir" }
        val nonce = randomBytes(GCM_NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(key), GCMParameterSpec(GCM_TAG_BITS, nonce))
        aad?.let { cipher.updateAAD(it) }
        val body = cipher.doFinal(plain)
        return nonce + body
    }

    /**
     * [sealed] verisini çözer. Etiket doğrulaması başarısızsa istisna fırlatır —
     * bu, yanlış ana parolanın da tek göstergesidir.
     */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 için 32 baytlık anahtar gerekir" }
        require(sealed.size > GCM_NONCE_BYTES + GCM_TAG_BITS / 8) { "Şifreli veri çok kısa" }
        val nonce = sealed.copyOfRange(0, GCM_NONCE_BYTES)
        val body = sealed.copyOfRange(GCM_NONCE_BYTES, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(key), GCMParameterSpec(GCM_TAG_BITS, nonce))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(body)
    }

    /** Zamanlama sızıntısı olmayan karşılaştırma. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** Yalnızca HIBP k-anonimlik sorgusu için; parola saklamada kullanılmaz. */
    fun sha1Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02X".format(it) }

    /**
     * `CharArray` → UTF-8 `ByteArray`. Ara `String` üretmez, bu yüzden ana parola
     * yığında değişmez bir kopya olarak kalmaz.
     */
    fun charsToUtf8(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        // Ara arabelleği de temizle.
        if (byteBuffer.hasArray()) byteBuffer.array().fill(0)
        return bytes
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
