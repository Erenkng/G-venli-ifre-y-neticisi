package app.kasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem
import app.kasa.ui.VaultViewModel
import app.kasa.ui.components.EmptyState
import app.kasa.ui.components.KasaBadge
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.RecentCard
import app.kasa.ui.components.SearchBarButton
import app.kasa.ui.components.SectionLabel
import app.kasa.ui.components.StrengthDot
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Kasa ekranı: arama, kategori süzgeci, son kullanılanlar şeridi ve
 * gruplanmış kayıt listesi.
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

    val categories = listOf<Category?>(null, Category.LOGIN, Category.CARD, Category.NOTE, Category.OTP)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item(key = "hero") {
            HeroHeader(
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(
                    R.string.vault_subtitle,
                    data.items.size,
                    stringResource(R.string.vault_never_synced)
                )
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
                    .padding(bottom = 20.dp)
            )
        }

        if (recents.isNotEmpty()) {
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
                        val tone = toneOf(entry)
                        val (background, foreground) = badgeColors(tone)
                        RecentCard(
                            name = entry.name,
                            subtitle = toneLabel(tone),
                            initial = entry.initial,
                            badgeBackground = background,
                            badgeForeground = foreground,
                            onClick = { viewModel.select(entry.id) }
                        )
                    }
                }
            }
        }

        item(key = "all-label") {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.vault_all), count = items.size)
        }

        if (items.isEmpty()) {
            item(key = "empty") {
                val empty = data.items.isEmpty()
                EmptyState(
                    title = stringResource(if (empty) R.string.vault_empty_title else R.string.vault_no_match_title),
                    subtitle = stringResource(if (empty) R.string.vault_empty_sub else R.string.vault_no_match_sub)
                )
            }
        } else {
            itemsIndexed(items = items, key = { _, entry -> entry.id }) { index, entry ->
                VaultRow(
                    item = entry,
                    position = groupPositionOf(index, items.size),
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
    position: app.kasa.ui.components.GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = toneOf(item)
    val (background, foreground) = badgeColors(tone)
    val breachMark = stringResource(R.string.breach_mark)

    KasaTile(position = position, onClick = onClick, modifier = modifier) {
        KasaBadge(text = item.initial, background = background, foreground = foreground)
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = KasaTheme.text.tileName,
                color = KasaTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(item.subtitle())
                    if (item.breached) append(" · ").append(breachMark)
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

@Composable
private fun categoryFilterLabel(category: Category?): String = stringResource(
    when (category) {
        null -> R.string.cat_all
        Category.LOGIN -> R.string.cat_login
        Category.CARD -> R.string.cat_card
        Category.NOTE -> R.string.cat_note
        Category.OTP -> R.string.cat_otp
    }
)
