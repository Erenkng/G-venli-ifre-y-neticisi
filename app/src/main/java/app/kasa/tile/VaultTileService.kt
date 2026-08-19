package app.kasa.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.kasa.KasaApplication
import app.kasa.MainActivity
import app.kasa.R
import app.kasa.widget.KasaWidgetProvider

/**
 * Hızlı ayarlar döşemesi: tek dokunuşla kasayı kilitler.
 *
 * Bu, telefonu birine uzatmadan önce yapılacak en hızlı hareket. Uygulamayı
 * açıp ayarlara girmek yerine perdeyi indirip döşemeye dokunmak yeter;
 * kasa anahtarı o anda bellekten silinir.
 *
 * Kasa zaten kilitliyse döşeme "aç" işlevi görür ve uygulamayı öne getirir.
 */
class VaultTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val container = KasaApplication.container(this)

        if (container.vaultRepository.isUnlocked) {
            container.autoLocker.lockNow()
            KasaWidgetProvider.refresh(this)
            updateTile()
        } else {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivityAndCollapseCompat(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val unlocked = KasaApplication.container(this).vaultRepository.isUnlocked

        tile.state = if (unlocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_shield)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (unlocked) R.string.set_lock_now else R.string.lock_unlock)
        }
        tile.updateTile()
    }

    /**
     * Android 14'ten itibaren `startActivityAndCollapse(Intent)` yasak;
     * `PendingIntent` alan sürümü kullanılmalı.
     */
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
