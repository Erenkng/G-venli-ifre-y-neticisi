package app.kasa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.VaultViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import app.kasa.ui.components.KasaChip
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.EmptyState
import app.kasa.ui.components.readablePane
import app.kasa.ui.components.SearchTopBar
import app.kasa.ui.components.KasaReveal
import app.kasa.ui.components.groupPositionOf
import kotlinx.coroutines.delay

/**
 * Tam ekran arama.
 *
 * Kapanırken sorgu temizlenir: arama metni de bir ipucudur ("banka" yazdıysan
 * kasada banka kaydın var demektir) ve ekranın açık kalması gerekmez.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.visibleItems.collectAsStateWithLifecycle()
    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    val recentItems by viewModel.recents.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(260)
        runCatching { focusRequester.requestFocus() }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.setQuery("") }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Cam yüzey: altındaki liste görünür kalıyor ama okunmuyor.
            // Tamamen opak bir zemin, aramanın kasanın **üstünde** açıldığı
            // bilgisini siliyordu; kullanıcı geri gittiğinde nereye döneceğini
            // ancak dönünce öğreniyordu.
            .background(MaterialTheme.colorScheme.surface.copy(alpha = SEARCH_GLASS_ALPHA))
            .windowInsetsPadding(WindowInsets.statusBars)
            // Cam zemin ekranın tamamını kaplıyor, içerik ortalanıyor: geniş
            // pencerede arama kutusunun bir metreye yayılması, yazılanı
            // görmeyi kolaylaştırmıyor.
            .readablePane()
    ) {
        SearchTopBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onBack = onClose,
            placeholder = stringResource(R.string.vault_search_hint),
            focusRequester = focusRequester
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        // Sonuç listesi arama çubuğundan sonra çözülüyor.
        //
        // Eskiden ekran tek karede yerini alıyordu: kasa listesi gidip arama
        // ekranı geliyor, arada bir hareket yok. İki ekran birbirine çok
        // benzediği için (aynı satırlar, aynı rozetler) geçişin olmaması
        // "hiçbir şey olmadı" gibi okunuyordu; kullanıcı arama kutusuna
        // dokunduğuna emin olamıyordu. Şimdi çubuk önce, liste onun ardından
        // geliyor ve sıra kendiliğinden anlaşılıyor.
        KasaReveal(
            visible = true,
            delayMillis = RESULTS_DELAY_MILLIS,
            blurRadius = 12.dp,
            lift = 10.dp,
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (query.isBlank()) {
                // ── boş arama ────────────────────────────────────────────
                //
                // Arama boş açılıyordu: kutuya dokunan kullanıcı bomboş bir
                // ekranla karşılaşıyor ve yazmaya başlayana kadar ekranda
                // hiçbir şey olmuyordu. Oysa o anda söylenebilecek iki şey
                // var — az önce ne aradığı ve az önce neyi açtığı. İkisi de
                // aradığı şeyin **büyük ihtimalle** o olduğunu söylüyor.
                if (recentQueries.isNotEmpty()) {
                    item(key = "recent-queries-label") {
                        SectionLabel(stringResource(R.string.search_recent_queries))
                        Spacer(Modifier.height(8.dp))
                    }
                    item(key = "recent-queries") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentQueries.forEach { term ->
                                KasaChip(
                                    text = term,
                                    onClick = { viewModel.setQuery(term) }
                                )
                            }
                        }
                    }
                }
                if (recentItems.isNotEmpty()) {
                    item(key = "recent-items-label") {
                        Spacer(Modifier.height(10.dp))
                        SectionLabel(stringResource(R.string.vault_recent))
                        Spacer(Modifier.height(8.dp))
                    }
                    itemsIndexed(
                        items = recentItems,
                        key = { _, item -> "recent-" + item.id },
                        contentType = { _, _ -> "vaultRow" }
                    ) { index, item ->
                        VaultRow(
                            item = item,
                            position = groupPositionOf(index, recentItems.size),
                            folderName = viewModel.folderName(item.folderId),
                            onClick = {
                                viewModel.select(item.id)
                                onClose()
                            }
                        )
                    }
                }
                if (recentQueries.isEmpty() && recentItems.isEmpty()) {
                    item(key = "search-blank") {
                        EmptyState(
                            title = stringResource(R.string.search_blank_title),
                            subtitle = stringResource(R.string.search_blank_sub),
                            icon = Icons.Rounded.SearchOff
                        )
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.vault_no_match_title),
                        subtitle = stringResource(R.string.vault_no_match_sub),
                        icon = Icons.Rounded.SearchOff
                    )
                }
            } else {
                itemsIndexed(
                    items = results,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "vaultRow" }
                ) { index, item ->
                    // Her harfte sonuç kümesi değişiyor; satırların yer
                    // değiştirmesi kayarak olursa göz aradığı kaydı takip
                    // edebiliyor, anlık yeniden dizilimde kaybediyor.
                    VaultRow(
                        item = item,
                        position = groupPositionOf(index, results.size),
                        folderName = viewModel.folderName(item.folderId),
                        onClick = {
                            // Terim ancak bir kayda götürdüğünde işe yaramış
                            // sayılıyor; geçmişe o an giriyor.
                            viewModel.rememberQuery(query)
                            viewModel.select(item.id)
                            onClose()
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = KasaMotion.effect(),
                            placementSpec = KasaMotion.medium(),
                            fadeOutSpec = KasaMotion.exit()
                        )
                    )
                }
            }
        }
        }
    }
}

/**
 * Arama çubuğu belirdikten sonra listenin gelmesi için beklenen süre.
 *
 * Klavye odağı da 260 ms sonra isteniyor; ikisinin aynı ana denk gelmesi
 * listeyi klavye açılırken kaydırıyordu.
 */
private const val RESULTS_DELAY_MILLIS = 90

/**
 * Arama yüzeyinin örtücülüğü.
 *
 * Alt sayfalardan yüksek: arama ekranı uzun bir sonuç listesi taşıyor ve
 * altından geçen satırlar okunanla karışıyordu. Cam burada derinlik için
 * değil, yalnızca bağlamı korumak için.
 */
private const val SEARCH_GLASS_ALPHA = 0.94f
