package app.kasa.data.repo

import androidx.compose.runtime.Immutable
import app.kasa.core.util.PasswordStrength
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem
import app.kasa.data.net.BreachChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Kasa sağlık taraması.
 *
 * Puan, tek tek parolaların gücünden değil kasanın bütününden çıkar: sızmış
 * bir parola, güçlü ama tekrar kullanılmış bir paroladan daha ağır bir sorundur
 * ve puanı ona göre düşürür. Böylece "hepsi 20 karakter ama üçü aynı" durumu
 * yüksek puan almaz.
 */
class SecurityAnalyzer(private val breachChecker: BreachChecker) {

    enum class FindingType { LEAKED, REUSED, WEAK, OLD, NO_2FA, RENEW_DUE }

    @Immutable
    data class Finding(
        val type: FindingType,
        val count: Int,
        val itemIds: List<String>
    )

    /**
     * Puanın nasıl çıktığı.
     *
     * Ekran 0-100 arası bir sayı gösteriyordu ve altındaki bulgularla arasındaki
     * bağ görünmüyordu. Hesap belirli olduğu için saklamanın gerekçesi de yok:
     * taban ortalama güç, üstüne her bulgu türünün oranına göre inen ceza.
     * Gösterilince puan bir yargı olmaktan çıkıp bir **liste** oluyor —
     * hangi kolun sayıyı en çok oynatacağı görünür hâle geliyor.
     */
    @Immutable
    data class Deduction(val type: FindingType, val points: Int)

    @Immutable
    data class Report(
        val score: Int,
        val findings: List<Finding>,
        val scannedAt: Long,
        val onlineCheckRan: Boolean,
        val updatedItems: List<VaultItem>,
        /** Ortalama parola gücünden gelen taban puan (0-100). */
        val basePoints: Int = 0,
        /** Tabandan inen cezalar; yalnızca sıfırdan büyük olanlar. */
        val deductions: List<Deduction> = emptyList(),
        /**
         * Puanın hesabına giren kayıt sayısı.
         *
         * Kasanın tamamı değil: yalnızca ölçülebilir bir sırrı olanlar. Ekran
         * bunu söylüyor, çünkü "kasanın puanı" ile "kasanın bir kısmının
         * puanı" aynı şey değil.
         */
        val scoredCount: Int = 0
    ) {
        val affectedCount: Int get() = findings.flatMap { it.itemIds }.distinct().size
    }

    companion object {
        /** Bir yıl. */
        const val OLD_PASSWORD_MILLIS = 365L * 24 * 60 * 60 * 1000
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val BREACH_CACHE_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** HIBP'nin k-anonimlik ön eki: özetin ilk beş onaltılık hanesi. */
        private const val PREFIX_LENGTH = 5

        /**
         * Aynı anda kaç ön ek indirilecek.
         *
         * Sınırsız bırakmak büyük bir kasada yüzlerce eşzamanlı bağlantı
         * açardı; altı, mobil bir bağlantıda gecikmeyi gizlemeye yetiyor ve
         * sunucuya yığılma olarak görünmüyor.
         */
        private const val MAX_PARALLEL_RANGES = 6
    }

