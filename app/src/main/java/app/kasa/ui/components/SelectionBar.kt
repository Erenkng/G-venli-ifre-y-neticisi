package app.kasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Seçim kipindeki eylem çubuğu.
 *
 * ### Neden gezinme çubuğunun üstünde, onun yerine değil
 *
 * Gezinme çubuğunu geçici olarak bu çubukla değiştirmek yaygın bir kalıp ama
 * burada yanlış: kullanıcı seçim yaparken sekme değiştirmek isteyebiliyor
 * (bir kaydı klasöre taşımadan önce klasörlere bakmak gibi) ve gezinme
 * çubuğunun kaybolması o yolu kapatıyor. Çubuk üstüne biniyor, altındaki
 * duruyor.
 *
 * Ayrıca gezinme çubuğuna dokunulmuyor: onun yüzeyi, camı ve hareketi
 * kendi dosyasında ve orada kalıyor.
 *
 * ### Neden cam
 *
 * Altındaki liste seçim sırasında görünür kalmalı — kullanıcı neyi seçtiğini
 * kontrol ediyor. Opak bir çubuk üç satırı gizliyordu.
 *
 * ### Sayı neden solda
 *
 * Seçim kipinde tek soru "kaç tane". Eylemler sağda ve parmağın doğal olarak
 * bulunduğu yerde; sayı, göz hareketinin başladığı yerde.
 */
@Composable
fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onFavorite: () -> Unit,
    onMoveToFolder: () -> Unit,
    onTrash: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = slideInVertically(KasaMotion.medium()) { it } + fadeIn(KasaMotion.enter()),
        exit = slideOutVertically(KasaMotion.exit()) { it } + fadeOut(KasaMotion.exit())
    ) {
        // Sistem çubuğu boşluğu burada **eklenmiyor**: çubuğu yerleştiren
        // taraf onu gezinme çubuğunun tamamı kadar yukarı kaldırıyor ve o
        // mesafe sistem boşluğunu zaten içeriyor. İkisini birden uygulamak
        // çubuğu bir sistem çubuğu yüksekliği kadar fazla kaldırıyordu.
        GlassPlate(
            shape = RoundedCornerShape(KasaRadius.full),
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.bulk_selected, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = KasaTheme.colors.ink
                )
                Row(
                    modifier = Modifier.padding(start = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    BarAction(
                        icon = Icons.Rounded.DoneAll,
                        label = stringResource(R.string.bulk_select_all),
                        accent = allSelected,
                        onClick = onSelectAll
                    )
                    BarAction(
                        icon = Icons.Rounded.Star,
                        label = stringResource(R.string.add_to_favorites),
                        onClick = onFavorite
                    )
                    BarAction(
                        icon = Icons.Rounded.Folder,
                        label = stringResource(R.string.bulk_folder),
                        onClick = onMoveToFolder
                    )
                    BarAction(
                        icon = Icons.Rounded.Delete,
                        label = stringResource(R.string.delete),
                        danger = true,
                        onClick = onTrash
                    )
                    BarAction(
                        icon = Icons.Rounded.Close,
                        label = stringResource(R.string.bulk_clear),
                        onClick = onClose
                    )
                }
            }
        }
    }
}

@Composable
private fun BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    danger: Boolean = false
) {
    // 48dp Android'in en küçük dokunma hedefi. Çubuktaki beş düğme yan yana
    // duruyor ve küçük olanlar birbirine yakın: yanlış düğmeye basmanın
    // sonucu burada 'kayıt silindi' olabiliyor.
    KasaIconButton(onClick = onClick, size = 48.dp, contentDescription = label) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                danger -> KasaTheme.colors.strengthWeak
                accent -> MaterialTheme.colorScheme.primary
                else -> KasaTheme.colors.ink2
            },
            modifier = Modifier.size(21.dp)
        )
    }
}
