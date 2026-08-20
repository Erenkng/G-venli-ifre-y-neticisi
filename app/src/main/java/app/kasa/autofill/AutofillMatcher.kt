package app.kasa.autofill

import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem

/**
 * Hangi kaydın hangi isteğe önerileceğine karar verir.
 *
 * ### Neden yeniden yazıldı
 *
 * Önceki eşleştirme, paket adının son parçasını kayıt adının içinde arıyordu:
 *
 * ```
 * appToken = packageName.substringAfterLast('.')      // "com.evil.garanti" → "garanti"
 * item.name.lowercase().contains(appToken)            // "Garanti Bankası" eşleşti
 * ```
 *
 * Paket adının son parçası kimseye ait değil. Herkes `com.evil.garanti`
 * yayımlayabilir ve kasadaki "Garanti" kaydının parolasını isteyebilir;
 * kullanıcı öneri menüsünde doğru kaydın adını gördüğü için gönül rahatlığıyla
 * dokunur. Ad benzerliği kolaylık gibi görünüyordu ama kimlik avına açık bir
 * kapıydı ve kapatılması hiçbir kolaylığı geri almıyor — yerine gelen şey,
 * kullanıcının bir kez elle seçmesi.
 *
 * ### Güven kademeleri
 *
 * | Kademe | Dayanak | Ne kadar güçlü |
 * |---|---|---|
 * | [Tier.LINKED] | kayıtta saklı `paket\|imza parmak izi` | kesin |
 * | [Tier.DOMAIN] | tarayıcıdaki sayfanın alan adı | tarayıcı kadar |
 * | [Tier.DELEGATED] | alan adının `assetlinks.json` beyanı | alan adı sahibi kadar |
 *
 * Bunların dışında **otomatik eşleşme yok**. Eşleşme bulunamadığında kullanıcı
 * "Kasa'dan seç" ile kaydı elle seçiyor ve o seçim [Tier.LINKED] bağını
 * kuruyor: ikinci seferde kendiliğinden geliyor. Yani ad benzerliğinin
 * kazandırdığı tek şey — ilk seferde bir dokunuş — bir kerelik bir bedele
 * dönüşüyor.
 */
object AutofillMatcher {

    enum class Tier { LINKED, DOMAIN, DELEGATED }

    data class Match(val item: VaultItem, val tier: Tier)

    /**
     * Ağ gerektirmeyen eşleşmeler. Doldurma yolunun sıcak kısmı burası:
     * tarayıcıda ve bağı kurulmuş uygulamalarda hiçbir istek atılmıyor.
     */
    fun offline(items: List<VaultItem>, caller: CallerIdentity): List<Match> {
        val usable = items.filter { it.fillable }
        val token = caller.linkToken()
        val domain = caller.webDomain

        val matches = LinkedHashMap<String, Match>()

        if (token != null) {
            usable.filter { item -> item.linkedApps.any { it.equals(token, ignoreCase = true) } }
                .forEach { matches[it.id] = Match(it, Tier.LINKED) }
        }

        // Alan adı yalnızca tarayıcıdan geldiğinde anlamlı. Bunun gerekçesi
        // StructureParser.isBrowser üzerinde yazılı: webDomain alanını
        // uygulamanın kendisi dolduruyor, sistem doğrulamıyor.
        if (caller.isBrowser && domain != null) {
            usable.filter { sameSite(it.host(), domain) }
                .forEach { item -> matches.getOrPut(item.id) { Match(item, Tier.DOMAIN) } }
        }

        return matches.values.sortedWith(
            compareBy<Match> { it.tier.ordinal }.thenByDescending { it.item.lastUsedAt }
        )
    }

