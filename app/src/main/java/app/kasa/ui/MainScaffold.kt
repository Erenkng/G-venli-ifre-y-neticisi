package app.kasa.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kasa.MainActivity
import app.kasa.R
import app.kasa.core.util.Haptics
import app.kasa.data.SettingsStore
import app.kasa.data.model.Category
import app.kasa.data.model.SmartFolder
import app.kasa.data.model.VaultFilter
import app.kasa.ui.components.FabAction
import app.kasa.ui.components.FabMenu
import app.kasa.ui.components.GlassBackdropScrim
import app.kasa.ui.components.KasaNavBar
import app.kasa.ui.components.KasaSnackbarHost
import app.kasa.ui.components.KasaTopBar
import app.kasa.ui.components.NavDestination
import app.kasa.ui.components.SelectionAction
import app.kasa.ui.components.SelectionBar
import app.kasa.ui.components.predictiveBack
import app.kasa.ui.components.rememberBackGesture
import app.kasa.ui.screens.ConfirmDialog
import app.kasa.ui.screens.FolderPickerSheet
import app.kasa.ui.screens.GeneratorScreen
import app.kasa.ui.screens.ItemDetailSheet
import app.kasa.ui.screens.ItemEditorScreen
import app.kasa.ui.screens.QrScanScreen
import app.kasa.ui.screens.SearchOverlay
import app.kasa.ui.components.contentRevealFraction
import app.kasa.ui.components.ExpandingSurface
import app.kasa.ui.components.KasaStatusBarScrim
import app.kasa.ui.screens.SecurityScreen
import app.kasa.ui.screens.SettingsScreen
import app.kasa.ui.screens.TrashScreen
import app.kasa.ui.screens.TypePickerSheet
import app.kasa.ui.screens.categoryIcon
import app.kasa.ui.screens.categoryLabel
import app.kasa.ui.screens.VaultScreen
import app.kasa.ui.theme.KasaMotion

private const val TAB_VAULT = "vault"
private const val TAB_GENERATE = "generate"
private const val TAB_SECURITY = "security"
private const val TAB_SETTINGS = "settings"

/**
 * Kilit açıkken görülen ana iskelet: dört sekme, genişleyen ekleme düğmesi,
 * üstüne binen katmanlar (arama, kayıt ayrıntısı, düzenleyici, karekod
 * tarayıcı) ve tek bir bildirim şeridi.
 *
 * Katmanlar ayrı sayfalar değil, aynı kompozisyonun üstünde duran örtülerdir;
 * bu sayede geri tuşu sırayla en üsttekini kapatır ve kasa listesi arkada
 * durumunu (kaydırma konumu, seçili süzgeç) korur.
 */
