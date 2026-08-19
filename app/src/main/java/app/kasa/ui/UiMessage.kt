package app.kasa.ui

import androidx.annotation.StringRes

/**
 * Ekrana gösterilecek kısa bildirim.
 *
 * ViewModel'ler hazır metin değil kaynak kimliği taşır; böylece dil değişince
 * (uygulama içi dil seçimi ya da sistem dili) bildirimler de doğru dilde çıkar
 * ve ViewModel'in `Context`'e bağımlılığı olmaz.
 */
data class UiMessage(
    @StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    @StringRes val actionRes: Int? = null,
    val action: (() -> Unit)? = null,
    val id: Long = nextId()
) {
    private companion object {
        var counter = 0L
        fun nextId(): Long = ++counter
    }
}
