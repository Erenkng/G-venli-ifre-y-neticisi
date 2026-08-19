package app.kasa.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.R
import app.kasa.core.security.SecureClipboard
import app.kasa.core.util.Haptics
import app.kasa.data.model.Attachment
import app.kasa.data.model.Category
import app.kasa.data.model.Folder
import app.kasa.data.model.SmartFolder
import app.kasa.data.model.VaultFilter
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kasa listesi, koleksiyonlar, arama, kayıt ayrıntısı ve düzenleyici.
 */
class VaultViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.vaultRepository

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    private val _view = MutableStateFlow<VaultFilter>(VaultFilter.All)
    val view: StateFlow<VaultFilter> = _view.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _editing = MutableStateFlow<VaultItem?>(null)
    val editing: StateFlow<VaultItem?> = _editing.asStateFlow()

    private val messages = Channel<UiMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val data = repository.data

    /** Etkin görünüme, süzgece ve arama metnine göre görünen kayıtlar. */
    val visibleItems: StateFlow<List<VaultItem>> =
        combine(repository.data, _category, _query, _view) { _, category, query, view ->
            repository.filter(category, query, view)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<VaultItem>> =
        repository.data.map { repository.recents() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<Folder>> =
        repository.data.map { it.folders.sortedBy { folder -> folder.name.lowercase(TR) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val smartCounts: StateFlow<Map<SmartFolder, Int>> =
        repository.data.map { repository.smartCounts() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val folderCounts: StateFlow<Map<String, Int>> =
        repository.data.map { repository.folderCounts() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val selectedItem: StateFlow<VaultItem?> =
        combine(repository.data, _selectedId) { data, id ->
            id?.let { selected -> data.items.firstOrNull { it.id == selected } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ------------------------------------------------------------- süzgeçler

    fun setCategory(category: Category?) {
        _category.value = category
    }

    fun setView(view: VaultFilter) {
        _view.value = view
        _category.value = null
        container.haptics.play(Haptics.Kind.TAP)
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun folderName(id: String?): String? = repository.folderById(id)?.name

    // ---------------------------------------------------------------- seçim

    fun select(id: String) {
        _selectedId.value = id
        viewModelScope.launch { repository.touch(id) }
    }

    fun dismissDetail() {
        _selectedId.value = null
    }

    fun startCreate(category: Category) {
        val folderId = (_view.value as? VaultFilter.InFolder)?.folderId
        _editing.value = VaultItem(name = "", category = category, folderId = folderId)
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
            if (repository.upsert(item)) {
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

    // ------------------------------------------------------------ çöp kutusu

    /** Kaydı çöp kutusuna taşır; geri alma hem şeritte hem çöp kutusunda. */
    fun moveToTrash(item: VaultItem) {
        viewModelScope.launch {
            if (repository.moveToTrash(item.id)) {
                container.haptics.play(Haptics.Kind.WARNING)
                _selectedId.value = null
                messages.send(
                    UiMessage(
                        textRes = R.string.trash_moved,
                        args = listOf(item.name),
                        actionRes = R.string.detail_undo,
                        action = { viewModelScope.launch { repository.restoreFromTrash(item.id) } }
                    )
                )
            }
        }
    }

    fun restoreFromTrash(item: VaultItem) {
        viewModelScope.launch {
            if (repository.restoreFromTrash(item.id)) {
                container.haptics.play(Haptics.Kind.SUCCESS)
                messages.send(UiMessage(R.string.trash_restored, listOf(item.name)))
            }
        }
    }

    fun purge(item: VaultItem) {
        viewModelScope.launch {
            if (repository.purge(item.id)) {
                container.haptics.play(Haptics.Kind.WARNING)
                _selectedId.value = null
                messages.send(UiMessage(R.string.trash_purged, listOf(item.name)))
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            if (repository.emptyTrash()) {
                container.haptics.play(Haptics.Kind.WARNING)
                messages.send(UiMessage(R.string.trash_emptied))
            }
        }
    }

    // --------------------------------------------------------------- klasör

    fun createFolder(name: String, thenAssignTo: String? = null) {
        viewModelScope.launch {
            val id = repository.createFolder(name) ?: return@launch
            container.haptics.play(Haptics.Kind.SUCCESS)
            if (thenAssignTo != null) repository.moveToFolder(thenAssignTo, id)
            messages.send(UiMessage(R.string.folder_created, listOf(name.trim())))
        }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch { repository.renameFolder(id, name) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            if (repository.deleteFolder(id)) {
                container.haptics.play(Haptics.Kind.WARNING)
                if ((_view.value as? VaultFilter.InFolder)?.folderId == id) _view.value = VaultFilter.All
            }
        }
    }

    // ------------------------------------------------------------------ ekler

    /** Kullanıcının seçtiği dosyayı okur, şifreler ve kayda bağlar. */
    fun attachFile(itemId: String, uri: Uri) {
        viewModelScope.launch {
            val resolver = container.appContext.contentResolver
            val name = displayName(uri) ?: "dosya"
            val mime = resolver.getType(uri) ?: "application/octet-stream"

            val bytes = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                messages.send(UiMessage(R.string.att_failed))
                return@launch
            }
            if (bytes.size > VaultRepository.MAX_ATTACHMENT_BYTES) {
                messages.send(
                    UiMessage(R.string.att_too_big, listOf(VaultRepository.MAX_ATTACHMENT_BYTES / (1024 * 1024)))
                )
                return@launch
            }

            val attachment = repository.addAttachment(itemId, name, mime, bytes)
            bytes.fill(0)
            if (attachment == null) {
                messages.send(UiMessage(R.string.att_failed))
            } else {
                container.haptics.play(Haptics.Kind.SUCCESS)
                messages.send(UiMessage(R.string.att_added, listOf(attachment.name)))
            }
        }
    }

    fun removeAttachment(itemId: String, attachmentId: String) {
        viewModelScope.launch {
            if (repository.removeAttachment(itemId, attachmentId)) {
                container.haptics.play(Haptics.Kind.TOGGLE)
                messages.send(UiMessage(R.string.att_removed))
            }
        }
    }

    /**
     * Eki kullanıcının seçtiği konuma çözüp yazar.
     *
     * Önizleme için önbelleğe açmak yerine yalnızca açık bir dışa aktarma
     * eylemi var: çözülmüş dosya, kullanıcının bilerek seçtiği yerin dışında
     * hiçbir zaman diske düşmüyor.
     */
    fun exportAttachment(attachment: Attachment, target: Uri) {
        viewModelScope.launch {
            val content = repository.readAttachment(attachment)
            if (content == null) {
                messages.send(UiMessage(R.string.att_failed))
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    container.appContext.contentResolver.openOutputStream(target)?.use { it.write(content) } != null
                }.getOrDefault(false)
            }
            content.fill(0)
            messages.send(
                if (ok) UiMessage(R.string.att_exported, listOf(attachment.name))
                else UiMessage(R.string.att_failed)
            )
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        container.appContext.contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    /** Dosya seçici açılırken otomatik kilit tetiklenmemeli. */
    fun suppressAutoLockForPicker() {
        container.autoLocker.suppressNextBackground()
    }

    // ----------------------------------------------------------------- diğer

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
            container.haptics.play(Haptics.Kind.TOGGLE)
        }
    }

    fun moveToFolder(itemId: String, folderId: String?) {
        viewModelScope.launch { repository.moveToFolder(itemId, folderId) }
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
            if (clearSeconds > 0) messages.send(UiMessage(R.string.copied_clip, listOf(clearSeconds)))
            else messages.send(UiMessage(R.string.copied))
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
        else repository.data.value.liveItems.count { it.password == item.password } - 1

    private companion object {
        val TR: java.util.Locale = java.util.Locale("tr", "TR")
    }
}
