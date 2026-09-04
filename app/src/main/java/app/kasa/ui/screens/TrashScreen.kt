package app.kasa.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.VaultRepository
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.SelectionAction
import app.kasa.ui.components.SelectionBar
import app.kasa.ui.components.KasaBackground
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.EmptyState
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaTheme
import java.util.concurrent.TimeUnit
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import app.kasa.ui.theme.KasaRadius

/**
 * Çöp kutusu — kendi ekranı.
 *
 * ### Neden kasa listesinin bir süzgeci değil
 *
 * Çöp kutusu bir koleksiyon değil, kasanın **başka bir hâli**. Orada duran
 * kayıtlar aranmıyor, otomatik doldurmada çıkmıyor, güvenlik puanına
 * katılmıyor ve otuz gün sonra kendiliğinden yok oluyorlar. Bunu kasa
 * listesinin bir süzgeci olarak göstermek, aynı ekranda birbirine hiç
 * benzemeyen iki davranış demekti: kullanıcı bir kayda dokunduğunda bazen
 * ayrıntı açılıyor, bazen "geri al" gerekiyordu.
 *
 * Ayrı ekranda her satırın iki eylemi var ve ikisi de görünür: geri al, kalıcı
 * sil. Kalan gün sayısı da satırda yazıyor — çöp kutusunun asıl bilgisi o.
 */
@Composable
fun TrashScreen(
    viewModel: VaultViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val items = remember(data) { data.items.filter { it.inTrash }.sortedByDescending { it.deletedAt } }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }

    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()

    // Ekrandan çıkarken seçim bırakılıyor: kimlikler kasa listesinin seçim
    // çubuğuyla aynı kümede duruyor ve çöp kutusunda seçilen kayıtlar orada
    // "12 seçildi" olarak görünürdü — üstelik hepsi silinmiş kayıtlar.
    DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }

    // Geri tuşu önce seçimi bırakıyor: otuz kaydı seçtikten sonra ekranın
    // tamamen kapanması, yapılan işi sessizce çöpe atmak olurdu.
    // Yalnızca seçimi bırakan işleyici burada. Kapatma MainScaffold'da ve
    // orada parmağa bağlı: Compose'da en son kaydedilen etkin geri işleyicisi
    // kazanıyor ve bu ekran daha sonra bestelendiği için buradaki düz bir
    // işleyici, yukarıdaki hareketi tamamen devre dışı bırakırdı — çöp kutusu
    // parmakla hiç kıpırdamaz, bırakınca birden giderdi.
    //
    // Sıra da doğru çalışıyor: seçim varken bu işleyici etkin ve kazanıyor,
    // seçim yokken kapanıyor ve sıra yukarıdaki harekete geliyor.
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    KasaBackground(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KasaIconButton(onClick = onClose, contentDescription = stringResource(R.string.close)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = KasaTheme.colors.ink2,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.padding(start = 4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.trash_title),
                        style = KasaTheme.text.sheetTitle,
                        color = KasaTheme.colors.ink
                    )
                    Text(
                        stringResource(R.string.trash_retention_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink2
                    )
                }
                if (items.isNotEmpty()) {
                    // Seçim kipine görünür bir giriş. Uzun basmak da açıyor
                    // ama uzun basmayı kimse denemiyor: kasa listesinde o yolu
                    // satır menüsündeki "Seç" gösteriyor, burada ise menü yok.
                    //
                    // Düğme hepsini seçiyor, çünkü seçim kipi sıfır seçimle
                    // var olamıyor — ve çöp kutusunda toplu işin çıkış noktası
                    // zaten "hepsi": ya hepsini geri al ya hepsini sil.
                    // Kullanıcı fazlasını buradan çıkarıyor.
                    KasaIconButton(
                        onClick = { viewModel.toggleSelectAll(items.map { it.id }) },
                        contentDescription = stringResource(R.string.bulk_select_all)
                    ) {
                        Icon(
                            Icons.Rounded.DoneAll,
                            contentDescription = null,
                            tint = if (selecting) MaterialTheme.colorScheme.primary
                            else KasaTheme.colors.ink2,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    KasaButton(
                        text = stringResource(R.string.trash_empty_action),
                        tone = ButtonTone.TEXT,
                        onClick = { confirmEmpty = true }
                    )
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.trash_empty_title),
                        subtitle = stringResource(R.string.trash_empty_sub),
                        icon = Icons.Rounded.DeleteOutline
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 24.dp
                        )
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            TrashRow(
                                item = item,
                                index = index,
                                total = items.size,
                                selectable = selecting,
                                selected = item.id in selection,
                                onRestore = {
                                    if (selecting) viewModel.toggleSelected(item.id)
                                    else viewModel.restoreFromTrash(item)
                                },
                                onPurge = { viewModel.purge(item) },
                                onLongPress = { viewModel.startSelection(item.id) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = KasaMotion.effect(),
                                    placementSpec = KasaMotion.medium(),
                                    fadeOutSpec = KasaMotion.exit()
                                )
                            )
                        }
                    }

                    // Seçim çubuğu listenin üstünde: kasa listesindekiyle aynı
                    // yerleşim ama farklı eylemler. Burada toplu iş yalnızca iki
                    // şey — geri almak ve kalıcı silmek.
                    SelectionBar(
                        count = selection.size,
                        allSelected = items.isNotEmpty() && selection.containsAll(items.map { it.id }),
                        onSelectAll = { viewModel.toggleSelectAll(items.map { it.id }) },
                        onClose = viewModel::clearSelection,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                    ) {
                        SelectionAction(
                            icon = Icons.Rounded.Restore,
                            label = stringResource(R.string.bulk_restore),
                            onClick = viewModel::restoreSelected
                        )
                        SelectionAction(
                            icon = Icons.Rounded.DeleteForever,
                            label = stringResource(R.string.delete_forever),
                            danger = true,
                            onClick = { confirmPurge = true }
                        )
                    }
                }
            }
        }
    }

    if (confirmPurge) {
        ConfirmDialog(
            title = stringResource(R.string.bulk_purge_title),
            body = stringResource(R.string.bulk_purge_body, selection.size),
            confirmText = stringResource(R.string.delete_forever),
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmPurge = false
                viewModel.purgeSelected()
            },
            onDismiss = { confirmPurge = false }
        )
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_confirm_title),
            body = stringResource(R.string.trash_empty_confirm_body),
            confirmText = stringResource(R.string.trash_empty_action),
            dismissText = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            },
            onDismiss = { confirmEmpty = false }
        )
    }
}

