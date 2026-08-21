package app.kasa.core.haptics

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Duyguyu darbelere çeviren sentezleyici.
 *
 * ### Burada ne oluyor
 *
 * Bu dosya motorun "aklı": elinde hiçbir hazır desen yok, yalnızca dört
 * eksenli bir duygu ve bu eksenleri titreşimin fiziksel özelliklerine bağlayan
 * kurallar var. Aynı kurallar sözlükteki on dört duyguyu da, ileride
 * eklenecek olanı da, ikisinin karışımını da üretebiliyor.
 *
 * ### Eksenler nasıl bağlanıyor
 *
 * **Uyarılma → şiddet.** Doğrudan ama doğrusal değil. İnsan algısı güce göre
 * üstel: iki kat daha güçlü bir titreşim iki kat daha güçlü hissetmiyor.
 * Stevens'ın üs yasası dokunsal şiddet için ~0.7 civarında bir üs veriyor,
 * yani eşit algılanan adımlar için fiziksel değer daha hızlı büyümeli.
 *
 * **Hoşluk → keskinlik.** Olumsuz olan sert, olumlu olan yumuşak. Bunun sebebi
 * gerçek dünya: kırılan, çarpan, reddedilen şeyler keskin bir geçiş üretiyor;
 * oturan, kapanan, tamamlanan şeyler yayvan. Bağ ters yönlü — hoşluk arttıkça
 * keskinlik düşüyor.
 *
 * **Kesinlik → ritim.** Tamamlanmış bir olay **tek** darbe: söylenecek şey
 * söylendi. Belirsiz ya da devam eden bir durum tekrarlı ve eşit aralıklı
 * değil — düzenli tekrar "çalışıyor" değil "takıldı" hissi veriyor.
 *
 * **Ağırlık → süre ve zarf.** Hafif olan tek ve kısa. Ağır olan yükselen bir
 * giriş alıyor: önce zemin, sonra darbe. Bir şeyin ağır olduğu, ona
 * ulaşmanın zaman almasından anlaşılıyor.
 *
 * ### Neden bu sayılar
 *
 * Süreler donanım kılavuzundan geliyor ve keyfi değil: 10 ms altındaki
 * boşluklar tek bir olay gibi duyuluyor, 50 ms civarı iki bağlı olay, 100 ms
 * üstü iki ayrı olay. Şiddet farkının hissedilmesi için oran ~1.4 gerekiyor —
 * bu yüzden üretilen darbeler arasında bilerek büyük farklar var, ince
 * ayrımlar zaten algılanmıyor.
 */
object HapticSynth {

    /**
     * Duyguyu jeste çevirir.
     *
     * Fonksiyon **saf**: aynı duygu her zaman aynı jesti veriyor. Rastgelelik
     * yok, çünkü aynı olayın her seferinde farklı hissetmesi kullanıcının
     * kurduğu eşleşmeyi bozar — dokunsal geri bildirimin bütün değeri o
     * eşleşmede.
     */
    fun compose(affect: Affect): HapticGesture {
        val beats = when {
            // Devam eden, sonucu belli olmayan durum: nabız gibi.
            affect.certainty < 0.3f -> pulsing(affect)
            // Olumsuz ve yüksek uyarılma: reddetme ya da alarm.
            affect.valence < -0.35f -> negative(affect)
            // Ağır ve olumlu: açılma, tamamlanma.
            affect.weight >= 0.4f && affect.valence > 0.25f -> arriving(affect)
            // Geri kalanı: tek darbe, eksenlere göre biçimlenmiş.
            else -> single(affect)
        }
        return HapticGesture(beats = beats, source = affect)
    }

    // ── eksen dönüşümleri ────────────────────────────────────────────────

    /**
     * Uyarılmadan algılanan şiddete.
     *
     * Üs 1'den küçük olsaydı düşük uyarılmalar birbirine yapışırdı; 1'den
     * büyük olduğu için düşük uçta ayrım korunuyor ve yüksek uçta doyum
     * oluyor. Taban [HapticGesture.AUDIBLE_FLOOR]'un üstünde: bir titreşim
     * çalmaya karar verildiyse hissedilmeli.
     */
    private fun intensityOf(arousal: Float): Float =
        (0.14f + 0.86f * arousal.coerceIn(0f, 1f).pow(STEVENS_EXPONENT)).coerceIn(0f, 1f)

