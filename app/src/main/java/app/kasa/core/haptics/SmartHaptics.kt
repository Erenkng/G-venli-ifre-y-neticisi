package app.kasa.core.haptics

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow

/**
 * Akıllı titreşim motoru.
 *
 * ### Ne yapıyor
 *
 * Uygulamanın hiçbir yerinde titreşim deseni yazılı değil. Çağıran taraf ne
 * hissettirmek istediğini söylüyor ([Affect]), motor onu o cihazda
 * çalınabilecek en iyi şeye çeviriyor:
 *
 * ```
 * Affect  →  HapticSynth  →  HapticGesture  →  HapticRenderer  →  VibrationEffect
 * (niyet)     (sentez)       (cihazsız tarif)   (yetenek merdiveni)  (donanım)
 * ```
 *
 * Bu dosya zincirin **karar veren** halkası: bir titreşimin çalınıp
 * çalınmayacağına, ne kadar güçlü çalınacağına ve hangi sistem kanalından
 * gideceğine burada karar veriliyor.
 *
 * ### Aklı ne
 *
 * Dört ayrı uyarlama var ve dördü de gerçek bir kusuru kapatıyor:
 *
 * **Tekrar sönümlemesi.** Aynı olay arka arkaya geldiğinde — listede on satır
 * kaydırmak, üreteç düğmesine üst üste basmak — her seferinde tam güçte
 * titremek gürültü. İnsan algısı da zaten uyum sağlıyor: tekrarlanan uyaran
 * fark edilmez oluyor, yani tam güç çalmak yalnızca pil harcıyor. Motor aynı
 * duygunun tekrarını üstel olarak sönümlüyor ve arada sessizlik olunca
 * sönümleme geri açılıyor.
 *
 * **Bütçe.** Kısa bir pencerede toplam titreşim süresi bir tavanı geçemiyor.
 * Hızlı kaydırmada her satır için titreme isteği gelebiliyor ve sıraya giren
 * titreşimler birbirinin üstüne biniyordu; tavan bunu kesiyor.
 *
 * **Bağlam.** Sessiz kip, pil tasarrufu ve kullanıcının yoğunluk tercihi
 * ölçeği doğrudan düşürüyor. Sessiz kipteyken dokunuş geri bildirimi çalmak,
 * kullanıcının sustur dediği şeyi çalmaya devam etmek.
 *
 * **Yol öğrenme.** Oluşturucu hangi basamağı kullandığını bildiriyor ve motor
 * bunu saklıyor. Bir cihaz zarf desteklediğini söyleyip fırlatıyorsa, ikinci
 * denemede o basamak atlanıyor — her titreşimde aynı istisnayı yeniden
 * üretmek yerine.
 *
 * ### Ne yapmıyor
 *
 * Rastgelelik yok. "Çeşitlilik olsun" diye aynı olayı her seferinde farklı
 * çalmak, dokunsal geri bildirimin tek işini bozar: kullanıcı titreşimi olayla
 * eşleştiremez. Aynı duygu, aynı bağlamda, her zaman aynı hissediyor.
 */
