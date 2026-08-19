package app.kasa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kasa.AppContainer
import app.kasa.KasaApplication

/**
 * ViewModel'lerin tek fabrikası.
 *
 * Hepsi aynı [AppContainer]'ı alır; böylece kasa anahtarını tutan depo
 * uygulamada tek bir örnektir ve ekranlar arasında geçerken kasa yeniden
 * çözülmez.
 */
@Composable
fun rememberKasaViewModelFactory(): ViewModelProvider.Factory {
    val context = LocalContext.current
    val container = remember(context) { KasaApplication.container(context) }
    return remember(container) {
        viewModelFactory {
            initializer { VaultViewModel(container) }
            initializer { GeneratorViewModel(container) }
            initializer { SecurityViewModel(container) }
            initializer { SettingsViewModel(container) }
            initializer { AuthViewModel(container) }
        }
    }
}

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { KasaApplication.container(context) }
}
