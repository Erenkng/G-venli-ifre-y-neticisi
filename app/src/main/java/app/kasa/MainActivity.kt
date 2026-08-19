package app.kasa

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.kasa.data.SettingsStore
import app.kasa.ui.BiometricGate
import app.kasa.ui.KasaApp
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.theme.KasaTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Uygulamanın tek Activity'si.
 *
 * `FragmentActivity`'den türüyor çünkü `BiometricPrompt` bir fragment yöneticisi
 * istiyor. Ekran görüntüsü engeli burada, pencere düzeyinde uygulanıyor:
 * `FLAG_SECURE` yalnızca ekran görüntüsünü değil, son uygulamalar listesindeki
 * önizlemeyi ve ekran yansıtmayı da kapatır — parola yöneticisinde üçü de gerekir.
 */
class MainActivity : FragmentActivity() {

    private var pendingAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as KasaApplication).container
        pendingAction = intent?.action

        // Ekran koruması ayarı değiştiğinde pencere bayrağını güncelle.
        lifecycleScope.launch {
            container.settingsStore.settings
                .map { it.blockScreenshots }
                .distinctUntilChanged()
                .collect { block ->
                    if (block) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        setContent {
            val settings by container.settingsStore.settings.collectAsState(initial = SettingsStore.Settings())
            val gate = remember { BiometricGate(this) }

            KasaTheme(
                themeMode = settings.theme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack
            ) {
                CompositionLocalProvider(LocalBiometricGate provides gate) {
                    KasaApp(
                        settings = settings,
                        startAction = pendingAction,
                        onActionConsumed = { pendingAction = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction = intent.action
    }

    companion object {
        const val ACTION_GENERATE = "app.kasa.action.GENERATE"
        const val ACTION_SEARCH = "app.kasa.action.SEARCH"
        const val ACTION_SECURITY = "app.kasa.action.SECURITY"
        const val ACTION_LOCK = "app.kasa.action.LOCK"
    }
}
