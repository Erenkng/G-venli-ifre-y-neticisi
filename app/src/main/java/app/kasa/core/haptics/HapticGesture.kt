package app.kasa.core.haptics

import androidx.compose.runtime.Immutable

/**
 * Cihazdan bağımsız titreşim tarifi.
 *
 * ### Neden araya bir katman giriyor
 *
 * Duygu ile `VibrationEffect` arasında doğrudan bir yol kurmak mümkündü ama o
 * yol her donanım yolu için baştan yazılmak zorunda kalırdı: zarf destekleyen
 * cihaz için bir kere, ilkel (primitive) destekleyenler için bir kere, yalnızca
 * genlik denetimi olanlar için bir kere daha. Üçü de aynı duyguyu üretmeye
 * çalışırken üçü de ayrı ayrı yanlış olabilirdi.
 *
 * Jest bu ortak dili kuruyor: sentezleyici duyguyu **darbelere** çeviriyor,
 * oluşturucu (renderer) darbeleri cihazın anladığı şeye. Yeni bir donanım yolu
 * eklemek tek bir oluşturucu yazmak demek; yeni bir duygu eklemek hiçbir
 * donanım koduna dokunmamak demek.
 *
 * ### Darbenin iki ekseni
 *
 * [Beat.intensity] ve [Beat.sharpness], Android 16'nın zarf API'sinin
 * (`BasicEnvelopeBuilder`) doğrudan aldığı iki eksen — ve bu tesadüf değil:
 * ikisi de insanların titreşimi ayırt ederken kullandığı boyutlar. Şiddet
 * "ne kadar çok", keskinlik "ne kadar net". Aynı şiddette ama farklı
 * keskinlikte iki darbe, biri tıklama öteki tok bir vuruş gibi hissediyor.
 */
@Immutable
data class HapticGesture(
    val beats: List<Beat>,
    /**
     * Bu jestin duygusal kaynağı.
     *
     * Oluşturucu bunu **kullanmıyor**; motor tekrar sönümlemesi ve günlük
     * tutmak için taşıyor. Jesti üreten duyguyu kaybetmek, "bu titreşim neden
     * böyle" sorusunu yanıtlanamaz kılardı.
     */
    val source: Affect
) {
    /**
     * Tek bir darbe.
     *
     * @param intensity 0..1 — algılanan güç. 0 sessizlik değil, **en düşük
     *        hissedilir** seviye; gerçek sessizlik [gapMs] ile yapılıyor.
     * @param sharpness 0..1 — 0 tok ve yayvan, 1 keskin ve net.
     * @param durationMs darbenin süresi.
     * @param gapMs bu darbeden sonraki sessizlik.
     */
    @Immutable
    data class Beat(
        val intensity: Float,
        val sharpness: Float,
        val durationMs: Int,
        val gapMs: Int = 0
    )

    /** Jestin toplam süresi, boşluklar dâhil. */
    val totalMillis: Int get() = beats.sumOf { it.durationMs + it.gapMs }

    /**
     * Aktüatörün gerçekten çalıştığı süre.
     *
     * Bütçe bunu okuyor, toplam süreyi değil: iki darbe arasındaki sessizlik
     * pil harcamıyor ve uğultuya katkısı yok. Boşlukları da saymak, ritimli
     * jestleri haksız yere pahalı gösteriyor ve peş peşe gelen birkaç olayda
     * bütçeyi erken tüketiyordu.
     */
    val vibratingMillis: Int get() = beats.sumOf { it.durationMs }

    /** En yüksek darbe şiddeti; kullanıcı ayarının tavanı buna uygulanıyor. */
    val peakIntensity: Float get() = beats.maxOfOrNull { it.intensity } ?: 0f

    /**
     * Bütün jesti ölçekler.
     *
     * Yalnızca şiddet ölçekleniyor, süre ve keskinlik değil: bir titreşimi
     * "daha sessiz" yapmak onu kısaltmak ya da yumuşatmak değil. Kısaltmak
     * jesti tanınmaz hâle getirir, yumuşatmak başka bir duyguya çevirir.
     */
    fun scaled(factor: Float): HapticGesture {
        val f = factor.coerceIn(0f, 1f)
        if (f >= 0.999f) return this
        return copy(beats = beats.map { it.copy(intensity = (it.intensity * f).coerceIn(0f, 1f)) })
    }

    /** Hiç hissedilmeyecek kadar zayıf mı: çalmaya değmez. */
    val isSilent: Boolean get() = beats.isEmpty() || peakIntensity < AUDIBLE_FLOOR

    companion object {
        /**
         * Altında titreşimin hissedilmediği eşik.
         *
         * Aktüatörler bu seviyenin altında da hareket ediyor ama insan derisi
         * ayırt etmiyor. Çalmak yerine hiç çalmamak, pil harcamayan ve
         * kullanıcıya "bozuk" hissi vermeyen davranış.
         */
        const val AUDIBLE_FLOOR = 0.06f
    }
}
