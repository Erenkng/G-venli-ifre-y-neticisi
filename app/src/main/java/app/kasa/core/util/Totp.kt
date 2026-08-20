package app.kasa.core.util

import app.kasa.core.crypto.Base32
import java.net.URI
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
        val key = Base32.decodeRfc4648(secret) ?: return null
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
     * `otpauth://totp/Issuer:hesap?secret=...&issuer=...&digits=6&period=30&algorithm=SHA1`
     * biçimindeki karekod bağlantısını ayrıştırır.
     */
    fun parseUri(uri: String): Config? = try {
        val parsed = URI(uri.trim())
        if (!parsed.scheme.equals("otpauth", ignoreCase = true)) null
        else if (!parsed.host.equals("totp", ignoreCase = true)) null
        else {
            val query = parsed.rawQuery.orEmpty().split("&")
                .mapNotNull { part ->
                    val i = part.indexOf('=')
                    if (i <= 0) null
                    else URLDecoder.decode(part.substring(0, i), "UTF-8").lowercase() to
                        URLDecoder.decode(part.substring(i + 1), "UTF-8")
                }.toMap()

            val secret = query["secret"]?.replace(" ", "").orEmpty()
            // Yalnızca geçerlilik sınanıyor; çözülen baytlar hemen siliniyor.
            val valid = secret.isNotBlank() &&
                Base32.decodeRfc4648(secret)?.also { it.fill(0) } != null
            if (!valid) null
            else {
                val label = URLDecoder.decode(parsed.path.orEmpty().removePrefix("/"), "UTF-8")
                val labelIssuer = label.substringBefore(':', "").trim()
                val account = label.substringAfter(':', label).trim()
                Config(
                    secret = secret.uppercase(),
                    digits = query["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6,
                    period = query["period"]?.toIntOrNull()?.coerceIn(15, 120) ?: 30,
                    algorithm = query["algorithm"]?.uppercase() ?: "SHA1",
                    issuer = query["issuer"] ?: labelIssuer,
                    account = account
                )
            }
        }
    } catch (t: Throwable) {
        null
    }
}