    /**
     * Yerli bir uygulama için alan adı beyanına dayanan eşleşmeler.
     *
     * Yalnızca [offline] boş döndüğünde ve çağıran tarayıcı değilken
     * çağrılıyor: her doldurmada ağa çıkmanın gizlilik bedeli var ve zaten
     * eşleşme bulunmuşsa ödemenin anlamı yok.
     */
    suspend fun delegated(
        items: List<VaultItem>,
        caller: CallerIdentity,
        links: DigitalAssetLinks
    ): List<Match> {
        if (caller.isBrowser) return emptyList()
        if (caller.certSha256 == null) return emptyList()

        val usable = items.filter { it.fillable }
        // Aynı alan adı birden çok kayıtta olabilir; her biri için ayrı sorgu
        // atmamak adına önce alan adları benzersizleştiriliyor.
        val hosts = usable.mapNotNull { it.host() }.distinct()

        val allowed = hosts.filter { host -> links.allows(host, caller) }.toSet()
        if (allowed.isEmpty()) return emptyList()

        return usable.filter { it.host() in allowed }
            .map { Match(it, Tier.DELEGATED) }
            .sortedByDescending { it.item.lastUsedAt }
    }

    /**
     * İki alan adı aynı siteye mi ait?
     *
     * Tam eşitlik ya da birinin ötekinin alt alan adı olması kabul ediliyor:
     * `mybank.com` kaydı `giris.mybank.com` sayfasında doldurulabiliyor.
     *
     * ### Bilinen sınır
     *
     * Doğrusu, karşılaştırmayı **kayıtlanabilir alan adı** (registrable domain)
     * düzeyinde yapmak; bunun için Public Suffix List gerekiyor ve o liste
     * düzenli güncellenmezse kendisi bir kusur kaynağı oluyor. Liste
     * paketlemek yerine burada iki koruma var: kısa olan tarafın en az iki
     * etiketi olmak zorunda, ve yaygın çok parçalı sonekler açıkça
     * reddediliyor. Böylece `com.tr` gibi bir "alan adı" hiçbir şeyle
     * eşleşmiyor.
     *
     * Listede olmayan egzotik bir sonek (`sch.uk` gibi) hâlâ yanlış eşleşme
     * üretebilir. Bunun tek somut sonucu, aynı sonek altındaki iki farklı
     * sitenin birbirinin kaydını önermesi; ve kullanıcı öneriyi görüp
     * dokunmadıkça bir şey olmuyor.
     */
    fun sameSite(host: String?, domain: String?): Boolean {
        if (host.isNullOrBlank() || domain.isNullOrBlank()) return false
        val a = host.lowercase().removePrefix("www.").trimEnd('.')
        val b = domain.lowercase().removePrefix("www.").trimEnd('.')
        if (a == b) return a.isRegistrable()

        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        if (!shorter.isRegistrable()) return false
        return longer.endsWith(".$shorter")
    }

    private fun String.isRegistrable(): Boolean {
        val labels = split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return false
        if (this in PUBLIC_SUFFIXES) return false
        return true
    }

    /** Otomatik doldurmada kullanılabilecek kayıt mı? */
    private val VaultItem.fillable: Boolean
        get() = !inTrash &&
            (category == Category.LOGIN || category == Category.OTP) &&
            // Kayıt bazlı ek kilit otomatik doldurmada uygulanamıyor: o akışta
            // yalnızca kasanın kilidi soruluyor. İşaretli kaydı yine de sunmak,
            // kullanıcının koruma altına aldığını sandığı parolayı en geniş
            // kapıdan vermek olurdu.
            !requireAuth

    /**
     * İki etiketli olduğu hâlde kayıtlanabilir olmayan sonekler.
     *
     * Tam liste değil ve olması da gerekmiyor: buradaki iş, `com.tr` ya da
     * `co.uk` gibi altında binlerce bağımsız sitenin durduğu yaygın sonekleri
     * "tek bir site" saymayı engellemek.
     */
    private val PUBLIC_SUFFIXES = setOf(
        "com.tr", "net.tr", "org.tr", "gov.tr", "edu.tr", "bel.tr", "web.tr", "k12.tr",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
        "com.br", "com.au", "net.au", "org.au", "com.cn", "com.mx", "com.ar",
        "co.jp", "ne.jp", "or.jp", "co.kr", "co.in", "co.za", "co.nz", "com.sg",
        "com.hk", "com.tw", "com.my", "com.ph", "com.vn", "com.pk", "com.eg",
        "com.sa", "com.ua", "com.pl", "com.ru", "co.il", "com.es", "com.pe"
    )
}
