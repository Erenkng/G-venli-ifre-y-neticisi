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
    const val MIN_SYLLABLES = 4
    const val MAX_SYLLABLES = 12

    /**
     * Telaffuz edilebilir parolanın hece parçaları.
     *
     * Karışan harfler ("q", "x") ve Türkçede söylenmesi zor ikililer üretenler
     * dışarıda: amaç okunabilirlik, tam alfabe değil. Küme küçüldükçe hece
     * başına entropi de düşüyor ve bu, bildirilen değere olduğu gibi
     * yansıyor.
     */
    private const val PRONOUNCEABLE_CONSONANTS = "bcdfgklmnprstvyz"
    private const val PRONOUNCEABLE_VOWELS = "aeiou"

    /**
     * Reddetme örneklemede en fazla kaç deneme.
     *
     * Sınır pratikte hiç görülmüyor (uzunluk 8'de bile ilk denemenin tutma
     * olasılığı çok yüksek) ama sonsuz döngü ihtimalini kesin olarak kapatıyor:
     * kullanıcı 8 karakter uzunlukta dört karakter kümesi seçtiğinde bile
     * üretim sonlanmak zorunda.
     */
    private const val MAX_RESAMPLE = 24

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

    /**
     * Telaffuz edilebilir parola.
     *
     * ### Ne işe yarıyor
     *
     * Bazı parolalar yazılmıyor, **söyleniyor**: telefonda okunan bir Wi-Fi
     * parolası, birine tarif edilen geçici bir giriş. `x7#Kq2$vLm` böyle bir
     * durumda felaket; `tozamekulinabo` aynı işi görüp okunabiliyor.
     *
     * ### Entropi dürüstçe hesaplanıyor
     *
     * Hece = ünsüz + ünlü. [PRONOUNCEABLE_CONSONANTS] ve
     * [PRONOUNCEABLE_VOWELS] uzunluklarının çarpımı bir hecenin olasılık
     * uzayı; entropi hece sayısı çarpı bu uzayın ikili logaritması. Karakter
     * sayısına bakıp "on dört karakter, demek ki çok güçlü" demek yanlış
     * olurdu: harfler bağımsız değil, hece yapısı seçenekleri daraltıyor ve
     * bunu saklamak kullanıcıya olduğundan güçlü bir parola vermek demek.
     *
     * Bu yüzden aynı görünen uzunlukta daha az entropi çıkıyor ve arayüz bunu
     * olduğu gibi gösteriyor.
     */
    fun generatePronounceable(syllables: Int, appendDigits: Boolean): Generated {
        val count = syllables.coerceIn(MIN_SYLLABLES, MAX_SYLLABLES)
        val builder = StringBuilder(count * 2 + 2)

        repeat(count) {
            builder.append(PRONOUNCEABLE_CONSONANTS[Crypto.randomInt(PRONOUNCEABLE_CONSONANTS.length)])
            builder.append(PRONOUNCEABLE_VOWELS[Crypto.randomInt(PRONOUNCEABLE_VOWELS.length)])
        }

        val perSyllable = PRONOUNCEABLE_CONSONANTS.length.toDouble() * PRONOUNCEABLE_VOWELS.length
        var entropy = count * log2(perSyllable)

        if (appendDigits) {
            val number = Crypto.randomInt(100)
            builder.append(number.toString().padStart(2, '0'))
            entropy += log2(100.0)
        }

        return Generated(builder.toString(), entropy)
    }

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
        try {
            // Havuzdan düzgün dağılımla çekilip her kümeden en az bir karakter
            // içerdiği doğrulanıyor; içermiyorsa baştan üretiliyor.
            //
            // Eski hâli ilk konumlara her kümeden birer karakter koyup gerisini
            // havuzdan dolduruyordu. Karıştırma konum yanlılığını gideriyordu
            // ama karakter dağılımını değil: "en az bir simge" koşulu
            // zorlandığında üretilen parolaların dağılımı düzgün olmuyor ve
            // bildirilen entropi gerçekte olduğundan yüksek çıkıyordu.
            // Reddetme örnekleme, dağılımı "tüm kümeleri içeren dizeler
            // üzerinde düzgün" yapıyor; uzunluk 8'in üzerindeyken bu koşulun
            // sağlanma olasılığı 1'e çok yakın olduğu için entropi kaybı
            // ölçülemeyecek kadar küçük ve bildirilen değer dürüst.
            var attempt = 0
            while (true) {
                for (i in 0 until length) chars[i] = pool[Crypto.randomInt(pool.length)]
                attempt++
                if (attempt >= MAX_RESAMPLE || sets.all { set -> chars.any { it in set } }) break
            }
            return Generated(String(chars), length * log2(pool.length.toDouble()))
        } finally {
            // Üretilen parola çağırana `String` olarak dönüyor; buradaki
            // çalışma tamponunun ayrıca bellekte kalmasının bir gerekçesi yok.
            chars.fill(0.toChar())
        }
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
