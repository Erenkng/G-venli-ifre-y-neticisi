package app.kasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.data.model.PasswordHistoryEntry
import app.kasa.data.model.VaultItem
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.sheetGlassColor
import app.kasa.ui.components.KasaIconButton
import app.kasa.ui.components.RevealButton
import app.kasa.ui.components.SheetBlurBehind
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.components.maskFade
import app.kasa.ui.components.rememberMaskFade
import app.kasa.ui.components.glassSurface
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bir kaydın eski parolaları.
 *
 * ### Neden gerekli
 *
 * Kasa parola değişikliklerinde eskisini zaten saklıyordu — ama arayüzde
 * yalnızca **sayısı** görünüyordu: "3 eski parola". Saklamanın tek sebebi
 * geri dönebilmek olduğuna göre, ulaşılamayan bir geçmiş hiçbir işe
 * yaramıyordu.
 *
 * Asıl senaryo şu: kullanıcı bir sitede parolasını değiştiriyor, kasaya
 * yenisini yazıyor, sonra sitenin değişikliği kaydetmediği ortaya çıkıyor.
 * O anda doğru parola yalnızca burada duruyor.
 *
 * ### Neden hepsi kapalı başlıyor
 *
 * Liste açıldığında ekranda aynı anda beş parola açık dursaydı, omuz
 * üstünden bakan biri için tek bir bakış bütün geçmişi vermeye yeterdi.
 * Her satır ayrı açılıyor ve açılan satır ötekini kapatmıyor — kullanıcı
 * iki eskiyi karşılaştırmak isteyebilir.
 *
 * ### Geri yükleme neden geçmişi silmiyor
 *
 * Eski bir parolayı geri yüklemek, o an kayıtlı olanı geçmişin başına
 * atıyor: işlem tersine çevrilebilir kalıyor. Yanlış satırı geri yükleyen
 * kullanıcı, tek dokunuşla geri dönebiliyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHistorySheet(
    item: VaultItem,
    onCopy: (String) -> Unit,
    onRestore: (PasswordHistoryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.detail_history),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.history_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink3,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(16.dp))

            item.history.forEachIndexed { index, entry ->
                HistoryRow(
                    entry = entry,
                    position = groupPositionOf(index, item.history.size),
                    onCopy = { onCopy(entry.password.reveal()) },
                    onRestore = { onRestore(entry) }
                )
                if (index < item.history.lastIndex) Spacer(Modifier.height(3.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryRow(
    entry: PasswordHistoryEntry,
    position: GroupPosition,
    onCopy: () -> Unit,
    onRestore: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    // Açılırken noktalar bir anda harfe dönmüyor: metin bulanıklaşıyor, takas
    // en bulanık karede oluyor. Aynı hareket kayıt ayrıntısında da var.
    val fade = rememberMaskFade(revealed)

    val shape = when (position) {
        GroupPosition.ONLY -> RoundedCornerShape(KasaRadius.l)
        GroupPosition.FIRST -> RoundedCornerShape(
            topStart = KasaRadius.l, topEnd = KasaRadius.l,
            bottomStart = KasaRadius.xs, bottomEnd = KasaRadius.xs
        )
        GroupPosition.LAST -> RoundedCornerShape(
            topStart = KasaRadius.xs, topEnd = KasaRadius.xs,
            bottomStart = KasaRadius.l, bottomEnd = KasaRadius.l
        )
        GroupPosition.MIDDLE -> RoundedCornerShape(KasaRadius.xs)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape, KasaTheme.colors.tile)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatChangedAt(entry.changedAt),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.maskFade(fade)) {
                Text(
                    text = if (fade.showPlain) entry.password.reveal() else MASK,
                    style = KasaTheme.text.mono,
                    color = KasaTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        RevealButton(revealed = revealed, onClick = { revealed = !revealed })
        KasaIconButton(
            onClick = onCopy,
            size = 36.dp,
            contentDescription = stringResource(R.string.copy_password)
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                tint = KasaTheme.colors.ink2,
                modifier = Modifier.size(17.dp)
            )
        }
        KasaIconButton(
            onClick = onRestore,
            size = 36.dp,
            contentDescription = stringResource(R.string.history_restore)
        ) {
            Icon(
                Icons.Rounded.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * Değişim anı.
 *
 * Göreli süre ("3 ay önce") değil kesin tarih: kullanıcı bu listeye bir
 * parolanın **hangisi** olduğunu bulmak için bakıyor ve o ayrımı yapan şey
 * tarihin kendisi. "3 ay önce" iki satırda da aynı yazabilir.
 */
private fun formatChangedAt(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr", "TR")).format(Date(millis))

/** Kapalıyken görünen maske; uzunluğu gerçek parolayı ele vermiyor. */
private const val MASK = "••••••••••••"

