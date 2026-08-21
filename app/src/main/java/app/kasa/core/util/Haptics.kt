package app.kasa.core.util

import android.content.Context
import app.kasa.core.haptics.Affect
import app.kasa.core.haptics.SmartHaptics

/**
 * Dokunsal geri bildirimin uygulama içindeki yüzü.
 *
 * ### Neden hâlâ burada
 *
 * Asıl iş [SmartHaptics] içinde: duygu modeli, sentez, cihaz yetenek
 * merdiveni ve uyarlama. Bu sınıf yalnızca uygulamanın kırk küsur çağrı
 * yerinin tanıdığı arayüzü koruyor.
 *
 * Çağrı yerlerini doğrudan duyguya geçirmek daha temiz görünürdü ama bir
 * ekran kodunun "valence 0.7, arousal 0.4" yazması yanlış soyutlama: ekran
 * ne olduğunu biliyor ("kopyalandı"), o olayın ne hissettirmesi gerektiğini
 * bilmesi gerekmiyor. Eşleme burada, tek bir yerde duruyor ve bir olayın
 * karakterini değiştirmek tek satırlık bir iş.
 *
 * ### Türler neyi anlatıyor
 *
 * Adları eski tablodan kalma ama artık **desen** değil, duygu sözlüğünde bir
 * girdiye işaret ediyorlar. `SUCCESS` artık "10 ms sonra 18 ms" değil,
 * "tamamlanmış, hoş, hafif bir olay" — ve o tarif her cihazda o cihazın
 * verebileceği en iyi şekilde çalınıyor.
 */
class Haptics(context: Context) {

    private val engine = SmartHaptics(context)

    /** Kullanıcının ayarı. Kapalıyken motor hiçbir şey çalmıyor. */
    var enabled: Boolean
        get() = engine.enabled
        set(value) {
            engine.enabled = value
        }

    /**
     * Kullanıcının yoğunluk tercihi (0..1).
     *
     * Sistem titreşim ayarının yerine geçmiyor, üstüne çarpılıyor: sistemde
     * kısılmış bir titreşimi buradan geri açmak mümkün değil.
     */
    var intensity: Float
        get() = engine.intensity
        set(value) {
            engine.intensity = value
        }

    /** Cihazda titreşim donanımı var mı. */
    val available: Boolean get() = engine.available

    /**
     * Etkileşim türleri.
     *
     * Uygulamanın olay sözlüğü. Yeni bir olay eklemek buraya bir satır ve
     * [affectOf] içine bir eşleme eklemek demek — hiçbir titreşim deseni
     * yazmadan.
     */
    enum class Kind {
        /** Bir şeye dokunuldu. */
        TAP,

        /** Sekme değişti, ekran açıldı. */
        NAV,

        /** Orta ağırlıkta bir eylem gerçekleşti. */
        MEDIUM,

        /** Kopyalandı, kaydedildi, tamamlandı. */
        SUCCESS,

        /** Hata, reddetme, dikkat isteyen durum. */
        WARNING,

        /** Anahtar açıldı/kapandı. */
        TOGGLE,

        /** Kaydırıcı adımı, sayaç tıkırtısı. */
        TICK,

        /** Kasa açıldı. */
        UNLOCK,

        /** Kasa kilitlendi. */
        LOCK,

        /** Basılı tutma eşiği geçildi. */
        THRESHOLD,

        /** Bir şey üretildi. */
        CREATE,

        /** Silme geri alındı. */
        UNDO,

        /** Kalıcı silme, geri alınamaz eylem. */
        DESTRUCTIVE,

        /** Güvenlik bulgusu: sızmış parola. */
        ALARM,

        /** Uzun bir iş sürüyor. */
        WORKING
    }

    fun play(kind: Kind) = engine.play(affectOf(kind))

    /**
     * İki olayın karışımı.
     *
     * "Kaydedildi ama parola zayıf" gibi bileşik anlar için. Arka arkaya iki
     * titreşim çalmak, iki ayrı şey olmuş gibi hissettiriyor.
     */
    fun play(primary: Kind, secondary: Kind, fraction: Float = 0.5f) =
        engine.play(affectOf(primary), affectOf(secondary), fraction)

    /** Sözlükte karşılığı olmayan, kendi karakterini taşıyan bir an için. */
    fun play(affect: Affect) = engine.play(affect)

    private fun affectOf(kind: Kind): Affect = when (kind) {
        Kind.TAP -> Affect.Touch
        Kind.NAV -> Affect.Navigate
        Kind.MEDIUM -> Affect.Confirm
        Kind.SUCCESS -> Affect.Confirm
        Kind.WARNING -> Affect.Reject
        Kind.TOGGLE -> Affect.Toggle
        Kind.TICK -> Affect.Tick
        Kind.UNLOCK -> Affect.Unlock
        Kind.LOCK -> Affect.Lock
        Kind.THRESHOLD -> Affect.Threshold
        Kind.CREATE -> Affect.Create
        Kind.UNDO -> Affect.Undo
        Kind.DESTRUCTIVE -> Affect.Destructive
        Kind.ALARM -> Affect.Alarm
        Kind.WORKING -> Affect.Working
    }
}
