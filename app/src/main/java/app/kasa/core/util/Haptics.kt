package app.kasa.core.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
        WORKING,

        /** Panoya kopyalandı. */
        COPY,

        /** Gizli bir değer panoya alındı. */
        SECRET,

        /** Korumanın kendisi değişti: ana parola, PIN, biyometri, anahtar. */
        SEAL,

        /** Çöp kutusuna atıldı; geri alınabilir. */
        DISCARD,

        /** Şimdi olmaz: bekleme süresi işliyor. */
        BLOCKED,

        /** Dosya eklendi. */
        ATTACH,

        /** Ek kaldırıldı, bir koruma katmanı silindi. */
        DETACH,

        /** Gizli alan açıldı ya da kapandı. */
        REVEAL,

        /** Form kabul etmedi. */
        DENY
    }

    /**
     * Dokunuş tıkırtısı açık mı.
     *
     * [Kind.TAP] uygulamadaki her dokunulabilir yüzeyden geliyor: parmak
     * yüzeye değdiği an çalınıyor ve sonucu olan denetimlerde onun ardından
     * bir de sonucun titreşimi geliyor. Bazı kullanıcı için bu çok; ama
     * "kaydedildi", "silindi", "kilit açıldı" gibi anlamlı olayların da
     * susması istenmiyor. Ayrı bir anahtar bu ikisini ayırıyor: tıkırtı
     * kapanıyor, olaylar duruyor.
     */
    @Volatile
    var touchTicks: Boolean = true

    fun play(kind: Kind) {
        if (kind == Kind.TAP && !touchTicks) return
        engine.play(affectOf(kind))
    }

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
        Kind.COPY -> Affect.Copy
        Kind.SECRET -> Affect.Secret
        Kind.SEAL -> Affect.Seal
        Kind.DISCARD -> Affect.Discard
        Kind.BLOCKED -> Affect.Wait
        Kind.ATTACH -> Affect.Attach
        Kind.DETACH -> Affect.Detach
        Kind.REVEAL -> Affect.Reveal
        Kind.DENY -> Affect.Deny
    }
}

/**
 * Dokunsal motorun bileşen ağacındaki karşılığı.
 *
 * Bir onay kutusunun ya da göz tuşunun titreşim verebilmek için görünüm
 * modeline erişmesi gerekmiyor — ve gerekseydi, o bileşenler yalnızca titreşim
 * uğruna kendilerini bir ekrana bağlamak zorunda kalırdı. Motor burada duruyor,
 * sağlanmadığı yerde (önizlemeler, testler) sessizce yok sayılıyor.
 */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

/**
 * Bileşenler için kısayol.
 *
 * Dönen işlev `remember` ile sabitleniyor: her yeniden birleştirmede yeni bir
 * lambda üretmek, onu parametre olarak alan bileşenleri gereksiz yere yeniden
 * birleştiriyor.
 */
@Composable
fun rememberHapticPlayer(): (Haptics.Kind) -> Unit {
    val haptics = LocalHaptics.current
    return remember(haptics) { { kind: Haptics.Kind -> haptics?.play(kind) } }
}
