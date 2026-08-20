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
import app.kasa.ui.components.EmptyState
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
@Composable
fun SearchOverlay(
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.visibleItems.collectAsStateWithLifecycle()
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
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
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
            if (results.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.vault_no_match_title),
                        subtitle = stringResource(R.string.vault_no_match_sub)
                    )
                }
            } else {
                itemsIndexed(items = results, key = { _, item -> item.id }) { index, item ->
                    // Her harfte sonuç kümesi değişiyor; satırların yer
                    // değiştirmesi kayarak olursa göz aradığı kaydı takip
                    // edebiliyor, anlık yeniden dizilimde kaybediyor.
                    VaultRow(
                        item = item,
                        position = groupPositionOf(index, results.size),
                        folderName = viewModel.folderName(item.folderId),
                        onClick = {
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
