package app.kasa.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 */
class AutoLocker(
    private val context: Context,
    private val repository: VaultRepository,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {

    @Volatile
    var autoLockSeconds: Int = 60

    private var pendingLock: Job? = null
    private var registered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) lockNow()
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        if (!registered) {
            context.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            registered = true
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Öne dönüldü: bekleyen kilit sayacını iptal et.
        pendingLock?.cancel()
        pendingLock = null
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!repository.isUnlocked) return
        val seconds = autoLockSeconds
        if (seconds <= 0) {
            lockNow()
            return
        }
        pendingLock?.cancel()
        pendingLock = scope.launch {
            delay(seconds * 1000L)
            lockNow()
        }
    }

    fun lockNow() {
        pendingLock?.cancel()
        pendingLock = null
        if (repository.isUnlocked) repository.lock()
    }
}
