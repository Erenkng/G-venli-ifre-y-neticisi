package app.kasa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.R
import app.kasa.core.util.Haptics
import app.kasa.data.repo.SecurityAnalyzer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Güvenlik ekranı: kasa puanı, bulgular ve tarama.
 *
 * Tarama sonuçları kasanın kendi şifreli blob'una yazılır (hangi kaydın
 * sızıntıda göründüğü de gizli bir bilgidir); ayrı bir önbellek dosyası
 * tutulmaz.
 */
class SecurityViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val scanning: Boolean = false,
        val progress: Float = 0f,
        val report: SecurityAnalyzer.Report? = null,
        val lastScanAt: Long = 0L
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val messages = Channel<UiMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    init {
        // Açılışta ağa çıkmadan yerel bir değerlendirme yap; kullanıcı ekrana
        // girdiğinde boş bir puan görmesin.
        viewModelScope.launch {
            val items = container.vaultRepository.data.value.items
            if (items.isNotEmpty()) {
                val report = container.securityAnalyzer.analyze(items, onlineCheck = false)
                _state.value = _state.value.copy(
                    report = report,
                    lastScanAt = container.vaultRepository.data.value.lastScanAt
                )
            }
        }
    }

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, progress = 0f)
            container.haptics.play(Haptics.Kind.MEDIUM)

            val onlineAllowed = container.settingsStore.settings.first().onlineBreachCheck
            val items = container.vaultRepository.data.value.items

            val report = container.securityAnalyzer.analyze(items, onlineAllowed) { progress ->
                _state.value = _state.value.copy(progress = progress)
            }

            container.vaultRepository.recordScan(report.updatedItems, report.scannedAt)
            container.settingsStore.setLastScanAt(report.scannedAt)

            _state.value = State(
                scanning = false,
                progress = 1f,
                report = report,
                lastScanAt = report.scannedAt
            )
            container.haptics.play(Haptics.Kind.SUCCESS)

            val count = report.findings.size
            messages.send(
                if (count == 0) UiMessage(R.string.sec_scan_clean)
                else UiMessage(R.string.sec_scan_done, listOf(count))
            )
        }
    }

    fun haptic(kind: Haptics.Kind) = container.haptics.play(kind)
}
