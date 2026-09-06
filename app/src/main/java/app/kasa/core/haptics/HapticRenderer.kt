package app.kasa.core.haptics

import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.roundToInt

/**
 * Cihazın dokunsal yetenekleri.
 *
 * ### Neden bir kez ölçülüp saklanıyor
 *
 * `arePrimitivesSupported` ve `areEnvelopeEffectsSupported` sistem servisine
 * gidiyor. Her titreşimde sormak, dokunuş başına birkaç IPC demek — ve cevap
 * cihazın ömrü boyunca değişmiyor.
 */
class VibratorCapabilities private constructor(
    val hasVibrator: Boolean,
    val envelopes: Boolean,
    val amplitude: Boolean,
    val primitives: Set<Int>
) {
    /** Elde en az bir gerçek yol var mı; yoksa motor tamamen susuyor. */
    val usable: Boolean get() = hasVibrator

    companion object {
        /**
         * Yetenekleri sorar.
         *
         * Her sorgu ayrı ayrı korunuyor. Bazı üretici katmanlarında bu
         * çağrılar beklenmedik biçimde fırlıyor ve bir titreşim sorgusunun
         * uygulamayı düşürmesi kabul edilemez; bilinmeyen yetenek "yok"
         * sayılıyor ve motor bir alt basamağa iniyor.
         */
        fun probe(vibrator: Vibrator?): VibratorCapabilities {
            if (vibrator == null || !runCatching { vibrator.hasVibrator() }.getOrDefault(false)) {
                return VibratorCapabilities(false, false, false, emptySet())
            }

            val envelopes = runCatching { vibrator.areEnvelopeEffectsSupported() }.getOrDefault(false)
            val amplitude = runCatching { vibrator.hasAmplitudeControl() }.getOrDefault(false)

            val wanted = intArrayOf(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            )
            val supported = runCatching {
                val flags = vibrator.arePrimitivesSupported(*wanted)
                wanted.filterIndexed { index, _ -> flags.getOrElse(index) { false } }.toSet()
            }.getOrDefault(emptySet())

            return VibratorCapabilities(true, envelopes, amplitude, supported)
        }
    }
}

/**
 * Jesti cihazın anladığı bir titreşime çevirir.
 *
 * ### Dört basamaklı merdiven
 *
 * Her basamak bir öncekinin gerçek yedeği; hiçbiri "olsa iyi olurdu" değil.
 * Üstten aşağı:
 *
 * 1. **Zarf** (Android 16+). Şiddet ve keskinliği doğrudan alıyor, yani
 *    jestin iki ekseni birebir karşılanıyor. Donanımdan bağımsız: sürücü
 *    kendi aktüatörüne uygun dalgayı kendisi üretiyor.
 * 2. **İlkeller** (primitive). Keskinlik doğrudan verilemiyor, en yakın
 *    ilkel seçiliyor: keskin olan `CLICK`, orta olan `TICK`, tok olan `THUD`.
 *    Çıktı bu cihazlarda zarftan daha "hazır" ama daha az esnek.
 * 3. **Dalga biçimi.** Keskinlik tamamen kayboluyor, yalnızca süre ve genlik
 *    kalıyor. Jestin ritmi korunuyor, dokusu değil.
 * 4. **Tek atış.** Ritim de kayboluyor. Yalnızca "bir şey oldu" kalıyor —
 *    ama hiç titreşim olmamasından iyi.
 *
 * ### İlkellerde bütün-ya-da-hiç kuralı
 *
 * Bir bileşimde **tek bir** desteklenmeyen ilkel varsa cihaz bileşimin
 * tamamını çalmıyor; sessizce hiçbir şey olmuyor. Bu yüzden ilkel yolu
 * yalnızca kullanılacak ilkellerin hepsi doğrulanmışsa seçiliyor ve
 * doğrulama tek tek değil küme olarak yapılıyor.
 */
object HapticRenderer {

    /** Hangi yolun kullanıldığı; motor bunu günlüğe ve uyarlamaya taşıyor. */
    enum class Path { ENVELOPE, PRIMITIVE, WAVEFORM, ONE_SHOT, NONE }

    data class Rendered(val effect: VibrationEffect?, val path: Path)