    /**
     * Hoşluktan keskinliğe.
     *
     * Nötr (0) hoşluk orta keskinlikte kalıyor; iki uç da kendi yönüne
     * gidiyor. Uyarılma keskinliği hafifçe yukarı çekiyor çünkü aynı hoşlukta
     * ama daha acil bir olay daha net olmalı.
     */
    private fun sharpnessOf(valence: Float, arousal: Float): Float {
        val fromValence = 0.5f - valence.coerceIn(-1f, 1f) * 0.42f
        return (fromValence + arousal * 0.16f).coerceIn(0f, 1f)
    }

    /**
     * Ağırlıktan süreye.
     *
     * Alt sınır 8 ms: daha kısası çoğu aktüatörde tam genliğe ulaşamadan
     * bitiyor ve cihazdan cihaza değişen bir şey duyuluyor. Üst sınır 220 ms:
     * bunun ötesi dokunsal geri bildirim olmaktan çıkıp uyarıya dönüşüyor.
     */
    private fun durationOf(weight: Float): Int =
        (MIN_BEAT_MILLIS + (MAX_BEAT_MILLIS - MIN_BEAT_MILLIS) * weight.coerceIn(0f, 1f).pow(0.8f))
            .roundToInt()

    // ── jest aileleri ────────────────────────────────────────────────────

    /**
     * Tek darbe: dokunuş, tık, onay.
     *
     * Ağırlık belli bir eşiği geçtiğinde önüne çok hafif bir "hazırlık"
     * darbesi giriyor. Bu, ağır bir nesnenin hareket etmeden önceki
     * gecikmesinin karşılığı; onsuz ağır bir onay yalnızca "daha uzun" bir
     * dokunuş gibi hissediyor.
     */
    private fun single(affect: Affect): List<HapticGesture.Beat> {
        val intensity = intensityOf(affect.arousal)
        val sharpness = sharpnessOf(affect.valence, affect.arousal)
        val duration = durationOf(affect.weight)

        val main = HapticGesture.Beat(intensity, sharpness, duration)
        if (affect.weight < PREPARATION_THRESHOLD) return listOf(main)

        // Hazırlık darbesi ana darbeden belirgin şekilde zayıf: 1.4 oranının
        // altında kalan bir fark zaten hissedilmiyor, yani ikisi tek bir
        // darbe gibi duyulurdu.
        val prep = HapticGesture.Beat(
            intensity = intensity * 0.34f,
            sharpness = sharpness * 0.6f,
            durationMs = (duration * 0.35f).roundToInt().coerceAtLeast(MIN_BEAT_MILLIS),
            gapMs = CONNECTED_GAP_MILLIS
        )
        return listOf(prep, main)
    }

    /**
     * Yükselerek gelen jest: kilit açıldı, üretildi, tamamlandı.
     *
     * Üç darbe artan şiddette ve **azalan** keskinlikte. Yükselme "bir şey
     * oluyor" diyor, son darbenin yumuşaklığı "ve oldu" diyor. Sert bitseydi
     * olumlu bir olay onay değil uyarı gibi hissederdi.
     */
    private fun arriving(affect: Affect): List<HapticGesture.Beat> {
        val peak = intensityOf(affect.arousal)
        val sharp = sharpnessOf(affect.valence, affect.arousal)
        val span = durationOf(affect.weight)

        return listOf(
            HapticGesture.Beat(
                intensity = peak * 0.30f,
                sharpness = (sharp + 0.18f).coerceAtMost(1f),
                durationMs = (span * 0.28f).roundToInt().coerceAtLeast(MIN_BEAT_MILLIS),
                gapMs = CONNECTED_GAP_MILLIS
            ),
            HapticGesture.Beat(
                intensity = peak * 0.62f,
                sharpness = sharp,
                durationMs = (span * 0.34f).roundToInt().coerceAtLeast(MIN_BEAT_MILLIS),
                gapMs = CONNECTED_GAP_MILLIS
            ),
            HapticGesture.Beat(
                intensity = peak,
                // Son darbe en yumuşak: oturma hissi buradan geliyor.
                sharpness = (sharp - 0.22f).coerceAtLeast(0f),
                durationMs = (span * 0.62f).roundToInt().coerceAtLeast(MIN_BEAT_MILLIS)
            )
        )
    }

