package app.kasa.core.haptics

import androidx.compose.runtime.Immutable

/**
 * Bir titreşimin **duygusal** tanımı.
 *
 * ### Neden desen tablosu değil
 *
 * Önceki dokunsal katman yedi sabit desenden oluşuyordu: `TAP` şu süre, şu
 * genlik; `SUCCESS` şu iki darbe. Bu yaklaşımın iki kırılma noktası var.
 *
 * Birincisi, tablonun büyümesi. Yeni bir olay geldiğinde ("parola sızmış",
 * "kayıt çoğaltıldı", "içe aktarma bitti") elde iki seçenek kalıyor: var olan
 * bir deseni yeniden kullanmak — yani iki farklı olayı aynı hissettirmek — ya
 * da tabloya elle uydurulmuş yeni bir satır eklemek. Yirmi satırdan sonra
 * hiçbiri ötekine bakılarak seçilmemiş yirmi desen oluyor.
 *
 * İkincisi, tablonun donanımı yok sayması. Sabit süre ve genlik, kendisini
 * yazan geliştiricinin telefonunda doğru hissettiriyor; başka bir aktüatörde
 * aynı sayılar bambaşka bir şey üretiyor.
 *
 * ### Onun yerine: duygu uzayı
 *
 * Burada bir olay **ne hissettirmesi gerektiğiyle** tanımlanıyor, nasıl
 * üretileceğiyle değil. Dört eksen var ve dördü de insanların titreşimi
 * ayırt ederken gerçekten kullandığı boyutlara karşılık geliyor:
 *
 * - **[valence]** — hoşluk. Olumlu bir olay yumuşak ve yuvarlak, olumsuz olan
 *   sert ve kesik hissettiriyor. Bu, üretilen dalganın **keskinliğine**
 *   dönüşüyor.
 * - **[arousal]** — uyarılma. Olayın ne kadar dikkat istediği. Üretilen
 *   dalganın **şiddetine** dönüşüyor.
 * - **[certainty]** — kesinlik. Tamamlanmış bir iş tek ve net bir darbe;
 *   belirsiz ya da devam eden bir durum tekrarlı ve düzensiz. Dalganın
 *   **ritmine** dönüşüyor.
 * - **[weight]** — ağırlık. Küçük bir onay ile kalıcı bir silme arasındaki
 *   fark. Dalganın **süresine** dönüşüyor.
 *
 * ### Neden bu dört eksen
 *
 * Valence–arousal ikilisi duygu araştırmasında yerleşik (Russell'ın dairesel
 * modeli) ve dokunsal geri bildirim çalışmalarında da tekrar tekrar aynı iki
 * boyuta düşülüyor: "ne kadar hoş" ve "ne kadar yoğun". Kesinlik ve ağırlık
 * ise arayüzden geliyor — bir arayüzde aynı hoşlukta ve yoğunlukta ama biri
 * "bitti" öteki "devam ediyor" diyen iki olay var, ve ikisi aynı
 * hissetmemeli.
 *
 * Eksenlerin hepsi -1..1 ya da 0..1 aralığında ve **sürekli**: iki olay
 * arasında ara bir değer seçmek anlamlı, çünkü sentezleyici o ara değeri de
 * çalabiliyor.
 */