@Composable
private fun TrashRow(
    item: VaultItem,
    index: Int,
    total: Int,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selected: Boolean = false
) {
    val daysLeft = remember(item.deletedAt) {
        val elapsed = System.currentTimeMillis() - item.deletedAt
        val days = TimeUnit.DAYS.convert(elapsed, TimeUnit.MILLISECONDS)
        (VaultRepository.TRASH_RETENTION_DAYS - days).coerceAtLeast(0)
    }

    KasaTile(
        position = groupPositionOf(index, total),
        onClick = onRestore,
        onLongClick = if (selectable) null else onLongPress,
        modifier = modifier
    ) {
        if (selectable) TrashSelectionMark(selected = selected) else EntryBadge(item = item)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = item.name,
                style = KasaTheme.text.tileName,
                color = KasaTheme.colors.ink
            )
            Text(
                // Kalan gün, çöp kutusundaki bir kaydın tek gerçek bilgisi:
                // kullanıcının karar vermek için ne kadar vakti kaldığı.
                text = stringResource(R.string.trash_days_left, daysLeft),
                style = MaterialTheme.typography.bodySmall,
                color = if (daysLeft <= 3) KasaTheme.colors.badgeWeakFg else KasaTheme.colors.ink2
            )
        }
        // Seçim kipinde satır düğmeleri gizleniyor: aynı satırda hem "bunu geri
        // al" hem "bunu seç" olması, dokunuşun ne yapacağını belirsiz kılıyor.
        if (!selectable) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KasaIconButton(onClick = onRestore, contentDescription = stringResource(R.string.restore)) {
                Icon(
                    Icons.Rounded.Restore,
                    contentDescription = null,
                    tint = KasaTheme.colors.ink2,
                    modifier = Modifier.size(20.dp)
                )
            }
            KasaIconButton(onClick = onPurge, contentDescription = stringResource(R.string.delete_forever)) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = KasaTheme.colors.badgeWeakFg,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

/**
 * Çöp kutusundaki satırın seçim işareti.
 *
 * Kasa listesindekiyle aynı biçim ama ayrı bir işlev: ikisi farklı dosyalarda
 * ve birini ortak bir yere taşımak, iki ekranın rozet yerleşimini de birbirine
 * bağlardı. Aynı görünen iki şeyin aynı olmak zorunda olmadığı yer burası —
 * çöp kutusunda rozet kalan günü de taşımıyor.
 */
@Composable
private fun TrashSelectionMark(selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = KasaMotion.small(),
        label = "trashSelectionMark"
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
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
 * Çöp kutusunun seçim çubuğu.
 *
 * Kasa listesindekinden ayrı, çünkü eylemleri ayrı: burada bir kaydı sık
 * kullanılana eklemenin ya da klasöre taşımanın karşılığı yok. Ortak bir
 * çubuk yazıp eylemleri parametreye almak mümkündü ama o zaman iki ekranın
 * eylem takımı tek bir yerde birbirine bağlanırdı ve birine eklenen her şey
 * ötekinde "burada görünmesin" koşulu doğururdu.
 */