    fun render(gesture: HapticGesture, caps: VibratorCapabilities): Rendered {
        if (!caps.usable || gesture.isSilent) return Rendered(null, Path.NONE)

        if (caps.envelopes) {
            envelope(gesture)?.let { return Rendered(it, Path.ENVELOPE) }
        }
        if (caps.primitives.isNotEmpty()) {
            primitive(gesture, caps)?.let { return Rendered(it, Path.PRIMITIVE) }
        }
        waveform(gesture, caps.amplitude)?.let { return Rendered(it, Path.WAVEFORM) }
        return Rendered(oneShot(gesture), Path.ONE_SHOT)
    }

    /**
     * Algılanan şiddeti donanımın sürüş oranına çevirir.
     *
     * ### Neden doğrudan geçmiyor
     *
     * Şiddet 0..1 aralığında **algısal** bir değer: "ne kadar güçlü
     * hissetmeli". Donanımın 0..1 aralığı ise voltaj: sıfıra yakın değerlerde
     * aktüatör kıpırdıyor ama deri bunu ayırt etmiyor. İkisini birebir
     * eşlemek, "hafif bir dokunuş" isteyen her olayı hiç hissedilmeyen bir
     * titreşime çeviriyordu.
     *
     * Ölçüm: en sık çalınan olay olan dokunuş 0,39 şiddet üretiyor ve
     * tekrar sönümlemesiyle birlikte 0,15'e kadar düşüyordu — 255 üzerinden
     * 39'luk bir genlik, yani kılıfın içinden hiç duyulmayan bir şey.
     *
     * Taban, "çalmaya karar verildiyse hissedilmeli" kuralının donanım
     * tarafındaki karşılığı. Darbeler arası oranlar sıkışıyor ama sıralama
     * korunuyor ve jestin tanınmasını sağlayan şey zaten süre, keskinlik ve
     * ritim.
     */
    private fun drive(intensity: Float): Float {
        val value = intensity.coerceIn(0f, 1f)
        if (value <= 0f) return 0f
        return PERCEPTIBLE_FLOOR + (1f - PERCEPTIBLE_FLOOR) * value
    }

    // ── 1. zarf ──────────────────────────────────────────────────────────

    /**
     * Şiddet–keskinlik denetim noktalarından zarf kurar.
     *
     * Her darbe iki denetim noktası veriyor: darbenin kendisi ve ardından
     * sıfıra iniş. **Sıfırda bitmek zorunlu** — sürücü aktüatörü ancak sıfır
     * hedefiyle frenleyebiliyor, sıcak biten bir zarf donanımı çınlar hâlde
     * bırakıyor ve bir sonraki titreşim bulanık başlıyor.
     *
     * Boşluklar da sıfır şiddetli birer nokta olarak giriyor; ayrı bir
     * "bekle" kavramı yok.
     */
    private fun envelope(gesture: HapticGesture): VibrationEffect? = runCatching {
        val builder = VibrationEffect.BasicEnvelopeBuilder()
        // Başlangıç keskinliği ilk darbeninki: sıfırdan başlamak her jesti
        // tok bir girişle açıyordu, keskin olması gerekenler dâhil.
        builder.setInitialSharpness(gesture.beats.first().sharpness.coerceIn(0f, 1f))

        gesture.beats.forEach { beat ->
            builder.addControlPoint(
                drive(beat.intensity),
                beat.sharpness.coerceIn(0f, 1f),
                beat.durationMs.toLong().coerceAtLeast(1L)
            )
            // Darbenin kapanışı: aynı keskinlikte sıfıra iniş.
            builder.addControlPoint(
                0f,
                beat.sharpness.coerceIn(0f, 1f),
                (beat.gapMs.coerceAtLeast(MIN_RELEASE_MILLIS)).toLong()
            )
        }
        builder.build()
    }.getOrNull()

    // ── 2. ilkeller ──────────────────────────────────────────────────────