@Immutable
data class Affect(
    /** Hoşluk: -1 tamamen olumsuz, 0 nötr, +1 tamamen olumlu. */
    val valence: Float,
    /** Uyarılma: 0 neredeyse fark edilmez, 1 acil. */
    val arousal: Float,
    /** Kesinlik: 0 belirsiz/devam eden, 1 tamamlanmış/kesin. */
    val certainty: Float,
    /** Ağırlık: 0 anlık ve önemsiz, 1 kalıcı ve geri alınamaz. */
    val weight: Float
) {
    init {
        require(valence in -1f..1f) { "valence -1..1 olmalı" }
        require(arousal in 0f..1f) { "arousal 0..1 olmalı" }
        require(certainty in 0f..1f) { "certainty 0..1 olmalı" }
        require(weight in 0f..1f) { "weight 0..1 olmalı" }
    }

    /**
     * İki duyguyu karıştırır.
     *
     * Bileşik olayların karşılığı: "kaydedildi **ve** zayıf parola" tek bir
     * duygu, iki ayrı titreşim değil. Arka arkaya iki titreşim çalmak, iki
     * ayrı şey olmuş gibi hissettiriyor.
     */
    fun blend(other: Affect, fraction: Float): Affect {
        val f = fraction.coerceIn(0f, 1f)
        fun mix(a: Float, b: Float) = a + (b - a) * f
        return Affect(
            valence = mix(valence, other.valence).coerceIn(-1f, 1f),
            arousal = mix(arousal, other.arousal).coerceIn(0f, 1f),
            certainty = mix(certainty, other.certainty).coerceIn(0f, 1f),
            weight = mix(weight, other.weight).coerceIn(0f, 1f)
        )
    }

    /**
     * Duygunun şiddetini ölçekler; işaretini ve karakterini korur.
     *
     * Tekrar sönümlemesi ve kullanıcının yoğunluk tercihi bunu kullanıyor:
     * aynı olay üst üste geldiğinde daha **sessiz** çalınıyor, başka bir şeye
     * dönüşmüyor. Ağırlık da hafifçe düşüyor çünkü tekrarlanan bir olay
     * öncekinden daha az önemli hâle geliyor.
     */
    fun scaled(factor: Float): Affect {
        val f = factor.coerceIn(0f, 1f)
        return copy(
            arousal = (arousal * f).coerceIn(0f, 1f),
            weight = (weight * (0.55f + 0.45f * f)).coerceIn(0f, 1f)
        )
    }

    /**
     * Sistem titreşim kanalı.
     *
     * Kullanıcının "dokunsal geri bildirim" ile "bildirim titreşimi" için ayrı
     * ayarları var ve Rahatsız Etmeyin kipi ikisini farklı ele alıyor. Bir
     * dokunuş onayını bildirim kanalından çalmak, kullanıcının kapattığını
     * sandığı şeyi çalmaya devam etmek olurdu.
     *
     * Ayrım ağırlıktan geliyor: hafif ve anlık olan dokunuş, ağır olan bildirim.
     */
    val channel: HapticChannel
        get() = when {
            weight >= 0.72f && arousal >= 0.6f -> HapticChannel.ALERT
            weight >= 0.5f -> HapticChannel.NOTIFICATION
            else -> HapticChannel.TOUCH
        }

    companion object {
        /**
         * Uygulamanın duygu sözlüğü.
         *
         * Bunlar **desen** değil, niyet. Her biri "bu an kullanıcıya ne
         * hissettirmeli" sorusunun cevabı; nasıl çalınacağına sentezleyici ve
         * cihaz karar veriyor.
         */

        /** Bir şeye dokunuldu. Neredeyse fark edilmeyen, yalnızca "duydum". */
        val Touch = Affect(valence = 0.1f, arousal = 0.18f, certainty = 0.9f, weight = 0.05f)

        /** Kaydırıcı adımı, sayaç tıkırtısı. Dokunuştan da hafif. */
        val Tick = Affect(valence = 0f, arousal = 0.10f, certainty = 0.95f, weight = 0.02f)

        /** Sekme değişti, ekran açıldı. Yer değiştirmenin karşılığı. */
        val Navigate = Affect(valence = 0.2f, arousal = 0.30f, certainty = 0.85f, weight = 0.15f)

        /** Anahtar açıldı/kapandı. İki durumlu bir şeyin oturması. */
        val Toggle = Affect(valence = 0.25f, arousal = 0.28f, certainty = 1f, weight = 0.12f)

        /** Basılı tutma eşiği geçildi; menü açılmak üzere. */
        val Threshold = Affect(valence = 0.05f, arousal = 0.42f, certainty = 0.35f, weight = 0.2f)

        /** Kopyalandı, kaydedildi. Kısa ve hoş bir tamamlanma. */
        val Confirm = Affect(valence = 0.7f, arousal = 0.4f, certainty = 1f, weight = 0.25f)

        /** Kasa açıldı. Günün en beklenen anı: yumuşak, açılan, tamamlanmış. */
        val Unlock = Affect(valence = 0.85f, arousal = 0.5f, certainty = 1f, weight = 0.5f)

        /** Kasa kilitlendi. Kapanan, ağır, olumlu ama sakin. */
        val Lock = Affect(valence = 0.3f, arousal = 0.35f, certainty = 1f, weight = 0.45f)

        /** Yanlış parola. Kesik, sert, kesinlikle olumsuz. */
        val Reject = Affect(valence = -0.75f, arousal = 0.7f, certainty = 1f, weight = 0.35f)

        /** Sızmış parola, güvenlik bulgusu. Rahatsız etmesi **gereken** an. */
        val Alarm = Affect(valence = -0.9f, arousal = 0.95f, certainty = 0.8f, weight = 0.85f)

        /** Kalıcı silme, kasa sıfırlama. Geri alınamaz olanın ağırlığı. */
        val Destructive = Affect(valence = -0.5f, arousal = 0.75f, certainty = 1f, weight = 1f)

        /** Uzun bir iş sürüyor (anahtar türetme, tarama). Belirsiz ve tekrarlı. */
        val Working = Affect(valence = 0f, arousal = 0.25f, certainty = 0.1f, weight = 0.3f)

        /** Üretildi. Bir şeyin yoktan var olması: yükselen, hoş. */
        val Create = Affect(valence = 0.6f, arousal = 0.45f, certainty = 0.7f, weight = 0.3f)

        /** Geri alındı. Bir şeyin geri dönmesi: hoş ama tersine. */
        val Undo = Affect(valence = 0.45f, arousal = 0.4f, certainty = 0.8f, weight = 0.25f)
    }
}

/**
 * Titreşimin hangi sistem kanalından çalınacağı.
 *
 * Kanal yalnızca ses kısıklığı değil: Rahatsız Etmeyin, pil tasarrufu ve
 * kullanıcının kategori bazlı yoğunluk ayarı bu ayrımı okuyor.
 */
enum class HapticChannel { TOUCH, NOTIFICATION, ALERT }
