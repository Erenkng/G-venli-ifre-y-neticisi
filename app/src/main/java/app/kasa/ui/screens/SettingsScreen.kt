package app.kasa.ui.screens

import app.kasa.ui.components.HeaderCollapse
import app.kasa.ui.components.REVEAL_WINDOW_MILLIS
import app.kasa.ui.components.staggeredReveal
import kotlinx.coroutines.delay
import app.kasa.ui.components.predictiveBackPush
import app.kasa.ui.components.rememberBackGesture
import app.kasa.ui.components.headerHandoff
import app.kasa.data.GradientTheme
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import app.kasa.ui.components.glassSurface
import app.kasa.ui.components.LoadingOverlay
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.clickableNoRipple
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.BuildConfig
import app.kasa.R
import app.kasa.core.crypto.KeystoreKeys
import app.kasa.data.ThemeMode
import app.kasa.data.model.Folder
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.SettingsViewModel
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaBadge
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaCard
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.components.KasaPinField
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.animatedCorner
import app.kasa.ui.components.pressRim
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaTheme

/**
 * Ayarlar ekranı.
 *
 * Sıralama bilinçli: önce her gün dokunulan görünüm ve cihaz anahtarları,
 * sonra güvenlik eşikleri, en sonda geri dönüşü olmayan kasa işlemleri.
 * Yıkıcı işlem (kasayı sil) en altta ve tek başına duruyor.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    vaultViewModel: VaultViewModel,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Başlığın ne kadar yukarı çıktığı (0..1).
     *
     * Üstteki cam çubuk bu ekranın kardeşinde çiziliyor ve kaydırma durumu
     * burada duruyor; oran yukarı bildiriliyor. Çubuğun kendisi buraya
     * konulsaydı, ekranın kaydedilmiş kopyasının içine düşerdi ve kendi
     * bulanıklığını bulanıklaştırırdı.
     */
    onHeaderCollapse: (Float) -> Unit = {},
    /**
     * Açık olan kategorinin adı; kategori yokken null.
     *
     * Üstteki cam çubuk ekranın dışında duruyor ve hangi kategoride
     * olunduğunu bilmiyor. Bilmeseydi kullanıcı "Güvenlik" sayfasının
     * ortasındayken çubukta "Ayarlar" yazardı — yani çubuk, tam da işe
     * yarayacağı yerde yanlış bilgi verirdi.
     */
    onSectionTitle: (String?) -> Unit = {}
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recoveryCode by viewModel.recoveryCode.collectAsStateWithLifecycle()
    val changeResult by viewModel.changeResult.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val gate = LocalBiometricGate.current

    var showChangeMaster by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showRotate by remember { mutableStateOf(false) }
    var showRecalibrate by remember { mutableStateOf(false) }
    var showDuress by remember { mutableStateOf(false) }
    var pinOn by remember { mutableStateOf(viewModel.pinEnabled) }

    val calibrationProgress by viewModel.calibrationProgress.collectAsStateWithLifecycle()

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.trustCurrentNetwork() }

    val folders by vaultViewModel.folders.collectAsStateWithLifecycle()
    val vaultData by vaultViewModel.data.collectAsStateWithLifecycle()
    var pendingExportPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingImportPassword by remember { mutableStateOf<CharArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && password != null) viewModel.exportTo(uri, password)
        else password?.fill('\u0000')
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val password = pendingImportPassword
        pendingImportPassword = null
        if (uri != null && password != null) viewModel.importFrom(uri, password)
        else password?.fill('\u0000')
    }

    // Chrome ve öteki yöneticilerin CSV dışa aktarımı. Ayrı bir seçici,
    // çünkü bu dosya şifreli değil ve parola sormanın anlamı yok.
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importCsv(uri) }

    val actions = listOf(
        VaultAction(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.set_change_master),
            subtitle = stringResource(R.string.set_change_master_sub, relativeTime(viewModel.masterKeyChangedAt())),
            background = KasaTheme.colors.badgeStrongBg,
            foreground = KasaTheme.colors.badgeStrongFg
        ) { showChangeMaster = true },
        VaultAction(
            icon = Icons.Rounded.Key,
            title = stringResource(R.string.set_recovery),
            subtitle = stringResource(R.string.set_recovery_sub),
            background = KasaTheme.colors.badgeMidBg,
            foreground = KasaTheme.colors.badgeMidFg
        ) { viewModel.regenerateRecoveryKey() },
        VaultAction(
            icon = Icons.Rounded.Folder,
            title = stringResource(R.string.set_folders),
            subtitle = stringResource(R.string.set_folders_sub, folders.size),
            background = KasaTheme.colors.badgeBlueBg,
            foreground = KasaTheme.colors.badgeBlueFg
        ) { showFolders = true },
        VaultAction(
            icon = Icons.Rounded.Delete,
            title = stringResource(R.string.trash_title),
            subtitle = stringResource(R.string.set_trash_sub, vaultData.trashedItems.size),
            background = KasaTheme.colors.badgeMidBg,
            foreground = KasaTheme.colors.badgeMidFg
        ) { onOpenTrash() },
        VaultAction(
            icon = Icons.Rounded.Download,
            title = stringResource(R.string.set_export),
            subtitle = stringResource(R.string.set_export_sub),
            background = KasaTheme.colors.badgeBlueBg,
            foreground = KasaTheme.colors.badgeBlueFg
        ) { showExport = true },
        VaultAction(
            icon = Icons.Rounded.Upload,
            title = stringResource(R.string.set_import),
            subtitle = stringResource(R.string.set_import_sub),
            background = KasaTheme.colors.badgeBlueBg,
            foreground = KasaTheme.colors.badgeBlueFg
        ) { showImport = true },
        VaultAction(
            icon = Icons.Rounded.SwapHoriz,
            title = stringResource(R.string.csv_title),
            subtitle = stringResource(R.string.csv_sub),
            background = KasaTheme.colors.badgeBlueBg,
            foreground = KasaTheme.colors.badgeBlueFg
        ) {
            // text/csv her cihazda tanınmıyor; text/comma-separated-values ve
            // düz metin de kabul ediliyor, yoksa dosya seçicide dosya gri
            // görünüyor ve kullanıcı seçemiyor.
            csvLauncher.launch(
                arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
            )
        },
        VaultAction(
            icon = Icons.Rounded.Autorenew,
            title = stringResource(R.string.rotate_title),
            subtitle = stringResource(R.string.rotate_sub),
            background = KasaTheme.colors.badgeStrongBg,
            foreground = KasaTheme.colors.badgeStrongFg
        ) { showRotate = true },
        VaultAction(
            icon = Icons.Rounded.Shield,
            title = stringResource(R.string.duress_title),
            subtitle = stringResource(R.string.duress_sub),
            background = KasaTheme.colors.badgeMidBg,
            foreground = KasaTheme.colors.badgeMidFg
        ) { showDuress = true },
        VaultAction(
            icon = Icons.Rounded.DeleteForever,
            title = stringResource(R.string.set_wipe),
            subtitle = stringResource(R.string.set_wipe_sub),
            background = KasaTheme.colors.badgeWeakBg,
            foreground = KasaTheme.colors.badgeWeakFg
        ) { showWipe = true }
    )

    // ── kategoriler ───────────────────────────────────────────────────────
    //
    // Ayarlar ekranı yetmiş satırlık tek bir listeydi. Her yeni anahtar onu
    // biraz daha uzatıyordu ve bir noktada "otomatik kilit süresi nerede"
    // sorusunun cevabı "kaydır ve ara" oldu. Uzun bir listede arama, göz
    // taramasıyla yapılıyor ve göz ancak ekranda duran kadarını tarayabiliyor.
    //
    // Şimdi altı kategori var ve her biri kendi ekranını açıyor. Bölünme
    // rastgele değil: kullanıcının bir ayarı ararken kendine sorduğu soruya
    // göre — "nasıl görünüyor", "nasıl kilitleniyor", "neyi koruyor",
    // "nasıl şifreleniyor", "kayıtlarım", "bu uygulama ne".
    //
    // Durum ve pencereler tek bir yerde kalıyor. Bölümleri ayrı dosyalara
    // taşımak, on beş parça yerel durumu ve on pencereyi yukarı taşımak
    // demekti; kazanılan şey dosya boyu, kaybedilen şey ise her ayarın hangi
    // pencereyi açtığını tek bakışta görebilmek olurdu.
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }

    // Kategoriden çıkış parmakla birlikte yürüyor: kullanıcı kenardan çekerken
    // içerik gidilen yolun tersine kayıyor, bırakınca oradan devam ediyor.
    val sectionBack = rememberBackGesture(enabled = section != null) { section = null }

    val sectionTitle = section?.let { stringResource(it.titleRes) }
    LaunchedEffect(sectionTitle) { onSectionTitle(sectionTitle) }
    // Ekrandan çıkarken çubuğun elinde kalan ad temizleniyor; yoksa başka bir
    // sekmeye geçildiğinde orada kategori adı yazılı kalırdı.
    DisposableEffect(Unit) { onDispose { onSectionTitle(null) } }

    // Kategori satırları sırayla beliriyor.
    //
    // Altı satır aynı karede geldiğinde tek bir blok olarak okunuyor ve
    // aralarındaki sıra kayboluyor; sırayla gelince göz onları ayrı ayrı
    // görüyor. Yalnızca bir kez: her geri dönüşte tekrarlansaydı ekran
    // "yükleniyor" gibi görünürdü.
    var revealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(REVEAL_WINDOW_MILLIS)
        revealed = true
    }

    val enterSpec = KasaMotion.enter<Float>()
    val exitSpec = KasaMotion.exit<Float>()
    val slideSpec = KasaMotion.large<IntOffset>()

    // Uzun işler için örtü.
    //
    // Anahtar döndürme bütün kasayı yeniden şifreliyor, ana parola değişimi
    // anahtarı yeniden türetiyor, içe aktarma her kaydı ayrı ayrı şifreliyor.
    // Üçü de saniyeler sürüyor ve ekranda hiçbir karşılığı yoktu: kullanıcı
    // aynı düğmeye ikinci kez basabiliyordu.
    //
    // Parola penceresi açıkken örtü çıkmıyor: o pencerenin kendi durum
    // yazısı var ve ikisi üst üste gelince pencere örtünün altında kalıyor.
    val overlayVisible = busy && !showChangeMaster

    AnimatedContent(
        targetState = section,
        transitionSpec = {
            // Kategoriye girerken içerik sağdan, çıkarken soldan: yön,
            // kullanıcının gezinme ağacında nereye gittiğini söylüyor.
            val forward = targetState != null
            val direction = if (forward) 1 else -1
            (fadeIn(enterSpec) + slideInHorizontally(slideSpec) { direction * it / 5 })
                .togetherWith(fadeOut(exitSpec) + slideOutHorizontally(slideSpec) { -direction * it / 6 })
        },
        label = "settingsSection"
    ) { current ->
    val listState = rememberLazyListState()
    HeaderCollapse(listState, onHeaderCollapse)
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            // Yalnızca kategori ekranı çekiliyor. Kök liste geri gidilecek
            // yerin kendisi; onu da kaydırmak, varılan yeri de yola çıkarırdı.
            .then(if (current != null) Modifier.predictiveBackPush(sectionBack) else Modifier),
        contentPadding = listContentPadding(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (current == null) {
        item(key = "hero") {
            HeroHeader(
                // Büyük başlık cam çubuğa devrederken küçülüp yukarı
                // çekiliyor: ikisi tek bir geçişin iki yarısı.
                modifier = Modifier.headerHandoff(listState),
                title = stringResource(R.string.set_title),
                subtitle = stringResource(
                    R.string.set_sub,
                    if (viewModel.argon2Available) "Argon2id" else "PBKDF2-SHA512"
                )
            )
        }

        items(SettingsSection.entries.size, key = { "sec-" + it }) { index ->
            val entry = SettingsSection.entries[index]
            SettingsCategoryRow(
                section = entry,
                position = groupPositionOf(index, SettingsSection.entries.size),
                modifier = Modifier.staggeredReveal(step = index, play = !revealed),
                onClick = { section = entry }
            )
        }
        } else {
            item(key = "section-top") {
                SettingsSectionTopBar(section = current, onBack = { section = null })
            }
        }

        if (current == SettingsSection.APPEARANCE) {
        item(key = "appearance") {
            KasaCard {
                Text(
                    stringResource(R.string.set_group_appearance),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(14.dp))
                KasaButtonGroup(
                    options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                    selected = settings.theme,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.set_theme_system
                                ThemeMode.LIGHT -> R.string.set_theme_light
                                ThemeMode.DARK -> R.string.set_theme_dark
                            }
                        )
                    },
                    onSelect = viewModel::setTheme,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_dynamic),
                    subtitle = stringResource(R.string.set_dynamic_sub),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    first = true
                )
                ToggleRow(
                    title = stringResource(R.string.set_amoled),
                    subtitle = stringResource(R.string.set_amoled_sub),
                    checked = settings.pureBlack,
                    onCheckedChange = viewModel::setPureBlack
                )

                // ── gradyan ailesi ────────────────────────────────────────
                //
                // Renk şemasından ayrı bir ayar. İkisini birleştirmek, karanlık
                // tema isteyen kullanıcıya aynı anda bir renk kimliği dayatmak
                // olurdu. Tam siyah açıkken bu seçim etkisiz: o kip zaten
                // "hiç ışık olmasın" demek ve pikselleri söndürmenin kazancı
                // oradan geliyor.
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.set_group_gradient))
                Spacer(Modifier.height(8.dp))
                KasaButtonGroup(
                    options = GradientTheme.entries.toList(),
                    selected = settings.gradientTheme,
                    label = {
                        stringResource(
                            when (it) {
                                GradientTheme.JADE -> R.string.gradient_jade
                                GradientTheme.SUNSET -> R.string.gradient_sunset
                                GradientTheme.DEEP -> R.string.gradient_deep
                            }
                        )
                    },
                    onSelect = viewModel::setGradientTheme,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_gradient_time),
                    subtitle = stringResource(R.string.set_gradient_time_sub),
                    checked = settings.gradientFollowsTime,
                    onCheckedChange = viewModel::setGradientFollowsTime,
                    first = true
                )
                // Tane, gradyanın bant oluşturmasını engellemek için zaten
                // hep vardı ama görünmeyecek kadar azdı. Bu anahtar onu
                // görülecek bir dokuya çıkarıyor: aynı gradyan, filmden gelen
                // taneli bir zemin üzerinde.
                ToggleRow(
                    title = stringResource(R.string.set_gradient_grain),
                    subtitle = stringResource(R.string.set_gradient_grain_sub),
                    checked = settings.gradientGrain,
                    onCheckedChange = viewModel::setGradientGrain
                )

                // Deneysel efektler.
                //
                // Kapatıldığında kod yolları hiç çalışmıyor: ivmeölçer
                // dinleyicisi kaydedilmiyor, sonsuz animasyon başlamıyor,
                // fazladan katman kurulmuyor. "Görünmez ama çalışıyor" bir
                // efekt kapatılmış sayılmaz.
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.set_group_effects))
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = stringResource(R.string.set_effects),
                    subtitle = stringResource(R.string.set_effects_sub),
                    checked = settings.experimentalEffects,
                    onCheckedChange = viewModel::setExperimentalEffects,
                    first = true
                )
            }
        }

        }

        if (current == SettingsSection.DEVICE) {
        item(key = "device") {
            Spacer(Modifier.height(14.dp))
            KasaCard {
                Text(
                    stringResource(R.string.set_group_device),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_haptic),
                    subtitle = stringResource(R.string.set_haptic_sub),
                    checked = settings.haptics,
                    onCheckedChange = viewModel::setHaptics,
                    first = true
                )
                ToggleRow(
                    title = stringResource(R.string.set_bio),
                    subtitle = stringResource(
                        if (gate?.available == false) R.string.set_bio_unavailable else R.string.set_bio_sub
                    ),
                    checked = settings.biometricUnlock,
                    enabled = gate?.available == true && viewModel.securityActionsAllowed,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            viewModel.disableBiometric()
                        } else {
                            enrollBiometric(
                                viewModel, gate, context,
                                if (settings.deviceCredentialUnlock) KeystoreKeys.AuthClass.DEVICE_CREDENTIAL
                                else KeystoreKeys.AuthClass.BIOMETRIC_ONLY
                            )
                        }
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.set_device_credential),
                    subtitle = stringResource(
                        if (settings.deviceCredentialUnlock) R.string.set_device_credential_warn
                        else R.string.set_device_credential_sub
                    ),
                    checked = settings.deviceCredentialUnlock,
                    enabled = viewModel.securityActionsAllowed,
                    onCheckedChange = { enable ->
                        // Sarmalayıcı yeniden kurulmak zorunda: iki sınıfın
                        // Keystore anahtarı ayrı ve bir anahtarın doğrulama
                        // koşulu üretimden sonra değiştirilemiyor.
                        enrollBiometric(
                            viewModel, gate, context,
                            if (enable) KeystoreKeys.AuthClass.DEVICE_CREDENTIAL
                            else KeystoreKeys.AuthClass.BIOMETRIC_ONLY
                        )
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.pin_title),
                    subtitle = stringResource(R.string.pin_sub),
                    checked = pinOn,
                    enabled = viewModel.securityActionsAllowed,
                    onCheckedChange = { enable ->
                        if (enable) showPinSetup = true else {
                            viewModel.clearPin()
                            pinOn = false
                        }
                    }
                )
                ToggleRow(
                    title = stringResource(R.string.set_fill),
                    subtitle = stringResource(R.string.set_fill_sub),
                    checked = isAutofillEnabled(context),
                    onCheckedChange = { openAutofillSettings(context) }
                )
                ToggleRow(
                    title = stringResource(R.string.set_screenshot),
                    subtitle = stringResource(R.string.set_screenshot_sub),
                    checked = settings.blockScreenshots,
                    onCheckedChange = viewModel::setBlockScreenshots
                )
            }
        }

        }

        if (current == SettingsSection.SECURITY) {
        item(key = "security") {
            Spacer(Modifier.height(14.dp))
            KasaCard {
                Text(
                    stringResource(R.string.set_group_security),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    stringResource(R.string.set_autolock),
                    style = MaterialTheme.typography.titleSmall,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_autolock_sub, durationLabel(settings.autoLockSeconds)),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(10.dp))
                KasaButtonGroup(
                    options = listOf(0, 30, 60, 300),
                    selected = settings.autoLockSeconds,
                    label = { durationLabel(it) },
                    onSelect = viewModel::setAutoLockSeconds,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.set_clip, settings.clipboardClearSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_clip_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(10.dp))
                KasaButtonGroup(
                    options = listOf(0, 15, 30, 60),
                    selected = settings.clipboardClearSeconds,
                    label = { if (it == 0) stringResource(R.string.dur_never) else stringResource(R.string.dur_seconds_short, it) },
                    onSelect = viewModel::setClipboardSeconds,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.set_wipe_attempts, settings.wipeAfterAttempts),
                    style = MaterialTheme.typography.titleSmall,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_wipe_attempts_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(10.dp))
                KasaButtonGroup(
                    options = listOf(0, 5, 10, 20),
                    selected = settings.wipeAfterAttempts,
                    label = { if (it == 0) stringResource(R.string.dur_never) else it.toString() },
                    onSelect = viewModel::setWipeAfterAttempts,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = stringResource(R.string.set_online),
                    subtitle = stringResource(R.string.set_online_sub),
                    checked = settings.onlineBreachCheck,
                    onCheckedChange = viewModel::setOnlineBreachCheck,
                    first = true
                )
                ToggleRow(
                    title = stringResource(R.string.set_lock_screen_off),
                    subtitle = stringResource(R.string.set_lock_screen_off_sub),
                    checked = settings.lockOnScreenOff,
                    onCheckedChange = viewModel::setLockOnScreenOff
                )
                ToggleRow(
                    title = stringResource(R.string.set_autofill_verify),
                    subtitle = stringResource(R.string.set_autofill_verify_sub),
                    checked = settings.autofillVerifyDomains,
                    onCheckedChange = viewModel::setAutofillVerifyDomains
                )

                // ── bağlama duyarlı kilit süresi ──────────────────────────
                ToggleRow(
                    title = stringResource(R.string.set_context_lock),
                    subtitle = stringResource(
                        if (settings.trustedNetworkHash.isBlank()) R.string.set_context_none
                        else R.string.set_context_trusted
                    ),
                    checked = settings.contextLockEnabled && settings.trustedNetworkHash.isNotBlank(),
                    onCheckedChange = { enable ->
                        if (!enable) viewModel.forgetTrustedNetwork()
                        else if (viewModel.locationPermissionGranted) viewModel.trustCurrentNetwork()
                        else {
                            viewModel.suppressAutoLockForPermission()
                            locationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                )
                if (settings.contextLockEnabled && settings.trustedNetworkHash.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.set_context_lock_seconds),
                        style = MaterialTheme.typography.titleSmall,
                        color = KasaTheme.colors.ink
                    )
                    Spacer(Modifier.height(10.dp))
                    KasaButtonGroup(
                        options = listOf(60, 300, 900, 1800),
                        selected = settings.contextLockSeconds,
                        label = { durationLabel(it) },
                        onSelect = viewModel::setContextLockSeconds,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    KasaButton(
                        text = stringResource(R.string.set_context_trust_current),
                        onClick = { viewModel.trustCurrentNetwork() },
                        tone = ButtonTone.TONAL,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.set_context_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )

                Spacer(Modifier.height(10.dp))
                KasaButton(
                    text = stringResource(R.string.set_lock_now),
                    onClick = viewModel::lockNow,
                    tone = ButtonTone.TONAL,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        }

        if (current == SettingsSection.CRYPTO) {
        item(key = "crypto") {
            Spacer(Modifier.height(14.dp))
            KasaCard {
                Text(
                    stringResource(R.string.set_group_crypto),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(14.dp))
                CryptoFact(
                    label = stringResource(R.string.calib_title),
                    value = viewModel.kdfSummary()
                )
                Spacer(Modifier.height(10.dp))
                CryptoFact(
                    label = stringResource(R.string.set_cipher_suite),
                    value = viewModel.cipherSuiteLabel
                )
                Spacer(Modifier.height(10.dp))
                CryptoFact(
                    label = stringResource(R.string.set_key_storage),
                    value = stringResource(
                        if (viewModel.hardwareBackedKey) R.string.set_hardware_key
                        else R.string.set_software_key
                    )
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.calib_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3
                )
                if (calibrationProgress != null) {
                    Spacer(Modifier.height(10.dp))
                    WavyProgress(
                        progress = calibrationProgress ?: 0f,
                        color = KasaTheme.colors.badgeStrongBg,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                KasaButton(
                    text = stringResource(
                        if (calibrationProgress != null) R.string.calib_running else R.string.calib_title
                    ),
                    onClick = { showRecalibrate = true },
                    tone = ButtonTone.TONAL,
                    enabled = calibrationProgress == null && viewModel.securityActionsAllowed,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        }

        if (current == SettingsSection.VAULT) {
        item(key = "vault-label") {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.set_group_vault))
        }

        items(actions.size) { index ->
            val action = actions[index]
            ActionTile(action, groupPositionOf(index, actions.size))
        }

        // ── hakkında ──────────────────────────────────────────────────────
        //
        // Buradaki bilgi ekranın geri kalanıyla aynı dile ait değildi: yukarıda
        // her şey kart içindeyken alt kısım ortalanmış çıplak metindi ve
        // ekranın bittiği yer bir kart değil, havada asılı iki satır gibi
        // duruyordu. Aynı döşemenin içine alındı; içerik değişmedi, dili
        // değişti.
        }

        if (current == SettingsSection.ABOUT) {
        item(key = "about") {
            Spacer(Modifier.height(26.dp))
            SectionLabel(stringResource(R.string.set_group_about))
        }
        item(key = "about_card") {
            KasaTile(position = GroupPosition.ONLY, onClick = {}) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KasaTheme.colors.badgeStrongBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        tint = KasaTheme.colors.badgeStrongFg,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = KasaTheme.text.tileName,
                        color = KasaTheme.colors.ink
                    )
                    Text(
                        stringResource(R.string.set_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink2
                    )
                }
            }
        }
        item(key = "about_crypto") {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(
                    R.string.set_crypto_line,
                    if (viewModel.argon2Available) "Argon2id" else "PBKDF2-SHA512",
                    stringResource(
                        if (viewModel.hardwareBackedKey) R.string.set_hardware_key
                        else R.string.set_software_key
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.set_offline_note),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
        }
        }
    }
    }


    if (overlayVisible) {
        LoadingOverlay(
            label = stringResource(R.string.chg_working),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
        )
    }

    // ── pencereler ────────────────────────────────────────────────────────

    if (showChangeMaster) {
        ChangeMasterDialog(
            busy = busy,
            error = changeResult is SettingsViewModel.ChangeResult.WrongCurrent,
            onConfirm = { current, new -> viewModel.changeMasterPassword(current, new) },
            onDismiss = {
                showChangeMaster = false
                viewModel.clearChangeResult()
            }
        )
    }

    LaunchedEffect(changeResult) {
        if (changeResult is SettingsViewModel.ChangeResult.Success) {
            showChangeMaster = false
            viewModel.clearChangeResult()
        }
    }

    recoveryCode?.let { code ->
        RecoveryCodeDialog(
            code = code,
            onCopy = { viewModel.copyRecoveryCode(code, settings.clipboardClearSeconds) },
            onDismiss = viewModel::dismissRecoveryCode
        )
    }

    if (showExport) {
        PasswordPromptDialog(
            title = stringResource(R.string.exp_title),
            description = stringResource(R.string.exp_sub),
            label = stringResource(R.string.exp_password),
            confirmText = stringResource(R.string.exp_create),
            requireConfirmation = true,
            // Dışa aktarılan dosyanın tek koruması bu parola: kasa dosyasının
            // aksine Keystore'a bağlı bir katmanı yok ve buluta ya da USB
            // belleğe gidebiliyor. Alt sınır bu yüzden kasa içi alanlardan
            // yüksek.
            minLength = 12,
            onConfirm = { password ->
                showExport = false
                pendingExportPassword = password
                viewModel.suppressAutoLockForPicker()
                exportLauncher.launch("kasa-yedek.kasa")
            },
            onDismiss = { showExport = false }
        )
    }

    if (showImport) {
        PasswordPromptDialog(
            title = stringResource(R.string.imp_title),
            description = stringResource(R.string.imp_sub),
            label = stringResource(R.string.imp_password),
            confirmText = stringResource(R.string.imp_pick),
            onConfirm = { password ->
                showImport = false
                pendingImportPassword = password
                viewModel.suppressAutoLockForPicker()
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showImport = false }
        )
    }

    if (showFolders) {
        FolderManagerDialog(
            folders = folders,
            counts = vaultViewModel.folderCounts.collectAsStateWithLifecycle().value,
            onCreate = { name -> vaultViewModel.createFolder(name) },
            onRename = { id, name -> vaultViewModel.renameFolder(id, name) },
            onDelete = { id -> vaultViewModel.deleteFolder(id) },
            onDismiss = { showFolders = false }
        )
    }

    if (showPinSetup) {
        PinSetupDialog(
            onConfirm = { pin, length ->
                showPinSetup = false
                pinOn = true
                viewModel.setPin(pin, length)
            },
            onDismiss = { showPinSetup = false }
        )
    }

    if (showRecalibrate) {
        PasswordPromptDialog(
            title = stringResource(R.string.calib_title),
            description = stringResource(R.string.calib_body),
            label = stringResource(R.string.lock_master),
            confirmText = stringResource(R.string.calib_confirm),
            minLength = 1,
            onConfirm = { password ->
                showRecalibrate = false
                viewModel.recalibrate(password)
            },
            onDismiss = { showRecalibrate = false }
        )
    }

    if (showRotate) {
        PasswordPromptDialog(
            title = stringResource(R.string.rotate_title),
            description = stringResource(R.string.rotate_body),
            label = stringResource(R.string.lock_master),
            confirmText = stringResource(R.string.rotate_confirm),
            minLength = 1,
            onConfirm = { password ->
                showRotate = false
                viewModel.rotateVaultKey(password)
            },
            onDismiss = { showRotate = false }
        )
    }

    if (showDuress) {
        DuressDialog(
            onSet = { password ->
                showDuress = false
                viewModel.setDuressPassword(password)
            },
            onClear = {
                showDuress = false
                viewModel.clearDuressPassword()
            },
            onDismiss = { showDuress = false }
        )
    }

    if (showWipe) {
        TypeToConfirmDialog(
            title = stringResource(R.string.wipe_title),
            body = stringResource(R.string.wipe_body),
            hint = stringResource(R.string.wipe_confirm_hint),
            word = stringResource(R.string.wipe_confirm_word),
            confirmText = stringResource(R.string.set_wipe),
            onConfirm = {
                showWipe = false
                viewModel.wipeVault { }
            },
            onDismiss = { showWipe = false }
        )
    }
}

private class VaultAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val background: Color,
    val foreground: Color,
    val onClick: () -> Unit
)

@Composable
private fun ActionTile(action: VaultAction, position: GroupPosition) {
    KasaTile(position = position, onClick = action.onClick) {
        KasaBadge(background = action.background, foreground = action.foreground) {
            Icon(action.icon, contentDescription = null, tint = action.foreground, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(action.title, style = KasaTheme.text.tileName, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink3)
        }
    }
}

@Composable
private fun ChangeMasterDialog(
    busy: Boolean,
    error: Boolean,
    onConfirm: (CharArray, CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val mismatch = confirm.isNotEmpty() && new != confirm
    val valid = current.isNotEmpty() && new.length >= 12 && new == confirm && !busy

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.chg_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(16.dp))
            app.kasa.ui.components.KasaPasswordField(
                value = current,
                onValueChange = { current = it },
                label = stringResource(R.string.chg_current),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                isError = error,
                supportingText = if (error) stringResource(R.string.chg_wrong_current) else null
            )
            Spacer(Modifier.height(8.dp))
            app.kasa.ui.components.KasaPasswordField(
                value = new,
                onValueChange = { new = it },
                label = stringResource(R.string.chg_new),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed }
            )
            Spacer(Modifier.height(8.dp))
            app.kasa.ui.components.KasaPasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.chg_new_again),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                isError = mismatch,
                supportingText = if (mismatch) stringResource(R.string.onb_mismatch) else null
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                KasaButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
                KasaButton(
                    text = stringResource(if (busy) R.string.chg_working else R.string.save),
                    onClick = { onConfirm(current.toCharArray(), new.toCharArray()) },
                    enabled = valid,
                    height = 46.dp
                )
            }
        }
    }
}

