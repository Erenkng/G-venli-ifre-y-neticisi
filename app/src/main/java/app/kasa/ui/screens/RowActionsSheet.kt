package app.kasa.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kasa.R
import app.kasa.core.util.WifiQr
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Bir kayda basılı tutunca açılan işlemler.
 *
 * ### Neden var
 *
 * Günlük kullanımın büyük kısmı tek bir iş: parolayı kopyalamak. Bunun için
 * kaydı açmak, gösterilen alanlar arasından doğru olanı bulmak ve geri
 * dönmek gerekiyordu — üç adım, hepsi de kaydın **içeriğini** ekrana getirmek
 * zorunda kalarak. Basılı tutma bu işi listede bitiriyor ve parola hiç
 * görünmüyor: kopyalanıyor, panodan da ayarlanan süre sonunda siliniyor.
 *
 * ### Kayıt bazlı kilit burada da geçerli
 *
 * İşaretli kayıtta kopyalama ve düzenleme bu menüde **gösterilmiyor**. Kilidi
 * bir kestirmeyle atlatılabilen koruma, koruma değildir; o kayıtlar ayrıntı
 * ekranından, doğrulamanın ardından açılıyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowActionsSheet(
    item: VaultItem,
    clipboardSeconds: Int,
    onCopySecret: (String) -> Unit,
    onCopyPlain: (String) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShowWifiQr: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val host = item.host()

    // İşaretli kayıtta gizli değere giden her yol kapalı; gerekçe yukarıda.
    val locked = item.requireAuth

    val actions = buildList {
        if (!locked && item.primarySecret.isNotBlank()) {
            add(
                RowAction(
                    label = stringResource(secretLabel(item.category)),
                    icon = Icons.Rounded.ContentCopy
                ) { onCopySecret(item.primarySecret) }
            )
        }
        if (!locked && item.username.isNotBlank()) {
            add(
                RowAction(
                    label = stringResource(R.string.copy_username),
                    icon = Icons.Rounded.Person
                ) { onCopyPlain(item.username) }
            )
        }
        // Wi-Fi karekodu yalnızca ağ adı olan kayıtta; kilitliyse parola
        // karekodun içinde olacağı için gösterilmiyor.
        if (!locked && item.category == Category.WIFI && WifiQr.isEncodable(item)) {
            add(
                RowAction(
                    label = stringResource(R.string.wifi_qr_action),
                    icon = Icons.Rounded.QrCode2
                ) { onShowWifiQr() }
            )
        }
        if (host != null) {
            add(
                RowAction(
                    label = stringResource(R.string.open_url),
                    icon = Icons.Rounded.OpenInNew
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://$host".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }
        add(
            RowAction(
                label = stringResource(
                    if (item.favorite) R.string.remove_from_favorites else R.string.add_to_favorites
                ),
                icon = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder
            ) { onToggleFavorite() }
        )
        if (!locked) {
            add(RowAction(stringResource(R.string.edit), Icons.Rounded.Edit) { onEdit() })
            add(RowAction(stringResource(R.string.duplicate), Icons.Rounded.ContentCopy) { onDuplicate() })
        }
        add(
            RowAction(
                label = stringResource(R.string.delete),
                icon = Icons.Rounded.Delete,
                destructive = true
            ) { onDelete() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                EntryBadge(item = item)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(item.name, style = KasaTheme.text.sheetTitle, color = KasaTheme.colors.ink)
                    Text(
                        item.subtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = KasaTheme.colors.ink2
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            actions.forEachIndexed { index, action ->
                ActionRow(
                    action = action,
                    position = groupPositionOf(index, actions.size),
                    onRun = {
                        action.run()
                        onDismiss()
                    }
                )
            }

            if (!locked && clipboardSeconds > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    // Kopyalamanın panoda ne kadar kalacağını söylemek, kopyalama
                    // eyleminin yarısı: kullanıcı süreyi bilmeden "yapıştırmayı
                    // unuttum" durumuna düşüyor.
                    text = stringResource(R.string.row_clipboard_note, clipboardSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = KasaTheme.colors.ink3,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private data class RowAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val run: () -> Unit
)

@Composable
private fun ActionRow(action: RowAction, position: GroupPosition, onRun: () -> Unit) {
    val tint: Color =
        if (action.destructive) KasaTheme.colors.badgeWeakFg else KasaTheme.colors.ink
    KasaTile(position = position, onClick = onRun) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(action.label, style = KasaTheme.text.tileName, color = tint)
    }
}

/**
 * "Gizli değeri kopyala" her türde aynı şeyi kopyalamıyor: girişte parola,
 * kartta numara, notta metnin kendisi. Etiket de bunu söylemeli, yoksa
 * kullanıcı neyin panoya gittiğini denemeden bilemiyor.
 */
private fun secretLabel(category: Category): Int = when (category) {
    Category.CARD -> R.string.copy_card_number
    Category.NOTE -> R.string.copy_note
    Category.OTP -> R.string.copy_code
    else -> R.string.copy_password
}
