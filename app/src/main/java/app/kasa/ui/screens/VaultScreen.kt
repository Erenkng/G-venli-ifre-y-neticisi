package app.kasa.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
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
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Kasa ekranı: arama, koleksiyonlar, kategori süzgeci, son kullanılanlar
 * şeridi ve gruplanmış kayıt listesi.
 */
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    settings: SettingsStore.Settings,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val items by viewModel.visibleItems.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val smartCounts by viewModel.smartCounts.collectAsStateWithLifecycle()
    val folderCounts by viewModel.folderCounts.collectAsStateWithLifecycle()

    val categories = remember { listOf<Category?>(null) + Category.entries.toList() }
    val inTrash = view.isTrash

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = listContentPadding(extraBottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item(key = "hero") {
            HeroHeader(
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

        if (items.isEmpty()) {
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
                    )
                )
            }
        } else {
            itemsIndexed(items = items, key = { _, entry -> entry.id }) { index, entry ->
                VaultRow(
                    item = entry,
                    position = groupPositionOf(index, items.size),
                    folderName = viewModel.folderName(entry.folderId),
                    onClick = { viewModel.select(entry.id) }
                )
            }
        }
    }
}

/** Gruplanmış listedeki tek kayıt satırı. */
@Composable
fun VaultRow(
    item: VaultItem,
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    folderName: String? = null
) {
    val tone = toneOf(item)
    val breachMark = stringResource(R.string.breach_mark)

    KasaTile(position = position, onClick = onClick, modifier = modifier) {
        EntryBadge(item = item)
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
        StrengthDot(tone)
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
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
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