@Composable
private fun RecoveryCodeDialog(code: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.onb_recovery_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.onb_recovery_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(18.dp)
            ) {
                Text(
                    code,
                    style = KasaTheme.text.mono,
                    color = KasaTheme.colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                KasaButton(
                    text = stringResource(R.string.copy),
                    onClick = onCopy,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
                KasaButton(
                    text = stringResource(R.string.onb_recovery_saved),
                    onClick = onDismiss,
                    height = 46.dp
                )
            }
        }
    }
}

/** Kasa, sistemde etkin otomatik doldurma servisi mi? */
private fun isAutofillEnabled(context: android.content.Context): Boolean = try {
    val manager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
    manager?.hasEnabledAutofillServices() == true
} catch (t: Throwable) {
    false
}

private fun openAutofillSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * Klasör yöneticisi.
 *
 * Klasör silmek kayıtları silmiyor; onay metni bunu açıkça söylüyor, çünkü
 * kullanıcının burada bekleyeceği en kötü sürpriz budur.
 */
@Composable
private fun FolderManagerDialog(
    folders: List<Folder>,
    counts: Map<String, Int>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Folder?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.set_folders),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(16.dp))

            if (folders.isEmpty()) {
                Text(
                    stringResource(R.string.folders_empty_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink3
                )
                Spacer(Modifier.height(16.dp))
            }

            folders.forEach { folder ->
                if (renaming == folder.id) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            app.kasa.ui.components.KasaTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                label = stringResource(R.string.folder_name)
                            )
                        }
                        app.kasa.ui.components.KasaChip(
                            text = stringResource(R.string.save),
                            onClick = {
                                onRename(folder.id, renameText)
                                renaming = null
                            }
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = KasaTheme.colors.ink3,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = KasaTheme.colors.ink
                            )
                            Text(
                                stringResource(R.string.set_folders_sub, counts[folder.id] ?: 0),
                                style = MaterialTheme.typography.bodySmall,
                                color = KasaTheme.colors.ink3
                            )
                        }
                        app.kasa.ui.components.KasaIconButton(
                            onClick = {
                                renaming = folder.id
                                renameText = folder.name
                            },
                            size = 36.dp,
                            contentDescription = stringResource(R.string.folder_rename)
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = KasaTheme.colors.ink2,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        app.kasa.ui.components.KasaIconButton(
                            onClick = { deleting = folder },
                            size = 36.dp,
                            contentDescription = stringResource(R.string.delete)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    app.kasa.ui.components.KasaTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = stringResource(R.string.folder_new)
                    )
                }
                app.kasa.ui.components.KasaChip(
                    text = stringResource(R.string.folder_create),
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreate(newName)
                            newName = ""
                        }
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                KasaButton(text = stringResource(R.string.close), onClick = onDismiss, height = 46.dp)
            }
        }
    }

    deleting?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.folder_delete_confirm),
            body = stringResource(R.string.folder_delete_body, folder.name),
            confirmText = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                onDelete(folder.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/**
 * Biyometrik sarmalayıcıyı istenen doğrulama sınıfıyla (yeniden) kurar.
 *
 * Sınıf değiştiğinde şifreleyici yeni takma addan geliyor ve eski anahtar
 * siliniyor; iki sınıfın aynı anda geçerli kalması, kapatıldığı sanılan bir
 * yolu açık bırakmak olurdu.
 */
private fun enrollBiometric(
    viewModel: SettingsViewModel,
    gate: app.kasa.ui.BiometricGate?,
    context: android.content.Context,
    authClass: KeystoreKeys.AuthClass
) {
    val cipher = viewModel.biometricAvailableCipher(authClass) ?: return
    if (gate == null) return
    gate.authenticate(
        title = context.getString(R.string.onb_biometric_title),
        subtitle = context.getString(R.string.onb_biometric_sub),
        negativeButton = context.getString(R.string.cancel),
        cipher = cipher,
        onSuccess = { authenticated -> viewModel.onBiometricEnrolled(authenticated, authClass) }
    )
}

/** Etiket solda, değer sağda duran tek satırlık kripto bilgisi. */
@Composable
private fun CryptoFact(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = KasaTheme.colors.ink2)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = KasaTheme.colors.ink)
    }
}

