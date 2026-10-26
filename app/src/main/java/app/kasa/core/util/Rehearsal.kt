package app.kasa.core.util

/**
 * Ana parolanın hafıza provası.
 *
 * ### Kapatılan açık bir hata değil, bir sessizlik
 *
 * Bu kasanın geri dönüşü olmayan tek arızası ana parolanın unutulmasıdır.
 * Her şey ona bağlı: kasa dosyası onunla açılıyor, anahtar yalnızca bellekte
 * duruyor, sunucu yok, "parolamı sıfırla" diye bir şey yok. Kurtarma anahtarı
 * ikinci bir yol ama yalnızca gerçekten saklanmışsa.
 *
 * Buna karşı uygulamaların yaptığı tek şey kurulumda "bunu unutma" demek.
 * Sonra biyometri devreye giriyor ve kullanıcı o parolayı **aylarca hiç
 * yazmıyor**. Unutma tam da böyle oluyor: bir anda değil, kullanılmayarak.
 *
 * Prova bunu tersine çeviriyor: artan aralıklarla parolayı bir kez
 * yazdırıyor. Aralığın artması önemli — her gün sorulan bir şey rahatsızlık,
 * hiç sorulmayan bir şey risk. Doğru yazıldıkça aralık uzuyor, yanlış
 * yazılınca başa dönüyor ve kurtarma anahtarının kontrol edilmesi öneriliyor.
 *
 * ### Bu fikir bize ait değil
 *
 * Açıkça yazılmalı: Enpass'te ana parola için aralıklı yineleme
 * hatırlatması zaten var ve PINPal diye bağımsız bir araç da aynı işi
 * yapıyor. Buradaki özgün olan şey fikir değil, **programın** araştırmaya
 * dayanması: aralıklar [STEP_HOURS] üzerinde anlatıldığı gibi seçildi.
 *
 * ### Ne yapmadığı
 *
 * Prova bir kilit değil. Kasayı kilitlemiyor, erişimi engellemiyor,
 * atlanabiliyor ve tamamen kapatılabiliyor. Yanlış cevap bir ceza
 * doğurmuyor — yalnızca "bu parolayı unutmak üzeresin" bilgisini, hâlâ
 * yapacak bir şey varken veriyor. Zorlayıcı bir prova, atlanan bir prova
 * olurdu.
 */
object Rehearsal {

    /**
     * Aralık basamakları (saat).
     *
     * ### Sayılar nereden geliyor
     *
     * İlk hâli 3/7/14/30/60/90 **gün** idi ve yanlıştı. Blocki ve
     * arkadaşlarının aralıklı yineleme çalışması (NDSS 2015) en iyi programın
     * ilk dönüşü **12 saat** sonra yaptığını ve aralıkları yaklaşık 1,5
     * katıyla büyüttüğünü buluyor; unutmanın en çok olduğu yer de tam o ilk
     * yarım gün. Üç günde başlayan bir merdiven, en kritik pencereyi
     * tamamen atlıyordu.
     *
     * Son basamakta kalınıyor (~45 gün): sonsuza kadar uzatmak "artık hiç
     * sormuyor"a varırdı ve o, bu özelliğin çözmeye çalıştığı durumun
     * kendisi.
     */
    private val STEP_HOURS = intArrayOf(12, 18, 27, 40, 60, 90, 135, 200, 300, 450, 675, 1080)

    const val FIRST_STEP = 0

    private const val HOUR_MILLIS = 60L * 60 * 1000

    /** [step] basamağındaki bekleme süresi. */
    fun intervalMillis(step: Int): Long =
        STEP_HOURS[step.coerceIn(0, STEP_HOURS.lastIndex)] * HOUR_MILLIS

    /**
     * Prova zamanı geldi mi?
     *
     * [lastAt] sıfırsa henüz hiç prova yapılmamış: ilk aralık **kurulumdan**
     * sayılıyor ve o yüzden çağıran kasanın kurulma zamanını veriyor. Sıfırı
     * "hemen sor" saymak, kurulumu yeni bitirmiş kullanıcıya parolayı iki kez
     * üst üste yazdırmak olurdu.
     */
    fun isDue(lastAt: Long, step: Int, now: Long): Boolean {
        if (lastAt <= 0L) return false
        val elapsed = now - lastAt
        // Geriye akan süre: duvar saati kullanıcının denetiminde ve saati
        // geri almak provayı erkene çekmemeli. Negatif geçen süre "henüz
        // değil" sayılıyor.
        if (elapsed < 0L) return false
        return elapsed >= intervalMillis(step)
    }

    /** Doğru cevaptan sonraki basamak. Son basamakta kalıyor. */
    fun advance(step: Int): Int = (step + 1).coerceAtMost(STEP_HOURS.lastIndex)

    /** Yanlış cevaptan sonra: en kısa aralığa dönülüyor. */
    fun reset(): Int = FIRST_STEP
}
