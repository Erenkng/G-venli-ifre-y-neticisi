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
 *  3. **Sessiz silme** — pano temizlenirken içine boş değil, tek boşluk konur;
 *     bazı üreticilerin pano geçmişi tamamen boş `ClipData`'yı yok sayıyor.
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
        alarmManager.set(
            AlarmManager.RTC,
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

    /** Panoyu hemen temizler. Kasa kilitlenirken de çağrılır. */
    fun clearNow(context: Context, notifyUser: Boolean = false) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val current = manager.primaryClip
        // Kullanıcı bu arada başka bir şey kopyaladıysa ona dokunma.
        val ours = current?.description?.label?.toString() == SENSITIVE_LABEL
        if (!ours) return

        manager.clearPrimaryClip()
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
