package app.kasa.core.util

import android.content.Context
import app.kasa.R
import app.kasa.core.crypto.Crypto
import kotlin.math.log2

/**
 * Parola ve sözcük dizisi üreteci.
 *
 * Tüm rastgelelik [Crypto.randomInt] üzerinden gelir; bu da modulo sapması
 * olmayan reddetme örneklemesiyle `SecureRandom`'a dayanır. `Random()` ya da
 * `Math.random()` burada kullanılmaz: ikisi de öngörülebilir tohumdan üretir.
 *
 * Üretilen her parola, seçilen her karakter kümesinden en az bir örnek
 * içerecek şekilde düzeltilir ve sonra Fisher-Yates ile karıştırılır. Bu,
 * "sembol açık ama parolada sembol yok" durumunu ortadan kaldırırken
 * entropiyi ölçülebilir biçimde korur.
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val LOWER_CLEAR = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val UPPER_CLEAR = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val DIGITS_CLEAR = "23456789"
    private const val SYMBOLS = "!#$%&*+-=?@^_"

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val MIN_WORDS = 3
    const val MAX_WORDS = 10

    data class Options(
        val length: Int = 20,
        val upper: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidLookalikes: Boolean = false
    )

    data class PassphraseOptions(
        val words: Int = 5,
        val separator: String = "-",
        val capitalize: Boolean = true,
        val appendNumber: Boolean = true
    )

    data class Generated(val value: String, val entropyBits: Double)

    fun generate(options: Options): Generated {
        val sets = buildList {
            add(if (options.avoidLookalikes) LOWER_CLEAR else LOWER)
            if (options.upper) add(if (options.avoidLookalikes) UPPER_CLEAR else UPPER)
            if (options.digits) add(if (options.avoidLookalikes) DIGITS_CLEAR else DIGITS)
            if (options.symbols) add(SYMBOLS)
        }
        val pool = sets.joinToString("")
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)

        val chars = CharArray(length)
        // Önce her kümeden birer zorunlu karakter
        sets.forEachIndexed { index, set ->
            if (index < length) chars[index] = set[Crypto.randomInt(set.length)]
        }
        for (i in sets.size until length) {
            chars[i] = pool[Crypto.randomInt(pool.length)]
        }
        shuffle(chars)

        return Generated(String(chars), length * log2(pool.length.toDouble()))
    }

    /**
     * Sözcük dizisi üretir. Sözlük ASCII Türkçe sözcüklerden oluşur; her yerde
     * sorunsuz yazılabilsin diye Türkçe'ye özgü harfler bilerek dışarıda tutulur.
     */
    fun generatePassphrase(words: List<String>, options: PassphraseOptions): Generated {
        require(words.isNotEmpty()) { "Sözlük boş" }
        val count = options.words.coerceIn(MIN_WORDS, MAX_WORDS)
        val picked = (0 until count).map { words[Crypto.randomInt(words.size)] }
            .map { if (options.capitalize) it.replaceFirstChar { c -> c.uppercaseChar() } else it }

        val body = picked.joinToString(options.separator)
        val suffix = if (options.appendNumber) options.separator + Crypto.randomInt(100).toString().padStart(2, '0') else ""

        val extras = if (options.appendNumber) log2(100.0) else 0.0
        return Generated(body + suffix, PasswordStrength.passphraseEntropy(count, words.size, extras))
    }

    /** Fisher-Yates: her permütasyon eşit olasılıkla. */
    private fun shuffle(chars: CharArray) {
        for (i in chars.size - 1 downTo 1) {
            val j = Crypto.randomInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
    }

    @Volatile
    private var cachedWords: List<String>? = null

    /** Sözlüğü yalnız bir kez okur; 500'ün üzerinde sözcük, sözcük başına ~9 bit. */
    fun words(context: Context): List<String> {
        cachedWords?.let { return it }
        val list = context.resources.openRawResource(R.raw.wordlist_tr)
            .bufferedReader()
            .useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toList() }
        cachedWords = list
        return list
    }
}
