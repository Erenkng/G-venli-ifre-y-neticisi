package app.kasa.core.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Kasadaki metin biçimli gizli veri: parola, üretici geçmişi, passkey özel anahtarı.
 *
 * ### Neden `String` değil
 *
 * `String` JVM'de değişmezdir. İçindeki karakter dizisine erişilemez, üzerine
 * yazılamaz ve çöp toplayıcının onu ne zaman gerçekten temizleyeceği
 * söylenemez. Kasa kilitlendikten sonra bile, o ana kadar açılmış her parola
 * yığında okunabilir hâlde durmayı sürdürür; bir bellek dökümü (ya da takas
 * alanına düşmüş bir sayfa) hepsini birden verir. [SecretBytes] bunu
 * anahtarlar için çözüyordu; bu sınıf aynı güvenceyi kasa içeriğine getiriyor.
 *
 * ### Neyin sözü veriliyor, neyin verilmiyor
 *
 * **Verilen:** kasa kilitlendiği anda uygulamanın elindeki hiçbir nesnede
 * okunabilir parola kalmaz — hepsi [wipe] ile sıfırlanır. Kayıt listesi,
 * parola geçmişi, üretici geçmişi ve passkey özel anahtarları aynı anda ölür.
 *
 * **Verilmeyen:** JSON çözücüsü alanı bize `String` olarak veriyor; kendi
 * ayrıştırıcımızı yazmadan bu adım atlanamaz. O `String` kopyalanır ve
 * referansı aynı ifadede düşer — yani ömrü bir sonraki çöp toplamaya kadardır.
 * Aynı şey ekranda gösterme ([reveal]) ve panoya kopyalama için de geçerli.
 * Kazanç "hiç `String` olmasın" değil, **kalıcı kopya olmasın**: kasa açık
 * kaldığı sürece bellekte duran uzun ömürlü nesne artık silinebilir bir
 * `CharArray`.
 */
@Serializable(with = SecretTextSerializer::class)
class SecretText private constructor(private val chars: CharArray) {

    /**
     * Eşleme anahtarı olarak kullanılabilmesi için içerik özeti kuruluşta
     * hesaplanıp saklanır: "aynı parola kaç kayıtta" araması bir `HashMap`
     * üzerinden dönüyor ve [wipe] sonrası içerikten yeniden hesaplanamaz.
     */
    private var cachedHash: Int = chars.contentHashCode()

    @Volatile
    private var wiped: Boolean = false

    val length: Int get() = if (wiped) 0 else chars.size

    val isWiped: Boolean get() = wiped

    fun isEmpty(): Boolean = length == 0

    fun isNotEmpty(): Boolean = length > 0

    fun isBlank(): Boolean = wiped || chars.all { it.isWhitespace() }

    fun isNotBlank(): Boolean = !isBlank()

    /**
     * Okunabilir metne çevirir.
     *
     * Burada kaçınılmaz olarak bir `String` doğar; çağrıyı gerçekten
     * gösterileceği ya da kopyalanacağı ana kadar ertele ve sonucu bir
     * değişkende tutma. Silinmiş gizli veride boş dizge döner — kilitlenme
     * anında yarışan bir arayüzü çökertmek doğru davranış olmazdı.
     */
    fun reveal(): String = if (wiped) "" else String(chars)

    /** Bağımsız bir `CharArray` kopyası; çağıran kendi kopyasını silmekle yükümlü. */
    fun copyChars(): CharArray = if (wiped) CharArray(0) else chars.copyOf()

    /** Kripto katmanına doğrudan geçirilebilen UTF-8 baytları. */
    fun toSecretBytes(): SecretBytes {
        val copy = copyChars()
        return try {
            SecretBytes.ofUtf8(copy)
        } finally {
            copy.fill(0.toChar())
        }
    }

    /** Karakterleri sıfırlar. Çağrıdan sonra bu gizli veri boş görünür. */
    fun wipe() {
        if (wiped) return
        wiped = true
        chars.fill(0.toChar())
        cachedHash = 0
    }

    /**
     * İçerik eşitliği. Yeniden kullanılan parola bulgusu buna dayanıyor.
     * Uzunluk eşitse karşılaştırma sabit zamanlı yapılır.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretText) return false
        if (wiped || other.wiped) return wiped && other.wiped
        if (chars.size != other.chars.size) return false
        var diff = 0
        for (i in chars.indices) diff = diff or (chars[i].code xor other.chars[i].code)
        return diff == 0
    }

    override fun hashCode(): Int = cachedHash

    /** Günlüğe ya da hata ayıklayıcıya asla içerik düşmesin. */
    override fun toString(): String = "SecretText(" + length + " karakter)"

    companion object {
        /** Paylaşılan boş değer. Silmek anlamsız olduğu için yan etkisi yok. */
        val EMPTY: SecretText = SecretText(CharArray(0))

        fun of(text: String): SecretText =
            if (text.isEmpty()) EMPTY else SecretText(text.toCharArray())

        /**
         * [chars] dizisini **devralır** — kopyalamaz, çağıran artık onu
         * kullanmamalı ve silmemelidir. Metin alanından gelen diziyi ikinci bir
         * kopya çıkarmadan içeri almanın yolu bu.
         */
        fun adopt(chars: CharArray): SecretText =
            if (chars.isEmpty()) EMPTY else SecretText(chars)
    }
}

/**
 * [SecretText] için JSON serileştiricisi.
 *
 * Dosyada sıradan bir dizge olarak durur — kasanın tamamı zaten tek bir
 * şifreli blob; alan başına ayrı bir şifreleme katmanı hiçbir şey eklemez,
 * yalnızca kaç gizli alan olduğunu ele verirdi. Bu serileştiricinin işi
 * biçimi değiştirmek değil, çözülen değerin **silinebilir** bir kaba
 * düşmesini sağlamak.
 */
object SecretTextSerializer : KSerializer<SecretText> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.kasa.SecretText", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SecretText) {
        encoder.encodeString(value.reveal())
    }

    override fun deserialize(decoder: Decoder): SecretText =
        SecretText.of(decoder.decodeString())
}
