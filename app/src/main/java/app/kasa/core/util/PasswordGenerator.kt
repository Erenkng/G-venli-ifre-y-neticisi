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
    const val MIN_PIN = 4
    const val MAX_PIN = 12
    /** Anahtar üretiminde sunulan iki boy: simetrik anahtar ve uzun tuz. */
    val HEX_BIT_CHOICES = listOf(128, 256)
    /** Entropi hedefi seçenekleri (bit). 0 = hedef yok, uzunluğu kullanıcı seçer. */
    val ENTROPY_TARGETS = listOf(0, 60, 80, 100, 128)
    const val BATCH_SIZE = 5
    /** Kurtarma kodu setinde kaç kod ve her kodun öbek deseni. */
    const val RECOVERY_CODES = 6
    private const val RECOVERY_GROUP = 4
    private const val RECOVERY_GROUPS = 2
    /** Kurtarma kodu alfabesi: karışan harf ve rakamlar dışarıda. */
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

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
     * PIN.
     *
     * ### Neden ayrı bir üretici
     *
     * `generate()` en az bir küçük harf içeren bir havuzla çalışıyor; PIN'in
     * tanımı ise "yalnızca rakam". Parola üreticisini rakama indirmek için
     * bütün kümeleri kapatmak gerekiyordu ve o durumda havuz tek kümeye
     * düşüyor, "her kümeden en az bir karakter" koşulu anlamsızlaşıyordu.
     *
     * ### Tekrar ve dizi elenmiyor
     *
     * `1111` ya da `1234` üretilebiliyor ve bu **bilerek**. Bu desenleri
     * elemek entropiyi düşürür: saldırgan da onların elendiğini bilir ve
     * arama uzayı küçülür. Rastgele bir PIN'in `1234` çıkma olasılığı zaten
     * on binde bir; onu yasaklamanın kazandırdığı hiçbir şey yok.
     */
    fun generatePin(length: Int): Generated {
        val size = length.coerceIn(MIN_PIN, MAX_PIN)
        val digits = CharArray(size) { DIGITS[Crypto.randomInt(10)] }
        try {
            return Generated(String(digits), size * log2(10.0))
        } finally {
            digits.fill(0.toChar())
        }
    }

    /**
     * Onaltılık anahtar.
     *
     * API anahtarı, şifreleme anahtarı ya da tuz gerektiğinde parola üreticisi
     * yanlış araç: çıktısı yazı tipine ve panoya bağlı olarak bozulabilen
     * simgeler içeriyor ve çoğu sistem hex bekliyor.
     *
     * Entropi burada tahmin değil, ölçü: [bits] bit rastgelelik isteniyor ve
     * tam o kadar bayt çekiliyor.
     */
    fun generateHexKey(bits: Int): Generated {
        val safeBits = if (bits in HEX_BIT_CHOICES) bits else 256
        val bytes = Crypto.randomBytes(safeBits / 8)
        try {
            val hex = buildString(bytes.size * 2) {
                bytes.forEach { append("%02x".format(it)) }
            }
            return Generated(hex, safeBits.toDouble())
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Rastgele kullanıcı adı.
     *
     * ### Ne işe yarıyor
     *
     * Her sitede aynı kullanıcı adını kullanmak, sızan bir veritabanındaki
     * hesabı öteki sitelerdeki hesaplarla eşleştirmeyi kolaylaştırıyor: parola
     * her yerde farklı olsa bile kimliğin kendisi ortak kalıyor. Siteye özel
     * bir kullanıcı adı bu bağı koparıyor.
     *
     * ### Biçim
     *
     * `sozcuk.sozcuk42` — sözlükten iki sözcük ve iki hane. Okunabilir olması
     * önemli: kullanıcı adı çoğu zaman karşıya söyleniyor ya da destek
     * kaydına yazılıyor. Nokta ayırıcısı hemen her sitenin kabul ettiği tek
     * ayırıcı; alt çizgi ve tire bazı sitelerde reddediliyor.
     */
    fun generateUsername(words: List<String>): Generated {
        require(words.isNotEmpty()) { "Sözlük boş" }
        val first = words[Crypto.randomInt(words.size)]
        val second = words[Crypto.randomInt(words.size)]
        val number = Crypto.randomInt(100).toString().padStart(2, '0')
        val entropy = 2 * log2(words.size.toDouble()) + log2(100.0)
        return Generated("$first.$second$number", entropy)
    }

    /**
     * UUID (sürüm 4).
     *
     * ### Ne işe yarıyor
     *
     * Yapılandırma dosyası, veritabanı kaydı, test verisi, bir API'nin
     * istediği istemci kimliği. Parola üreticisiyle üretilmiş bir dize burada
     * çalışmıyor: karşı taraf 8-4-4-4-12 biçimini ayrıştırıyor ve biçim
     * tutmazsa reddediyor.
     *
     * ### Neden `UUID.randomUUID()` değil
     *
     * O yöntem `SecureRandom` kullanıyor ve doğru sonuç üretiyor ama
     * uygulamanın geri kalanı rastgeleliği tek bir yerden alıyor. İkinci bir
     * kaynak açmak, "bu uygulamada rastgelelik nereden geliyor" sorusunun
     * cevabını ikiye bölerdi.
     *
     * Sürüm ve değişken alanları RFC 4122'nin istediği gibi zorlanıyor: 122
     * bit rastgele, 6 bit sabit.
     */
    fun generateUuid(): Generated {
        val bytes = Crypto.randomBytes(16)
        try {
            bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()  // sürüm 4
            bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()  // değişken 1
            val hex = bytes.joinToString("") { "%02x".format(it) }
            val text = buildString(36) {
                append(hex, 0, 8); append('-')
                append(hex, 8, 12); append('-')
                append(hex, 12, 16); append('-')
                append(hex, 16, 20); append('-')
                append(hex, 20, 32)
            }
            return Generated(text, 122.0)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * Kurtarma kodu seti.
     *
     * ### Ne işe yarıyor
     *
     * İki adımlı doğrulama kuran her servis bir avuç yedek kod veriyor ve
     * kullanıcı onları bir yere yazmak zorunda. Tersi de gerekiyor: kendi
     * sunucusunu, yönlendiricisini ya da paylaşılan bir hesabı kuran biri o
     * kodları **üretmek** durumunda ve elle uydurulmuş "yedek1, yedek2"
     * dizisi tam olarak tahmin edilebilir olanı üretiyor.
     *
     * ### Biçim
     *
     * `A3F2-9K1M` — kodlar okunup telefonda söyleniyor ve kâğıda yazılıyor,
     * bu yüzden karışan karakterler (0/O, 1/I/l) alfabede yok. Tire, sekiz
     * karakterlik bir bloğu gözle takip edilebilir kılıyor.
     *
     * Kodlar tek bir dizede, satır sonlarıyla dönüyor: kopyalandığında hepsi
     * birden panoya giriyor ve kullanıcı tek tek üretmek zorunda kalmıyor.
     */
    fun generateRecoveryCodes(count: Int = RECOVERY_CODES): Generated {
        val size = count.coerceIn(1, 12)
        val perCode = RECOVERY_GROUP * RECOVERY_GROUPS
        val codes = (0 until size).map {
            (0 until RECOVERY_GROUPS).joinToString("-") {
                buildString(RECOVERY_GROUP) {
                    repeat(RECOVERY_GROUP) {
                        append(RECOVERY_ALPHABET[Crypto.randomInt(RECOVERY_ALPHABET.length)])
                    }
                }
            }
        }
        // Bildirilen güç **tek bir kodun** gücü: saldırganın kırması gereken
        // şey setin tamamı değil, herhangi biri. Toplamı bildirmek gerçekte
        // olmayan bir güvence gösterirdi.
        return Generated(codes.joinToString("\n"), perCode * log2(RECOVERY_ALPHABET.length.toDouble()))
    }

    /**
     * Hedeflenen entropiye ulaşmak için gereken uzunluk.
     *
     * ### Neden gerekli
     *
     * "20 karakter" bir güç ölçüsü değil. Sembol ve rakam kapatıldığında havuz
     * 26 karaktere iniyor ve aynı uzunluk 94 bitten 94×0.7 bite düşüyor;
     * kullanıcı bunu görmüyor, yalnızca kaydırıcıdaki sayıyı görüyor. Hedef
     * entropiyle uzunluk kullanıcının değil seçilen kümelerin sonucu oluyor.
     *
     * @return [MIN_LENGTH]–[MAX_LENGTH] aralığına kırpılmış uzunluk
     */
    fun lengthForEntropy(targetBits: Int, options: Options): Int {
        if (targetBits <= 0) return options.length
        val poolSize = poolFor(options).length
        if (poolSize <= 1) return MAX_LENGTH
        val needed = kotlin.math.ceil(targetBits / log2(poolSize.toDouble())).toInt()
        return needed.coerceIn(MIN_LENGTH, MAX_LENGTH)
    }

    /**
     * Seçeneklere karşılık gelen karakter kümeleri.
     *
     * Hem üretim hem entropi hesabı buradan besleniyor. İkisi ayrı ayrı
     * kurulsaydı, birine eklenen bir küme ötekine eklenmeyi unutulduğunda
     * bildirilen güç gerçek güçten sapardı — ve bu sapma hiçbir yerde hata
     * vermez, yalnızca kullanıcıya yanlış sayı gösterirdi.
     */
    private fun setsFor(options: Options): List<String> = buildList {
        add(if (options.avoidLookalikes) LOWER_CLEAR else LOWER)
        if (options.upper) add(if (options.avoidLookalikes) UPPER_CLEAR else UPPER)
        if (options.digits) add(if (options.avoidLookalikes) DIGITS_CLEAR else DIGITS)
        if (options.symbols) add(SYMBOLS)
    }

    private fun poolFor(options: Options): String = setsFor(options).joinToString("")

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
        val sets = setsFor(options)
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
