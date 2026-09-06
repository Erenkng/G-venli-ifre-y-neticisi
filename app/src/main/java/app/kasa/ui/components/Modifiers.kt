package app.kasa.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import app.kasa.core.util.Haptics
import app.kasa.core.util.rememberHapticPlayer

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
): Modifier = composed {
    val own = remember { MutableInteractionSource() }
    val source = interactionSource ?: own
    pressFeedback(source, enabled)
    clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

/**
 * Parmak yüzeye değdiği anda gelen tıkırtı.
 *
 * ### Neden burada, çağrı yerlerinde değil
 *
 * Titreşim, olayı **bilen** yerde çalınıyordu: görünüm modeli "kopyalandı"
 * ya da "kaydedildi" derken uygun duyguyu veriyor. Ama bir denetime dokunmak
 * ile o dokunuşun bir sonucu olması iki ayrı şey ve ikincisi olmayan pek çok
 * denetim var — yonga, sekme, kategori satırı, açılır menüdeki bir seçenek.
 * Onlara dokunulduğunda parmağa hiçbir şey gelmiyordu ve kullanıcının
 * gördüğü şey "kimi tıklamalarda titreşim yok" oluyordu. Uygulamadaki her
 * dokunulabilir yüzey bu ilkelden geçtiği için, geri bildirimin doğru yeri
 * burası.
 *
 * ### Neden basış anında, tıklama anında değil
 *
 * Tıklama anında çalınsaydı, sonucu olan denetimlerde iki titreşim aynı ana
 * düşerdi: genel "dokunuldu" ile anlamlı "kaydedildi" birbirinin üstüne
 * binip tek bir bulanık darbe olurdu. Basışta çalınınca ikisi ayrılıyor —
 * parmak inerken yüzey karşılık veriyor, parmak kalkınca sonuç geliyor.
 * Fiziksel bir düğmenin tıkı ile yaptığı işin sesi de böyle ayrı.
 *
 * ### Kaydırmada çalmıyor
 *
 * `clickable`, kaydırılabilir bir kabın içinde basış olayını dokunuşun
 * kaydırmaya dönüşmediği anlaşılana kadar geciktiriyor. Yani listeyi
 * kaydırırken satırların altından titreşim gelmiyor; bu davranış bedava
 * geliyor, ayrıca bir şey yapmak gerekmiyor.
 */
@Composable
private fun pressFeedback(source: MutableInteractionSource, enabled: Boolean) {
    val play = rememberHapticPlayer()
    LaunchedEffect(source, enabled, play) {
        if (!enabled) return@LaunchedEffect
        source.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) play(Haptics.Kind.TAP)
        }
    }
}

/**
 * Dokunma ve basılı tutma, dalga efekti olmadan.
 *
 * [clickableNoRipple] ile aynı görsel davranış; tek farkı uzun basışı da
 * taşıması. Ayrı bir işlev olmasının sebebi `combinedClickable`ın deneysel
 * olması ve tek bir yerde işaretlenmesinin, her çağrı yerine dağıtmaktan
 * temiz olması.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableNoRipple(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    role: Role? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val own = remember { MutableInteractionSource() }
    val source = interactionSource ?: own
    pressFeedback(source, enabled)
    val play = rememberHapticPlayer()
    combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        // Uzun basış eşiği geçildiğinde ayrı bir duygu: dokunuşun tıkırtısı
        // "değdin" diyor, bu "eşiği geçtin" diyor. İkisi aynı olsaydı uzun
        // basmanın tuttuğu an parmaktan anlaşılmazdı.
        onLongClick = onLongClick?.let { handler ->
            {
                play(Haptics.Kind.THRESHOLD)
                handler()
            }
        },
        onClick = onClick
    )
}

/** Genişliği piksel cinsinden bildirir; kaydırıcı konumu bunun üzerinden hesaplanır. */
fun Modifier.onSizeChangedPx(onWidth: (Float) -> Unit): Modifier =
    this.onSizeChanged { onWidth(it.width.toFloat()) }