/**
 * PIN kurma penceresi.
 *
 * PIN iki kez isteniyor: yanlış yazılmış bir PIN'in bedeli, beş denemede
 * katmanın düşmesi ve kullanıcının ana parolaya dönmek zorunda kalması.
 */
@Composable
private fun PinSetupDialog(
    onConfirm: (CharArray, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var length by remember { mutableStateOf(6) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }

    val complete = first.length == length && second.length == length
    val mismatch = complete && first != second

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.pin_setup_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.pin_setup_body, length),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.pin_length),
                style = MaterialTheme.typography.titleSmall,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(8.dp))
            KasaButtonGroup(
                options = listOf(4, 5, 6),
                selected = length,
                label = { it.toString() },
                onSelect = {
                    length = it
                    first = ""
                    second = ""
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            KasaPinField(
                value = first,
                onValueChange = { if (it.length <= length) first = it },
                label = stringResource(R.string.pin_title)
            )
            Spacer(Modifier.height(8.dp))
            KasaPinField(
                value = second,
                onValueChange = { if (it.length <= length) second = it },
                label = stringResource(R.string.pin_confirm_title),
                isError = mismatch,
                supportingText = if (mismatch) stringResource(R.string.pin_mismatch) else null
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.pin_note),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                KasaButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
                KasaButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        val chars = first.toCharArray()
                        first = ""
                        second = ""
                        onConfirm(chars, chars.size)
                    },
                    enabled = complete && !mismatch,
                    height = 46.dp
                )
            }
        }
    }
}

