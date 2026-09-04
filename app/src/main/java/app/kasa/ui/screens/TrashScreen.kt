package app.kasa.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.kasa.ui.components.KasaBackground
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.EmptyState
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaTheme
import java.util.concurrent.TimeUnit

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

    BackHandler { onClose() }

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
                        Icons.Rounded.ArrowBack,
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                            onRestore = { viewModel.restoreFromTrash(item) },
                            onPurge = { viewModel.purge(item) },
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
    modifier: Modifier = Modifier
) {
    val daysLeft = remember(item.deletedAt) {
        val elapsed = System.currentTimeMillis() - item.deletedAt
        val days = TimeUnit.DAYS.convert(elapsed, TimeUnit.MILLISECONDS)
        (VaultRepository.TRASH_RETENTION_DAYS - days).coerceAtLeast(0)
    }

    KasaTile(position = groupPositionOf(index, total), onClick = onRestore, modifier = modifier) {
        EntryBadge(item = item)
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
