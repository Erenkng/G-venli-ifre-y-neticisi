package app.kasa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    onActionConsumed: () -> Unit
) {
    val factory = rememberKasaViewModelFactory()
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val lockState by authViewModel.lockState.collectAsStateWithLifecycle()

    KasaBackground(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = lockState,
            transitionSpec = {
                (fadeIn(KasaMotion.enter()) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = KasaMotion.large()
                )) togetherWith fadeOut(KasaMotion.exit())
            },
            label = "lockState"
        ) { state ->
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    VaultRepository.LockState.NeedsSetup ->
                        OnboardingScreen(viewModel = authViewModel)

                    VaultRepository.LockState.Locked ->
                        UnlockScreen(viewModel = authViewModel, settings = settings)

                    VaultRepository.LockState.Unlocked ->
                        MainScaffold(
                            settings = settings,
                            factory = factory,
                            startAction = startAction,
                            onActionConsumed = onActionConsumed
                        )
                }
            }
        }
    }
}
