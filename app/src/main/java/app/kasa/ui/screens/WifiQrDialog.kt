package app.kasa.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kasa.R
import app.kasa.core.util.WifiQr
import app.kasa.data.model.VaultItem
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Wi-Fi kaydının karekodu.
 *
 * ### Neden bir pencere, ayrı bir ekran değil
 *
 * Karekod tek bir iş için açılıyor ve iş bitince kapanıyor: karşıdaki telefon
 * okudu. Ayrı bir ekran, geri gitme yükü ve gezinme geçmişinde kalan bir iz
 * demek olurdu; bu görüntünün gezinme geçmişinde durmasının hiçbir yararı yok.
 *
 * ### Zemin her zaman beyaz
 *
 * Karekod okuyucular koyu modül–açık zemin kontrastına göre çalışıyor.
 * Karanlık temada koyu bir zemine çizmek kontrastı tersine çevirir ve pek çok
 * okuyucu bunu okuyamaz. Bu yüzden karekodun kendi zemini temadan bağımsız.
 */
@Composable
fun WifiQrDialog(item: VaultItem, onDismiss: () -> Unit) {
    val payload = remember(item.id, item.extras) { WifiQr.payload(item) }
    val bitmap = remember(payload) { WifiQr.bitmap(payload, QR_SIZE_PX) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(KasaRadius.l))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                WifiQr.ssidOf(item).ifBlank { item.name },
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.wifi_qr_sub),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            if (bitmap != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(KasaRadius.m))
                        // Okuyucular koyu modül–açık zemin bekliyor; zemin
                        // temadan bağımsız beyaz.
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
            } else {
                Text(
                    stringResource(R.string.wifi_qr_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))
            KasaButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                tone = ButtonTone.TONAL,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Karekodun piksel boyu.
 *
 * Ekranda ölçekleneceği için kesin bir değer gerekmiyor ama çok küçük
 * üretilmiş bir karekod büyütüldüğünde modüller bulanıklaşıyor ve okuyucu
 * kenarları ayıramıyor.
 */
private const val QR_SIZE_PX = 640
