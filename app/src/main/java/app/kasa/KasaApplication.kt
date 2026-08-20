package app.kasa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import app.kasa.core.security.AutoLocker
import app.kasa.core.util.Haptics
import app.kasa.data.SettingsStore
import app.kasa.data.VaultStore
import app.kasa.data.net.BreachChecker
import app.kasa.data.repo.SecurityAnalyzer
import app.kasa.data.repo.VaultRepository
import app.kasa.widget.KasaWidgetProvider
import app.kasa.work.SecurityScanWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Uygulama nesnesi ve elle kurulan bağımlılık kabı.
 *
 * Hilt/Dagger yerine sade bir kap kullanılıyor: bu boyuttaki bir uygulamada
 * ek açıklama işleyicisi derleme süresini ikiye katlıyor, kazandırdığı tek şey
 * ise burada zaten elle yazılmış on satır. Ayrıca kasa anahtarını tutan
 * [VaultRepository]'nin ömrünü tek bir yerde görebilmek güvenlik açısından
 * değerli.
 */
class KasaApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()

        // Ayarlar değiştikçe çalışma zamanı davranışını güncelle.
        container.settingsStore.settings
            .map { it.haptics }
            .distinctUntilChanged()
            .onEach { container.haptics.enabled = it }
            .launchIn(container.scope)

        container.settingsStore.settings
            .map { it.autoLockSeconds }
            .distinctUntilChanged()
            .onEach { container.autoLocker.autoLockSeconds = it }
            .launchIn(container.scope)

        // Kilit durumu değişince ana ekran aracını tazele. StateFlow zaten
        // aynı değeri iki kez yaymıyor; ayrıca ayıklamaya gerek yok.
        container.vaultRepository.lockState
            .onEach { KasaWidgetProvider.refresh(this) }
            .launchIn(container.scope)

        container.autoLocker.start()

        container.scope.launch {
            SecurityScanWorker.schedule(this@KasaApplication)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_SECURITY,
            getString(R.string.notif_channel_security),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notif_channel_security_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SECURITY = "kasa.security"

        fun container(context: Context): AppContainer =
            (context.applicationContext as KasaApplication).container
    }
}

/** Uygulama ömrü boyunca yaşayan tekil nesneler. */
class AppContainer(private val application: Application) {

    /** Uygulama ömürlü bağlam; hiçbir Activity sızdırmaz. */
    val appContext: Context get() = application.applicationContext

    val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    val settingsStore by lazy { SettingsStore(application) }
    val vaultStore by lazy { VaultStore(application) }
    val haptics by lazy { Haptics(application) }
    val breachChecker by lazy { BreachChecker() }
    val securityAnalyzer by lazy { SecurityAnalyzer(breachChecker) }

    val vaultRepository: VaultRepository by lazy {
        VaultRepository(application, vaultStore, settingsStore, scope)
    }

    val autoLocker: AutoLocker by lazy {
        AutoLocker(application, vaultRepository, scope)
    }

    val workManager: WorkManager by lazy { WorkManager.getInstance(application) }
}
