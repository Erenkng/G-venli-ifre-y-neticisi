package app.kasa.data.repo

import app.kasa.core.util.PasswordStrength
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem
import app.kasa.data.net.BreachChecker
import kotlinx.coroutines.Dispatchers
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

    enum class FindingType { LEAKED, REUSED, WEAK, OLD, NO_2FA }

    data class Finding(
        val type: FindingType,
        val count: Int,
        val itemIds: List<String>
    )

    data class Report(
        val score: Int,
        val findings: List<Finding>,
        val scannedAt: Long,
        val onlineCheckRan: Boolean,
        val updatedItems: List<VaultItem>
    ) {
        val affectedCount: Int get() = findings.flatMap { it.itemIds }.distinct().size
    }

    companion object {
        /** Bir yıl. */
        const val OLD_PASSWORD_MILLIS = 365L * 24 * 60 * 60 * 1000
        const val BREACH_CACHE_MILLIS = 7L * 24 * 60 * 60 * 1000
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
        var onlineRan = false
        val updated = if (onlineCheck && withPasswords.isNotEmpty()) {
            val total = withPasswords.size
            var done = 0
            val results = HashMap<String, Int>()
            for (item in withPasswords) {
                val fresh = now - item.breachCheckedAt < BREACH_CACHE_MILLIS
                if (fresh) {
                    results[item.id] = item.breachCount
                } else {
                    val count = breachChecker.timesSeen(item.password.reveal())
                    if (count != null) {
                        onlineRan = true
                        results[item.id] = count
                    } else {
                        results[item.id] = item.breachCount
                    }
                }
                done++
                onProgress(done.toFloat() / total)
            }
            items.map { item ->
                val count = results[item.id]
                if (count != null && (count != item.breachCount || now - item.breachCheckedAt >= BREACH_CACHE_MILLIS)) {
                    item.copy(breachCount = count, breachCheckedAt = now)
                } else item
            }
        } else {
            onProgress(1f)
            items
        }

        // ---- bulgular ----
        val leaked = updated.filter { it.breached }
        val weak = updated.filter {
            it.password.isNotBlank() &&
                PasswordStrength.evaluate(it.password.reveal()).tone == PasswordStrength.Tone.WEAK
        }
        val reusedGroups = updated
            .filter { it.password.isNotBlank() }
            .groupBy { it.password }
            .filterValues { it.size > 1 }
        val reused = reusedGroups.values.flatten()

        val old = updated.filter {
            it.password.isNotBlank() && now - it.passwordChangedAt > OLD_PASSWORD_MILLIS
        }
        val no2fa = updated.filter {
            it.category == Category.LOGIN && it.password.isNotBlank() && it.totpSecret.isBlank()
        }

        val findings = buildList {
            if (leaked.isNotEmpty()) add(Finding(FindingType.LEAKED, leaked.size, leaked.map { it.id }))
            if (reused.isNotEmpty()) add(Finding(FindingType.REUSED, reused.size, reused.map { it.id }))
            if (weak.isNotEmpty()) add(Finding(FindingType.WEAK, weak.size, weak.map { it.id }))
            if (old.isNotEmpty()) add(Finding(FindingType.OLD, old.size, old.map { it.id }))
            if (no2fa.isNotEmpty()) add(Finding(FindingType.NO_2FA, no2fa.size, no2fa.map { it.id }))
        }

        Report(
            score = score(updated, leaked.size, reused.size, weak.size, old.size, no2fa.size),
            findings = findings,
            scannedAt = now,
            onlineCheckRan = onlineRan,
            updatedItems = updated
        )
    }

    /**
     * 0-100 arası kasa puanı.
     *
     * Taban, parolaların ortalama gücüdür (0-100). Üstüne yapısal cezalar iner:
     * sızıntı en ağırı, sonra tekrar kullanım, sonra zayıflık, en son yaş ve
     * eksik 2FA. Kayıtsız kasa 100 sayılmaz — ölçecek bir şey yoktur, 100 verilir
     * ki kullanıcı boş kasada uyarı görmesin.
     */
    private fun score(
        items: List<VaultItem>,
        leaked: Int,
        reused: Int,
        weak: Int,
        old: Int,
        no2fa: Int
    ): Int {
        val scored = items.filter { it.password.isNotBlank() }
        if (scored.isEmpty()) return 100

        val averageStrength = scored
            .map { PasswordStrength.evaluate(it.password.reveal()).score.toDouble() }
            .average()

        var value = averageStrength * 100.0
        val total = scored.size.toDouble()

        value -= 45.0 * (leaked / total)
        value -= 25.0 * (reused / total)
        value -= 20.0 * (weak / total)
        value -= 10.0 * (old / total)
        value -= 8.0 * (no2fa / total)

        return value.coerceIn(0.0, 100.0).roundToInt()
    }
}