class SmartHaptics(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = runCatching {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    }.getOrNull()

    private val audio: AudioManager? = runCatching {
        appContext.getSystemService(AudioManager::class.java)
    }.getOrNull()

    private val power: PowerManager? = runCatching {
        appContext.getSystemService(PowerManager::class.java)
    }.getOrNull()

    /** Yetenekler bir kez ölçülüyor; cihazın ömrü boyunca değişmiyor. */
    private val capabilities: VibratorCapabilities by lazy { VibratorCapabilities.probe(vibrator) }

    /** Kullanıcının açma/kapama tercihi. */
    @Volatile
    var enabled: Boolean = true

    /**
     * Kullanıcının yoğunluk tercihi (0..1).
     *
     * Sistem ayarının yerine geçmiyor, onun üstüne çarpılıyor: sistemde
     * titreşim kısılmışsa burada 1 olması onu geri açmıyor.
     */
    @Volatile
    var intensity: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    // ── uyarlama durumu ──────────────────────────────────────────────────

    private val lastAffect = ThreadLocalAffect()
    private var repeatCount: Int = 0
    private var lastPlayedAt: Long = 0L

    /** Kayan pencere içindeki toplam titreşim süresi. */
    private val budgetSpentMillis = AtomicLong(0L)
    private var budgetWindowStart: Long = 0L

    /** Fırlattığı görülen yollar; ikinci kez denenmiyor. */
    private val brokenPaths = mutableSetOf<HapticRenderer.Path>()

    /**
     * Titreşimin hazırlandığı ve çalındığı iş parçacığı.
     *
     * ### Neden çağıranın iş parçacığında olmuyor
     *
     * [Vibrator.vibrate] bir binder çağrısı: sistem sürecine gidip dönüyor ve
     * bedeli genelde birkaç milisaniye, yük altında çok daha fazla. Bu
     * uygulamada titreşim **basış anında** çalınıyor — yani tam da basış
     * animasyonunun ilk karesinin çizildiği anda. Arayüz iş parçacığında
     * yapılan çağrı o kareyi düşürüyordu ve görülen şey, düğmeye basınca
     * animasyonun takılmasıydı.
     *
     * ### Neden tek bir iş parçacığı
     *
     * Motorun durumu (tekrar sayacı, bütçe penceresi, bozuk yollar) kilitsiz
     * tutuluyor. Tek bir iş parçacığı bütün o durumu oraya hapsediyor: sıra
     * korunuyor, yarış yok, kilit de yok. [lastAffect] zaten `ThreadLocal`
     * ve burada doğru yere düşüyor.
     *
     * ### Neden sıra kısa ve taşan atılıyor
     *
     * Geç gelen bir titreşim yanlış olayı işaret ediyor; hiç gelmemesinden
     * kötü. Hızlı kaydırmada onlarca istek gelebiliyor ve bunları sıraya
     * dizmek, parmağın çoktan geçtiği satırların titreşimini sonradan
     * çalmak demek. Sıra dolduğunda [ThreadPoolExecutor.DiscardPolicy] yeni
     * geleni sessizce atıyor; ayrıca çalınmadan önce isteğin yaşı da
     * denetleniyor.
     */
    private val worker = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(WORKER_QUEUE),
        { runnable -> Thread(runnable, "kasa-haptics").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy()
    )

    init {
        // Yetenek yoklaması da binder çağrıları yapıyor. İlk titreşimde
        // arayüz iş parçacığında yapılmasın diye kurulumda ısıtılıyor.
        runCatching { worker.execute { capabilities } }
    }

    // ── genel arayüz ─────────────────────────────────────────────────────

    /**
     * Bir duyguyu çalar.
     *
     * Hiçbir koşulda fırlatmıyor ve hiçbir koşulda çağıranı bekletmiyor:
     * titreşim bir yan etki, akışın parçası değil. Bir titreşimin
     * çalınamaması kullanıcının fark etmeyeceği bir şey; çökme değil.
     *
     * Çağıran taraf yalnızca isteği bırakıyor; hazırlık ve donanım çağrısı
     * [worker] üzerinde yapılıyor. Gerekçesi orada yazılı.
     */
    fun play(affect: Affect) {
        if (!enabled) return
        // Zaman **istek anında** okunuyor, çalınma anında değil: sönümleme ve
        // bütçe kullanıcının dokunma sıklığına bakıyor ve o sıklık burada.
        val requestedAt = SystemClock.uptimeMillis()
        runCatching { worker.execute { render(affect, requestedAt) } }
    }

    private fun render(affect: Affect, now: Long) {
        val caps = capabilities
        if (!caps.usable) return

        // Beklerken bayatlayan istek atılıyor.
        if (SystemClock.uptimeMillis() - now > STALE_MILLIS) return
        val scale = contextScale() * repetitionScale(affect, now) * intensity
        if (scale <= 0f) return

        val gesture = HapticSynth.compose(affect).scaled(scale)
        if (gesture.isSilent) return
        if (!claimBudget(gesture.vibratingMillis, now)) return

        val rendered = renderUsable(gesture, caps) ?: return
        val effect = rendered.effect ?: return

        val played = runCatching {
            vibrator?.vibrate(effect, attributesFor(affect.channel))
        }.isSuccess

        if (!played) {
            // Yol bu cihazda çalışmıyor: bir daha denenmiyor.
            brokenPaths += rendered.path
            return
        }
        lastPlayedAt = now
    }

    /**
     * İki duygunun karışımını çalar.
     *
     * Bileşik olaylar için: "kaydedildi ama parola zayıf" tek bir his olmalı.
     * Arka arkaya iki titreşim, iki ayrı olay olduğunu söylerdi.
     */
    fun play(primary: Affect, secondary: Affect, fraction: Float = 0.5f) {
        play(primary.blend(secondary, fraction))
    }

    /** Cihazda hiç titreşim yoksa arayüz bunu bilmek isteyebilir. */
    val available: Boolean get() = capabilities.usable

    // ── uyarlama ─────────────────────────────────────────────────────────

    /**
     * Aynı duygunun tekrarında sönümleme.
     *
     * İlk çalışta tam güç. Sonraki her tekrar üstel olarak düşüyor ama bir
     * tabana oturuyor: tamamen susmak, kullanıcının hâlâ beklediği geri
     * bildirimi yok etmek olurdu — kaydırırken tıkırtının kesilmesi
     * "bozuldu" hissi veriyor.
     *
     * Sayaç yalnızca art arda gelen dokunuşlarda büyüyor; aralarında kısa
     * bir sessizlik olan iki dokunuş ayrı iki olay ve ikisi de tam güçte.
     */
    private fun repetitionScale(affect: Affect, now: Long): Float {
        val previous = lastAffect.value
        val sameEvent = previous != null && previous.sameCharacterAs(affect)
        val quietFor = now - lastPlayedAt

        // Sönümleme yalnızca **hızlı** tekrarda.
        //
        // Eskiden sayaç, aynı olay bir saniyeden kısa aralıkla geldiği sürece
        // büyüyordu ve normal kullanım — saniyede bir düğmeye dokunmak — onu
        // tavana çıkarıyordu. Sönümlemenin var olma sebebi kesintisiz uğultu
        // (liste kaydırma, basılı tutulan bir düğme); saniyede bir dokunuş
        // uğultu değil, ayrı ayrı olaylar.
        repeatCount = when {
            !sameEvent -> 0
            quietFor > RAPID_REPEAT_MILLIS -> 0
            else -> (repeatCount + 1).coerceAtMost(REPEAT_CAP)
        }
        lastAffect.value = affect

        // Yüksek uyarılmalı olaylar sönümlenmiyor: alarmın ikincisi de
        // birincisi kadar acil, ve sönümlemek onu gizlemek olurdu.
        if (affect.arousal >= NO_DAMPING_AROUSAL) return 1f

        return (REPEAT_FLOOR + (1f - REPEAT_FLOOR) * REPEAT_DECAY.pow(repeatCount.toFloat()))
            .coerceIn(REPEAT_FLOOR, 1f)
    }

    /**
     * Bağlamdan gelen ölçek.
     *
     * Sessiz kip, pil tasarrufu ve zil kipi burada okunuyor. Değer sıfırsa
     * hiçbir şey çalınmıyor — ve bu, "çok kısık çal" ile aynı şey değil:
     * kullanıcı sustur dediğinde susmak gerekiyor.
     */
    private fun contextScale(): Float {
        var scale = 1f

        // Pil tasarrufu: titreşim pahalı bir motor sürüyor.
        if (runCatching { power?.isPowerSaveMode }.getOrNull() == true) {
            scale *= POWER_SAVE_SCALE
        }

        // Sessiz kip dokunsal geri bildirimi de kapsıyor. Titreşimli kipte
        // ise dokunuş geri bildirimi beklenen davranış.
        when (runCatching { audio?.ringerMode }.getOrNull()) {
            AudioManager.RINGER_MODE_SILENT -> return 0f
            AudioManager.RINGER_MODE_VIBRATE -> scale *= 1f
            else -> Unit
        }

        return scale
    }

    /**
     * Kayan pencere bütçesi.
     *
     * Hızlı kaydırmada satır başına bir titreşim isteği gelebiliyor ve
     * bunlar sıraya girip birbirinin üstüne biniyordu; sonuç kesintisiz bir
     * uğultu. Pencere içindeki toplam süre tavanı geçtiğinde yeni istekler
     * sessizce düşürülüyor.
     *
     * Düşürülen istek kuyruğa alınmıyor: gecikmeli çalan bir dokunsal geri
     * bildirim, ait olduğu olaydan koptuğu için yanlış bilgi veriyor.
     */
    private fun claimBudget(millis: Int, now: Long): Boolean {
        if (now - budgetWindowStart > BUDGET_WINDOW_MILLIS) {
            budgetWindowStart = now
            budgetSpentMillis.set(0L)
        }
        val spent = budgetSpentMillis.addAndGet(millis.toLong())
        return spent <= BUDGET_MILLIS
    }

    /** Bozuk olduğu görülen basamakları atlayarak oluşturur. */
    private fun renderUsable(
        gesture: HapticGesture,
        caps: VibratorCapabilities
    ): HapticRenderer.Rendered? {
        val rendered = HapticRenderer.render(gesture, caps)
        if (rendered.path !in brokenPaths) return rendered

        // Bilinen bozuk yol: yeteneği düşürüp yeniden dene. Tek seferlik
        // düşürme yeterli, çünkü merdivenin her basamağı bir alttakinden
        // daha az şey istiyor.
        val reduced = VibratorCapabilities.probe(null)
        return HapticRenderer.render(gesture, reduced).takeIf { it.path !in brokenPaths }
    }

    /**
     * Kanala göre sistem nitelikleri.
     *
     * Bu, "hangi ses seviyesi" değil, **hangi kullanıcı ayarı** sorusunun
     * cevabı: dokunuş geri bildirimi ile bildirim titreşimini kullanıcı ayrı
     * ayrı kapatabiliyor ve Rahatsız Etmeyin ikisini farklı ele alıyor. Yanlış
     * kanaldan çalmak, kapatılmış sanılan bir şeyi çalmaya devam etmek.
     */
    private fun attributesFor(channel: HapticChannel): VibrationAttributes {
        val usage = when (channel) {
            HapticChannel.TOUCH -> VibrationAttributes.USAGE_TOUCH
            HapticChannel.NOTIFICATION -> VibrationAttributes.USAGE_NOTIFICATION
            HapticChannel.ALERT -> VibrationAttributes.USAGE_ALARM
        }
        return VibrationAttributes.Builder().setUsage(usage).build()
    }

    /**
     * İki duygu "aynı olay" sayılır mı.
     *
     * Tam eşitlik aramak işe yaramıyor: karışımlar ve ölçeklenmiş duygular
     * asla birbirine eşit çıkmaz ama kullanıcı için aynı olaydır. Ölçüt
     * karakterin yakınlığı — eksenlerin hepsi dar bir bant içindeyse aynı
     * olay sayılıyor.
     */
    private fun Affect.sameCharacterAs(other: Affect): Boolean =
        kotlin.math.abs(valence - other.valence) < SAME_EVENT_BAND &&
            kotlin.math.abs(arousal - other.arousal) < SAME_EVENT_BAND &&
            kotlin.math.abs(certainty - other.certainty) < SAME_EVENT_BAND

    /**
     * Son duyguyu iş parçacığı başına tutan küçük kap.
     *
     * Titreşim istekleri arayüz iş parçacığından geliyor ama arka plan
     * işlerinden de gelebiliyor (tarama bitti, kilit süresi doldu). İkisinin
     * tekrar sayacını paylaşması, arka plandaki bir olayın kullanıcının
     * dokunuş geri bildirimini sönümlemesine yol açardı.
     */
    private class ThreadLocalAffect {
        private val holder = ThreadLocal<Affect?>()
        var value: Affect?
            get() = holder.get()
            set(v) = holder.set(v)
    }

    private companion object {
        /**
         * Sırada bekleyebilecek istek sayısı.
         *
         * Kısa: geç gelen bir titreşim yanlış olayı işaret ediyor ve hiç
         * gelmemesinden kötü. Dört, art arda gelen birkaç isteğin (basış +
         * sonucun titreşimi) kaybolmamasına yetiyor; hızlı kaydırmadaki
         * onlarca isteği ise sıraya dizmiyor.
         */
        const val WORKER_QUEUE = 4

        /**
         * Bir isteğin bayatlama süresi.
         *
         * Bunu geçen istek çalınmıyor. İnsan, dokunuşla titreşim arasındaki
         * ~100ms'lik gecikmeyi ikisinin ayrı olayı olarak okumaya başlıyor;
         * o noktadan sonra çalmak, olmayan bir olayı bildirmek oluyor.
         */
        const val STALE_MILLIS = 90L

        /** Bu uyarılmanın üstündeki olaylar sönümlenmiyor. */
        const val NO_DAMPING_AROUSAL = 0.8f

        /** Her tekrarda kalan oran. */
        const val REPEAT_DECAY = 0.72f

        /**
         * Sönümlemenin inebileceği en düşük ölçek.
         *
         * Eskiden 0,35'ti ve donanım tarafında taban olmadığı için tekrarlanan
         * dokunuşlar tamamen hissedilmez oluyordu. Yükseltildi: sönümlemenin
         * işi gürültüyü kesmek, geri bildirimi yok etmek değil.
         */
        const val REPEAT_FLOOR = 0.55f

        const val REPEAT_CAP = 8

        /**
         * Tekrarın "hızlı" sayılması için üst sınır.
         *
         * Bundan uzun aralıklarla gelen aynı olay tekrar değil, ayrı bir
         * olay: kullanıcı arada başka bir şey yapmış, dokunuşu bekliyor.
         */
        const val RAPID_REPEAT_MILLIS = 320L

        const val POWER_SAVE_SCALE = 0.45f

        /** Bütçe penceresi ve o pencerede izin verilen toplam titreşim. */
        const val BUDGET_WINDOW_MILLIS = 1_000L
        const val BUDGET_MILLIS = 320L

        /** İki duygunun "aynı olay" sayılması için eksen başına tolerans. */
        const val SAME_EVENT_BAND = 0.12f
    }
}