/**
 * Zorlama parolası penceresi.
 *
 * Kurulu olup olmadığı **gösterilmiyor** — gösterecek bir bilgi de yok, çünkü
 * dosyada saklanmıyor. Ekran her iki eylemi de sunuyor: yeni bir parola
 * belirlemek ya da var olanı kaldırmak. Bu belirsizlik özelliğin kendisi;
 * "kurulu" yazan bir satır, telefona bakan zorlayıcıya cevabı verirdi.
 */
@Composable
private fun DuressDialog(
    onSet: (CharArray) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val mismatch = confirm.isNotEmpty() && password != confirm
    val valid = password.length >= 8 && password == confirm

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.duress_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.duress_body),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2
            )
            Spacer(Modifier.height(16.dp))
            KasaPasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.duress_field),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed }
            )
            Spacer(Modifier.height(8.dp))
            KasaPasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.exp_password_again),
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                isError = mismatch,
                supportingText = if (mismatch) stringResource(R.string.onb_mismatch) else null
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.duress_note),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
            Spacer(Modifier.height(18.dp))
            KasaButton(
                text = stringResource(R.string.duress_set_action),
                onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    confirm = ""
                    onSet(chars)
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                KasaButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
                KasaButton(
                    text = stringResource(R.string.duress_clear_action),
                    onClick = onClear,
                    tone = ButtonTone.TONAL,
                    height = 46.dp
                )
            }
        }
    }
}

