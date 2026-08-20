package app.kasa.data.model

/**
 * Kart numarasından ödeme ağını tanır.
 *
 * ### Neden logo değil, isim
 *
 * Visa, Mastercard, Troy gibi işaretler tescilli markalar ve kullanımları
 * marka kılavuzlarına bağlı. Uygulama bunları **çizmiyor**; kartın yüzünde
 * ağın adı yazıyor ve arka plan o ağın bilinen renk ailesinden geliyor.
 * Kullanıcı kartı bir bakışta tanıyor, uygulama da başkasının işaretini
 * kopyalamış olmuyor.
 *
 * ### Tanıma sırası
 *
 * Aralıklar dardan genişe doğru sınanıyor; tersi olsaydı `4` ile başlayan
 * her numara Visa sayılır ve daha uzun ön ekler hiç sıraya gelmezdi.
 *
 * ### Troy
 *
 * Türkiye'nin ulusal kart şeması. Yurt içi kartları `9792` ile başlıyor.
 * Uluslararası kullanımdaki `65` ön eki Discover ile ortak olduğu için
 * oraya Discover atanıyor: bir Türk kullanıcı için Troy'u öne almak
 * cazip görünüyor ama yanlış tanıma, tanımamaktan kötü — kart yüzünde
 * yanlış ağ adı yazması kullanıcıya yanlış bilgi vermek olur.
 */
enum class CardBrand(
    val displayName: String,
    /** Kart yüzündeki degradenin iki ucu. */
    val startColor: Long,
    val endColor: Long,
    /** Numara gruplama deseni: her sayı bir öbeğin uzunluğu. */
    val grouping: List<Int>,
    /** Güvenlik kodunun hane sayısı. */
    val cvvLength: Int
) {
    VISA("Visa", 0xFF1F2A6E, 0xFF3B52B4, listOf(4, 4, 4, 4), 3),
    MASTERCARD("Mastercard", 0xFFB43318, 0xFFE8871E, listOf(4, 4, 4, 4), 3),
    AMEX("American Express", 0xFF0F7FB8, 0xFF0B4E73, listOf(4, 6, 5), 4),
    TROY("Troy", 0xFF00909A, 0xFF00505F, listOf(4, 4, 4, 4), 3),
    DISCOVER("Discover", 0xFFE0701A, 0xFF9E4109, listOf(4, 4, 4, 4), 3),
    JCB("JCB", 0xFF0B4EA2, 0xFF7A1B3A, listOf(4, 4, 4, 4), 3),
    UNIONPAY("UnionPay", 0xFFB01228, 0xFF0B3B8C, listOf(4, 4, 4, 4), 3),
    DINERS("Diners Club", 0xFF00679E, 0xFF01405F, listOf(4, 6, 4), 3),
    MAESTRO("Maestro", 0xFF0B4EA2, 0xFFB01228, listOf(4, 4, 4, 4), 3),

    /** Tanınmayan ya da henüz yeterince hane girilmemiş kart. */
    UNKNOWN("Kart", 0xFF33474F, 0xFF16232A, listOf(4, 4, 4, 4), 3);

    companion object {

        /**
         * Numaranın ön ekinden ağı bulur. Rakam dışındaki her şey yok sayılıyor,
         * çünkü kullanıcı numarayı boşluklu ya da tireli girmiş olabilir.
         */
        fun detect(cardNumber: String): CardBrand {
            val digits = cardNumber.filter { it.isDigit() }
            if (digits.length < 2) return UNKNOWN

            fun startsWith(vararg prefixes: String) = prefixes.any { digits.startsWith(it) }
            fun inRange(length: Int, from: Int, to: Int): Boolean {
                if (digits.length < length) return false
                val head = digits.take(length).toIntOrNull() ?: return false
                return head in from..to
            }

            return when {
                startsWith("34", "37") -> AMEX
                startsWith("9792") -> TROY
                startsWith("6011") -> DISCOVER
                inRange(3, 644, 649) -> DISCOVER
                startsWith("65") -> DISCOVER
                inRange(4, 3528, 3589) -> JCB
                inRange(3, 300, 305) -> DINERS
                startsWith("3095", "36", "38", "39") -> DINERS
                startsWith("5018", "5020", "5038", "5893", "6304", "6759", "6761", "6762", "6763") -> MAESTRO
                inRange(2, 51, 55) -> MASTERCARD
                inRange(4, 2221, 2720) -> MASTERCARD
                startsWith("62") -> UNIONPAY
                digits.startsWith("4") -> VISA
                else -> UNKNOWN
            }
        }

        /**
         * Numarayı ağın kendi desenine göre öbekler: Visa 4-4-4-4, Amex 4-6-5.
         * Fazla haneler son öbeğe ekleniyor; kullanıcının yazdığı hiçbir rakam
         * gösterimde kaybolmuyor.
         */
        fun group(cardNumber: String, brand: CardBrand = detect(cardNumber)): String {
            val digits = cardNumber.filter { it.isDigit() }
            if (digits.isEmpty()) return ""
            val chunks = mutableListOf<String>()
            var index = 0
            for (size in brand.grouping) {
                if (index >= digits.length) break
                chunks += digits.substring(index, minOf(index + size, digits.length))
                index += size
            }
            if (index < digits.length) chunks += digits.substring(index)
            return chunks.joinToString(" ")
        }

        /**
         * Son dört hane dışındaki her şeyi maskeler ve ağın desenine göre öbekler.
         *
         * Kart yüzünde varsayılan gösterim bu: ekranda açık numara durması,
         * omuz üstünden bakan biri için kartı doğrudan kullanılabilir kılar.
         */
        fun mask(cardNumber: String, brand: CardBrand = detect(cardNumber)): String {
            val digits = cardNumber.filter { it.isDigit() }
            if (digits.length <= 4) return digits
            val hidden = "•".repeat(digits.length - 4) + digits.takeLast(4)
            val chunks = mutableListOf<String>()
            var index = 0
            for (size in brand.grouping) {
                if (index >= hidden.length) break
                chunks += hidden.substring(index, minOf(index + size, hidden.length))
                index += size
            }
            if (index < hidden.length) chunks += hidden.substring(index)
            return chunks.joinToString(" ")
        }

        /**
         * Luhn sağlaması.
         *
         * Kart numaralarının son hanesi, önceki hanelerden hesaplanan bir
         * kontrol rakamı. Tek başına kartın gerçek olduğunu söylemiyor ama
         * **yazım hatasını** yakalıyor: tek hane yanlış ya da iki komşu hane
         * yer değiştirmişse sağlama tutmuyor. Kart numarasını elle giren
         * kullanıcıya bunu söylemek, yanlış kaydedilmiş bir kartı aylar sonra
         * ödeme anında keşfetmesine engel oluyor.
         *
         * Hane sayısı 12'nin altındaysa henüz yazılmakta olan bir numara
         * sayılıp `null` dönüyor: yarım girdiye "geçersiz" demek, kullanıcıyı
         * yazarken sürekli kırmızı görmeye mahkûm ederdi.
         */
        fun luhnValid(cardNumber: String): Boolean? {
            val digits = cardNumber.filter { it.isDigit() }
            if (digits.length < 12) return null
            var sum = 0
            var double = false
            for (i in digits.indices.reversed()) {
                var value = digits[i] - '0'
                if (double) {
                    value *= 2
                    if (value > 9) value -= 9
                }
                sum += value
                double = !double
            }
            return sum % 10 == 0
        }
    }
}
