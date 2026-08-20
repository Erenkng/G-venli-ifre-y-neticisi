package app.kasa.autofill

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bir alan adının hangi Android uygulamalarına giriş bilgisi vermeye izin
 * verdiğini doğrular.
 *
 * ### Kapatılan açık
 *
 * Kasadaki kayıtların çoğu bir **alan adıyla** anılıyor (tarayıcıda
 * kaydedildiği için). Sonradan aynı hesabın uygulaması kurulduğunda o uygulama
 * doldurma istiyor ve elimizde yalnızca paket adı oluyor. Paket adının alan
 * adıyla bir ilgisi olduğunu gösteren hiçbir şey yok — `com.evil.garanti` da
 * bir paket adı.
 *
 * Alan adı sahibi bu bağı kendi sunucusunda ilan ediyor:
 * `https://<alan adı>/.well-known/assetlinks.json`. İçinde `get_login_creds`
 * ilişkisiyle listelenen paket adı **ve imza parmak izi**, o alan adının
 * kimlik bilgilerini almaya yetkili. Parmak izi olmadan liste işe yaramazdı:
 * aynı paket adıyla başka bir imza taşıyan bir APK de "com.bankam.app" olurdu.
 *
 * ### Neden `get_login_creds`, `handle_all_urls` değil
 *
 * `handle_all_urls`, uygulamanın o alan adının bağlantılarını **açmasına**
 * izin veriyor; parola paylaşımıyla ilgisi yok. Bir alan adı sahibi
 * bağlantılarını bir ortağın uygulamasına yönlendirmiş olabilir ve bu, o
 * ortağa parolaları vermeye razı olduğu anlamına gelmez. Doğru ilişki
 * `delegate_permission/common.get_login_creds` — adı zaten bunu söylüyor.
 *
 * ### Gizlilik bedeli açıkça söylenmeli
 *
 * Bu sorgu, alan adının sunucusuna "bu IP'de bu alan adı için kayıtlı bir
 * kimlik bilgisi var" diyor. Bilginin gittiği yer, kimlik bilgisinin zaten ait
 * olduğu taraf — alınabilecek en az kötü alıcı. Yine de bir ağ isteği ve
 * kapatılabilir olması gerekiyor; kapatıldığında uygulama eşleşmesi yalnızca
 * kullanıcının elle kurduğu bağlarla çalışıyor.
 *
 * ### Önbellek
 *
 * Sonuç diske yazılıyor. Olumlu sonuç bir hafta, olumsuz sonuç bir gün
 * geçerli. Olumsuzun kısa tutulmasının sebebi: alan adı sahibi dosyayı yeni
 * eklemiş olabilir ve kullanıcı bir hafta boyunca "çalışmıyor" görmemeli.
 * Olumlunun uzun tutulmasının sebebi de gizlilik — her doldurmada bir istek
 * atmak, kullanıcının hangi uygulamayı ne zaman açtığını sunucuya bildirmek
 * olurdu.
 */
class DigitalAssetLinks(private val context: Context) {