    /**
     * Olumsuz jest: yanlış parola, sızıntı, kalıcı silme.
     *
     * İki sert darbe, aralarında **fark edilir** bir boşluk. Tek darbe olsaydı
     * onaydan ayırt edilemezdi; ikisi arasındaki boşluk "hayır" ile "tamam"
     * arasındaki farkın tamamı.
     *
     * Uyarılma çok yüksekse (alarm) üçüncü bir darbe ekleniyor ve boşluklar
     * kısalıyor: acele eden bir şey aralarında beklemiyor.
     */
    private fun negative(affect: Affect): List<HapticGesture.Beat> {
        val intensity = intensityOf(affect.arousal)
        // Olumsuz olayda keskinlik tabanı yüksek tutuluyor: yumuşak bir
        // reddetme, reddetme gibi hissetmiyor.
        val sharpness = sharpnessOf(affect.valence, affect.arousal).coerceAtLeast(0.72f)
        val duration = durationOf(affect.weight * 0.7f)
        val urgent = affect.arousal >= URGENT_AROUSAL
        val gap = if (urgent) URGENT_GAP_MILLIS else SEPARATE_GAP_MILLIS

        val beats = mutableListOf(
            HapticGesture.Beat(intensity, sharpness, duration, gap),
            HapticGesture.Beat(intensity, sharpness, duration, if (urgent) gap else 0)
        )
        if (urgent) {
            beats += HapticGesture.Beat(intensity, sharpness, duration)
        }
        return beats
    }

    /**
     * Nabız: sonucu henüz belli olmayan bir iş sürüyor.
     *
     * Aralıklar bilerek **eşit değil**. Eşit aralıklı bir tekrar makine gibi
     * duyuluyor ve "takıldı" hissi veriyor; hafifçe değişen aralık canlı bir
     * şeyin çalıştığını söylüyor. Değişim sabit bir örüntüden geliyor,
     * rastgeleden değil — aynı olay her seferinde aynı hissetmeli.
     */
    private fun pulsing(affect: Affect): List<HapticGesture.Beat> {
        val intensity = intensityOf(affect.arousal) * 0.7f
        val sharpness = sharpnessOf(affect.valence, affect.arousal) * 0.75f
        val duration = durationOf(affect.weight * 0.4f)

        return PULSE_JITTER.mapIndexed { index, jitter ->
            HapticGesture.Beat(
                // Ortadaki darbe en güçlü: nefes alan bir şeyin karşılığı.
                intensity = intensity * (0.7f + 0.3f * (1f - abs(index - 1) * 0.5f)),
                sharpness = sharpness,
                durationMs = duration,
                gapMs = if (index == PULSE_JITTER.lastIndex) 0 else (PULSE_GAP_MILLIS * jitter).roundToInt()
            )
        }
    }

    /** Algılanan şiddetin fiziksel genliğe üssü (Stevens güç yasası). */
    private const val STEVENS_EXPONENT = 0.72f

    /** Bu ağırlığın üstündeki tek darbeler hazırlık darbesi alıyor. */
    private const val PREPARATION_THRESHOLD = 0.35f

    private const val URGENT_AROUSAL = 0.85f

    /** Tek olay gibi duyulan boşluk. */
    private const val CONNECTED_GAP_MILLIS = 8

    /** İki bağlı olay gibi duyulan boşluk. */
    private const val SEPARATE_GAP_MILLIS = 62

    /** Acil bir dizide daha sıkı boşluk. */
    private const val URGENT_GAP_MILLIS = 38

    private const val PULSE_GAP_MILLIS = 90

    /** Nabız aralıklarının sabit düzensizliği; rastgele değil, tekrarlanabilir. */
    private val PULSE_JITTER = listOf(1.0f, 0.78f, 1.18f)

    private const val MIN_BEAT_MILLIS = 8
    private const val MAX_BEAT_MILLIS = 220
}
