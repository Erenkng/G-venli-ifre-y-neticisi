package app.kasa.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role

/**
 * Dalga (ripple) yerine ölçek/biçim geri bildirimi kullanan tıklama.
 *
 * Tasarımın tamamı dokunuşa "sıkışarak" yanıt veriyor; üstüne bir de dalga
 * eklemek iki ayrı geri bildirimin üst üste binmesine yol açıyor. Erişilebilirlik
 * açısından kayıp yok: rol ve tıklama etiketi hâlâ veriliyor, TalkBack düğmeyi
 * doğru okuyor.
 */
fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick
)

/** Genişliği piksel cinsinden bildirir; kaydırıcı konumu bunun üzerinden hesaplanır. */
fun Modifier.onSizeChangedPx(onWidth: (Float) -> Unit): Modifier =
    this.onSizeChanged { onWidth(it.width.toFloat()) }