    /**
     * @param onlineCheck kapalıysa hiçbir ağ isteği yapılmaz; sızıntı bulgusu
     *        yalnızca daha önce önbelleğe alınmış sonuçlardan üretilir.
     */
    suspend fun analyze(
        items: List<VaultItem>,
        onlineCheck: Boolean,
        onProgress: (Float) -> Unit = {}
    ): Report = withContext(Dispatchers.Default) {

        val now = System.currentTimeMillis()
        val withPasswords = items.filter { it.password.isNotBlank() }

        // ---- sızıntı denetimi (ağ) ----
        //
        // Ön eke göre gruplanıyor: her ayrı ön ek için **bir** istek, kalanlar
        // cihazda çözülüyor. Gerekçesi [BreachChecker.range] üzerinde yazılı —
        // hem dört yüz ardışık gidiş-dönüşü birkaç isteğe indiriyor hem de aynı
        // ön eki tekrar tekrar sormanın sızdırdığı bilgiyi ortadan kaldırıyor.
        var onlineRan = false
        val updated = if (onlineCheck && withPasswords.isNotEmpty()) {
            // Yalnızca önbelleği bayatlamış olanlar sorulacak.
            val stale = withPasswords.filter { now - it.breachCheckedAt >= BREACH_CACHE_MILLIS }
            // Büyük harfe çevirmek açıkça yapılıyor.
            //
            // `Crypto.sha1Hex` bugün `%02X` ile büyük harf üretiyor ve
            // [BreachChecker.range] de anahtarlarını büyük harfe çeviriyor —
            // ama bu eşleşme örtük kalırsa, biçimi değiştiren bir düzenleme
            // sızıntı denetimini sessizce **hep temiz** gösterirdi. Sessiz
            // çünkü kimse "sıfır sızıntı" sonucundan şüphelenmez.
            val hashes = stale.associate { it.id to breachChecker.hashOf(it.password).uppercase() }
            val prefixes = hashes.values.map { it.take(PREFIX_LENGTH) }.distinct()

            // Sınırlı eşzamanlılık: istekler paralel ama sunucuya aynı anda
            // yığılmıyor. Sınırsız bırakmak, büyük bir kasada yüzlerce eşzamanlı
            // bağlantı açardı ve bunun kendisi de bir desen.
            val ranges = HashMap<String, Map<String, Int>>()
            val gate = Semaphore(MAX_PARALLEL_RANGES)
            var done = 0
            coroutineScope {
                prefixes.map { prefix ->
                    async {
                        val result = gate.withPermit { breachChecker.range(prefix) }
                        synchronized(ranges) {
                            if (result != null) ranges[prefix] = result
                            done++
                            onProgress(done.toFloat() / prefixes.size)
                        }
                    }
                }.forEach { it.await() }
            }
            onlineRan = ranges.isNotEmpty()
            onProgress(1f)

            items.map { item ->
                val hash = hashes[item.id] ?: return@map item
                val table = ranges[hash.take(PREFIX_LENGTH)] ?: return@map item
                // Ön ek indirildiyse cevap kesin: listede yoksa parola
                // sızıntılarda hiç görülmemiş demek, yani sıfır.
                val count = table[hash.substring(PREFIX_LENGTH)] ?: 0
                item.copy(breachCount = count, breachCheckedAt = now)
            }
        } else {
            onProgress(1f)
            items
        }

        // ---- bulgular ----
        //
        // Zayıflık ve tekrar kullanım artık [VaultItem.measuredSecret] üzerinden.
        //
        // Önceden yalnızca `password` alanına bakılıyordu ve şema tabanlı
        // türler (SSH anahtarı, lisans, banka) sırlarını `extras` içinde
        // tuttuğu için taramanın tamamen dışında kalıyorlardı. Sızıntı
        // denetimi onlar için zaten anlamsız — bir özel anahtar HIBP'nin
        // parola kümesinde olmaz — ama **tekrar kullanım** anlamlı: iki SSH
        // anahtarına aynı parolayı vermek gerçek bir bulgu.
        val leaked = updated.filter { it.breached }
        val measurable = updated.mapNotNull { item ->
            item.measuredSecret?.let { item to it }
        }
        val weak = measurable
            .filter { (_, secret) -> PasswordStrength.evaluate(secret).tone == PasswordStrength.Tone.WEAK }
            .map { it.first }
        val reused = measurable
            .groupBy { it.second }
            .filterValues { it.size > 1 }
            .values.flatten()
            .map { it.first }

        val old = updated.filter {
            it.password.isNotBlank() && now - it.passwordChangedAt > OLD_PASSWORD_MILLIS
        }
        val no2fa = updated.filter {
            it.category == Category.LOGIN && it.password.isNotBlank() && it.totpSecret.isBlank()
        }

        // Kullanıcının kendi koyduğu yenileme aralığı dolanlar.
        //
        // Genel "bir yıldan eski" ölçütünden ayrı tutuluyor: o, hiç
        // düşünülmemiş kayıtlar için bir taban; bu ise kullanıcının o kayıt
        // için bilerek seçtiği süre. İkisini aynı bulguda toplamak, kullanıcının
        // kararını uygulamanın varsayılanının içinde kaybederdi.
        val renewDue = updated.filter {
            it.renewEveryDays > 0 &&
                it.password.isNotBlank() &&
                now - it.passwordChangedAt > it.renewEveryDays * DAY_MILLIS
        }

        val findings = buildList {
            if (leaked.isNotEmpty()) add(Finding(FindingType.LEAKED, leaked.size, leaked.map { it.id }))
            if (reused.isNotEmpty()) add(Finding(FindingType.REUSED, reused.size, reused.map { it.id }))
            if (weak.isNotEmpty()) add(Finding(FindingType.WEAK, weak.size, weak.map { it.id }))
            if (old.isNotEmpty()) add(Finding(FindingType.OLD, old.size, old.map { it.id }))
            if (no2fa.isNotEmpty()) add(Finding(FindingType.NO_2FA, no2fa.size, no2fa.map { it.id }))
            if (renewDue.isNotEmpty()) {
                add(Finding(FindingType.RENEW_DUE, renewDue.size, renewDue.map { it.id }))
            }
        }

        val breakdown = score(
            measurable = measurable.map { it.second },
            leaked = leaked.size,
            reused = reused.size,
            weak = weak.size,
            old = old.size,
            no2fa = no2fa.size,
            renewDue = renewDue.size
        )

        Report(
            score = breakdown.total,
            findings = findings,
            scannedAt = now,
            onlineCheckRan = onlineRan,
            updatedItems = updated,
            basePoints = breakdown.base,
            deductions = breakdown.deductions,
            scoredCount = measurable.size
        )
    }