/**
 * Ayarların kategorileri.
 *
 * Sıra kullanım sıklığına göre: görünüm en sık dokunulan, "hakkında" en az.
 * Alfabetik ya da "önem" sırası, kullanıcının aradığı şeyin nerede olduğunu
 * tahmin etmesini zorlaştırırdı.
 */
enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val icon: ImageVector
) {
    APPEARANCE(R.string.set_group_appearance, R.string.set_cat_appearance_sub, Icons.Rounded.Palette),
    DEVICE(R.string.set_group_device, R.string.set_cat_device_sub, Icons.Rounded.PhoneAndroid),
    SECURITY(R.string.set_group_security, R.string.set_cat_security_sub, Icons.Rounded.Shield),
    CRYPTO(R.string.set_group_crypto, R.string.set_cat_crypto_sub, Icons.Rounded.Lock),
    VAULT(R.string.set_group_vault, R.string.set_cat_vault_sub, Icons.Rounded.Inventory2),
    ABOUT(R.string.set_group_about, R.string.set_cat_about_sub, Icons.Rounded.Info)
}

/**
 * Hub'daki kategori satırı.
 *
 * Alt başlık kategorinin içindekileri **sayarak** değil örnekleyerek anlatıyor:
 * "4 ayar" hiçbir şey söylemiyor, "tema, renk, yazı tipi" ise kullanıcının
 * aradığı şeyin burada olup olmadığını okumadan anlamasını sağlıyor.
 */
