package app.kasa.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Rakam öbekleyen görsel dönüşüm: kart numarasına boşluk, son kullanma
 * tarihine eğik çizgi koyar.
 *
 * ### Neden ayırıcıyı değere yazmıyoruz
 *
 * En kolay yol, kullanıcı yazarken değerin kendisine boşluk eklemek olurdu.
 * O yol her seferinde aynı yerde kırılıyor: kullanıcı imleci ortaya götürüp
 * bir hane siliyor, silinen şey ayırıcı oluyor, imleç zıplıyor ve yazdığı
 * rakam yanlış öbeğe düşüyor. Kaydedilen değer de ayırıcı taşıdığı için
 * arama, kopyalama ve Luhn sağlaması ayrıca temizlemek zorunda kalıyor.
 *
 * Burada saklanan değer **yalnızca rakam**; ayırıcılar sadece çizilirken
 * araya giriyor. Silme, imleç ve seçim hep rakamlar üzerinde çalışıyor.
 *
 * ### Öbek deseni türe göre değişiyor
 *
 * Visa 4-4-4-4 yazılıyor ama American Express 4-6-5. Sabit dörtlü öbekleme,
 * Amex kartını kullanıcının elindeki karttan farklı gösterirdi ve numarayı
 * karşılaştırmak zorlaşırdı. Desen [CardBrand] tarafından veriliyor.
 *
 * Desendeki öbekler bittiğinde kalan haneler ayırıcısız ekleniyor: fazla
 * hane yazan kullanıcının rakamı kaybolmuyor, yalnızca öbeklenmiyor.
 *
 * @param groups her öbeğin uzunluğu, sırayla
 * @param separator öbekler arasına konacak karakter
 */
class GroupedDigitsTransformation(
    private val groups: List<Int>,
    private val separator: Char = ' '
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val rendered = buildString(digits.length + groups.size) {
            var index = 0
            for (size in groups) {
                if (index >= digits.length) break
                if (index > 0) append(separator)
                append(digits, index, minOf(index + size, digits.length))
                index += size
            }
            if (index < digits.length) append(digits, index, digits.length)
        }

        return TransformedText(AnnotatedString(rendered), Mapping(groups, digits.length))
    }

    /**
     * İmleç eşlemesi.
     *
     * İki yön de ayrı ayrı yazılmak zorunda: Compose imleci ham metinde
     * tutuyor ama çizilen metinde gösteriyor. Eşleme yanlışsa alan çalışıyor
     * gibi görünüyor ve yalnızca kullanıcı imleci ortaya götürdüğünde
     * bozuluyor — yani en zor fark edilen hata biçimi.
     */
    private class Mapping(
        private val groups: List<Int>,
        private val digitCount: Int
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            var separators = 0
            var boundary = 0
            for (size in groups) {
                boundary += size
                if (offset > boundary) separators++ else break
            }
            return offset + separators
        }

        override fun transformedToOriginal(offset: Int): Int {
            var separators = 0
            var boundary = 0
            for (size in groups) {
                boundary += size
                // Ayırıcının çizilen metindeki yeri: öbek sonu + o ana kadarki
                // ayırıcı sayısı.
                if (offset > boundary + separators) separators++ else break
            }
            return (offset - separators).coerceIn(0, digitCount)
        }
    }
}

/**
 * Son kullanma tarihi: `AA/YY`.
 *
 * Kullanıcı dört rakam yazıyor, eğik çizgiyi uygulama koyuyor. Öncesinde
 * çizgiyi kullanıcının yazması bekleniyordu ve yazmayanın kaydı `1226`
 * olarak duruyordu — kart yüzünde okunamayan, dışa aktarıldığında da
 * ayrıştırılamayan bir değer.
 */
val ExpiryTransformation = GroupedDigitsTransformation(listOf(2, 2), '/')

/** Alandaki her şeyi eleyip yalnızca rakamları, en fazla [max] tane bırakır. */
fun digitsOnly(raw: String, max: Int): String = raw.filter { it.isDigit() }.take(max)

/**
 * `AAYY` değerini insan okuyacak biçime çevirir.
 *
 * Kart yüzünde ve ayrıntı sayfasında kullanılıyor. Yarım girilmiş bir tarih
 * (`12`) olduğu gibi dönüyor; eksik veriye eğik çizgi eklemek, girilmemiş yılı
 * girilmiş gibi gösterirdi.
 */
fun formatExpiry(value: String): String {
    val digits = value.filter { it.isDigit() }
    return when {
        digits.length <= 2 -> digits
        else -> digits.take(2) + "/" + digits.drop(2).take(2)
    }
}

/**
 * Son kullanma tarihi geçmiş mi.
 *
 * Kart, son kullanma ayının **sonuna kadar** geçerli: 12/26 yazan bir kart
 * 31 Aralık 2026 akşamına kadar çalışıyor. Ayın ilk gününde "geçti" demek,
 * kullanıcıya hâlâ kullanabileceği bir kartı sildirirdi.
 *
 * @return tarih okunamıyorsa `null`
 */
fun expiryExpired(value: String, nowYear: Int, nowMonth: Int): Boolean? {
    val digits = value.filter { it.isDigit() }
    if (digits.length < 4) return null
    val month = digits.take(2).toIntOrNull() ?: return null
    val year = digits.drop(2).take(2).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    // İki haneli yıl: 2000'li yıllar. Kart son kullanma tarihleri geçmişe
    // dönük yazılmıyor, bu yüzden yüzyıl belirsizliği pratikte yok.
    val fullYear = 2000 + year
    return fullYear < nowYear || (fullYear == nowYear && month < nowMonth)
}