    @Serializable
    private data class CacheEntry(
        val packages: List<String>,
        val fetchedAt: Long,
        val ok: Boolean
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false) // yönlendirme, alan adı sahibinin beyanı değil
            .build()
    }

    private val cacheFile: File get() = File(context.filesDir, "kasa/assetlinks.json")

    private val memory = HashMap<String, CacheEntry>()

    /**
     * [domain] alan adı, [caller] uygulamasına giriş bilgisi vermeye izin
     * veriyor mu?
     *
     * Parmak izi okunamamışsa doğrulama yapılmadan `false` dönüyor: paket
     * adına tek başına güvenmek, bu sınıfın var olma sebebini ortadan
     * kaldırırdı.
     */
    suspend fun allows(domain: String, caller: CallerIdentity): Boolean {
        val pkg = caller.packageName ?: return false
        val cert = caller.certSha256 ?: return false
        val token = "$pkg|$cert"

        cached(domain)?.let { return token in it.packages }

        val entry = withContext(Dispatchers.IO) { fetch(domain) }
        store(domain, entry)
        return token in entry.packages
    }

    private fun cached(domain: String): CacheEntry? {
        val entry = memory[domain] ?: readCache()[domain] ?: return null
        val age = System.currentTimeMillis() - entry.fetchedAt
        val ttl = if (entry.ok) POSITIVE_TTL else NEGATIVE_TTL
        return if (age in 0..ttl) entry else null
    }

    private fun fetch(domain: String): CacheEntry {
        val url = "https://$domain/.well-known/assetlinks.json"
        val empty = CacheEntry(emptyList(), System.currentTimeMillis(), ok = false)

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Kasa")
                // Sunucu bizi tanımasın diye çerez ya da başka bir kimlik
                // taşınmıyor; OkHttp varsayılanı zaten çerez tutmuyor.
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching empty
                val body = response.body?.string().orEmpty()
                if (body.length > MAX_BODY) return@runCatching empty
                CacheEntry(parse(body), System.currentTimeMillis(), ok = true)
            }
        }.getOrDefault(empty)
    }

    /**
     * Beyan listesinden yetkili `paket|parmak izi` çiftlerini çıkarır.
     *
     * Ayrıştırma bilerek katı: beklenen alanların biri eksikse o girdi
     * atlanıyor. Bir güven beyanını "muhtemelen bunu demek istemiş" diye
     * yorumlamak, güven beyanı olmaktan çıkarır.
     */
    private fun parse(body: String): List<String> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val array = root as? JsonArray ?: return emptyList()

        val result = mutableListOf<String>()
        array.forEach { element ->
            val statement = element as? JsonObject ?: return@forEach

            val relations = statement["relation"]?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: return@forEach
            val grantsLogin = relations.any {
                runCatching { it.jsonPrimitive.content }.getOrNull() == RELATION_LOGIN_CREDS
            }
            if (!grantsLogin) return@forEach

            val target = statement["target"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?: return@forEach
            val namespace = target["namespace"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            if (namespace != "android_app") return@forEach

            val pkg = target["package_name"]
                ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                ?: return@forEach

            val fingerprints = target["sha256_cert_fingerprints"]
                ?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: return@forEach

            fingerprints.forEach { fingerprint ->
                val value = runCatching { fingerprint.jsonPrimitive.content }.getOrNull()
                if (!value.isNullOrBlank()) result += "$pkg|${value.uppercase()}"
            }
        }
        return result
    }

    private fun readCache(): Map<String, CacheEntry> = runCatching {
        val file = cacheFile
        if (!file.exists()) return emptyMap()
        val parsed: Map<String, CacheEntry> = json.decodeFromString(file.readText())
        memory.putAll(parsed)
        parsed
    }.getOrDefault(emptyMap())

    private fun store(domain: String, entry: CacheEntry) = runCatching {
        memory[domain] = entry
        val merged = readCache().toMutableMap()
        merged[domain] = entry
        // Önbellek sınırsız büyümesin: en eski girdiler düşüyor.
        val trimmed = merged.entries
            .sortedByDescending { it.value.fetchedAt }
            .take(MAX_CACHED_DOMAINS)
            .associate { it.key to it.value }
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(json.encodeToString(trimmed))
    }

    /** Kullanıcı önbelleği elle temizleyebiliyor; ayarlardan çağrılıyor. */
    fun clearCache() {
        memory.clear()
        runCatching { cacheFile.delete() }
    }

    private companion object {
        const val RELATION_LOGIN_CREDS = "delegate_permission/common.get_login_creds"
        const val POSITIVE_TTL = 7L * 24 * 60 * 60 * 1000
        const val NEGATIVE_TTL = 24L * 60 * 60 * 1000
        const val MAX_BODY = 512 * 1024
        const val MAX_CACHED_DOMAINS = 200
    }
}
