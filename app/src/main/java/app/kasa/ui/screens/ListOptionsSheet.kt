package app.kasa.ui.screens

import app.kasa.ui.components.SheetBlurBehind
import app.kasa.ui.components.sheetGlassColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.data.SettingsStore
import app.kasa.ui.components.KasaButtonGroup
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Liste düzeni: sıralama ve yoğunluk.
 *
 * Arama çubuğunun sağındaki üç nokta bunu açıyor. O noktalar tasarımdan
 * gelmişti ve hiçbir şey yapmıyordu; dokunulabilir görünüp dokunulunca hiçbir
 * şey olmayan bir öğe, kullanıcıya "bu uygulamanın bazı yerleri çalışmıyor"
 * diyor.
 *
 * Buraya konan iki tercih de kasası büyüyen kullanıcının işi: yüz kaydı geçince
 * "en son kullandığım" sıralaması yetmiyor ve rahat yerleşim ekrana altı satır
 * sığdırıyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListOptionsSheet(
    sortOrder: SettingsStore.SortOrder,
    density: SettingsStore.ListDensity,
    onSortChange: (SettingsStore.SortOrder) -> Unit,
    onDensityChange: (SettingsStore.ListDensity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val orders = SettingsStore.SortOrder.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Cam levha: altındaki ekran görünür kalıyor ama okunmuyor.
        // Pencerenin arkası SheetBlurBehind ile bulanıklaşıyor; ikisi
        // birlikte Android 17'nin alt sayfa yüzeyini kuruyor.
        containerColor = sheetGlassColor(),

        dragHandle = null
    ) {
        SheetBlurBehind()
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxContentHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(KasaTheme.colors.ink3)
            )
            Spacer(Modifier.height(18.dp))

            Text(
                stringResource(R.string.list_sort),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(12.dp))

            orders.forEachIndexed { index, order ->
                KasaTile(
                    position = groupPositionOf(index, orders.size),
                    onClick = { onSortChange(order) }
                ) {
                    Text(
                        text = stringResource(sortLabel(order)),
                        style = KasaTheme.text.tileName,
                        color = KasaTheme.colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (order == sortOrder) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.list_density),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(12.dp))
            KasaButtonGroup(
                options = SettingsStore.ListDensity.entries.toList(),
                selected = density,
                label = { stringResource(densityLabel(it)) },
                onSelect = onDensityChange,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun sortLabel(order: SettingsStore.SortOrder): Int = when (order) {
    SettingsStore.SortOrder.LAST_USED -> R.string.sort_last_used
    SettingsStore.SortOrder.NAME -> R.string.sort_name
    SettingsStore.SortOrder.NEWEST -> R.string.sort_newest
    SettingsStore.SortOrder.WEAKEST -> R.string.sort_weakest
}

private fun densityLabel(density: SettingsStore.ListDensity): Int = when (density) {
    SettingsStore.ListDensity.COMFORTABLE -> R.string.density_comfortable
    SettingsStore.ListDensity.COMPACT -> R.string.density_compact
}
