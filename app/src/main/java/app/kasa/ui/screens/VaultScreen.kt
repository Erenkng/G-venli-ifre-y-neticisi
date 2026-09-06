package app.kasa.ui.screens

import app.kasa.ui.components.staggeredReveal
import app.kasa.ui.components.REVEAL_WINDOW_MILLIS
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import app.kasa.ui.components.HeaderCollapse
import app.kasa.ui.components.headerHandoff
import app.kasa.ui.components.SkeletonRows
import app.kasa.ui.components.edgeDepth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.core.util.Haptics
import app.kasa.core.util.PasswordStrength
import app.kasa.core.util.rememberHapticPlayer
import app.kasa.data.SettingsStore
import app.kasa.data.model.Category
import app.kasa.data.model.SmartFolder
import app.kasa.data.model.VaultFilter
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.VaultRepository
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.EmptyState
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.RecentCard
import app.kasa.ui.components.SearchBarButton
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.StrengthDot
import app.kasa.ui.components.clickableNoRipple
import app.kasa.ui.components.glassSurface
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Kasa ekranı: arama, koleksiyonlar, kategori süzgeci, son kullanılanlar
 * şeridi ve gruplanmış kayıt listesi.
 */
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings,
    onOpenSearch: () -> Unit,
    /** Arama çubuğunun kök koordinatlardaki yeri; açılış oradan büyüyor. */
    onSearchBounds: ((androidx.compose.ui.geometry.Rect, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * Başlığın ne kadar yukarı çıktığı (0..1).
     *
     * Üstteki cam çubuk bu ekranın kardeşinde çiziliyor ve kaydırma durumu
     * burada duruyor; oran yukarı bildiriliyor. Çubuğun kendisi buraya
     * konulsaydı, ekranın kaydedilmiş kopyasının içine düşerdi ve kendi
     * bulanıklığını bulanıklaştırırdı.
     */
    onHeaderCollapse: (Float) -> Unit = {}
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val items by viewModel.visibleItems.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val smartCounts by viewModel.smartCounts.collectAsStateWithLifecycle()
    val leakAlertCount by viewModel.leakAlertCount.collectAsStateWithLifecycle()
    val folderCounts by viewModel.folderCounts.collectAsStateWithLifecycle()

    val categories = remember { listOf<Category?>(null) + Category.entries.toList() }
    val inTrash = view.isTrash

    // Basılı tutulan kayıt. Ayrıntı ekranından ayrı tutuluyor: bu menü kaydın
    // içeriğini hiç ekrana getirmeden iş bitirmek için var.
    var actionTarget by remember { mutableStateOf<VaultItem?>(null) }
    var listMenuOpen by remember { mutableStateOf(false) }
    var qrTarget by remember { mutableStateOf<VaultItem?>(null) }

    // Sıralama listede uygulanıyor, depoda değil: süzgeç sonucu zaten burada
    // ve sıralama bir görüntüleme tercihi — kasanın içeriğine ait değil.
    val sorted = remember(items, settings.sortOrder) { sortItems(items, settings.sortOrder) }
    val compact = settings.listDensity == SettingsStore.ListDensity.COMPACT
    val listReady by viewModel.listReady.collectAsStateWithLifecycle()

    // İskeletin yerini alan içerik sırayla beliriyor; iskelet kaybolup liste
    // birden gelirse ikisi ayrı iki olay gibi okunuyor, sırayla gelince olan
    // şey tek bir olay: biçim doluyor.
    //
    // Pencere kapandıktan sonra kaydırırken görüş alanına giren satırlar
    // yerinde duruyor — hepsi belirseydi liste sürekli kıpırdayan bir şey
    // olurdu. Ekran döndürüldüğünde de tekrarlanmıyor: liste zaten
    // görülmüştü.
    var revealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sorted.isNotEmpty()) {
        if (sorted.isNotEmpty() && !revealed) {
            delay(REVEAL_WINDOW_MILLIS)
            revealed = true
        }
    }
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    // Çöp kutusunda seçim kipi kapalı: çubuğun eylemleri (sık kullanılana
    // ekle, klasöre taşı, çöpe at) silinmiş kayıtlar için ya anlamsız ya da
    // etkisiz. Oradaki toplu iş geri yükleme ve kalıcı silme; ikisi de çöp
    // kutusu ekranının kendi işleri.
    val selecting = selection.isNotEmpty() && !inTrash

    val listState = rememberLazyListState()

    HeaderCollapse(listState, onHeaderCollapse)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = listContentPadding(extraBottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item(key = "hero") {
            HeroHeader(
                // Büyük başlık cam çubuğa devrederken küçülüp yukarı
                // çekiliyor: ikisi tek bir geçişin iki yarısı.
                modifier = Modifier.headerHandoff(listState),
                title = stringResource(if (inTrash) R.string.trash_title else R.string.vault_title),
                subtitle = if (inTrash) {
                    stringResource(R.string.trash_note, VaultRepository.TRASH_RETENTION_DAYS)
                } else {
                    stringResource(
                        R.string.vault_subtitle,
                        data.liveItems.size,
                        stringResource(R.string.vault_never_synced)
                    )
                }
            )
        }

        item(key = "search") {
            SearchBarButton(
                placeholder = stringResource(R.string.vault_search),
                onClick = onOpenSearch,
                onMenuClick = { listMenuOpen = true },
                onBoundsChanged = onSearchBounds,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }

        item(key = "filters") {
            KasaButtonGroup(
                options = categories,
                selected = category,
                label = { categoryFilterLabel(it) },
                onSelect = { viewModel.setCategory(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // ── güvenlik uyarısı ─────────────────────────────────────────────
        //
        // Sızmış parolalar zaten tespit ediliyordu ama kullanıcı güvenlik
        // sekmesine gitmedikçe görmüyordu — ve oraya gitmek için bir sebebi
        // olmuyordu, çünkü bir şey olduğunu bilmiyordu. Uyarı, kullanıcının
        // zaten olduğu yerde duruyor.
        //
        // Yalnızca sızıntı için: zayıf ve tekrar eden parolalar da bulgu ama
        // onlar "bir gün düzelt" işi. Sızıntı, parolanın **şu anda** başkasının
        // elinde olduğu anlamına geliyor ve listenin başında durmayı hak eden
        // tek bulgu bu. Her bulguyu buraya koymak, hiçbirinin okunmamasıyla
        // sonuçlanırdı.
        //
        // Kart kaydırılarak kapatılabiliyor ve kapatma kalıcı; hangi bulgunun
        // kapatıldığı hatırlanıyor, "uyarma" değil. Gerekçesi
        // VaultViewModel.leakAlertCount üzerinde yazılı.
        if (leakAlertCount > 0 && !inTrash && view == VaultFilter.All) {
            item(key = "leak-alert") {
                LeakAlert(
                    count = leakAlertCount,
                    onOpen = { viewModel.setView(VaultFilter.Smart(SmartFolder.LEAKED)) },
                    onDismiss = viewModel::dismissLeakAlert,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        // ── koleksiyonlar ────────────────────────────────────────────────
        // Yalnızca gösterecek bir şey varken çıkar: boş kasada ikinci bir
        // süzgeç sırası, hiçbir şey kazandırmadan ekranı doldururdu.
        val visibleSmart = SmartFolder.entries.filter { (smartCounts[it] ?: 0) > 0 }
        if (visibleSmart.isNotEmpty() || folders.isNotEmpty()) {
            item(key = "collections-label") {
                Spacer(Modifier.height(8.dp))
                SectionLabel(stringResource(R.string.collections_label))
            }
            item(key = "collections") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    item(key = "all") {
                        CollectionChip(
                            label = stringResource(R.string.collection_all),
                            icon = null,
                            count = data.liveItems.size,
                            selected = view == VaultFilter.All,
                            onClick = { viewModel.setView(VaultFilter.All) }
                        )
                    }
                    items(visibleSmart, key = { it.name }) { kind ->
                        CollectionChip(
                            label = smartFolderLabel(kind),
                            icon = smartFolderIcon(kind),
                            count = smartCounts[kind] ?: 0,
                            selected = (view as? VaultFilter.Smart)?.kind == kind,
                            danger = kind == SmartFolder.LEAKED,
                            onClick = { viewModel.setView(VaultFilter.Smart(kind)) }
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        CollectionChip(
                            label = folder.name,
                            icon = Icons.Rounded.Folder,
                            count = folderCounts[folder.id] ?: 0,
                            selected = (view as? VaultFilter.InFolder)?.folderId == folder.id,
                            onClick = { viewModel.setView(VaultFilter.InFolder(folder.id)) }
                        )
                    }
                }
            }
        }

        if (recents.isNotEmpty() && view == VaultFilter.All && category == null) {
            item(key = "recents-label") {
                SectionLabel(stringResource(R.string.vault_recent))
            }
            item(key = "recents") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recents, key = { it.id }) { entry ->
                        RecentCard(
                            name = entry.name,
                            subtitle = categoryLabel(entry.category),
                            onClick = { viewModel.select(entry.id) },
                            badge = { EntryBadge(item = entry, size = 38.dp, cornerRadius = 13.dp) }
                        )
                    }
                }
            }
        }

        item(key = "all-label") {
            Spacer(Modifier.height(12.dp))
            SectionLabel(
                text = when (val current = view) {
                    VaultFilter.All -> stringResource(R.string.vault_all)
                    is VaultFilter.InFolder ->
                        viewModel.folderName(current.folderId) ?: stringResource(R.string.vault_all)
                    is VaultFilter.Smart -> smartFolderLabel(current.kind)
                },
                count = items.size
            )
        }

        if (items.isEmpty() && !listReady) {
            // İlk süzme hesabı daha bitmedi: gelecek olanın biçimi
            // gösteriliyor, "hiç kayıt yok" değil. Gerekçesi SkeletonRows
            // üzerinde yazılı.
            item(key = "skeleton") {
                SkeletonRows(count = 6, modifier = Modifier.padding(top = 4.dp))
            }
        } else if (items.isEmpty()) {
            item(key = "empty") {
                val emptyVault = data.liveItems.isEmpty()
                EmptyState(
                    title = stringResource(
                        when {
                            inTrash -> R.string.trash_empty_title
                            emptyVault -> R.string.vault_empty_title
                            else -> R.string.vault_no_match_title
                        }
                    ),
                    subtitle = stringResource(
                        when {
                            inTrash -> R.string.trash_empty_sub
                            emptyVault -> R.string.vault_empty_sub
                            else -> R.string.vault_no_match_sub
                        }
                    ),
                    icon = when {
                        inTrash -> Icons.Rounded.DeleteOutline
                        emptyVault -> Icons.Rounded.Lock
                        else -> Icons.Rounded.SearchOff
                    },
                    // Süzgeç yüzünden boş kalan listede çıkış yolu süzgeci
                    // temizlemek; boş kasada ise ilk kaydı eklemek. İkisi
                    // aynı ekranda ama aynı şey değil.
                    actionLabel = when {
                        inTrash || emptyVault -> null
                        else -> stringResource(R.string.vault_clear_filters)
                    },
                    onAction = when {
                        inTrash || emptyVault -> null
                        else -> ({
                            viewModel.setCategory(null)
                            viewModel.setView(VaultFilter.All)
                        })
                    }
                )
            }
        } else {
            itemsIndexed(
                items = sorted,
                key = { _, entry -> entry.id },
                // Bütün satırlar aynı türde; bunu söylemek Compose'un satır
                // bestesini yeniden kurmak yerine yeniden kullanmasını
                // sağlıyor ve kaydırmada beste maliyetini düşürüyor.
                contentType = { _, _ -> "vaultRow" }
            ) { index, entry ->
                // Silinen kayıt yerinden kaybolmuyor, kalanlar boşluğu
                // kayarak kapatıyor. Anlık atlamada kullanıcı hangi satırın
                // gittiğini göremiyor ve "yanlış olanı mı sildim" sorusu
                // kalıyordu; geri alma şeridi de bu yüzden geç fark ediliyordu.
                VaultRow(
                    item = entry,
                    position = groupPositionOf(index, sorted.size),
                    folderName = if (compact) null else viewModel.folderName(entry.folderId),
                    compact = compact,
                    selectable = selecting,
                    selected = entry.id in selection,
                    // Seçim kipindeyken dokunuş kaydı açmıyor, seçiyor:
                    // aynı hareketin iki farklı sonucu olması kipin kendisi.
                    onClick = {
                        if (selecting) viewModel.toggleSelected(entry.id)
                        else viewModel.select(entry.id)
                    },
                    // Basılı tutma kipteyken de işlem sayfasını açsaydı,
                    // kullanıcı seçtiklerini kaybetme riskiyle her basışta
                    // bir pencere görürdü.
                    onLongClick = {
                        if (selecting) viewModel.toggleSelected(entry.id)
                        else actionTarget = entry
                    },
                    modifier = Modifier
                        .staggeredReveal(step = index, play = !revealed)
                        .animateItem(
                            fadeInSpec = KasaMotion.effect(),
                            placementSpec = KasaMotion.medium(),
                            fadeOutSpec = KasaMotion.exit()
                        )
                        // Ekranın uçlarına yaklaşan satırlar geriye çekiliyor:
                        // liste düz bir şerit değil, uçları kıvrılan bir yüzey
                        // gibi okunuyor ve gezinme çubuğunun altına giren
                        // içeriğin oraya gitmesi doğal görünüyor.
                        .edgeDepth()
                )
            }
        }
    }

    if (listMenuOpen) {
        ListOptionsSheet(
            sortOrder = settings.sortOrder,
            density = settings.listDensity,
            onSortChange = viewModel::setSortOrder,
            onDensityChange = viewModel::setListDensity,
            onDismiss = { listMenuOpen = false }
        )
    }

    qrTarget?.let { target ->
        WifiQrDialog(item = target, onDismiss = { qrTarget = null })
    }

    actionTarget?.let { target ->
        RowActionsSheet(
            item = target,
            clipboardSeconds = settings.clipboardClearSeconds,
            onCopySecret = { viewModel.copySecret(it, settings.clipboardClearSeconds) },
            onCopyPlain = viewModel::copyPlain,
            onEdit = { viewModel.startEdit(target) },
            onDuplicate = { viewModel.duplicate(target) },
            onShowWifiQr = { qrTarget = target },
            onToggleFavorite = { viewModel.toggleFavorite(target.id) },
            onDelete = { viewModel.moveToTrash(target) },
            // Seçim kipine giriş buradan: basılı tutmanın kendisi zaten bu
            // sayfayı açıyor ve o hareketi seçime bağlamak, sayfanın öteki
            // sekiz işlemini erişilmez yapardı.
            onSelect = if (target.inTrash) null else ({ viewModel.startSelection(target.id) }),
            onDismiss = { actionTarget = null }
        )
    }
}

/**
 * Seçim kipindeki satırın rozet yerine taşıdığı işaret.
 *
 * Rozetin **yerine** geçiyor, yanına eklenmiyor: satırın yanına ikinci bir
 * yuvarlak koymak satırı daraltıyor ve seçim kipinden çıkınca yerleşim
 * zıplıyordu. Rozet zaten kaydın kimliğini söylüyor ve seçim kipinde asıl
 * soru "hangileri seçili" — kimlik listenin adında duruyor.
 */
@Composable
private fun SelectionMark(selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = KasaMotion.small(),
        label = "selectionMark"
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else Color.Transparent
            )
            .then(
                if (selected) Modifier
                else Modifier.border(
                    2.dp,
                    KasaTheme.colors.ink3.copy(alpha = 0.45f),
                    RoundedCornerShape(KasaRadius.full)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Sızmış parola uyarısı.
 *
 * ### Neden kırmızı bir kart değil
 *
 * Tam kırmızı bir yüzey, listenin başında her açılışta duran bir alarm
 * oluyor ve üçüncü açılışta artık okunmuyor — uyarı körlüğü. Burada zemin
 * sakin, yalnızca işaret ve sayı güç renginde. Uyarının işi korkutmak değil,
 * bir yere **götürmek**.
 *
 * ### Kapatma
 *
 * Kart iki yöne de kaydırılarak kapatılıyor; kapatma kalıcı. Bir "kapat"
 * düğmesi yok: düğme kartın içinde yer kaplar ve asıl işi olan "buraya bas,
 * seni oraya götüreyim" ile aynı yüzeyde iki hedef oluştururdu. Kaydırma
 * kartın tamamını hedef yapıyor ve yanlışlıkla kapatmayı zorlaştırıyor —
 * yolun üçte birini geçmek gerekiyor ve eşiği geçince parmağa tıkırtı geliyor.
 *
 * Kapatılan şey uyarının kendisi değil **o günkü bulgu**: hangi kayıtların
 * sızmış olduğu hatırlanıyor. Sonradan başka bir parola sızarsa kart geri
 * geliyor. Aksi hâlde kullanıcı bir kez kapatarak, henüz duymadığı bir haberi
 * de kapatmış olurdu. Bulgu çözüldüğünde kart zaten kendiliğinden gidiyor.
 */
@Composable
private fun LeakAlert(
    count: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KasaTheme.colors
    val scope = rememberCoroutineScope()
    val play = rememberHapticPlayer()

    // Kaydırma yolu ve eşiği kartın kendi genişliğinden geliyor: sabit bir
    // piksel değeri dar ekranda kartın yarısı, geniş ekranda beşte biri olurdu.
    var width by remember { mutableFloatStateOf(0f) }
    val slide = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    // Kart önce yana kayıyor, sonra kapladığı yer kapanıyor. Yalnızca kaysaydı
    // arkasında kendi boyunda bir boşluk kalır ve altındaki satırlar bir kare
    // sonra yukarı zıplardı.
    val present = remember { MutableTransitionState(true) }
    LaunchedEffect(present.isIdle, present.currentState) {
        if (present.isIdle && !present.currentState) onDismiss()
    }

    // Yay ve süre bileşim sırasında çözülüyor: animasyonu başlatan kod bir eş
    // yordamın içinde ve orada @Composable bir işlev çağrılamaz.
    val settleSpec = KasaMotion.medium<Float>()
    val flingSpec = KasaMotion.exit<Float>()

    AnimatedVisibility(
        visibleState = present,
        exit = shrinkVertically(KasaMotion.exit()) + fadeOut(KasaMotion.exit()),
        modifier = modifier
    ) {
        Box(Modifier.onSizeChanged { width = it.width.toFloat() }) {
            // Kartın altındaki ipucu, kartın **çıktığı** yönde duruyor: yani
            // parmağın açtığı boşlukta. Kaydırma ilerledikçe beliriyor.
            //
            // Yol oranı burada değil çizim sırasında okunuyor. Bileşim
            // gövdesinde okunsaydı sürüklemenin her karesi bütün kartı
            // yeniden birleştirirdi; graphicsLayer'ın bloğu ise animasyonlu
            // değer değişince yalnızca katmanı yeniden çiziyor.
            Row(
                modifier = Modifier.matchParentSize().padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SwipeHint(tint = colors.ink3) { if (slide.value > 0f) swipeFraction(slide.value, width) else 0f }
                SwipeHint(tint = colors.ink3) { if (slide.value < 0f) swipeFraction(slide.value, width) else 0f }
            }

            LeakAlertCard(
                count = count,
                onOpen = onOpen,
                modifier = Modifier
                    .offset { IntOffset(slide.value.roundToInt(), 0) }
                    // Kart uzaklaştıkça soluyor: kapanmanın henüz
                    // tamamlanmadığı, kartın hâlâ orada olmasından okunuyor.
                    .graphicsLayer { alpha = 1f - swipeFraction(slide.value, width) * FADE_ON_SWIPE }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val past = armed
                                scope.launch {
                                    if (past) {
                                        val edge = if (slide.value < 0f) -width else width
                                        slide.animateTo(edge * OVERSHOOT, flingSpec)
                                        present.targetState = false
                                    } else {
                                        armed = false
                                        slide.animateTo(0f, settleSpec)
                                    }
                                }
                            },
                            onDragCancel = {
                                armed = false
                                scope.launch { slide.animateTo(0f, settleSpec) }
                            }
                        ) { change, drag ->
                            change.consume()
                            val next = slide.value + drag
                            scope.launch { slide.snapTo(next) }
                            // Eşik parmak hâlâ ekrandayken bildiriliyor:
                            // kullanıcı bırakmadan önce ne olacağını biliyor.
                            val past = width > 0f && abs(next) > width * DISMISS_FRACTION
                            if (past != armed) {
                                armed = past
                                if (past) play(Haptics.Kind.THRESHOLD)
                            }
                        }
                    }
            )
        }
    }
}

/** Kaydırma yolunun ne kadarının kat edildiği (0..1). */
private fun swipeFraction(slide: Float, width: Float): Float =
    if (width > 0f) (abs(slide) / width).coerceIn(0f, 1f) else 0f

/**
 * Kaydırınca beliren ipucu.
 *
 * Saydamlık bir değer değil bir **işlev** olarak geliyor: çağıran taraf onu
 * bileşim sırasında okumak zorunda kalmıyor, blok çizim sırasında çalışıyor.
 */
@Composable
private fun SwipeHint(tint: Color, alpha: () -> Float) {
    Icon(
        Icons.Rounded.VisibilityOff,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(22.dp)
            .graphicsLayer { this.alpha = alpha() }
    )
}

/** Uyarının kendisi; kaydırma kabuğu [LeakAlert] tarafında. */
@Composable
private fun LeakAlertCard(
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KasaTheme.colors
    // Satırın kabı KasaTile: basınca küçülme, köşe yuvarlanması, kenar ışığı
    // ve cam yüzey listedeki her satırla aynı. Kendi kabını kurmak, aynı
    // davranışı ikinci kez — ve zamanla farklı — yazmak olurdu.
    KasaTile(position = GroupPosition.ONLY, onClick = onOpen, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(colors.strengthWeak.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PrivacyTip,
                contentDescription = null,
                tint = colors.strengthWeak,
                modifier = Modifier.size(21.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.leak_alert_title, count),
                style = KasaTheme.text.tileName,
                color = colors.ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.leak_alert_sub),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Gruplanmış listedeki tek kayıt satırı. */
@Composable
fun VaultRow(
    item: VaultItem,
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    folderName: String? = null,
    onLongClick: (() -> Unit)? = null,
    /**
     * Sıkışık yerleşim: ikincil satır yok.
     *
     * Yüz kaydı geçen bir kasada rahat yerleşim ekrana altı satır sığdırıyor ve
     * liste sonsuz görünüyor. İkincil satırın taşıdığı bilgi (kullanıcı adı,
     * klasör, sızıntı işareti) kayıt açıldığında zaten görünüyor; listede asıl
     * iş aradığını **bulmak** ve onun için ad ile rozet yetiyor.
     */
    compact: Boolean = false,
    /**
     * Seçim kipi açık mı ve bu satır seçili mi.
     *
     * İki ayrı bayrak, çünkü seçim kipinde **seçili olmayan** satırın da
     * görünümü değişiyor: rozetinin yerinde boş bir daire duruyor ve satır
     * "seçilebilir" olduğunu böyle söylüyor. Tek bayrakla, kipteyken seçili
     * olmayan satır sıradan bir satırdan ayırt edilemezdi.
     */
    selectable: Boolean = false,
    selected: Boolean = false
) {
    val tone = toneOf(item)
    val breachMark = stringResource(R.string.breach_mark)

    KasaTile(position = position, onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        if (selectable) SelectionMark(selected = selected) else EntryBadge(item = item)
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name,
                    style = KasaTheme.text.tileName,
                    color = KasaTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.attachments.isNotEmpty()) {
                    Text(
                        text = "· ${item.attachments.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink3
                    )
                }
            }
            if (!compact) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(item.subtitle())
                    if (folderName != null) {
                        if (isNotEmpty()) append(" · ")
                        append(folderName)
                    }
                    if (item.breached) {
                        if (isNotEmpty()) append(" · ")
                        append(breachMark)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.breached) KasaTheme.colors.strengthWeak else KasaTheme.colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            }
        }
        StrengthDot(tone)
    }
}

/**
 * Kayıtları seçilen düzene göre sıralar.
 *
 * Sıralama bir görüntüleme tercihi; kasanın içeriğine ait değil ve bu yüzden
 * depoda değil burada. "En zayıf önce" kasayı temizlerken kullanılıyor:
 * gücü ölçülemeyen türler (not, kart) sona düşüyor, çünkü onlar için
 * "zayıf" diye bir şey yok.
 */
private fun sortItems(items: List<VaultItem>, order: SettingsStore.SortOrder): List<VaultItem> =
    when (order) {
        SettingsStore.SortOrder.LAST_USED ->
            items.sortedWith(compareByDescending<VaultItem> { it.lastUsedAt }.thenBy { it.name.lowercase() })
        SettingsStore.SortOrder.NAME -> items.sortedBy { it.name.lowercase() }
        SettingsStore.SortOrder.NEWEST -> items.sortedByDescending { it.createdAt }
        SettingsStore.SortOrder.WEAKEST -> {
            // Güç ölçümü kayıt başına **bir kez** yapılıyor.
            //
            // Karşılaştırıcının içinde çağrılsaydı her karşılaştırmada bir kez
            // çalışırdı: 500 kayıtta yaklaşık 4500 ölçüm ve her biri ayrıca
            // parolayı String'e açıyor. Sıralamanın kendisi O(n log n), ölçümün
            // de öyle olması gerekmiyor.
            val scores = items.associate { item ->
                item.id to if (item.category == Category.LOGIN && item.password.isNotBlank()) {
                    PasswordStrength.evaluate(item.password.reveal()).score
                } else {
                    Float.MAX_VALUE
                }
            }
            items.sortedWith(
                compareBy<VaultItem> { scores[it.id] ?: Float.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

/**
 * Koleksiyon çipi: simge, ad ve sayaç.
 *
 * Sayacı çipin içinde göstermek, "sızmış" görünümüne dokunmadan önce kaç
 * kayıt olduğunu bilmeyi sağlıyor; boş bir görünüme girip geri dönmek
 * gereksiz bir adım.
 */
@Composable
private fun CollectionChip(
    label: String,
    icon: ImageVector?,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        KasaMotion.small(),
        label = "collectionChip"
    )

    val background = when {
        selected && danger -> KasaTheme.colors.badgeWeakBg
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        selected && danger -> KasaTheme.colors.badgeWeakFg
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        danger -> KasaTheme.colors.strengthWeak
        else -> KasaTheme.colors.ink2
    }

    Row(
        modifier = Modifier
            .height(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(background)
            .then(
                if (selected) Modifier
                else Modifier.border(
                    1.5.dp,
                    KasaTheme.colors.ink3.copy(alpha = 0.4f),
                    RoundedCornerShape(KasaRadius.full)
                )
            )
            .clickableNoRipple(interactionSource = interaction, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1
        )
        Text(
            text = count.toString(),
            style = KasaTheme.text.mono.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = foreground.copy(alpha = 0.7f)
        )
    }
}

/**
 * Sızıntı uyarısının kapanması için kartın kat etmesi gereken yolun oranı.
 *
 * Üçte bir: kaza eseri geçilmeyecek kadar uzun, bilerek yapıldığında tek bir
 * hareketle bitecek kadar kısa. Yarıya çıkarılsaydı kullanıcı parmağını
 * ekranın kenarına kadar götürmek zorunda kalırdı.
 */
private const val DISMISS_FRACTION = 0.33f

/** Kart tam kenardan değil, biraz ötesinden çıkıyor: sonu görünmüyor. */
private const val OVERSHOOT = 1.15f

/** Kaydırıldıkça sönme miktarı; tamamen kaybolmuyor, geri gelebilir. */
private const val FADE_ON_SWIPE = 0.8f
