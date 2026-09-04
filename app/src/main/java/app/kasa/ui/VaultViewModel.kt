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
import kotlinx.coroutines.flow.onEach
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

    /**
     * İlk liste hesabı tamamlandı mı.
     *
     * `visibleItems` bir `stateIn` ve başlangıç değeri boş liste. Kasa
     * açıldıktan sonra ilk süzme sonucu gelene kadar ekranda o boş liste
     * duruyor — yani "yükleniyor" ile "hiç kayıt yok" aynı görünüyor. İkisi
     * bir parola yöneticisinde birbirinden çok uzak iki cümle.
     *
     * Bayrak ilk gerçek yayından sonra kalkıyor ve bir daha inmiyor: sonraki
     * süzmeler önceki sonucu koruduğu için ekran hiç boşalmıyor.
     */
    private val _listReady = MutableStateFlow(false)
    val listReady: StateFlow<Boolean> = _listReady.asStateFlow()

    /** Etkin görünüme, süzgece ve arama metnine göre görünen kayıtlar. */
    val visibleItems: StateFlow<List<VaultItem>> =
        combine(repository.data, _category, _query, _view) { _, category, query, view ->
            repository.filter(category, query, view)
        }
            // Bayrak ilk **gerçek** sonuçla kalkıyor. Depoyu beklemek yanlıştı:
            // depo bir StateFlow ve elindeki değeri anında veriyor, oysa
            // beklenen şey süzmenin kendisi.
            .onEach { _listReady.value = true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /**
     * Bir kaydı geçmişteki parolasına döndürür.
     *
     * ### Neden depoya yeni bir parola olarak gidiyor
     *
     * Geri yükleme, geçmişten bir satırı **silmek** değil; o değeri yeniden
     * geçerli yapmak. Kayıt normal yoldan güncellendiği için depo o an
     * kayıtlı olan parolayı geçmişin başına atıyor — yani işlem tersine
     * çevrilebilir kalıyor ve yanlış satırı seçen kullanıcı tek dokunuşla
     * geri dönebiliyor.
     *
     * Geçmişten kopya alınıyor: gizli metnin sahipliği kasada kalmalı, yoksa
     * geçmiş satırının temizlenmesi geçerli parolayı da siler.
     */
    fun restorePassword(item: VaultItem, entry: app.kasa.data.model.PasswordHistoryEntry) {
        viewModelScope.launch {
            val restored = item.copy(
                password = app.kasa.core.crypto.SecretText.adopt(entry.password.copyChars())
            )
            if (repository.upsert(restored)) {
                container.haptics.play(Haptics.Kind.UNDO)
                messages.send(UiMessage(R.string.history_restored))
            }
        }
    }

    fun save(item: VaultItem) {
        viewModelScope.launch {
            val isNew = repository.byId(item.id) == null
            if (repository.upsert(item)) {
                container.haptics.play(if (isNew) Haptics.Kind.CREATE else Haptics.Kind.SUCCESS)
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

    // --------------------------------------------------------- toplu seçim

    /**
     * Seçili kayıtların kimlikleri. Boşken seçim kipi kapalı.
     *
     * ### Neden ayrı bir kip
     *
     * Otuz kaydı bir klasöre taşımanın tek yolu, otuz kez kaydı açıp
     * klasörünü değiştirmekti. Kasa büyüdükçe bu iş yapılmaz hâle geliyor ve
     * kullanıcı düzeni bırakıyor — yani klasörler var ama kimse kullanmıyor.
     *
     * ### Neden kimlik kümesi, kayıt listesi değil
     *
     * Seçim sırasında kayıtlar değişebiliyor (arama daraltılıyor, bir kayıt
     * siliniyor). Kimlik tutmak, artık var olmayan bir kaydın seçili
     * kalmasını da zararsız kılıyor: toplu işlem eşleşmeyeni sessizce
     * atlıyor.
     */
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun toggleSelected(id: String) {
        val current = _selection.value
        _selection.value = if (id in current) current - id else current + id
        container.haptics.play(Haptics.Kind.TAP)
    }

    /** Seçim kipini bu kayıtla başlatır. */
    fun startSelection(id: String) {
        _selection.value = setOf(id)
        container.haptics.play(Haptics.Kind.THRESHOLD)
    }

    fun clearSelection() {
        if (_selection.value.isEmpty()) return
        _selection.value = emptySet()
        container.haptics.play(Haptics.Kind.NAV)
    }

    /**
     * Görünen kayıtların hepsini seçer; hepsi zaten seçiliyse seçimi
     * kaldırır.
     *
     * Aynı düğmenin iki işi olması, ikinci bir "hiçbirini seçme" düğmesi
     * eklemekten daha az yer kaplıyor ve durumu düğmenin kendisi söylüyor.
     */
    fun toggleSelectAllVisible() {
        val all = visibleItems.value.map { it.id }.toSet()
        _selection.value = if (all.isNotEmpty() && _selection.value.containsAll(all)) emptySet() else all
        container.haptics.play(Haptics.Kind.TOGGLE)
    }

    /**
     * Görünenlerin hepsi seçili mi.
     *
     * Ekran iskeleti bunu doğrudan hesaplasaydı, görünen kayıt listesini
     * okumak zorunda kalır ve listedeki her değişiklikte bütün iskelet
     * yeniden birleşirdi. Burada tek bir mantıksal değere iniyor.
     */
    val allVisibleSelected: StateFlow<Boolean> =
        combine(visibleItems, _selection) { items, selected ->
            items.isNotEmpty() && selected.containsAll(items.map { it.id })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun trashSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (repository.moveToTrash(ids)) {
                container.haptics.play(Haptics.Kind.DISCARD)
                _selection.value = emptySet()
                messages.send(
                    UiMessage(
                        textRes = R.string.bulk_trashed,
                        args = listOf(ids.size),
                        actionRes = R.string.detail_undo,
                        action = { viewModelScope.launch { repository.restoreFromTrash(ids) } }
                    )
                )
            }
        }
    }

    /** Seçili kayıtları çöp kutusundan geri alır. */
    fun restoreSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (repository.restoreFromTrash(ids)) {
                container.haptics.play(Haptics.Kind.UNDO)
                _selection.value = emptySet()
                messages.send(UiMessage(R.string.bulk_restored, listOf(ids.size)))
            }
        }
    }

    /**
     * Seçili kayıtları geri dönüşsüz siler.
     *
     * Geri alma **yok**: silinen şey artık hiçbir yerde durmuyor ve bir
     * "geri al" şeridi, kullanıcıya olmayan bir güvence verirdi. Onay
     * penceresi bu yüzden çağrı yerinde, işlemden önce.
     */
    fun purgeSelected() {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (repository.purge(ids)) {
                container.haptics.play(Haptics.Kind.DESTRUCTIVE)
                _selection.value = emptySet()
                messages.send(UiMessage(R.string.bulk_purged, listOf(ids.size)))
            }
        }
    }

    /** Verilen kayıtların hepsini seçer; hepsi zaten seçiliyse seçimi kaldırır. */
    fun toggleSelectAll(ids: List<String>) {
        val all = ids.toSet()
        _selection.value = if (all.isNotEmpty() && _selection.value.containsAll(all)) emptySet() else all
        container.haptics.play(Haptics.Kind.TOGGLE)
    }

    fun favoriteSelected(favorite: Boolean) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (repository.setFavorite(ids, favorite)) {
                container.haptics.play(Haptics.Kind.TOGGLE)
                _selection.value = emptySet()
                messages.send(
                    UiMessage(
                        if (favorite) R.string.bulk_favorited else R.string.bulk_unfavorited,
                        listOf(ids.size)
                    )
                )
            }
        }
    }

    fun moveSelectedToFolder(folderId: String?) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (repository.moveToFolder(ids, folderId)) {
                container.haptics.play(Haptics.Kind.SUCCESS)
                _selection.value = emptySet()
                messages.send(UiMessage(R.string.bulk_moved, listOf(ids.size)))
            }
        }
    }

    // ------------------------------------------------------------ çöp kutusu

    /** Kaydı çöp kutusuna taşır; geri alma hem şeritte hem çöp kutusunda. */
    fun moveToTrash(item: VaultItem) {
        viewModelScope.launch {
            if (repository.moveToTrash(item.id)) {
                container.haptics.play(Haptics.Kind.DISCARD)
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
                container.haptics.play(Haptics.Kind.UNDO)
                messages.send(UiMessage(R.string.trash_restored, listOf(item.name)))
            }
        }
    }

    fun purge(item: VaultItem) {
        viewModelScope.launch {
            if (repository.purge(item.id)) {
                container.haptics.play(Haptics.Kind.DESTRUCTIVE)
                _selectedId.value = null
                messages.send(UiMessage(R.string.trash_purged, listOf(item.name)))
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            if (repository.emptyTrash()) {
                container.haptics.play(Haptics.Kind.DESTRUCTIVE)
                messages.send(UiMessage(R.string.trash_emptied))
            }
        }
    }

    // --------------------------------------------------------------- klasör

    fun createFolder(name: String, thenAssignTo: String? = null) {
        viewModelScope.launch {
            val id = repository.createFolder(name) ?: return@launch
            container.haptics.play(Haptics.Kind.CREATE)
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
                container.haptics.play(Haptics.Kind.DISCARD)
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
                container.haptics.play(Haptics.Kind.ATTACH)
                messages.send(UiMessage(R.string.att_added, listOf(attachment.name)))
            }
        }
    }

    fun removeAttachment(itemId: String, attachmentId: String) {
        viewModelScope.launch {
            if (repository.removeAttachment(itemId, attachmentId)) {
                container.haptics.play(Haptics.Kind.DETACH)
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

    /**
     * Kaydın kopyasını çıkarır ve düzenleyiciyi açar.
     *
     * Kopya çıkarmanın tek sebebi genellikle onu değiştirmek: aynı sitede
     * ikinci hesap, farklı kullanıcı adı. Düzenleyicinin kendiliğinden açılması
     * bu adımı atlıyor; kullanıcı kopyayı listede bulup ayrıca dokunmak
     * zorunda kalmıyor.
     */
    fun duplicate(item: VaultItem) {
        viewModelScope.launch {
            val copy = repository.duplicate(item.id)
            if (copy != null) {
                container.haptics.play(Haptics.Kind.CREATE)
                _editing.value = copy
            }
        }
    }

    fun setSortOrder(order: app.kasa.data.SettingsStore.SortOrder) {
        viewModelScope.launch { container.settingsStore.setSortOrder(order) }
    }

    fun setListDensity(density: app.kasa.data.SettingsStore.ListDensity) {
        viewModelScope.launch { container.settingsStore.setListDensity(density) }
    }

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
        container.haptics.play(Haptics.Kind.SECRET)
        viewModelScope.launch {
            if (clearSeconds > 0) messages.send(UiMessage(R.string.copied_clip, listOf(clearSeconds)))
            else messages.send(UiMessage(R.string.copied))
        }
    }

    fun copyPlain(text: String) {
        if (text.isEmpty()) return
        SecureClipboard.copyPlain(container.appContext, text)
        container.haptics.play(Haptics.Kind.COPY)
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