@Composable
fun MainScaffold(
    settings: SettingsStore.Settings,
    factory: ViewModelProvider.Factory,
    startAction: String?,
    startItemId: String? = null,
    onActionConsumed: () -> Unit
) {
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val vaultViewModel: VaultViewModel = viewModel(factory = factory)
    val generatorViewModel: GeneratorViewModel = viewModel(factory = factory)
    val securityViewModel: SecurityViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    var tab by rememberSaveable { mutableStateOf(TAB_VAULT) }
    // Çöp kutusu ve tür seçici kendi katmanlarında; kasa listesinin bir
    // süzgeci değiller. Gerekçe TrashScreen ve TypePickerSheet üzerinde yazılı.
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var typePickerOpen by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // Arama çubuğunun ekrandaki yeri. Kaydedilmiyor: yeniden oluşturulduğunda
    // çubuk kendini yeniden ölçüp bildiriyor ve o ana kadar açılış tam ekran
    // beliriyor — yanlış bir yerden büyümektense hiç büyümemek doğru.
    var searchOrigin by remember { mutableStateOf<Rect?>(null) }
    var searchOriginCorner by remember { mutableFloatStateOf(0f) }
    var qrTarget by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // Root / hata ayıklayıcı uyarısı yalnızca bir kez gösterilir; kullanıcıyı
    // her açılışta aynı uyarıyla karşılamak onu uyarıyı okumamaya alıştırır.
    var showIntegrityWarning by remember {
        mutableStateOf(authViewModel.integrity.suspicious && !settings.integrityWarningShown)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val selectedItem by vaultViewModel.selectedItem.collectAsStateWithLifecycle()
    val editing by vaultViewModel.editing.collectAsStateWithLifecycle()
    val vaultView by vaultViewModel.view.collectAsStateWithLifecycle()
    val selection by vaultViewModel.selection.collectAsStateWithLifecycle()
    val allVisibleSelected by vaultViewModel.allVisibleSelected.collectAsStateWithLifecycle()
    val vaultFolders by vaultViewModel.folders.collectAsStateWithLifecycle()
    var folderPickerOpen by remember { mutableStateOf(false) }
    val vaultData by vaultViewModel.data.collectAsStateWithLifecycle()

    // Kısayoldan gelen kayıt: kasa zaten açık olduğu için (bu bileşen yalnızca
    // açık kasada çiziliyor) doğrudan ayrıntı açılıyor.
    LaunchedEffect(startItemId) {
        if (startItemId != null) {
            tab = TAB_VAULT
            vaultViewModel.select(startItemId)
            onActionConsumed()
        }
    }

    // Kısayol ve döşemeden gelen eylemler.
    LaunchedEffect(startAction) {
        when (startAction) {
            MainActivity.ACTION_GENERATE -> tab = TAB_GENERATE
            MainActivity.ACTION_SECURITY -> tab = TAB_SECURITY
            MainActivity.ACTION_SEARCH -> {
                tab = TAB_VAULT
                searchOpen = true
            }
        }
        if (startAction != null) onActionConsumed()
    }

    // Tüm ViewModel'lerin bildirimleri tek şeritte toplanır.
    ShowMessages(vaultViewModel.messageFlow, snackbarHostState, context)
    ShowMessages(generatorViewModel.messageFlow, snackbarHostState, context)
    ShowMessages(securityViewModel.messageFlow, snackbarHostState, context)
    ShowMessages(settingsViewModel.messageFlow, snackbarHostState, context)

    // Ayarlarda açık olan kategori. Ekranın kendisi bildiriyor: çubuk ekranın
    // dışında duruyor ve kategori gezinmesi ekranın içinde.
    var settingsSection by remember { mutableStateOf<String?>(null) }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // Çubuktaki başlık gezinme etiketiyle aynı değil: gezinme çubuğunda
    // "Kasa" yazan sekme, çöp kutusundayken çöp kutusunu gösteriyor.
    val topBarTitle = when {
        tab == TAB_VAULT && vaultView.isTrash -> stringResource(R.string.trash_title)
        tab == TAB_VAULT -> stringResource(R.string.vault_title)
        tab == TAB_GENERATE -> stringResource(R.string.gen_title)
        tab == TAB_SECURITY -> stringResource(R.string.sec_title)
        else -> settingsSection ?: stringResource(R.string.set_title)
    }

    // Üst çubuğun geri oku hangi durumlarda anlamlı: ayarlarda bir kategori
    // açıkken ve kasada "Tümü" dışında bir görünümdeyken. İkisinin de zaten
    // bir BackHandler'ı var; ok yalnızca onu görünür kılıyor.
    val canGoBack = (tab == TAB_SETTINGS && settingsSection != null) ||
        (tab == TAB_VAULT && vaultView != VaultFilter.All)

    val destinations = listOf(
        NavDestination(TAB_VAULT, stringResource(R.string.nav_vault), Icons.Rounded.Lock),
        NavDestination(TAB_GENERATE, stringResource(R.string.nav_generate), Icons.Rounded.AutoAwesome),
        NavDestination(TAB_SECURITY, stringResource(R.string.nav_security), Icons.Rounded.Shield),
        NavDestination(TAB_SETTINGS, stringResource(R.string.nav_settings), Icons.Rounded.Tune)
    )

    // Geri tuşu: en üstteki katmanı kapat, hiçbiri yoksa kasa sekmesine dön.
    BackHandler(enabled = qrTarget != null) { qrTarget = null }
    val trashBack = rememberBackGesture(enabled = qrTarget == null && trashOpen) {
        // Seçim kümesi kasayla çöp kutusu arasında ortak. Geçişte
        // temizlenmezse çöp kutusunda "3 seçildi" yazarken silinecek olan
        // kasadaki üç kayıt oluyordu; kalıcı silmede bunun bedeli ağır.
        vaultViewModel.clearSelection()
        trashOpen = false
    }
    val editorBack = rememberBackGesture(enabled = qrTarget == null && editing != null) {
        vaultViewModel.cancelEdit()
    }
    BackHandler(enabled = qrTarget == null && editing == null && selectedItem != null) {
        vaultViewModel.dismissDetail()
    }
    BackHandler(enabled = qrTarget == null && editing == null && selectedItem == null && searchOpen) {
        searchOpen = false
    }
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null && !searchOpen && fabExpanded
    ) { fabExpanded = false }
    // Seçim kipi geri tuşuyla kapanıyor: kullanıcının otuz kaydı seçtikten
    // sonra yanlışlıkla uygulamadan çıkmasındansa seçimi bırakması iyi.
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null &&
            !searchOpen && !fabExpanded && selection.isNotEmpty()
    ) { vaultViewModel.clearSelection() }
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null &&
            !searchOpen && !fabExpanded && selection.isEmpty() && tab != TAB_VAULT
    ) { tab = TAB_VAULT }
    // Kasa sekmesindeyken bir koleksiyonun içindeysek geri tuşu önce
    // "Tümü" görünümüne döner; uygulamadan çıkmaz.
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null &&
            !searchOpen && !fabExpanded && tab == TAB_VAULT && vaultView != VaultFilter.All
    ) { vaultViewModel.setView(VaultFilter.All) }

    // Gezinti çubuğunun bulanıklaştıracağı arka plan kopyası.
    //
    // İçerik bir kez çiziliyor ve aynı anda bu katmana kaydediliyor; çubuk
    // sonra o kaydı kendi altına düşen parçası için yeniden çiziyor. İçeriği
    // iki kez besteleme yok, dolayısıyla ek bir kare maliyeti de yok.
    val backdrop = rememberGraphicsLayer()

    // Üstteki cam çubuğun ne kadar yerinde olduğu.
    //
    // Ekranların kendisinde tutulamıyordu: çubuk içeriğin kardeşi olarak, yani
    // ekranın dışında çiziliyor. Ekran kaydırma durumunu biliyor, çubuk oranı
    // istiyor; ikisini buluşturan yer burası.
    //
    // Sekme değişince sıfırlanıyor. Yeni ekran kendi oranını bir sonraki
    // karede bildiriyor ve o kare boyunca çubuk, terk edilen sekmenin
    // kaydırmasıyla açık kalırdı.
    // `by` ile değil: temsilci kullanmak değeri beste sırasında okumak demek
    // ve bu değer kaydırmanın her adımında değişiyor — bütün iskelet her
    // karede yeniden birleşirdi. Durum nesnesi olduğu gibi taşınıyor ve
    // yalnızca çizim aşamasında okunuyor.
    val headerCollapse = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(tab) { headerCollapse.floatValue = 0f }

    // Görünürlük seyrek değişiyor (eşik geçilirken bir kez), bu yüzden
    // türetilmiş durum olarak beste sırasında okunabiliyor.
    val barVisible by remember {
        derivedStateOf { headerCollapse.floatValue > BAR_APPEAR_THRESHOLD }
    }
    val scrimFade: FiniteAnimationSpec<Float> = KasaMotion.effect()
    val scrimStrength by animateFloatAsState(
        targetValue = if (barVisible && !searchOpen) 0f else 1f,
        animationSpec = scrimFade,
        label = "statusScrim"
    )

    Box(Modifier.fillMaxSize()) {
        // Sayfa içeriği tüm ekranı kaplıyor: liste sonuna kadar kayıyor ve
        // gezinti çubuğunun altına giriyor. Eskiden çubuk bir `Column` içinde
        // içeriğin yanında duruyordu; içerik alanı orada bittiği için son
        // kayıtlar bir geçiş olmadan kesiliyordu.
        // Ekran içeriği gezinti çubuğunun bulanıklaştıracağı katmana
        // kaydediliyor. Kayıt **bedava değil**: her karede tam ekran boyutunda
        // bir arabelleğe çiziliyor ve sonra ekrana kopyalanıyor. 120 Hz'de bu
        // kopya kare bütçesinin görünür bir kısmını yiyor.
        //
        // Bu yüzden yalnızca bulanıklık gerçekten çizilecekse kaydediliyor;
        // altında içerik yokken (boş kasa) ya da hareket kapalıyken içerik
        // doğrudan ekrana gidiyor ve ara katman hiç kurulmuyor.
        val needsBackdrop = vaultData.liveItems.isNotEmpty()

        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (!needsBackdrop) Modifier else Modifier.drawWithContent {
                        // Katman kaydı başarısız olursa (örneğin beste
                        // dağıtılırken katman serbest bırakılmışsa) içerik
                        // doğrudan çiziliyor: bulanıklık kaybolur, ekran
                        // kaybolmaz.
                        val recorded = runCatching {
                            backdrop.record { this@drawWithContent.drawContent() }
                            drawLayer(backdrop)
                        }.isSuccess
                        if (!recorded) drawContent()
                    }
                )
        ) {
            // Sekme geçişi yönlü: gezinti çubuğunda sağa gidildiğinde içerik de
            // sağdan geliyor. Yön burada süs değil bilgi — kullanıcı hangi
            // yönde ilerlediğini geçişin kendisinden okuyor ve dört sekmenin
            // sırası zihinde bir şerit olarak kalıyor. Ani takas (eski hâl) bu
            // sırayı hiç kurmuyordu; her sekme öncekiyle ilgisiz görünüyordu.
            //
            // Kayma mesafesi genişliğin altıda biri: tam genişlik kaydırmak
            // sekme değişimini sayfa değişimi gibi gösterirdi, oysa dördü de
            // aynı düzeyde duruyor.
            //
            // transitionSpec bir @Composable lambda değil; belirteçler bu yüzden
            // burada, beste içinde okunuyor ve lambdaya hazır değer olarak
            // giriyor. (KasaMotion sistem ayarını CompositionLocal'dan okuduğu
            // için yalnızca beste içinde çağrılabiliyor.)
            val tabSlideIn: FiniteAnimationSpec<IntOffset> = KasaMotion.large()
            val tabSlideOut: FiniteAnimationSpec<IntOffset> = KasaMotion.exit()
            val tabFadeIn: FiniteAnimationSpec<Float> = KasaMotion.enter()
            val tabFadeOut: FiniteAnimationSpec<Float> = KasaMotion.exit()

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = destinations.indexOfFirst { it.key == targetState } >=
                        destinations.indexOfFirst { it.key == initialState }
                    val direction = if (forward) 1 else -1
                    (slideInHorizontally(tabSlideIn) { direction * it / 6 } +
                        fadeIn(tabFadeIn)) togetherWith
                        (slideOutHorizontally(tabSlideOut) { -direction * it / 6 } +
                            fadeOut(tabFadeOut))
                },
                label = "tab",
                modifier = Modifier.fillMaxSize()
            ) { current ->
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        TAB_VAULT -> VaultScreen(
                            viewModel = vaultViewModel,
                            settings = settings,
                            onHeaderCollapse = { headerCollapse.floatValue = it },
                            onOpenSearch = { searchOpen = true },
                            onSearchBounds = { rect, corner ->
                                searchOrigin = rect
                                searchOriginCorner = corner
                            }
                        )

                        TAB_GENERATE -> GeneratorScreen(
                            viewModel = generatorViewModel,
                            settings = settings,
                            onHeaderCollapse = { headerCollapse.floatValue = it },
                            onUseForNewEntry = { generated ->
                                vaultViewModel.startEdit(
                                    app.kasa.data.model.VaultItem(
                                        name = "",
                                        password = app.kasa.core.crypto.SecretText.of(generated)
                                    )
                                )
                            }
                        )

                        TAB_SECURITY -> SecurityScreen(
                            viewModel = securityViewModel,
                            settings = settings,
                            onHeaderCollapse = { headerCollapse.floatValue = it },
                            onOpenCollection = { kind ->
                                vaultViewModel.setView(VaultFilter.Smart(kind))
                                tab = TAB_VAULT
                            }
                        )

                        TAB_SETTINGS -> SettingsScreen(
                            viewModel = settingsViewModel,
                            vaultViewModel = vaultViewModel,
                            onHeaderCollapse = { headerCollapse.floatValue = it },
                            onSectionTitle = { settingsSection = it },
                            onOpenTrash = {
                                vaultViewModel.clearSelection()
                                trashOpen = true
                            }
                        )
                    }
                }
            }
        }

        val onNavigate: (String) -> Unit = {
            if (it != tab) vaultViewModel.haptic(Haptics.Kind.NAV)
            tab = it
            fabExpanded = false
        }

        // Boş kasada çubuğun altında bulanıklaştırılacak içerik yok; tam
        // güçte bir buzlu cam orada yalnızca zemini bulandırıyor.
        val contentBehind = needsBackdrop

        // Çubuğun yüksekliği ölçülüyor, çünkü menü örtüsünün bulanıklığı
        // orada bitmek zorunda: kaydedilmiş kopya çubuğu içermiyor ve o
        // kopyayı çubuğun üstüne çizmek çubuğu görünmez yapıyor. Yükseklik
        // sabit bir sayı değil — sistem çubuğu boşluğu cihaza göre değişiyor.
        var navBarHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        KasaNavBar(
            destinations = destinations,
            selected = tab,
            onSelect = onNavigate,
            backdrop = backdrop,
            contentBehind = contentBehind,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navBarHeight = with(density) { it.height.toDp() } }
        )

        // Durum çubuğunun altındaki ince cam.
        //
        // İçerik kenardan kenara çizildiği için saatin ve pil simgesinin
        // okunabilirliği o an oradan geçen şeyin rengine kalıyordu. Bant
        // gezinme çubuğuyla aynı kaydedilmiş kopyayı kullanıyor; ikinci bir
        // ekran kaydı almıyor, yani kare bütçesine ek yük getirmiyor.
        // İnce cam yalnızca başlık çubuğu yokken: ikisi üst üste gelince aynı
        // bölge iki kez bulanıklaştırılıyor ve sonuç, iki katmanın da tek
        // başına verdiğinden koyu bir leke oluyor.
        KasaStatusBarScrim(
            backdrop = backdrop.takeIf { needsBackdrop },
            strength = scrimStrength,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Kayan içeriğin üstünde duran cam başlık çubuğu. Arama açıkken
        // gizleniyor: arama kendi tam ekran yüzeyini getiriyor ve altında
        // kalan bir başlık, kapanmamış bir ekran gibi duruyor.
        KasaTopBar(
            title = topBarTitle,
            visible = barVisible && !searchOpen,
            progress = { headerCollapse.floatValue },
            backdrop = backdrop.takeIf { needsBackdrop },
            // Geri oku yalnızca gerçekten dönülecek bir yer varken. Düğme
            // kendi eylemini taşımıyor, sistemin geri gönderisini
            // tetikliyor: yukarıdaki BackHandler'lar hangisinin çalışacağına
            // zaten karar veriyor ve buraya ikinci bir karar tablosu yazmak,
            // ikisinin zamanla ayrışmasına açık kapı bırakırdı.
            onBack = if (canGoBack) {
                {
                    vaultViewModel.haptic(Haptics.Kind.NAV)
                    backDispatcher?.onBackPressed()
                }
            } else null,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Seçim kipindeki eylem çubuğu. Gezinme çubuğunun üstüne biniyor,
        // yerine geçmiyor: kullanıcı seçim yaparken sekme değiştirmek
        // isteyebiliyor ve gezinme çubuğunun kaybolması o yolu kapatıyor.
        SelectionBar(
            // Seçim kasa sekmesine ait. Başka sekmedeyken seçim **duruyor**
            // (kullanıcı klasörlerine bakıp dönebilsin diye) ama çubuk
            // görünmüyor: ayarlar ekranının üstünde duran bir "12 seçildi"
            // çubuğu, orada işe yaramayan bir eylem takımı demek. Çöp kutusu
            // da aynı sebeple dışarıda: kendi seçim çubuğu var ve tam ekran
            // açılırken iki çubuk birbirinin üstünden geçiyordu.
            count = if (tab == TAB_VAULT && !trashOpen) selection.size else 0,
            allSelected = allVisibleSelected,
            onSelectAll = vaultViewModel::toggleSelectAllVisible,
            onClose = { vaultViewModel.clearSelection() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SELECTION_BAR_LIFT)
        ) {
            SelectionAction(
                icon = Icons.Rounded.Star,
                label = stringResource(R.string.add_to_favorites),
                onClick = { vaultViewModel.favoriteSelected(true) }
            )
            SelectionAction(
                icon = Icons.Rounded.Folder,
                label = stringResource(R.string.bulk_folder),
                onClick = { folderPickerOpen = true }
            )
            SelectionAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.delete),
                danger = true,
                onClick = { vaultViewModel.trashSelected() }
            )
        }

        if (folderPickerOpen) {
            FolderPickerSheet(
                folders = vaultFolders,
                onPick = { folderId ->
                    vaultViewModel.moveSelectedToFolder(folderId)
                    folderPickerOpen = false
                },
                onDismiss = { folderPickerOpen = false }
            )
        }

        // Menü açıkken ekran buzlanıyor. Düz bir karartmanın altında liste
        // hâlâ okunuyordu ve menüyle dikkat için yarışıyordu; gerekçesi
        // GlassBackdropScrim üzerinde yazılı.
        GlassBackdropScrim(
            visible = fabExpanded,
            onDismiss = { fabExpanded = false },
            // Kaydedilmemiş bir kopyayı bulanıklaştırmak boş bir kare çizmek
            // olurdu: boş kasada kayıt hiç alınmıyor.
            backdrop = backdrop.takeIf { needsBackdrop },
            blurBottomInset = navBarHeight,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            // Çöp kutusundayken yeni kayıt eklemek anlamsız.
            // Seçim kipinde gizleniyor: eylem çubuğuyla aynı köşeyi
            // paylaşıyorlar ve seçim sürerken yeni kayıt eklemek zaten
            // kullanıcının yapmak istediği şey değil.
            visible = tab == TAB_VAULT && !vaultView.isTrash && selection.isEmpty(),
            enter = fadeIn(KasaMotion.effect()) + scaleIn(
                initialScale = 0.7f,
                animationSpec = KasaMotion.medium()
            ),
            exit = fadeOut(KasaMotion.exit()) + scaleOut(
                targetScale = 0.7f,
                animationSpec = KasaMotion.exit()
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 112.dp)
        ) {
            FabMenu(
                expanded = fabExpanded,
                onExpandedChange = {
                    fabExpanded = it
                    vaultViewModel.haptic(if (it) Haptics.Kind.THRESHOLD else Haptics.Kind.TAP)
                },
                icon = Icons.Rounded.Add,
                // Beş birincil tür menüde, kalan dördü "Diğer" ile açılan
                // seçicide. Ayrımın gerekçesi Category.primary üzerinde yazılı.
                actions = Category.primary.map { category ->
                    FabAction(categoryLabel(category), categoryIcon(category)) {
                        fabExpanded = false
                        vaultViewModel.startCreate(category)
                    }
                } + FabAction(stringResource(R.string.fab_other), Icons.Rounded.MoreHoriz) {
                    fabExpanded = false
                    typePickerOpen = true
                }
            )
        }

        // ── üst katmanlar ──────────────────────────────────────────────────

        // ── arama: çubuğun yerinden büyüyerek açılıyor ─────────────────────
        //
        // Önceden ekran %96'dan tam boya solarak geliyordu ve dokunulan çubukla
        // açılan ekran arasında hiçbir görsel bağ yoktu: kullanıcı bir şeye
        // dokunuyor, başka bir şey beliriyordu. Şimdi açılan yüzey tam olarak
        // çubuğun bulunduğu yerden ve boyundan büyüyor, geri gidince oraya
        // dönüyor. Tekniğin gerekçesi ExpandingSurface üzerinde yazılı.
        // ── neden Animatable ve neden ertelenmiş okuma ─────────────────────
        //
        // Önceki hâli `animateFloatAsState` ile bir Float okuyordu ve o okuma
        // **beste aşamasındaydı**: değer her karede değiştiği için ana iskele
        // saniyede 120 kez yeniden besteleniyor, altındaki bütün ekran onunla
        // birlikte geçiyordu. Arama açılırken hissedilen takılma buydu.
        //
        // Değer artık bir `Animatable` içinde ve hiçbir yerde beste
        // aşamasında okunmuyor: yüzeye işlev olarak, içeriğe `graphicsLayer`
        // bloğunda geçiyor. İkisi de çizim aşamasında çalışıyor, yani
        // animasyon boyunca tek bir yeniden besteleme yok.
        val searchExpand = remember { Animatable(0f) }
        // Bileşenin var olup olmadığı ayrı bir durum: bu **kaba** bir sinyal
        // (açıldı/kapandı), kare başına değişmiyor.
        var searchVisible by remember { mutableStateOf(false) }

        LaunchedEffect(searchOpen) {
            if (searchOpen) searchVisible = true
            searchExpand.animateTo(
                targetValue = if (searchOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = SEARCH_DAMPING,
                    stiffness = SEARCH_STIFFNESS,
                    visibilityThreshold = 0.001f
                )
            )
            if (!searchOpen) searchVisible = false
        }

        if (searchVisible) {
            ExpandingSurface(
                progress = { searchExpand.value },
                origin = searchOrigin,
                color = MaterialTheme.colorScheme.surface,
                originCornerPx = searchOriginCorner
            )
            Box(
                Modifier.graphicsLayer {
                    alpha = contentRevealFraction(searchExpand.value, opening = searchOpen)
                }
            ) {
                SearchOverlay(
                    viewModel = vaultViewModel,
                    settings = settings,
                    onClose = { searchOpen = false }
                )
            }
        }

        selectedItem?.let { item ->
            ItemDetailSheet(
                item = item,
                viewModel = vaultViewModel,
                settings = settings,
                onDismiss = { vaultViewModel.dismissDetail() },
                onEdit = {
                    vaultViewModel.dismissDetail()
                    vaultViewModel.startEdit(item)
                }
            )
        }

        AnimatedVisibility(
            visible = editing != null,
            enter = slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = KasaMotion.large()
            ) + fadeIn(KasaMotion.enter()),
            exit = slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = KasaMotion.exit()
            ) + fadeOut(KasaMotion.exit())
        ) {
            editing?.let { item ->
                Box(Modifier.predictiveBack(editorBack)) {
                    ItemEditorScreen(
                        initial = item,
                        viewModel = vaultViewModel,
                        onScanQr = { onResult -> qrTarget = onResult },
                        onClose = { vaultViewModel.cancelEdit() }
                    )
                }
            }
        }

        // Çöp kutusu kendi penceresi gibi: tam ekran, kendi geri tuşu, kasa
        // listesinin süzgeçlerinden bağımsız.
        AnimatedVisibility(
            visible = trashOpen,
            enter = slideInVertically(
                initialOffsetY = { it / 5 },
                animationSpec = KasaMotion.large()
            ) + fadeIn(KasaMotion.enter()),
            exit = slideOutVertically(
                targetOffsetY = { it / 5 },
                animationSpec = KasaMotion.exit()
            ) + fadeOut(KasaMotion.exit())
        ) {
            Box(Modifier.predictiveBack(trashBack)) {
                TrashScreen(
                    viewModel = vaultViewModel,
                    onClose = {
                        vaultViewModel.clearSelection()
                        trashOpen = false
                    }
                )
            }
        }

        if (typePickerOpen) {
            TypePickerSheet(
                onPick = { category ->
                    typePickerOpen = false
                    vaultViewModel.startCreate(category)
                },
                onDismiss = { typePickerOpen = false }
            )
        }

        AnimatedVisibility(
            visible = qrTarget != null,
            enter = fadeIn(KasaMotion.enter()),
            exit = fadeOut(KasaMotion.exit())
        ) {
            QrScanScreen(
                onResult = { value ->
                    qrTarget?.invoke(value)
                    qrTarget = null
                },
                onClose = { qrTarget = null }
            )
        }

        if (showIntegrityWarning) {
            ConfirmDialog(
                title = stringResource(R.string.warn_root_title),
                body = stringResource(R.string.warn_root_body),
                confirmText = stringResource(R.string.warn_understood),
                dismissText = stringResource(R.string.close),
                onConfirm = {
                    showIntegrityWarning = false
                    authViewModel.markIntegrityWarningShown()
                },
                onDismiss = {
                    showIntegrityWarning = false
                    authViewModel.markIntegrityWarningShown()
                }
            )
        }

        KasaSnackbarHost(
            hostState = snackbarHostState,
            backdrop = backdrop.takeIf { needsBackdrop },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 120.dp)
        )
    }
}

