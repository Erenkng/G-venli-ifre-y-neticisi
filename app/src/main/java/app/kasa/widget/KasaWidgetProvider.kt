package app.kasa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.kasa.KasaApplication
import app.kasa.MainActivity
import app.kasa.R
import app.kasa.data.repo.VaultRepository

/**
 * Ana ekran araç birimi.
 *
 * Bilinçli olarak **hiçbir gizli veri göstermez** — ne parola, ne 2FA kodu,
 * ne kayıt adı. Ana ekran araç birimleri kilit ekranında da görünebilir ve
 * omuz üstünden okunabilir; oraya kod basmak, kasanın bütün otomatik kilit
 * mantığını anlamsız kılardı.
 *
 * Gösterdiği tek şey kasanın durumu (kilitli/açık, kaç kayıt) ve iki hızlı
 * eylem: üreticiyi aç, aramayı aç.
 */
class KasaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    private fun buildViews(context: Context): RemoteViews {
        val repository = KasaApplication.container(context).vaultRepository
        val locked = !repository.isUnlocked
        val count = if (locked) 0 else repository.data.value.items.size

        val status = when {
            repository.lockState.value is VaultRepository.LockState.NeedsSetup ->
                context.getString(R.string.vault_empty_title)
            locked -> context.getString(R.string.lock_title)
            else -> context.getString(
                R.string.vault_subtitle,
                count,
                context.getString(R.string.vault_never_synced)
            )
        }

        return RemoteViews(context.packageName, R.layout.widget_kasa).apply {
            setTextViewText(R.id.widget_status, status)
            setOnClickPendingIntent(R.id.widget_root, activityIntent(context, null, 0))
            setOnClickPendingIntent(
                R.id.widget_action_generate,
                activityIntent(context, MainActivity.ACTION_GENERATE, 1)
            )
            setOnClickPendingIntent(
                R.id.widget_action_open,
                activityIntent(context, MainActivity.ACTION_SEARCH, 2)
            )
        }
    }

    private fun activityIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (action != null) this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Kilit durumu değişince araç birimini tazeler. */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, KasaWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val intent = Intent(context, KasaWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
