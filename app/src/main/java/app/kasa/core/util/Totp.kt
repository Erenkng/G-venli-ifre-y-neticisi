package app.kasa.core.util

import app.kasa.core.crypto.Base32
import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 zaman tabanlı tek kullanımlık parola (TOTP) üreteci.
 *
 * Kod tamamen çevrimdışı hesaplanır; hiçbir sunucuya gizli anahtar gitmez.
 */
object Totp {

    data class Config(
        val secret: String,
        val digits: Int = 6,
        val period: Int = 30,
        val algorithm: String = "SHA1",
        val issuer: String = "",
        val account: String = ""
    )

    /** Verilen ana için kodu üretir. Anahtar geçersizse `null`. */
    fun code(
        secret: String,
        digits: Int = 6,
        period: Int = 30,
        algorithm: String = "SHA1",
        timeMillis: Long = System.currentTimeMillis()
    ): String? {
        val key = keyBytes(secret) ?: return null
        if (key.isEmpty()) return null
        val counter = timeMillis / 1000L / period
        return try {
            hotp(key, counter, digits, algorithm)
        } finally {
            // TOTP gizli anahtarı ikinci faktörün tamamı: parola kadar
            // değerli. Çözülen baytlar kod üretildiği anda sıfırlanıyor.
            key.fill(0)
        }
    }

    /** Geçerli kodun bitmesine kalan saniye. */
    fun secondsRemaining(period: Int = 30, timeMillis: Long = System.currentTimeMillis()): Int {
        val seconds = timeMillis / 1000L
        return (period - (seconds % period)).toInt()
    }

    /** 0..1 arası ilerleme; halka göstergesi bunu kullanır. */
    fun progress(period: Int = 30, timeMillis: Long = System.currentTimeMillis()): Float {
        val millisIntoPeriod = timeMillis % (period * 1000L)
        return (millisIntoPeriod.toFloat() / (period * 1000f)).coerceIn(0f, 1f)
    }