@Composable
private fun SettingsCategoryRow(
    section: SettingsSection,
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Kasa listesindeki satırla aynı hareket: basınca küçülüyor, köşeleri
    // yuvarlanıyor ve dokunulan kenar parlıyor. Ayarlar hub'ı da bir liste ve
    // aynı işi yapan iki yüzeyin farklı davranması, ikisini de yabancılaştırır.
    val scale by animateFloatAsState(if (pressed) 0.968f else 1f, KasaMotion.small(), label = "catScale")
    val loose = animatedCorner(KasaRadius.l, label = "catLoose")
    val tight = animatedCorner(if (pressed) KasaRadius.l else CATEGORY_TIGHT, label = "catTight")
    // Ok basınca ileri kayıyor: gidilecek yönü satırın kendisi söylüyor.
    val nudge by animateFloatAsState(if (pressed) 1f else 0f, KasaMotion.small(), label = "catNudge")

    val shape = categoryShape(position, tight, loose)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .glassSurface(shape, MaterialTheme.colorScheme.surfaceContainerLow)
            .pressRim(shape = shape, color = MaterialTheme.colorScheme.primary)
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(section.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = KasaTheme.colors.ink
            )
            Text(
                stringResource(section.summaryRes),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = KasaTheme.colors.ink3,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { translationX = nudge * CHEVRON_NUDGE.toPx() }
        )
    }
}

