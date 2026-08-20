package app.kasa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kasa.R
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Onay penceresi.
 *
 * Yıkıcı eylemlerde onay düğmesi hata rengiyle ve **ikinci sırada** durur;
 * kullanıcının parmağının doğal olarak düştüğü yerde "vazgeç" olur.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = stringResource(R.string.cancel)
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(title, style = MaterialTheme.typography.titleLarge, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = KasaTheme.colors.ink2)
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                // ── neden eşit paylaşım ────────────────────────────────
                //
                // Ağırlıksız bir Row çocuklarını sırayla ölçüyor: ilk düğme
                // istediği genişliği alıyor, ikinciye kalanı kalıyor. İki
                // etiket birlikte sığmadığında **ikinci** düğme eziliyordu ve
                // "Vazgeç" ekranda "Vaz" olarak duruyordu — hata her zaman
                // aynı düğmede, yani fark edilmesi de zor.
                //
                // Eşit ağırlıkla ikisi de aynı genişliği alıyor ve hangi
                // etiketin uzun olduğu düzeni değiştirmiyor.
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KasaButton(
                    text = confirmText,
                    onClick = onConfirm,
                    tone = if (destructive) ButtonTone.OUTLINED else ButtonTone.FILLED,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                )
                KasaButton(
                    text = dismissText,
                    onClick = onDismiss,
                    tone = if (destructive) ButtonTone.FILLED else ButtonTone.TONAL,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Tek parola isteyen pencere (dışa aktarma, içe aktarma).
 *
 * [requireConfirmation] açıkken parola iki kez istenir; yanlış yazılmış bir
 * dışa aktarma parolası, dosyayı kalıcı olarak açılamaz hâle getirir.
 */
@Composable
fun PasswordPromptDialog(
    title: String,
    description: String,
    label: String,
    confirmText: String,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    requireConfirmation: Boolean = false,
    confirmLabel: String = stringResource(R.string.exp_password_again),
    minLength: Int = 8
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val valid = password.length >= minLength && (!requireConfirmation || password == confirmation)
    val mismatch = requireConfirmation && confirmation.isNotEmpty() && password != confirmation

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        DialogSurface {
            Text(title, style = MaterialTheme.typography.titleLarge, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(10.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = KasaTheme.colors.ink2)
            Spacer(Modifier.height(18.dp))

            KasaPasswordField(
                value = password,
                onValueChange = { password = it },
                label = label,
                revealed = revealed,
                onRevealToggle = { revealed = !revealed },
                imeAction = if (requireConfirmation) ImeAction.Next else ImeAction.Done
            )

            if (requireConfirmation) {
                Spacer(Modifier.height(8.dp))
                KasaPasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = confirmLabel,
                    revealed = revealed,
                    onRevealToggle = { revealed = !revealed },
                    isError = mismatch,
                    supportingText = if (mismatch) stringResource(R.string.onb_mismatch) else null
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                // ── neden eşit paylaşım ────────────────────────────────
                //
                // Ağırlıksız bir Row çocuklarını sırayla ölçüyor: ilk düğme
                // istediği genişliği alıyor, ikinciye kalanı kalıyor. İki
                // etiket birlikte sığmadığında **ikinci** düğme eziliyordu ve
                // "Vazgeç" ekranda "Vaz" olarak duruyordu — hata her zaman
                // aynı düğmede, yani fark edilmesi de zor.
                //
                // Eşit ağırlıkla ikisi de aynı genişliği alıyor ve hangi
                // etiketin uzun olduğu düzeni değiştirmiyor.
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KasaButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    tone = ButtonTone.TONAL,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                )
                KasaButton(
                    text = confirmText,
                    onClick = {
                        val chars = password.toCharArray()
                        password = ""
                        confirmation = ""
                        onConfirm(chars)
                    },
                    enabled = valid,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Onaylamak için belirli bir sözcüğün yazılmasını isteyen pencere. */
@Composable
fun TypeToConfirmDialog(
    title: String,
    body: String,
    hint: String,
    word: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = KasaTheme.colors.ink2)
            Spacer(Modifier.height(18.dp))
            app.kasa.ui.components.KasaTextField(
                value = typed,
                onValueChange = { typed = it },
                label = hint,
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                // ── neden eşit paylaşım ────────────────────────────────
                //
                // Ağırlıksız bir Row çocuklarını sırayla ölçüyor: ilk düğme
                // istediği genişliği alıyor, ikinciye kalanı kalıyor. İki
                // etiket birlikte sığmadığında **ikinci** düğme eziliyordu ve
                // "Vazgeç" ekranda "Vaz" olarak duruyordu — hata her zaman
                // aynı düğmede, yani fark edilmesi de zor.
                //
                // Eşit ağırlıkla ikisi de aynı genişliği alıyor ve hangi
                // etiketin uzun olduğu düzeni değiştirmiyor.
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KasaButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = typed.trim().equals(word, ignoreCase = false),
                    tone = ButtonTone.OUTLINED,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                )
                KasaButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    height = 46.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogSurface(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.xl))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(24.dp)
    ) {
        content()
    }
}
