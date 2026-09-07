package app.kasa.data.net

import app.kasa.core.crypto.Crypto
import app.kasa.core.crypto.SecretText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * "Have I Been Pwned" parola sızıntısı denetimi — k-anonimlik yöntemiyle.
 *
 * Parolanın kendisi hiçbir zaman cihazdan çıkmaz. Yapılan şu:
 *
 *  1. Parolanın SHA-1 özeti yerelde hesaplanır (örn. `21BD1...`).
 *  2. Sunucuya yalnızca ilk **5 karakter** gönderilir (`21BD1`).
 *  3. Sunucu o ön ekle başlayan bütün özetlerin kalanını (yaklaşık 500-1000
 *     satır) geri yollar.
 *  4. Eşleşme cihazda aranır.
 *
 * Böylece sunucu hangi parolanın sorulduğunu bilemez; elinde yalnızca
 * "bu kullanıcı şu ön ekten bir şey sordu" bilgisi kalır.
 *
 * `Add-Padding` başlığı, yanıt uzunluğundan ön ek tahmin edilmesini de
 * engellemek için rastgele dolgu ister.
 *
 * SHA-1 burada bir güvenlik ilkeli değil, yalnızca HIBP'nin veri kümesiyle
 * uyum için gereken bir dizin anahtarıdır; kasa içinde hiçbir yerde
 * kullanılmaz.
 */
class BreachChecker {

    private companion object {
        /**
         * Yanıttan okunacak en çok bayt.
         *
         * Bir aralık yanıtı en fazla birkaç bin satır, yani onlarca kilobayt;
         * `Add-Padding` dolgusuyla birlikte bile bunun yanında küçük kalıyor.
         * Sınır cömert ama sınırsız değil: `body.string()` gövdenin tamamını
         * belleğe alıyor ve "uç nokta TLS ile doğrulanıyor" bir boyut sözü
         * vermiyor. Kaynağın davranışına güvenmek yerine okunan miktarı
         * sınırlamak, ödenen bedeli sıfır tutuyor.
         */
        const val MAX_BODY = 4L * 1024 * 1024
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Parolanın SHA-1 özeti (büyük harfli onaltılık).
     *
     * Dışarı açık çünkü ön eke göre gruplama çağıranın işi: aynı ön ekteki
     * parolalar tek bir istekle çözülüyor ve bunu yapabilmek için özetin
     * kendisine erişmek gerekiyor. Özet gizli değil — gizli olan hangi
     * parolaya ait olduğu ve o bilgi cihazdan çıkmıyor.
     */
    fun hashOf(password: SecretText): String {
        // Parola baytları özet alındıktan hemen sonra sıfırlanıyor.
        val bytes = password.toSecretBytes()
        return try {
            Crypto.sha1Hex(bytes.raw())
        } finally {
            bytes.wipe()
        }
    }

    /**
     * Bir ön ekin tamamını indirir: kalan özet → görülme sayısı.
     *
     * ### Neden parola başına değil ön ek başına
     *
     * Tarama her parola için ayrı bir istek atıyordu. Dört yüz kayıtlı bir
     * kasada bu, ardışık dört yüz gidiş-dönüş demekti; üstelik aynı parola
     * birden çok kayıtta olabildiği için (tekrar kullanım bulgusu bunu zaten
     * söylüyor) çoğu istek aynı cevabı getiriyordu.
     *
     * Asıl mesele hız da değil: HIBP zaten k-anonimlik ile çalışıyor ve bir
     * ön eki sorunca o ön ekle başlayan **bütün** özetler geliyor. Aynı ön eki
     * on kez sormak sunucuya "bu kullanıcının bu ön ekte on parolası var"
     * demek — k-anonimliğin sakladığı şeyi trafik deseni sızdırıyordu.
     *
     * @return kalan özet → sayı eşlemesi, ağ hatasında `null`
     */
    suspend fun range(prefix: String): Map<String, Int>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.pwnedpasswords.com/range/$prefix")
            .header("Add-Padding", "true")
            .header("User-Agent", "Kasa-Android")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.peekBody(MAX_BODY).string()
                val map = HashMap<String, Int>()
                for (line in body.lineSequence()) {
                    val separator = line.indexOf(':')
                    if (separator <= 0) continue
                    val count = line.substring(separator + 1).trim().toIntOrNull() ?: continue
                    // Dolgu satırlarının sayacı 0'dır; onlar eşleşme sayılmaz
                    // ama haritada durmalarının da zararı yok — arayan taraf
                    // bulamazsa zaten 0 kabul ediyor.
                    map[line.substring(0, separator).uppercase()] = count
                }
                map
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * @return parolanın sızıntılarda kaç kez görüldüğü, ağ hatasında `null`.
     *
     * Tek bir parola için; toplu tarama [range] üzerinden gidiyor.
     */
    suspend fun timesSeen(password: SecretText): Int? = withContext(Dispatchers.IO) {
        if (password.isBlank()) return@withContext 0
        val hash = hashOf(password)
        val prefix = hash.substring(0, 5)
        val suffix = hash.substring(5)

        val request = Request.Builder()
            .url("https://api.pwnedpasswords.com/range/$prefix")
            .header("Add-Padding", "true")
            .header("User-Agent", "Kasa-Android")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.peekBody(MAX_BODY).string()
                for (line in body.lineSequence()) {
                    val separator = line.indexOf(':')
                    // Uzunluk eşitliği şart. Eskiden yalnızca satırın ':' öncesi
                    // kadarı karşılaştırılıyordu; kısa (bozuk ya da kötü niyetli)
                    // bir satır, kendi ön ekiyle başlayan her parolayı "sızmış"
                    // gösterebilirdi.
                    if (separator != suffix.length) continue
                    if (line.regionMatches(0, suffix, 0, separator, ignoreCase = true)) {
                        val count = line.substring(separator + 1).trim().toIntOrNull() ?: 0
                        // Dolgu satırlarının sayacı 0'dır; onlar eşleşme sayılmaz.
                        return@withContext count
                    }
                }
                0
            }
        } catch (t: Throwable) {
            null
        }
    }
}