/** Kategori ekranının üst çubuğu: geri ve başlık. */
@Composable
private fun SettingsSectionTopBar(section: SettingsSection, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KasaIconButton(onClick = onBack, contentDescription = stringResource(R.string.back)) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = KasaTheme.colors.ink2,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            stringResource(section.titleRes),
            style = KasaTheme.text.hero,
            color = KasaTheme.colors.ink
        )
    }
}

/** Grubun iç köşeleri; basılınca [KasaRadius].l'ye açılıyor. */
private val CATEGORY_TIGHT = 6.dp

/** Okun basınca kat ettiği yol. */
private val CHEVRON_NUDGE = 3.dp

/**
 * Kategori satırının köşeleri.
 *
 * [app.kasa.ui.components] içindeki karşılığı özel (private) ve üç
 * parametreli; buradaki iki yarıçap ayarlar hub'ına özel olduğu için
 * kopyalamak yerine kendi ölçüsüyle yazıldı. Grubun dış köşeleri geniş, iç
 * köşeleri dar: satırlar tek bir blok gibi okunuyor ama sınırları belli.
 */
private fun categoryShape(
    position: GroupPosition,
    tight: Dp = CATEGORY_TIGHT,
    loose: Dp = KasaRadius.l
): androidx.compose.ui.graphics.Shape {
    return when (position) {
        GroupPosition.ONLY -> RoundedCornerShape(loose)
        GroupPosition.FIRST -> RoundedCornerShape(
            topStart = loose, topEnd = loose, bottomStart = tight, bottomEnd = tight
        )
        GroupPosition.LAST -> RoundedCornerShape(
            topStart = tight, topEnd = tight, bottomStart = loose, bottomEnd = loose
        )
        GroupPosition.MIDDLE -> RoundedCornerShape(tight)
    }
}
