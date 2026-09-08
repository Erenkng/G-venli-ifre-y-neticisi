package app.kasa.core.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import app.kasa.R

/**
 * Pano, Android'de sistem genelinde okunabilen bir alandır. Bir parolayı oraya
 * koymak kaçınılmaz bir risktir; risk üç yerden azaltılır:
 *
 *  1. **Hassas işaretleme** — Android 13+ panoya konan verinin gizli olduğunu
 *     bilir ve ekrandaki panoyu önizleme balonunda içeriği göstermez.
 *  2. **Otomatik temizleme** — kullanıcının belirlediği süre sonunda (varsayılan
 *     30 sn) pano bir alarmla silinir; uygulama arka planda öldürülse bile
 *     alarm çalışır, çünkü iş [ClipboardClearReceiver] tarafından yapılır.
 *  3. **Kilitlenince temizleme** — kasa kilitlendiği anda pano da boşaltılır;
 *     alarmın gecikmesi ya da hiç çalışmaması durumunda ikinci yol bu.
 *
 * İkinci ve üçüncü maddenin neden uzun süre hiç çalışmadığı [clearNow]
 * üzerinde yazılı. Pano **geçmişi** kapsam dışında: gerekçesi de orada.
 */
object SecureClipboard {

    private const val ACTION_CLEAR = "app.kasa.action.CLEAR_CLIPBOARD"
    private const val REQUEST_CODE = 4711
    const val SENSITIVE_LABEL = "Kasa"

    fun copySensitive(context: Context, text: String, clearAfterSeconds: Int) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(SENSITIVE_LABEL, text)

        clip.description.extras = PersistableBundle().apply {
            // Sistem bu bayrağı görünce içeriği pano önizleme balonunda gizler.
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            // Bazı üreticiler kendi anahtarlarına bakıyor.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        manager.setPrimaryClip(clip)

        if (clearAfterSeconds > 0) scheduleClear(context, clearAfterSeconds)
    }

    /** Gizli olmayan metinler (kullanıcı adı, adres) için sade kopyalama. */
    fun copyPlain(context: Context, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText(SENSITIVE_LABEL, text))
    }

    private fun scheduleClear(context: Context, seconds: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = clearIntent(context)
        alarmManager.cancel(pending)
        // RTC değil RTC_WAKEUP.
        //
        // `RTC` cihaz uyuyorsa alarmı uyanana kadar bekletiyor. Panodaki şey
        // bir parola ve "otuz saniye sonra silinir" sözü, telefon cebe girdiği
        // anda "bir dahaki uyanışta silinir"e dönüşüyordu — yani sözün
        // tutulmadığı durum tam da riskin en yüksek olduğu durum.
        //
        // Alarm yine de kesin değil (kesin alarm Android 12'den beri ayrı bir
        // izin istiyor ve bir pano temizliği için o izni istemek orantısız);
        // bu yüzden pano kasa kilitlenirken de temizleniyor. İki yol birbirinin
        // yedeği.
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + seconds * 1000L,
            pending
        )
    }

    fun cancelScheduledClear(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(clearIntent(context))
    }

    private fun clearIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClipboardClearReceiver::class.java).setAction(ACTION_CLEAR)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Panoyu hemen temizler. Kasa kilitlenirken ve alarm dolduğunda çağrılıyor.
     *
     * ### Neden okumaya güvenilmiyor
     *
     * Burada önce pano okunuyor, etiketi "Kasa" değilse dokunulmuyordu.
     * Niyet doğruydu — kullanıcı bu arada başka bir şey kopyaladıysa onu
     * silmemek — ama Android 10'dan beri **odakta olmayan bir uygulama panoyu
     * okuyamıyor**: `primaryClip` arka planda `null` dönüyor. Yani etiket
     * karşılaştırması hep başarısız oluyor ve işlev her seferinde erken
     * çıkıyordu.
     *
     * Bunun sonucu şuydu: pano temizleme hiç çalışmıyordu. İki çağıran da
     * uygulama arka plandayken geliyor — alarm alıcısı zaten süreç dışından,
     * kilit ise tam da uygulamadan çıkılınca. Kopyalanan parola panoda
     * süresiz kalıyordu, ekranda "30 saniye sonra silinecek" yazarken.
     *
     * Artık okuma yalnızca bir **ipucu**: okunabiliyor ve bizim değilse
     * dokunulmuyor; okunamıyorsa temizleniyor. Ödenen bedel, kullanıcının o
     * kısa pencerede kopyaladığı bir metni kaybetme ihtimali; kazanılan şey,
     * panoda kalan bir parolanın olmaması. Bir parola yöneticisinde bu takas
     * tek yönlü.
     *
     * Temizlik `clearPrimaryClip` ile, tek adımda. Panoya önce bir boşluk
     * yazıp sonra boşaltmak parolayı pano **geçmişinden** de düşürürdü, ama
     * yazma işlemi Android 13'ten beri ekranda bir pano önizleme balonu
     * çıkarabiliyor: her kilitlenmede görünen bir balon, kazandırdığı şeyin
     * yanında ağır kalıyor. Pano geçmişi bu yüzden bilinen bir sınır olarak
     * duruyor.
     */
    fun clearNow(context: Context, notifyUser: Boolean = false) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return

        val current = runCatching { manager.primaryClip }.getOrNull()
        if (current != null && current.description?.label?.toString() != SENSITIVE_LABEL) return

        runCatching { manager.clearPrimaryClip() }

        if (notifyUser) {
            Toast.makeText(context, R.string.clipboard_cleared, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Pano temizleme alarmının alıcısı. */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SecureClipboard.clearNow(context, notifyUser = false)
    }
}
