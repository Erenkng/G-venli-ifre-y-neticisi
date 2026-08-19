package app.kasa.core.util

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Parola gücü ölçümü.
 *
 * Salt "büyük harf + rakam + sembol var mı" denetimi yanıltıcıdır: `Passw0rd!`
 * bu ölçüte göre kusursuzdur ama saniyede kırılır. Burada ham entropiden
 * başlanıp gerçek saldırganın kullandığı örüntüler için ceza kesilir:
 * sözlük parçaları, klavye dizileri, yinelenen karakterler, yıl sayıları ve
 * yaygın parolalar.
 */
object PasswordStrength {

    enum class Tone { WEAK, MID, STRONG }

    data class Result(
        /** Ceza sonrası etkin entropi (bit). */
        val entropyBits: Double,
        /** 0..1 arası, arayüzün kullandığı normalize edilmiş güç. */
        val score: Float,
        val tone: Tone
    )

    /**
     * Saldırganın saniyede yapabileceği çevrimdışı deneme sayısı.
     * Modern GPU çiftliği, hızlı bir özet fonksiyonu varsayımıyla.
     */
    private const val GUESSES_PER_SECOND = 1e11

    private val COMMON = setOf(
        "123456", "123456789", "12345678", "password", "qwerty", "111111", "12345",
        "123123", "1234567890", "1234567", "000000", "abc123", "iloveyou", "admin",
        "monkey", "dragon", "letmein", "welcome", "login", "princess", "sunshine",
        "master", "hello", "freedom", "whatever", "trustno1", "passw0rd", "qwerty123",
        "sifre", "parola", "sifre123", "galatasaray", "fenerbahce", "besiktas",
        "trabzonspor", "istanbul", "ankara", "izmir", "turkiye", "merhaba", "seni",
        "asdasd", "asd123", "qazwsx", "zxcvbn", "1q2w3e4r", "qweasd", "159357"
    )

    private val KEYBOARD_ROWS = listOf(
        "qwertyuiopgu", "asdfghjkl", "zxcvbnm",
        "qwertyuiopğü", "asdfghjklşi", "zxcvbnmöç",
        "1234567890", "0987654321"
    )

    fun evaluate(password: String): Result {
        if (password.isEmpty()) return Result(0.0, 0f, Tone.WEAK)

        val raw = rawEntropy(password)
        val penalty = patternPenalty(password)
        val effective = max(0.0, raw - penalty)

        // 28 bit altı pratikte anlık; 110 bit ve üstü tam puan.
        val score = ((effective - 28.0) / 82.0).coerceIn(0.0, 1.0).toFloat()
        val tone = when {
            effective < 45 -> Tone.WEAK
            effective < 70 -> Tone.MID
            else -> Tone.STRONG
        }
        return Result(effective, score, tone)
    }

    /** Karakter havuzunun büyüklüğünden gelen kuramsal üst sınır. */
    private fun rawEntropy(password: String): Double {
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) pool += 33
        if (password.any { it.isWhitespace() }) pool += 1
        // Türkçe ve diğer ASCII dışı harfler
        if (password.any { it.code > 127 }) pool += 20
        if (pool == 0) pool = 26
        return password.length * log2(pool.toDouble())
    }

    /**
     * Tahmin edilebilir yapı için düşülen bit sayısı. Değerler zxcvbn'in
     * yaklaşımına dayanır ama telefonda sözlük taşımadan çalışır.
     */
    private fun patternPenalty(password: String): Double {
        val lower = password.lowercase(java.util.Locale.ROOT)
        var penalty = 0.0

        // Yaygın parola ya da onun küçük bir varyantı
        for (common in COMMON) {
            if (lower == common) return rawEntropy(password) - 4.0
            if (lower.contains(common)) {
                penalty += log2(common.length.toDouble()) * 3.5
            }
        }

        // Klavye dizileri: "asdf", "qwerty", "1234"
        for (row in KEYBOARD_ROWS) {
            var run = 0
            for (i in lower.indices) {
                val idx = row.indexOf(lower[i])
                val prevIdx = if (i > 0) row.indexOf(lower[i - 1]) else -99
                if (idx >= 0 && prevIdx >= 0 && idx - prevIdx == 1) run++ else run = 0
                if (run >= 2) penalty += 2.5
            }
        }

        // Yinelenen karakter: "aaaa", "!!!!"
        var repeat = 1
        for (i in 1 until password.length) {
            if (password[i] == password[i - 1]) {
                repeat++
                penalty += min(repeat.toDouble(), 6.0)
            } else repeat = 1
        }

        // Yıl gibi görünen dört haneli sayılar
        Regex("(19|20)\\d{2}").findAll(password).forEach { penalty += 8.0 }

        // Sona eklenmiş klasik "1" ya da "!" ekleri
        if (Regex(".*[a-zA-Z]+[0-9]{1,4}[!.?]?$").matches(password)) penalty += 6.0

        // Yalnızca rakam
        if (password.all { it.isDigit() }) penalty += password.length * 1.2

        // Aynı harfin küçük/büyük varyantından ibaret basit büyük harf kullanımı
        if (password.isNotEmpty() && password[0].isUpperCase() && password.drop(1).none { it.isUpperCase() }) {
            penalty += 1.5
        }
        return penalty
    }

    /** Sözcük dizisi (passphrase) için entropi: sözlük büyüklüğüne dayanır. */
    fun passphraseEntropy(wordCount: Int, dictionarySize: Int, extras: Double = 0.0): Double =
        wordCount * log2(dictionarySize.toDouble()) + extras

    /** Verilen entropinin çevrimdışı kaba kuvvetle kırılma süresi (saniye). */
    fun crackSeconds(entropyBits: Double): Double =
        2.0.pow(entropyBits - 1) / GUESSES_PER_SECOND
}
