package app.kasa.core.crypto

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * Anahtar türetme maliyetinin cihaza göre ölçülmesi.
 *
 * Sabit parametre yanlış bir soruya verilmiş cevap. 64 MiB / 3 tur, amiral
 * gemisi bir telefonda yarım saniyenin altında biter — yani cihazın
 * kaldırabileceğinin çok altında bir maliyet, saldırgana bedava indirim.
 * Aynı parametre dört yıllık orta segment bir telefonda üç saniye sürer —
 * kullanıcı her açılışta üç saniye bekleyemeyeceği için ilk yapacağı şey
 * biyometriye geçip ana parolayı unutmak olur.
 *
 * Doğrusu, maliyeti **süreye** göre belirlemek: kullanıcının kabul edeceği bir
 * bütçe (varsayılan 800 ms) seçilir ve o bütçeyi dolduran en yüksek parametre
 * ölçümle bulunur. Bulunan değerler kasa başlığına yazıldığı için kasa başka
 * bir cihaza taşındığında da doğru şekilde açılır — orada yeniden ölçüm
 * yapılmaz, dosyada ne yazıyorsa o kullanılır.
 */
object KdfCalibration {

    /** Kullanıcının kilit açarken bekleyeceği hedef süre. */
    const val DEFAULT_TARGET_MILLIS = 800L

    /** Dışa aktarılan dosyalar için: gecikme orada tek seferlik, daha cömert. */
    const val EXPORT_TARGET_MILLIS = 2_000L

    /**
     * Bellek tavanı. Argon2 belleği tek seferde ayırır; cihazın uygulama başına
     * ayırdığı yığından fazlasını istemek doğrudan çökme demektir.
     */
    private const val MIN_MEMORY_KIB = 32 * 1024        // 32 MiB
    private const val MAX_MEMORY_KIB = 512 * 1024       // 512 MiB
    private const val PROBE_MEMORY_KIB = 32 * 1024
    private const val MIN_ITERATIONS = 2
    private const val MAX_ITERATIONS = 12

    data class Result(
        val params: Kdf.Params,
        val measuredMillis: Long,
        val probeMillis: Long
    )