    private fun hotp(key: ByteArray, counter: Long, digits: Int, algorithm: String): String? = try {
        val macAlgorithm = when (algorithm.uppercase()) {
            "SHA256", "SHA-256" -> "HmacSHA256"
            "SHA512", "SHA-512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }
        val mac = Mac.getInstance(macAlgorithm)
        mac.init(SecretKeySpec(key, macAlgorithm))
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val modulo = when (digits) {
            7 -> 10_000_000
            8 -> 100_000_000
            else -> 1_000_000
        }
        (binary % modulo).toString().padStart(digits, '0')
    } catch (t: Throwable) {
        null
    }

    /** Görüntülemede kodu "123 456" biçiminde ikiye ayırır. */
    fun pretty(code: String): String =
        if (code.length % 2 == 0) {
            val half = code.length / 2
            code.substring(0, half) + " " + code.substring(half)
        } else code

    /**
     * Kullanıcının alana yazdığı ya da yapıştırdığı metnin ne olduğu.
     *
     * ### Neden tek bir "anahtar" alanı yetmiyordu
     *
     * Servisler ikinci faktörü üç ayrı biçimde veriyor: karekodun altındaki
     * `otpauth://` bağlantısı, boşluklarla ya da tirelerle gruplanmış bir
     * Base32 anahtarı, ve daha seyrek olarak onaltılık bir tohum. Alan
     * yalnızca boşluksuz Base32 kabul ediyordu; ötekileri yapıştıran kullanıcı
     * kırmızı bir çerçeve görüyor ve kaydı hiç ekleyemiyordu. Oysa üçü de aynı
     * şeyi taşıyor ve hangisinin geleceğine kullanıcı karar vermiyor.
     */
    sealed interface Input {
        /** `otpauth://` bağlantısı: anahtarın yanında hane, periyot ve ad da geliyor. */
        data class Uri(val config: Config) : Input

        /** Düz anahtar; ayraçlarından arındırılmış hâli. */
        data class Secret(val text: String) : Input

        /**
         * Google Authenticator'ın toplu dışa aktarma karekodu.
         *
         * Tek bir hesap değil, bir listeyi taşıyor ve içeriği protokol
         * arabelleği olarak kodlanmış. Ayrı bir durum: kullanıcıya "geçersiz
         * anahtar" demek yanlış olurdu, elindeki şey geçerli — yalnızca başka
         * bir şey.
         */
        data object Migration : Input

        /** `otpauth://` ama TOTP değil (örneğin sayaç tabanlı `hotp`). */
        data object Unsupported : Input

        data object Empty : Input
    }

    /**
     * Yapıştırılan metni tanır.
     *
     * Bağlantı metnin ortasında da olabilir: kullanıcı bir e-postadan ya da
     * kurulum sayfasından kopyalarken yanında bir iki sözcük getiriyor.
     */
    fun read(raw: String): Input {
        val text = raw.trim()
        if (text.isEmpty()) return Input.Empty
        if (text.startsWith(MIGRATION_SCHEME, ignoreCase = true)) return Input.Migration

        val start = text.indexOf(SCHEME, ignoreCase = true)
        if (start >= 0) {
            val candidate = text.substring(start).takeWhile { !it.isWhitespace() }
            return parseUri(candidate)?.let { Input.Uri(it) } ?: Input.Unsupported
        }
        return Input.Secret(normalizeSecret(text))
    }

    /**
     * Anahtarı saklanacak biçime sokar.
     *
     * Ayraçlar atılıyor ve büyük harfe çevriliyor. Servisler anahtarı okunsun
     * diye dörtlü gruplara ayırıyor; o boşluklar anahtarın parçası değil.
     */
    fun normalizeSecret(raw: String): String =
        raw.filter { !it.isWhitespace() && it != '-' && it != '_' && it != '=' }.uppercase()

    /**
     * Anahtarın baytları.
     *
     * Önce Base32 deneniyor — yaygın olan bu. Base32 alfabesinde `0`, `1`, `8`
     * ve `9` yok; bu rakamları taşıyan bir dize Base32 olamaz ve onaltılık
     * olarak denenmesi bir belirsizlik yaratmıyor. Yalnızca `A`-`F` ve `2`-`7`
     * kullanan bir dize ikisine de uyuyor, orada Base32 kazanıyor: ikinci
     * faktör anahtarlarının neredeyse tamamı o biçimde dağıtılıyor.
     */
    fun keyBytes(secret: String): ByteArray? =
        Base32.decodeRfc4648(secret) ?: decodeHex(secret)

    /**
     * Anahtar kod üretmeye yeter mi.
     *
     * Alt sınır beş bayt. RFC 4226 daha uzununu öneriyor ama öneri anahtarı
     * **üretene**; kullanıcıya verilen anahtarın uzunluğuna karar veren servis
     * ve kısa bir anahtarı reddetmek, kullanıcının hesabını hiç ekleyememesi
     * demek. Uygulamanın işi burada anahtarı yargılamak değil, çalıştırmak.
     */
    fun isValidSecret(secret: String): Boolean {
        val key = keyBytes(secret) ?: return false
        return try {
            key.size >= MIN_SECRET_BYTES
        } finally {
            key.fill(0)
        }
    }

    private fun decodeHex(text: String): ByteArray? {
        if (text.length < 2 || text.length % 2 != 0) return null
        val out = ByteArray(text.length / 2)
        for (i in out.indices) {
            val high = text[i * 2].digitToIntOrNull(16)
            val low = text[i * 2 + 1].digitToIntOrNull(16)
            if (high == null || low == null) {
                out.fill(0)
                return null
            }
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }

    /**
     * `otpauth://totp/Issuer:hesap?secret=...&issuer=...&digits=6&period=30&algorithm=SHA1`
     * biçimindeki karekod bağlantısını ayrıştırır.
     *
     * Ayrıştırma elle yapılıyor, [URI] ile değil: etikette boşluk geçen
     * bağlantılar ("Acme Corp:ali@ornek.com") gerçek karekodlarda çıkıyor ve
     * [URI] onlarda istisna atıp bütün bağlantıyı çöpe atıyordu.
     */
    fun parseUri(uri: String): Config? = try {
        val text = uri.trim()
        if (!text.startsWith(SCHEME, ignoreCase = true)) null
        else {
            val rest = text.substring(SCHEME.length)
            val kind = rest.takeWhile { it != '/' && it != '?' }
            if (!kind.equals("totp", ignoreCase = true)) null
            else {
                val afterKind = rest.drop(kind.length)
                val label = decode(afterKind.substringBefore('?').removePrefix("/"))
                val query = afterKind.substringAfter('?', "").split("&")
                    .mapNotNull { part ->
                        val i = part.indexOf('=')
                        if (i <= 0) null
                        else decode(part.substring(0, i)).lowercase() to decode(part.substring(i + 1))
                    }.toMap()

                val secret = normalizeSecret(query["secret"].orEmpty())
                if (!isValidSecret(secret)) null
                else {
                    val labelIssuer = label.substringBefore(':', "").trim()
                    val account = label.substringAfter(':', label).trim()
                    Config(
                        secret = secret,
                        digits = query["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6,
                        period = query["period"]?.toIntOrNull()?.coerceIn(15, 120) ?: 30,
                        algorithm = normalizeAlgorithm(query["algorithm"]),
                        issuer = (query["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer).trim(),
                        account = account
                    )
                }
            }
        }
    } catch (t: Throwable) {
        null
    }

    /** Bilinmeyen ad SHA1'e düşüyor: RFC 6238'in varsayılanı da o. */
    fun normalizeAlgorithm(raw: String?): String = when (raw?.uppercase()?.replace("-", "")) {
        "SHA256" -> "SHA256"
        "SHA512" -> "SHA512"
        else -> "SHA1"
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private const val SCHEME = "otpauth://"
    private const val MIGRATION_SCHEME = "otpauth-migration://"

    /** Kod üretmeye yeten en kısa anahtar. */
    private const val MIN_SECRET_BYTES = 5
}
