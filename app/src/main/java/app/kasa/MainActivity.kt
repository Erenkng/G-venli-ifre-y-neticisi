package app.kasa

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
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
import app.kasa.core.util.LocalHaptics
import app.kasa.core.util.RecentShortcuts
import app.kasa.data.SettingsStore
import app.kasa.ui.BiometricGate
import app.kasa.ui.KasaApp
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.theme.KasaTheme
import app.kasa.ui.theme.SurfaceEffects
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

    /** Kısayoldan gelen kayıt kimliği; kasa açıldıktan sonra o kayıt açılıyor. */
    private var pendingItemId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handOffSplashScreen()
        enableEdgeToEdge()

        val container = (application as KasaApplication).container
        pendingAction = intent?.action
        pendingItemId = intent?.getStringExtra(RecentShortcuts.EXTRA_ITEM_ID)

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
                pureBlack = settings.pureBlack,
                gradientTheme = settings.gradientTheme,
                gradientFollowsTime = settings.gradientFollowsTime,
                grainLevel = settings.grainLevel,
                // Ana anahtar kapalıyken hiçbir alt bayrak okunmuyor: efekt
                // "görünmez ama çalışıyor" durumuna düşmesin diye kapatma
                // gerçekten kapatıyor.
                effects = if (!settings.experimentalEffects) SurfaceEffects.None else SurfaceEffects(
                    tilt = settings.effectTilt,
                    pressBloom = settings.effectPressBloom,
                    shimmer = settings.effectShimmer,
                    edgeDepth = settings.effectEdgeDepth,
                    parallax = settings.effectParallax,
                    holdCharge = settings.effectHoldCharge,
                    tiltDepth = settings.effectTiltDepth,
                    focusGlow = settings.effectFocusGlow
                )
            ) {
                CompositionLocalProvider(
                    LocalBiometricGate provides gate,
                    LocalHaptics provides container.haptics
                ) {
                    KasaApp(
                        settings = settings,
                        startAction = pendingAction,
                        startItemId = pendingItemId,
                        onActionConsumed = {
                            pendingAction = null
                            pendingItemId = null
                        }
                    )
                }
            }
        }
    }

    /**
     * Açılış ekranından uygulamaya geçiş.
     *
     * ### Neden elle yazıldı
     *
     * Sistemin varsayılan çıkışı işareti olduğu yerde bırakıp pencereyi
     * karartıyor: kadran bir an duruyor, sonra yok oluyor. Kadran o sırada
     * dönüşünü yeni bitirmiş; "açıldı" diyen hareketin ardından gelen bu
     * duraklama, açılışı bittiği yerde kesiyor.
     *
     * Burada işaret **kullanıcıya doğru** büyüyerek çözülüyor — kasanın kapağı
     * açılmış, içeriden ekran görünüyor. Altındaki uygulama zaten çizilmiş
     * durumda (bu geri çağrı ilk kare hazır olduktan sonra çalışıyor), yani
     * kullanıcı boş bir kareye değil kendi kasasına geçiyor.
     *
     * ### Neden uyumluluk kitaplığı yok
     *
     * `minSdk` 36; platformun kendi açılış ekranı API'si (31+) her cihazda var.
     * core-splashscreen yalnızca daha eski sürümler için köprü kuruyor.
     *
     * ### remove() çağrılmak zorunda
     *
     * Bir çıkış dinleyicisi kurulduğu anda açılış ekranını kaldırma
     * sorumluluğu bize geçiyor; çağrılmazsa uygulama kalıcı olarak örtünün
     * altında kalır. Bu yüzden iki yol da kapatıldı: animasyonun bitişi ve
     * ondan biraz uzun bir emniyet gecikmesi. [removed] ikisinin aynı anda
     * çalışmasını engelliyor.
     */
    private fun handOffSplashScreen() {
        splashScreen.setOnExitAnimationListener { splashView ->
            var removed = false
            val finish = Runnable {
                if (!removed) {
                    removed = true
                    splashView.remove()
                }
            }

            splashView.iconView?.animate()
                ?.scaleX(SPLASH_ICON_SCALE)
                ?.scaleY(SPLASH_ICON_SCALE)
                ?.alpha(0f)
                ?.setDuration(SPLASH_EXIT_MILLIS)
                ?.setInterpolator(AccelerateInterpolator())
                ?.start()

            splashView.animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_MILLIS)
                .withEndAction(finish)
                .start()

            // Animasyon herhangi bir sebeple bitişini bildirmezse (pencere
            // erken ayrılırsa olabiliyor) örtü yine de kalkıyor.
            splashView.postDelayed(finish, SPLASH_EXIT_MILLIS + 120L)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction = intent.action
        pendingItemId = intent.getStringExtra(RecentShortcuts.EXTRA_ITEM_ID)
    }

    companion object {
        const val ACTION_GENERATE = "app.kasa.action.GENERATE"
        const val ACTION_SEARCH = "app.kasa.action.SEARCH"
        const val ACTION_SECURITY = "app.kasa.action.SECURITY"
        const val ACTION_LOCK = "app.kasa.action.LOCK"

        /** Açılış işaretinin çözülme süresi. */
        private const val SPLASH_EXIT_MILLIS = 260L

        /** Kaybolurken ne kadar büyüdüğü. Fazlası "patlama" gibi görünüyor. */
        private const val SPLASH_ICON_SCALE = 1.28f
    }
}