    /**
     * Her darbeyi keskinliğine en yakın ilkele eşler.
     *
     * Eşleme öncesinde **bütün** jestin ihtiyaç duyduğu ilkeller toplanıp
     * destek kontrol ediliyor: bir tanesi eksikse bu yol tamamen bırakılıyor,
     * çünkü kısmi destek diye bir şey yok.
     */
    private fun primitive(gesture: HapticGesture, caps: VibratorCapabilities): VibrationEffect? {
        val chosen = gesture.beats.map { primitiveFor(it.sharpness) }
        if (!chosen.all { it in caps.primitives }) return null

        return runCatching {
            val composition = VibrationEffect.startComposition()
            gesture.beats.forEachIndexed { index, beat ->
                composition.addPrimitive(
                    chosen[index],
                    drive(beat.intensity),
                    if (index == 0) 0 else gesture.beats[index - 1].gapMs
                )
            }
            composition.compose()
        }.getOrNull()
    }

    /**
     * Keskinlikten ilkele.
     *
     * Üç bant: tok, orta, keskin. Daha ince bir eşleme mümkün ama ilkellerin
     * kendi karakterleri arasındaki fark zaten bu üç banttan daha ince
     * değil — `TICK` ile `CLICK` arasında ara bir ilkel yok.
     */
    private fun primitiveFor(sharpness: Float): Int = when {
        sharpness >= 0.66f -> VibrationEffect.Composition.PRIMITIVE_CLICK
        sharpness >= 0.33f -> VibrationEffect.Composition.PRIMITIVE_TICK
        else -> VibrationEffect.Composition.PRIMITIVE_THUD
    }

    // ── 3. dalga biçimi ──────────────────────────────────────────────────

    /**
     * Süre ve genlik dizileri.
     *
     * Genlik denetimi yoksa dizi verilmiyor: cihaz açık/kapalı deseni olarak
     * çalıyor. Bu durumda keskinlik de şiddet de kayboluyor, geriye yalnızca
     * ritim kalıyor — ve ritim jestin tanınabilir kalan son parçası.
     *
     * Dizi her zaman bir sessizlikle başlıyor (`0`), çünkü `createWaveform`
     * ilk değeri bekleme süresi sayıyor.
     */
    private fun waveform(gesture: HapticGesture, amplitudeControl: Boolean): VibrationEffect? =
        runCatching {
            val timings = mutableListOf(0L)
            val amplitudes = mutableListOf(0)
            gesture.beats.forEach { beat ->
                timings += beat.durationMs.toLong().coerceAtLeast(1L)
                amplitudes += (drive(beat.intensity) * 255f).roundToInt().coerceIn(1, 255)
                if (beat.gapMs > 0) {
                    timings += beat.gapMs.toLong()
                    amplitudes += 0
                }
            }
            if (amplitudeControl) {
                VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
            } else {
                VibrationEffect.createWaveform(timings.toLongArray(), -1)
            }
        }.getOrNull()

    // ── 4. tek atış ──────────────────────────────────────────────────────

    /**
     * Son çare: jestin toplam süresi ve tepe şiddetiyle tek bir titreşim.
     *
     * Ritim kayboluyor ama olayın **olduğu** bilgisi kalıyor. Süre üst
     * sınırla kesiliyor: bir jestin toplamı uzun olabiliyor ve o kadar süren
     * kesintisiz bir titreşim, geri bildirim olmaktan çıkıp rahatsızlığa
     * dönüşüyor.
     */
    private fun oneShot(gesture: HapticGesture): VibrationEffect? = runCatching {
        VibrationEffect.createOneShot(
            gesture.totalMillis.toLong().coerceIn(10L, MAX_ONE_SHOT_MILLIS),
            (drive(gesture.peakIntensity) * 255f).roundToInt().coerceIn(1, 255)
        )
    }.getOrNull()

    /**
     * Donanımın hissedilir olduğu en düşük sürüş oranı.
     *
     * Cihazdan cihaza değişiyor ama bu civarı çoğu doğrusal titreşim
     * aktüatöründe eşiğin biraz üstünde: yeterince düşük ki "hafif" olan
     * hâlâ hafif hissetsin, yeterince yüksek ki hissedilsin.
     */
    private const val PERCEPTIBLE_FLOOR = 0.32f

    /** Zarfın sıfıra inmesi için gereken en kısa süre. */
    private const val MIN_RELEASE_MILLIS = 12

    private const val MAX_ONE_SHOT_MILLIS = 180L
}
