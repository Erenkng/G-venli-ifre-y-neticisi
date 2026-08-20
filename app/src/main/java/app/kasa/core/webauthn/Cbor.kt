package app.kasa.core.webauthn

import java.io.ByteArrayOutputStream

/**
 * WebAuthn'ın ihtiyaç duyduğu kadarıyla CBOR yazıcısı (RFC 8949).
 *
 * Passkey üretimi iki yerde CBOR istiyor: açık anahtarın COSE gösterimi ve
 * onu saran attestation nesnesi. İhtiyaç bu kadar dar olduğu için tam bir
 * CBOR kitaplığı almak yerine dört ana tür (pozitif tamsayı, negatif tamsayı,
 * bayt dizisi, metin) ve harita yazılıyor. Okuma tarafı hiç gerekmiyor:
 * ürettiğimiz baytları yalnızca doğrulayıcı taraf çözüyor.
 *
 * Yazım **kurallı** (canonical/CTAP2) biçimde: her sayı en kısa gösterimiyle
 * kodlanıyor ve harita anahtarları çağıran tarafından zaten kurallı sırada
 * veriliyor. Kurallılık şart, çünkü attestation nesnesinin baytları imzanın
 * içine giriyor — aynı veriden farklı baytlar üretmek imzayı bozar.
 */
object Cbor {

    private const val MAJOR_UNSIGNED = 0
    private const val MAJOR_NEGATIVE = 1
    private const val MAJOR_BYTES = 2
    private const val MAJOR_TEXT = 3
    private const val MAJOR_MAP = 5

    class Writer {
        private val out = ByteArrayOutputStream()

        fun int(value: Long): Writer = apply {
            if (value >= 0) head(MAJOR_UNSIGNED, value)
            // Negatif tamsayılar -1-n olarak kodlanır: -7 → n = 6.
            else head(MAJOR_NEGATIVE, -1L - value)
        }

        fun int(value: Int): Writer = int(value.toLong())

        fun bytes(value: ByteArray): Writer = apply {
            head(MAJOR_BYTES, value.size.toLong())
            out.write(value)
        }

        fun text(value: String): Writer = apply {
            val encoded = value.toByteArray(Charsets.UTF_8)
            head(MAJOR_TEXT, encoded.size.toLong())
            out.write(encoded)
        }

        /** [entries] anahtar sayısını bildirir; ardından anahtar/değer çiftleri yazılır. */
        fun mapHeader(entries: Int): Writer = apply { head(MAJOR_MAP, entries.toLong()) }

        fun raw(value: ByteArray): Writer = apply { out.write(value) }

        fun build(): ByteArray = out.toByteArray()

        private fun head(major: Int, value: Long) {
            val prefix = major shl 5
            when {
                value < 24 -> out.write(prefix or value.toInt())
                value <= 0xFF -> {
                    out.write(prefix or 24)
                    out.write(value.toInt() and 0xFF)
                }
                value <= 0xFFFF -> {
                    out.write(prefix or 25)
                    out.write((value shr 8).toInt() and 0xFF)
                    out.write(value.toInt() and 0xFF)
                }
                value <= 0xFFFF_FFFFL -> {
                    out.write(prefix or 26)
                    for (shift in intArrayOf(24, 16, 8, 0)) out.write((value shr shift).toInt() and 0xFF)
                }
                else -> {
                    out.write(prefix or 27)
                    for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) {
                        out.write((value shr shift).toInt() and 0xFF)
                    }
                }
            }
        }
    }

    fun writer(): Writer = Writer()
}
