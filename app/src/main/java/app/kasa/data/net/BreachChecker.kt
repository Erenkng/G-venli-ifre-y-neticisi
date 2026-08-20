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

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * @return parolanın sızıntılarda kaç kez görüldüğü, ağ hatasında `null`.
     */
    suspend fun timesSeen(password: SecretText): Int? = withContext(Dispatchers.IO) {
        if (password.isBlank()) return@withContext 0
        // Parola baytları özet alındıktan hemen sonra sıfırlanıyor; ağ katmanına
        // giden tek şey özetin ilk beş onaltılık hanesi.
        val bytes = password.toSecretBytes()
        val hash = try {
            Crypto.sha1Hex(bytes.raw())
        } finally {
            bytes.wipe()
        }
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
                val body = response.body?.string() ?: return@withContext null
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
