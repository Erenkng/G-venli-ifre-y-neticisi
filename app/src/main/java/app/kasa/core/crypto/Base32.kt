package app.kasa.core.crypto

/**
 * RFC 4648 Base32 (TOTP gizli anahtarları için) ve Crockford Base32
 * (kurtarma anahtarı için) kodlayıcıları.
 *
 * Crockford varyantı I, L, O ve U harflerini içermez; el yazısıyla not alınan
 * bir kurtarma anahtarında 1/I ve 0/O karışıklığını ortadan kaldırır.
 */
object Base32 {

    private const val RFC4648 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encodeRfc4648(data: ByteArray, pad: Boolean = false): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(RFC4648[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) sb.append(RFC4648[(buffer shl (5 - bitsLeft)) and 0x1F])
        if (pad) while (sb.length % 8 != 0) sb.append('=')
        return sb.toString()
    }

    /** Boşluk, tire ve dolgu karakterlerini yok sayar. Geçersizse `null` döner. */
    fun decodeRfc4648(text: String): ByteArray? {
        val cleaned = text.uppercase().filter { it != '=' && !it.isWhitespace() && it != '-' }
        if (cleaned.isEmpty()) return null
        val out = ArrayList<Byte>(cleaned.length * 5 / 8 + 1)
        var buffer = 0
        var bitsLeft = 0
        for (c in cleaned) {
            val idx = RFC4648.indexOf(c)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    fun encodeCrockford(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(CROCKFORD[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) sb.append(CROCKFORD[(buffer shl (5 - bitsLeft)) and 0x1F])
        return sb.toString()
    }

    fun decodeCrockford(text: String): ByteArray? {
        val cleaned = buildString {
            for (raw in text.uppercase()) {
                when (raw) {
                    '-', ' ', '\n', '\t' -> {}
                    'I', 'L' -> append('1')
                    'O' -> append('0')
                    'U' -> return null
                    else -> append(raw)
                }
            }
        }
        if (cleaned.isEmpty()) return null
        val out = ArrayList<Byte>(cleaned.length * 5 / 8 + 1)
        var buffer = 0
        var bitsLeft = 0
        for (c in cleaned) {
            val idx = CROCKFORD.indexOf(c)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}

/**
 * 120 bitlik kurtarma anahtarı. Ana parola unutulduğunda kasa anahtarını
 * çözebilen ikinci ve tek yedek yoldur.
 *
 * 120 bit, kaba kuvvetle bulunması evrenin yaşından uzun süren bir alandır;
 * yine de Argon2id ile türetildiği için sızdırılmış bir dosya üzerinde
 * çevrimdışı deneme yapmak da ayrıca pahalıdır.
 */
object RecoveryKey {

    const val ENTROPY_BYTES = 15   // 120 bit -> 24 Crockford karakteri

    fun generate(): String = format(Base32.encodeCrockford(Crypto.randomBytes(ENTROPY_BYTES)))

    /** "XXXX-XXXX-..." biçimine sokar. */
    fun format(raw: String): String = raw.chunked(4).joinToString("-")

    /** Kullanıcının yazdığı metni normalize edip parola baytlarına çevirir. */
    fun toSecret(typed: String): SecretBytes? {
        val bytes = Base32.decodeCrockford(typed) ?: return null
        if (bytes.size < ENTROPY_BYTES) return null
        return SecretBytes(bytes.copyOf(ENTROPY_BYTES))
    }
}
