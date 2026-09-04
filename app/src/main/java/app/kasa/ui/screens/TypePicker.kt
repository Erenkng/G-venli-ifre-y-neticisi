package app.kasa.ui.screens

import app.kasa.ui.components.SheetBlurBehind
import app.kasa.ui.components.sheetGlassColor
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import app.kasa.data.model.Category
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.groupPositionOf
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Kayıt türü seçici.
 *
 * ### Neden eylem düğmesinde hepsi yok
 *
 * Dokuz tür var ve eylem düğmesinin menüsüne dokuzunu birden koymak, menüyü
 * ekranın yarısına yayıp asıl işi — "yeni bir giriş ekle" — öteki sekizin
 * arasında kaybediyordu. Menüde günlük kullanılan beşi duruyor; kalanlar
 * "Diğer" ile açılan bu listede.
 *
 * Ayrım keyfî değil, kullanım sıklığına dayanıyor: giriş, kart, 2FA ve not bir
 * parola yöneticisinin gövdesi; banka hesabı da Türkiye'de IBAN yüzünden sık
 * aranan bir kayıt. Kimlik, SSH anahtarı, lisans ve Wi-Fi ayda bir açılıyor ve
 * bir dokunuş uzakta olmaları bir şey kaybettirmiyor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypePickerSheet(
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    categories: List<Category> = Category.secondary
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                stringResource(R.string.type_picker_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(14.dp))

            categories.forEachIndexed { index, category ->
                TypeRow(
                    category = category,
                    position = groupPositionOf(index, categories.size),
                    onClick = { onPick(category) }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TypeRow(
    category: Category,
    position: GroupPosition,
    onClick: () -> Unit
) {
    val (background, foreground) = categoryTint(category)

    KasaTile(position = position, onClick = onClick) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon(category),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = categoryLabel(category),
                style = KasaTheme.text.tileName,
                color = KasaTheme.colors.ink
            )
            Text(
                text = stringResource(categoryHint(category)),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink2
            )
        }
    }
}

/**
 * Türün ne işe yaradığını söyleyen tek satır.
 *
 * Adın kendisi her zaman yetmiyor: "Anahtar" tek başına neyin anahtarı olduğunu
 * söylemiyor, "Lisans" da sürücü belgesiyle karışabiliyor. Seçim ekranında
 * yanlış tür seçmenin bedeli, kaydı silip yeniden kurmak.
 */
private fun categoryHint(category: Category): Int = when (category) {
    Category.LOGIN -> R.string.type_hint_login
    Category.CARD -> R.string.type_hint_card
    Category.OTP -> R.string.type_hint_otp
    Category.BANK -> R.string.type_hint_bank
    Category.IDENTITY -> R.string.type_hint_identity
    Category.SSH_KEY -> R.string.type_hint_ssh
    Category.LICENSE -> R.string.type_hint_license
    Category.WIFI -> R.string.type_hint_wifi
}