/** Bir ViewModel'in bildirim akışını şeride bağlar. */
@Composable
private fun ShowMessages(
    flow: kotlinx.coroutines.flow.Flow<UiMessage>,
    hostState: SnackbarHostState,
    context: android.content.Context
) {
    LaunchedEffect(flow) {
        flow.collect { message ->
            val text = context.getString(message.textRes, *message.args.toTypedArray())
            val action = message.actionRes?.let { context.getString(it) }
            val result = hostState.showSnackbar(
                message = text,
                actionLabel = action,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = false
            )
            if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
        }
    }
}

/**
 * Arama açılışının yay katsayıları.
 *
 * Sönümleme 1'e yakın: aşma yok. Büyüyen bir yüzeyin ekran kenarını geçip
 * geri gelmesi, kenarın kendisini esneyen bir şey gibi gösteriyordu ve ekran
 * kenarı esnemiyor. Sertlik orta-düşük: geçiş yaklaşık 380 ms sürüyor, yani
 * gözün büyümeyi takip edebileceği kadar yavaş, beklemeye dönüşmeyecek kadar
 * hızlı.
 */
private const val SEARCH_DAMPING = 0.96f
private const val SEARCH_STIFFNESS = 520f

/**
 * Çubuğun belirdiği kaydırma oranı.
 *
 * Sıfır değil: parmağın listeye dokunurken ürettiği birkaç piksellik kayma da
 * bir değişim ve eşik tam sıfırda olsaydı çubuk o dokunuşta bir kare için
 * beste ağacına girip çıkardı.
 */
private const val BAR_APPEAR_THRESHOLD = 0.02f

/**
 * Seçim çubuğunun gezinme çubuğunun üstünde kaldığı mesafe.
 *
 * Gezinme çubuğunun yüksekliği ölçülebilirdi ama bu değer sabit tutuluyor:
 * ölçüm bir kare geriden geliyor ve çubuk belirirken zıplıyordu. Sabit
 * mesafe, gezinme çubuğunun tasarım yüksekliğinden türetildi.
 */
private val SELECTION_BAR_LIFT = 96.dp
