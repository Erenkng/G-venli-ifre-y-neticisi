package app.kasa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import app.kasa.ui.components.KasaNavBar
import app.kasa.ui.components.KasaSnackbarHost
import app.kasa.ui.components.NavDestination
import app.kasa.ui.components.Scrim
import app.kasa.ui.screens.ConfirmDialog
import app.kasa.ui.screens.GeneratorScreen
import app.kasa.ui.screens.ItemDetailSheet
import app.kasa.ui.screens.ItemEditorScreen
import app.kasa.ui.screens.QrScanScreen
import app.kasa.ui.screens.SearchOverlay
import app.kasa.ui.screens.SecurityScreen
import app.kasa.ui.screens.SettingsScreen
import app.kasa.ui.screens.VaultScreen

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
    onActionConsumed: () -> Unit
) {
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val vaultViewModel: VaultViewModel = viewModel(factory = factory)
    val generatorViewModel: GeneratorViewModel = viewModel(factory = factory)
    val securityViewModel: SecurityViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    var tab by rememberSaveable { mutableStateOf(TAB_VAULT) }
    var fabExpanded by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
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

    val destinations = listOf(
        NavDestination(TAB_VAULT, stringResource(R.string.nav_vault), Icons.Rounded.Lock),
        NavDestination(TAB_GENERATE, stringResource(R.string.nav_generate), Icons.Rounded.AutoAwesome),
        NavDestination(TAB_SECURITY, stringResource(R.string.nav_security), Icons.Rounded.Shield),
        NavDestination(TAB_SETTINGS, stringResource(R.string.nav_settings), Icons.Rounded.Tune)
    )

    // Geri tuşu: en üstteki katmanı kapat, hiçbiri yoksa kasa sekmesine dön.
    BackHandler(enabled = qrTarget != null) { qrTarget = null }
    BackHandler(enabled = qrTarget == null && editing != null) { vaultViewModel.cancelEdit() }
    BackHandler(enabled = qrTarget == null && editing == null && selectedItem != null) {
        vaultViewModel.dismissDetail()
    }
    BackHandler(enabled = qrTarget == null && editing == null && selectedItem == null && searchOpen) {
        searchOpen = false
    }
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null && !searchOpen && fabExpanded
    ) { fabExpanded = false }
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null &&
            !searchOpen && !fabExpanded && tab != TAB_VAULT
    ) { tab = TAB_VAULT }
    // Kasa sekmesindeyken bir koleksiyonun içindeysek geri tuşu önce
    // "Tümü" görünümüne döner; uygulamadan çıkmaz.
    BackHandler(
        enabled = qrTarget == null && editing == null && selectedItem == null &&
            !searchOpen && !fabExpanded && tab == TAB_VAULT && vaultView != VaultFilter.All
    ) { vaultViewModel.setView(VaultFilter.All) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    TAB_VAULT -> VaultScreen(
                        viewModel = vaultViewModel,
                        settings = settings,
                        onOpenSearch = { searchOpen = true }
                    )

                    TAB_GENERATE -> GeneratorScreen(
                        viewModel = generatorViewModel,
                        settings = settings,
                        onUseForNewEntry = { generated ->
                            vaultViewModel.startEdit(
                                app.kasa.data.model.VaultItem(name = "", password = generated)
                            )
                        }
                    )

                    TAB_SECURITY -> SecurityScreen(
                        viewModel = securityViewModel,
                        settings = settings,
                        onOpenCollection = { kind ->
                            vaultViewModel.setView(VaultFilter.Smart(kind))
                            tab = TAB_VAULT
                        }
                    )

                    TAB_SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel,
                        vaultViewModel = vaultViewModel,
                        onOpenTrash = {
                            vaultViewModel.setView(VaultFilter.Smart(SmartFolder.TRASH))
                            tab = TAB_VAULT
                        }
                    )
                }
            }

            KasaNavBar(
                destinations = destinations,
                selected = tab,
                onSelect = {
                    if (it != tab) vaultViewModel.haptic(Haptics.Kind.NAV)
                    tab = it
                    fabExpanded = false
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        }

        Scrim(
            visible = fabExpanded,
            onDismiss = { fabExpanded = false },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            // Çöp kutusundayken yeni kayıt eklemek anlamsız.
            visible = tab == TAB_VAULT && !vaultView.isTrash,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 104.dp)
        ) {
            FabMenu(
                expanded = fabExpanded,
                onExpandedChange = {
                    fabExpanded = it
                    vaultViewModel.haptic(Haptics.Kind.MEDIUM)
                },
                icon = Icons.Rounded.Add,
                actions = listOf(
                    FabAction(stringResource(R.string.add_login), Icons.Rounded.Password) {
                        fabExpanded = false
                        vaultViewModel.startCreate(Category.LOGIN)
                    },
                    FabAction(stringResource(R.string.add_card), Icons.Rounded.CreditCard) {
                        fabExpanded = false
                        vaultViewModel.startCreate(Category.CARD)
                    },
                    FabAction(stringResource(R.string.add_note), Icons.Rounded.Notes) {
                        fabExpanded = false
                        vaultViewModel.startCreate(Category.NOTE)
                    },
                    FabAction(stringResource(R.string.add_otp), Icons.Rounded.Timer) {
                        fabExpanded = false
                        vaultViewModel.startCreate(Category.OTP)
                    }
                )
            )
        }

        // ── üst katmanlar ──────────────────────────────────────────────────

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.96f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.96f)
        ) {
            SearchOverlay(
                viewModel = vaultViewModel,
                settings = settings,
                onClose = { searchOpen = false }
            )
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
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
        ) {
            editing?.let { item ->
                ItemEditorScreen(
                    initial = item,
                    viewModel = vaultViewModel,
                    onScanQr = { onResult -> qrTarget = onResult },
                    onClose = { vaultViewModel.cancelEdit() }
                )
            }
        }

        AnimatedVisibility(
            visible = qrTarget != null,
            enter = fadeIn(),
            exit = fadeOut()
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp)
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
