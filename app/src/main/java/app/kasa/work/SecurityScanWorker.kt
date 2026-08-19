package app.kasa.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kasa.KasaApplication
import app.kasa.MainActivity
import app.kasa.R
import app.kasa.data.repo.SecurityAnalyzer
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Haftalık arka plan güvenlik taraması.
 *
 * Kritik ayrıntı: bu iş **kasa kilitliyken hiçbir şey yapamaz** ve yapmaya da
 * çalışmaz. Arka planda parolaları çözebilmek için anahtarı bellekte ya da
 * diskte açık tutmak gerekirdi; bu, otomatik kilidin bütün anlamını
 * ortadan kaldırırdı. Bu yüzden iş, kasa kilitliyse hiçbir şey yapmadan
 * başarıyla döner ve bir sonraki sefere bakar.
 *
 * Kullanıcı uygulamayı açıp kasayı açtığında iş bir sonraki tetiklenmesinde
 * gerçekten çalışır ve sızıntı bulursa bildirim gönderir.
 */
class SecurityScanWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = KasaApplication.container(context)
        val repository = container.vaultRepository

        if (!repository.isUnlocked) return Result.success()

        val settings = container.settingsStore.settings.first()
        if (!settings.onlineBreachCheck) return Result.success()

        val items = repository.data.value.items
        if (items.isEmpty()) return Result.success()

        val report = container.securityAnalyzer.analyze(items, onlineCheck = true)
        repository.recordScan(report.updatedItems, report.scannedAt)
        container.settingsStore.setLastScanAt(report.scannedAt)

        notifyFindings(report)
        return Result.success()
    }

    private fun notifyFindings(report: SecurityAnalyzer.Report) {
        if (!canNotify()) return

        val leaked = report.findings.firstOrNull { it.type == SecurityAnalyzer.FindingType.LEAKED }
        val old = report.findings.firstOrNull { it.type == SecurityAnalyzer.FindingType.OLD }

        val (title, body) = when {
            leaked != null -> context.getString(R.string.notif_leak_title) to
                context.getString(R.string.notif_leak_body, leaked.count)
            old != null -> context.getString(R.string.notif_old_title) to
                context.getString(R.string.notif_old_body, old.count)
            else -> return
        }

        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SECURITY
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, KasaApplication.CHANNEL_SECURITY)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Bildirim içeriği kilit ekranında gizlenir: hangi hesabın
            // sızdığı da gizli bir bilgidir.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val WORK_NAME = "kasa.security.scan"
        private const val NOTIFICATION_ID = 3141

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SecurityScanWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
        }
    }
}
