package app.kasa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kasa.data.SettingsStore
import app.kasa.data.repo.VaultRepository
import app.kasa.ui.components.KasaBackground
import app.kasa.ui.screens.OnboardingScreen
import app.kasa.ui.screens.UnlockScreen
import app.kasa.ui.theme.KasaMotion

/**
 * Uygulamanın kökü: kasa durumu neyse onu gösterir.
 *
 * Üç durum var ve aralarında geçiş kullanıcı eylemiyle değil, kasanın
 * kendi durumuyla olur — otomatik kilit devreye girdiğinde hangi ekranda
 * olursanız olun kilit ekranına düşersiniz ve o anda çözülmüş kayıtlar
 * bellekten silinmiş olur.
 */
@Composable
fun KasaApp(
    settings: SettingsStore.Settings,
    startAction: String?,
    startItemId: String?,
    onActionConsumed: () -> Unit
) {
    val factory = rememberKasaViewModelFactory()
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val lockState by authViewModel.lockState.collectAsStateWithLifecycle()
    val stateHolder = rememberSaveableStateHolder()

    KasaBackground(modifier = Modifier.fillMaxSize()) {
        // transitionSpec @Composable değil; belirteçler beste içinde okunup
        // lambdaya hazır değer olarak giriyor.
        val enterFade: FiniteAnimationSpec<Float> = KasaMotion.enter()
        val enterScale: FiniteAnimationSpec<Float> = KasaMotion.large()
        val exitFade: FiniteAnimationSpec<Float> = KasaMotion.exit()

        AnimatedContent(
            targetState = lockState,
            transitionSpec = {
                (fadeIn(enterFade) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = enterScale
                )) togetherWith fadeOut(exitFade)
            },
            label = "lockState"
        ) { state ->
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    VaultRepository.LockState.NeedsSetup ->
                        OnboardingScreen(viewModel = authViewModel)

                    VaultRepository.LockState.Locked ->
                        UnlockScreen(viewModel = authViewModel, settings = settings)

                    // Kilit açıldığında kullanıcı bıraktığı yere dönüyor.
                    //
                    // ### Neden ayrı bir tutucu gerekti
                    //
                    // Kilitlenince [MainScaffold] besteden bütünüyle çıkıyor:
                    // uygulamadan çıkıp bir parolayı yapıştırıp geri dönen
                    // kullanıcı, parmağını okuttuktan sonra kendini kasa
                    // listesinin en başında buluyordu. Açık olan kayıt
                    // kapanmıyordu — o bilgi görünüm modelinde duruyor — ama
                    // hangi sekmede olduğu, listenin nereye kaydırıldığı ve
                    // ayarların hangi kategorisinin açık olduğu kayboluyordu,
                    // çünkü `rememberSaveable` bir bileşen ağaçtan çıkınca
                    // onu tutacak bir yer olmadığında değerini atıyor.
                    //
                    // [rememberSaveableStateHolder] o yeri kuruyor: ağaçtan
                    // çıkan içeriğin kaydedilebilir durumu anahtarıyla
                    // saklanıyor ve geri geldiğinde aynı yerden devam ediyor.
                    //
                    // ### Neden yalnızca bellekte
                    //
                    // Tutucu süreç ölümünü aşmıyor ve bu bilerek böyle:
                    // saklanan şeyin içinde açık kaydın kimliği de var ve onu
                    // sistemin örnek durumu olarak diske yazdırmak, kasanın
                    // içindekine dair bir izi uygulamanın dışına taşımak
                    // olurdu. Uygulama bellekten düştüğünde baştan başlamak
                    // doğru davranış.
                    VaultRepository.LockState.Unlocked ->
                        stateHolder.SaveableStateProvider(MAIN_STATE_KEY) {
                            MainScaffold(
                                settings = settings,
                                factory = factory,
                                startAction = startAction,
                                startItemId = startItemId,
                                onActionConsumed = onActionConsumed
                            )
                        }
                }
            }
        }
    }
}

/** Ana iskelenin kaydedilebilir durumunun anahtarı. */
private const val MAIN_STATE_KEY = "main"
