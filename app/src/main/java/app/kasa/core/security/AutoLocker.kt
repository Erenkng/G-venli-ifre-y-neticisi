package app.kasa.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.kasa.data.repo.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Otomatik kilit.
 *
 * Bir parola yöneticisinin en sık gerçekleşen tehdidi uzaktaki bir saldırgan
 * değil, masada açık kalmış bir telefondur. Bu yüzden kilit üç ayrı olayla
 * kapanır:
 *
 *  - **Ekran kapandığında**, süre beklemeden (ACTION_SCREEN_OFF).
 *  - **Uygulama arka plana geçtiğinde**, ayarlanan süre kadar sonra.
 *  - **Süre "hemen" seçiliyse**, arka plana geçer geçmez.
 *
 * Sayaç arka planda çalışan bir coroutine'dir; uygulama öldürülse bile kasa
 * anahtarı yalnızca bellekte durduğu için süreçle birlikte yok olur.
 *
 * ### Sayaç neden tek başına yetmiyor
 *
 * `delay` tekdüze saate dayanıyor ve o saat cihaz derin uykudayken
 * **ilerlemiyor**; süreç dondurulduğunda ise coroutine hiç çalışmıyor. Yani
 * "beş dakika sonra kilitle" demek, gerçekte "beş dakika **açık** kaldıktan
 * sonra kilitle" demekti: cebe giren telefonda sayaç neredeyse duruyordu.
 * Ekran kapanınca kilitleme açıksa bu fark edilmiyor, ama o ayar
 * kapatılabilir ve kapatan kullanıcı tam da süreye güvenen kullanıcı.
 *
 * Bu yüzden süre iki yerde tutuluyor: coroutine (uygulama yaşarken çalışan
 * hızlı yol) ve [SystemClock.elapsedRealtime] cinsinden bir **son tarih**.
 * Son tarih derin uykuyu da sayıyor ve öne dönüldüğünde ilk iş onu
 * denetlemek. Böylece sayaç hiç çalışmasa bile kasa, kullanıcı geri
 * döndüğünde kilitli.
 */
