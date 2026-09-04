package app.kasa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.data.model.Folder
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaTheme

/**
 * Seçili kayıtların taşınacağı klasörü seçtiren sayfa.
 *
 * ### Neden "klasörsüz" de bir seçenek
 *
 * Taşımanın tersi de taşıma: kullanıcı bir kaydı klasörden **çıkarmak**
 * isteyebilir. Bunun için ayrı bir işlem eklemek, aynı şeyin iki farklı
 * yerde durması olurdu; liste zaten "nereye" sorusunu soruyor ve "hiçbir
 * yere" o sorunun geçerli bir cevabı.
 *
 * ### Klasör yoksa
 *
 * Sayfa yine açılıyor ve yalnızca "klasörsüz" görünüyor. Hiç açılmamak,
 * düğmeye basan kullanıcıya hiçbir şey söylemezdi; boş liste ise klasör
 * diye bir şeyin var olduğunu öğretiyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<Folder>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = SHEET_ALPHA),
        dragHandle = null
    ) {
        app.kasa.ui.components.SheetBlurBehind()
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxContentHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.bulk_folder),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(16.dp))

            val total = folders.size + 1
            KasaTile(position = groupPositionOf(0, total), onClick = { onPick(null) }) {
                Icon(
                    Icons.Rounded.FolderOff,
                    contentDescription = null,
                    tint = KasaTheme.colors.ink3,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    stringResource(R.string.bulk_no_folder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = KasaTheme.colors.ink
                )
            }
            folders.forEachIndexed { index, folder ->
                Spacer(Modifier.height(3.dp))
                KasaTile(
                    position = groupPositionOf(index + 1, total),
                    onClick = { onPick(folder.id) }
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = KasaTheme.colors.ink
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private const val SHEET_ALPHA = 0.88f