    /** [score] sonucunun taşıyıcısı. */
    private class Scored(val total: Int, val base: Int, val deductions: List<Deduction>)

    /**
     * 0-100 arası kasa puanı ve dökümü.
     *
     * Taban, ölçülebilir sırların ortalama gücüdür (0-100). Üstüne yapısal
     * cezalar iner: sızıntı en ağırı, sonra tekrar kullanım, sonra zayıflık, en
     * son yaş, eksik 2FA ve dolan yenileme. Ölçülecek sırrı olmayan kasa 100
     * alır — orada verilecek bir yargı yok ve kullanıcı boş kasada uyarı
     * görmemeli.
     *
     * Yenileme cezası sonradan eklendi: bulgu listede duruyordu ama puana hiç
     * girmiyordu, yani kullanıcının kendi koyduğu kural ihlal edildiğinde sayı
     * kımıldamıyordu. Ağırlığı en düşük olanı, çünkü ihlal edilen şey bir risk
     * değil bir **niyet**.
     */
    private fun score(
        measurable: List<String>,
        leaked: Int,
        reused: Int,
        weak: Int,
        old: Int,
        no2fa: Int,
        renewDue: Int
    ): Scored {
        if (measurable.isEmpty()) return Scored(100, 100, emptyList())

        val averageStrength = measurable
            .map { PasswordStrength.evaluate(it).score.toDouble() }
            .average()

        val base = averageStrength * 100.0
        val total = measurable.size.toDouble()

        val weights = listOf(
            FindingType.LEAKED to 45.0 * (leaked / total),
            FindingType.REUSED to 25.0 * (reused / total),
            FindingType.WEAK to 20.0 * (weak / total),
            FindingType.OLD to 10.0 * (old / total),
            FindingType.NO_2FA to 8.0 * (no2fa / total),
            FindingType.RENEW_DUE to 6.0 * (renewDue / total)
        )

        val value = (base - weights.sumOf { it.second }).coerceIn(0.0, 100.0)
        // Yuvarlanınca sıfıra düşen cezalar listeye girmiyor: "−0 puan" diye
        // bir satır, okuyana hiçbir şey söylemeyip yer kaplıyor.
        val deductions = weights
            .map { (type, points) -> Deduction(type, points.roundToInt()) }
            .filter { it.points > 0 }

        return Scored(value.roundToInt(), base.roundToInt().coerceIn(0, 100), deductions)
    }
}