class AutoLocker(
    private val context: Context,
    private val repository: VaultRepository,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    @Volatile
    var autoLockSeconds: Int = 60

    /**
     * Bağlama duyarlı kilit ayarları.
     *
     * Kapalıyken [autoLockSeconds] her yerde geçerli. Açıkken, kayıtlı ağın
     * özetiyle eşleşen bir Wi-Fi'daysak [trustedSeconds] kullanılıyor; başka
     * her durumda — bilinmeyen ağ, mobil veri, izin yok — kısa olan hangisiyse
     * o. Şüphede kalındığında sıkı olan tarafı seçmek, bu özelliğin tek
     * güvenlik kuralı.
     */
    @Volatile
    var contextLockEnabled: Boolean = false

    @Volatile
    var trustedNetworkHash: String = ""

    @Volatile
    var trustedSeconds: Int = 300

    /**
     * Ekran kapanınca beklemeden kilitle.
     *
     * Varsayılan açık ve öyle kalması gerekiyor: ekranı kapatmak bilinçli bir
     * hareket, kullanıcı işini bitirdiğini söylüyor. Kapatılabilir olmasının
     * sebebi, telefonu sık sık uyandırıp kasaya bakan kullanıcının her
     * seferinde ana parola yazmak zorunda kalması — o kullanıcı için tek
     * alternatif otomatik kilidi büsbütün uzatmak olurdu ve bu daha kötü.
     */
    @Volatile
    var lockOnScreenOff: Boolean = true

    private var pendingLock: Job? = null
    private var registered = false

    /**
     * Kilitlenmesi gereken an, [SystemClock.elapsedRealtime] cinsinden.
     *
     * Derin uykuyu da sayan tek saat bu. 0 ise bekleyen kilit yok.
     */
    @Volatile
    private var lockDeadline = 0L

    /**
     * Sistem seçicileri (dosya seçme, izin isteği) uygulamayı kısa süreliğine
     * arka plana alır. Bunu "kullanıcı uygulamadan çıktı" saymak, dışa aktarma
     * gibi işleri ortasından kesiyordu: kasa kilitlenince çözülmüş kayıtlar
     * bellekten düşüyor ve seçiciden dönüldüğünde yazacak veri kalmıyordu.
     *
     * Bu yüzden çağıran taraf, seçiciyi açmadan hemen önce [suppressNextBackground]
     * diyerek **tek bir** arka plana geçişi affettirebilir. Af yalnızca kısa bir
     * pencere için geçerlidir; kullanıcı gerçekten uygulamadan çıkarsa süre
     * dolmuş olur ve kilit normal şekilde işler. Ekran kapanması hiçbir koşulda
     * affedilmez.
     */
    private var suppressUntil = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && lockOnScreenOff) lockNow()
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        if (!registered) {
            // Android 14'ten (API 34) itibaren bağlam üzerinden kaydedilen her
            // alıcının dışa aktarılıp aktarılmadığı açıkça belirtilmek zorunda.
            // Bu alıcı yalnızca sistemin yayınladığı ACTION_SCREEN_OFF'u dinliyor,
            // dışarıya kapalı olmalı.
            ContextCompat.registerReceiver(
                context,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Öne dönüldü. Sayacı iptal etmeden **önce** son tarihe bakılıyor:
        // coroutine derin uyku ya da dondurulmuş süreç yüzünden hiç
        // çalışmamış olabilir ve o durumda iptal etmek, dolmuş bir süreyi
        // sessizce silmek olurdu.
        val deadline = lockDeadline
        if (deadline != 0L && SystemClock.elapsedRealtime() >= deadline) {
            lockNow()
            return
        }
        pendingLock?.cancel()
        pendingLock = null
        lockDeadline = 0L
    }

    fun suppressNextBackground() {
        // Duvar saati değil tekdüze saat: af bir **süre** ve kullanıcının
        // değiştirebildiği bir saate bağlanmasının hiçbir gerekçesi yok.
        suppressUntil = SystemClock.elapsedRealtime() + SUPPRESS_WINDOW_MILLIS
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!repository.isUnlocked) return

        // Af, kilidi **ertelemek** için var; kaldırmak için değil.
        //
        // Burada af alındığında hiç sayaç kurulmuyordu ve bu, kasayı süresiz
        // açık bırakan bir yol açıyordu: kullanıcı dışa aktarma seçicisini
        // açıp geri dönmezse (telefonu cebe koyup gitmek yeter) o oturumda
        // bir daha `onStop` gelmiyor ve kilitleyecek hiçbir şey kalmıyordu.
        // Artık af yalnızca "hemen kilitle" durumunu yumuşatıyor: seçici
        // saniyeler içinde döndüğü ve dönüşte `onStart` sayacı iptal ettiği
        // için özellik bozulmuyor, ama dönülmezse kasa yine kilitleniyor.
        val suppressed = SystemClock.elapsedRealtime() < suppressUntil
        if (suppressed) suppressUntil = 0L

        val configured = effectiveLockSeconds()
        val seconds = if (suppressed) maxOf(configured, SUPPRESS_WINDOW_SECONDS) else configured
        if (seconds <= 0) {
            lockNow()
            return
        }

        pendingLock?.cancel()
        // Son tarih tekdüze saatte tutuluyor; gerekçesi sınıf belgesinde.
        lockDeadline = SystemClock.elapsedRealtime() + seconds * 1000L
        pendingLock = scope.launch {
            delay(seconds * 1000L)
            lockNow()
        }
    }

    /**
     * Şu anda hangi süre geçerli?
     *
     * Güvenilen ağdayken gecikme uzuyor; ama asla kısalmıyor: kullanıcı genel
     * süreyi 5 dakika, bağlam süresini 1 dakika seçtiyse evde de 5 dakika
     * uygulanır. "Bağlam" burada yalnızca gevşetmek için var, sıkılaştırmak
     * zaten genel ayarın işi.
     */
    private fun effectiveLockSeconds(): Int {
        if (!contextLockEnabled) return autoLockSeconds
        val trusted = runCatching {
            TrustedNetwork.isTrusted(context, trustedNetworkHash)
        }.getOrDefault(false)
        if (!trusted) return autoLockSeconds
        // 0 = "hemen": güvenilen ağda bunu uzatmak kullanıcının açık isteği.
        return if (autoLockSeconds <= 0) trustedSeconds else maxOf(autoLockSeconds, trustedSeconds)
    }

    fun lockNow() {
        pendingLock?.cancel()
        pendingLock = null
        lockDeadline = 0L
        suppressUntil = 0L
        if (repository.isUnlocked) repository.lock()
    }

    private companion object {
        const val SUPPRESS_WINDOW_SECONDS = 30
        const val SUPPRESS_WINDOW_MILLIS = SUPPRESS_WINDOW_SECONDS * 1000L
    }
}
