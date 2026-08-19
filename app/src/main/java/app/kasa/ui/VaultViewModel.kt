package app.kasa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.R
import app.kasa.core.security.SecureClipboard
import app.kasa.core.util.Haptics
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Kasa listesi, arama, kayıt ayrıntısı ve düzenleyici için durum.
 */
class VaultViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.vaultRepository

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _editing = MutableStateFlow<VaultItem?>(null)
    val editing: StateFlow<VaultItem?> = _editing.asStateFlow()

    private val messages = Channel<UiMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val data = repository.data

    /** Etkin süzgece ve arama metnine göre görünen kayıtlar. */
    val visibleItems: StateFlow<List<VaultItem>> =
        combine(repository.data, _category, _query) { _, category, query ->
            repository.filter(category, query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<VaultItem>> =
        combine(repository.data, _category) { _, _ -> repository.recents() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedItem: StateFlow<VaultItem?> =
        combine(repository.data, _selectedId) { data, id ->
            id?.let { selected -> data.items.firstOrNull { it.id == selected } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCategory(category: Category?) {
        _category.value = category
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun select(id: String) {
        _selectedId.value = id
        viewModelScope.launch { repository.touch(id) }
    }

    fun dismissDetail() {
        _selectedId.value = null
    }

    fun startCreate(category: Category) {
        _editing.value = VaultItem(name = "", category = category)
    }

    fun startEdit(item: VaultItem) {
        _editing.value = item
    }

    fun cancelEdit() {
        _editing.value = null
    }

    fun save(item: VaultItem) {
        viewModelScope.launch {
            val isNew = repository.byId(item.id) == null
            val ok = repository.upsert(item)
            if (ok) {
                container.haptics.play(Haptics.Kind.SUCCESS)
                messages.send(
                    UiMessage(
                        if (isNew) R.string.detail_created else R.string.detail_updated,
                        listOf(item.name)
                    )
                )
                _editing.value = null
            }
        }
    }

    fun delete(item: VaultItem) {
        viewModelScope.launch {
            if (repository.delete(item.id)) {
                container.haptics.play(Haptics.Kind.WARNING)
                _selectedId.value = null
                messages.send(
                    UiMessage(
                        textRes = R.string.detail_deleted,
                        args = listOf(item.name),
                        actionRes = R.string.detail_undo,
                        action = { viewModelScope.launch { repository.restore(item) } }
                    )
                )
            }
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
            container.haptics.play(Haptics.Kind.TOGGLE)
        }
    }

    /**
     * Gizli bir değeri panoya kopyalar ve otomatik temizleme alarmını kurar.
     * Kullanıcı adı gibi gizli olmayan değerler için [copyPlain] kullanılır.
     */
    fun copySecret(text: String, clearSeconds: Int) {
        if (text.isEmpty()) return
        SecureClipboard.copySensitive(container.appContext, text, clearSeconds)
        container.haptics.play(Haptics.Kind.SUCCESS)
        viewModelScope.launch {
            if (clearSeconds > 0) {
                messages.send(UiMessage(R.string.copied_clip, listOf(clearSeconds)))
            } else {
                messages.send(UiMessage(R.string.copied))
            }
        }
    }

    fun copyPlain(text: String) {
        if (text.isEmpty()) return
        SecureClipboard.copyPlain(container.appContext, text)
        container.haptics.play(Haptics.Kind.SUCCESS)
        viewModelScope.launch { messages.send(UiMessage(R.string.copied)) }
    }

    fun haptic(kind: Haptics.Kind) = container.haptics.play(kind)

    /** Bu kimlikte bir kayıt kasada var mı? Düzenleyici başlığı buna bakar. */
    fun isExisting(id: String): Boolean = repository.byId(id) != null

    /** Aynı parolayı kullanan diğer kayıt sayısı — ayrıntı ekranındaki uyarı için. */
    fun reuseCount(item: VaultItem): Int =
        if (item.password.isBlank()) 0
        else repository.data.value.items.count { it.password == item.password } - 1
}