    /**
     * Cihazı ölçer ve [targetMillis] bütçesini dolduran parametreleri döndürür.
     *
     * Ağırdır (birkaç Argon2 turu, tipik olarak 1,5–3 saniye). Yalnızca kasa
     * kurulurken, ana parola değişirken ve kullanıcı açıkça istediğinde çalışır.
     */
    fun calibrate(
        context: Context,
        targetMillis: Long = DEFAULT_TARGET_MILLIS,
        onProgress: (Float) -> Unit = {}
    ): Result {
        if (!Kdf.argon2Available) return calibratePbkdf2(targetMillis, onProgress)

        val ceiling = memoryCeiling(context)
        val probePassword = SecretBytes(Crypto.randomBytes(16))
        val salt = Crypto.randomBytes(Kdf.SALT_BYTES)

        try {
            // 1. Sondaj: küçük ve ucuz bir turla cihazın birim maliyetini öğren.
            val probeParams = Kdf.Params(
                algorithm = Kdf.ALG_ARGON2ID,
                salt = salt,
                iterations = 1,
                memoryKib = PROBE_MEMORY_KIB,
                parallelism = Kdf.ARGON2_PARALLELISM
            )
            onProgress(0.15f)
            val probeMillis = measure { Kdf.derive(probePassword, probeParams).wipe() }
            onProgress(0.5f)

            // Argon2'nin maliyeti bellek × tur ile doğrusal ölçeklenir.
            val unitsPerMillis = PROBE_MEMORY_KIB.toDouble() / max(1L, probeMillis)
            val targetUnits = unitsPerMillis * targetMillis

            // 2. Bütçeyi önce belleğe ver: bellek-zorluk, saldırganın donanım
            //    avantajını asıl kıran şey. Tur sayısı sonra dengeler.
            var memoryKib = (targetUnits / MIN_ITERATIONS)
                .roundToInt()
                .coerceIn(MIN_MEMORY_KIB, min(ceiling, MAX_MEMORY_KIB))
            memoryKib = roundToMebibyte(memoryKib)

            var iterations = (targetUnits / memoryKib)
                .roundToInt()
                .coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)

            // 3. Doğrulama turu: tahmin tutmadıysa tur sayısıyla bir kez düzelt.
            var params = Kdf.Params(Kdf.ALG_ARGON2ID, salt, iterations, memoryKib, Kdf.ARGON2_PARALLELISM)
            onProgress(0.7f)
            var measured = measure { Kdf.derive(probePassword, params).wipe() }

            if (measured > targetMillis * 1.35 && iterations > MIN_ITERATIONS) {
                iterations = max(MIN_ITERATIONS, (iterations * targetMillis / measured).toInt())
                params = Kdf.Params(Kdf.ALG_ARGON2ID, salt, iterations, memoryKib, Kdf.ARGON2_PARALLELISM)
                onProgress(0.9f)
                measured = measure { Kdf.derive(probePassword, params).wipe() }
            } else if (measured < targetMillis * 0.65 && iterations < MAX_ITERATIONS) {
                iterations = min(MAX_ITERATIONS, max(iterations + 1, (iterations * targetMillis / max(1, measured)).toInt()))
                params = Kdf.Params(Kdf.ALG_ARGON2ID, salt, iterations, memoryKib, Kdf.ARGON2_PARALLELISM)
                onProgress(0.9f)
                measured = measure { Kdf.derive(probePassword, params).wipe() }
            }

            onProgress(1f)
            Log.i(
                "KdfCalibration",
                "Argon2id ${memoryKib / 1024} MiB / $iterations tur → ${measured} ms (hedef $targetMillis ms)"
            )
            // Gerçek kasa için taze bir tuz: ölçümde kullanılan tuz kullanılmaz.
            return Result(params.withFreshSalt(), measured, probeMillis)
        } catch (t: Throwable) {
            Log.w("KdfCalibration", "Ölçüm başarısız, sabit parametrelere dönülüyor")
            return Result(Kdf.defaultParams(), 0, 0)
        } finally {
            probePassword.wipe()
        }
    }

    /** Argon2 yoksa aynı bütçeyi PBKDF2 tur sayısına çevirir. */
    private fun calibratePbkdf2(targetMillis: Long, onProgress: (Float) -> Unit): Result {
        val probePassword = SecretBytes(Crypto.randomBytes(16))
        val salt = Crypto.randomBytes(Kdf.SALT_BYTES)
        val probeIterations = 50_000

        return try {
            onProgress(0.3f)
            val probeParams = Kdf.Params(Kdf.ALG_PBKDF2_SHA512, salt, probeIterations, 0, 1)
            val probeMillis = max(1L, measure { Kdf.derive(probePassword, probeParams).wipe() })

            val scaled = (probeIterations.toDouble() * targetMillis / probeMillis).toInt()
            // OWASP tabanının altına asla inilmez; ölçüm yalnızca yukarı çeker.
            val iterations = scaled.coerceIn(Kdf.PBKDF2_ITERATIONS, 5_000_000)
            onProgress(1f)

            Result(
                Kdf.Params(Kdf.ALG_PBKDF2_SHA512, Crypto.randomBytes(Kdf.SALT_BYTES), iterations, 0, 1),
                (iterations.toLong() * probeMillis) / probeIterations,
                probeMillis
            )
        } catch (t: Throwable) {
            Result(Kdf.defaultParams(), 0, 0)
        } finally {
            probePassword.wipe()
        }
    }

    /**
     * Uygulamanın güvenle ayırabileceği bellek tavanı.
     *
     * `largeMemoryClass` uygulama başına ayrılan yığın sınırı (MB). Argon2 bu
     * yığının dışında, yerel bellekte çalışıyor ama cihazın toplam belleği yine
     * de sınır; tavanı sınıfın üçte biriyle sınırlamak, ölçüm sırasında ya da
     * düşük bellekte kilit açarken çökmeyi önlüyor.
     */
    private fun memoryCeiling(context: Context): Int {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 64 * 1024
        if (manager.isLowRamDevice) return MIN_MEMORY_KIB

        val classMib = max(manager.largeMemoryClass, manager.memoryClass)
        val ceilingKib = (classMib / 3) * 1024
        return ceilingKib.coerceIn(MIN_MEMORY_KIB, MAX_MEMORY_KIB)
    }

    private fun roundToMebibyte(kib: Int): Int = max(1, kib / 1024) * 1024

    private inline fun measure(block: () -> Unit): Long = measureTimeMillis(block)
}
