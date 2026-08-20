package app.kasa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Tasarımın koyu, köşeleri hafif yuvarlatılmış bildirim şeridi.
 *
 * Material'ın varsayılan snackbar'ı yerine bu kullanılıyor çünkü tasarımda
 * eylem metni sağa yaslı, yazı tipi ve renkleri kasanın kendi paletinden
 * geliyor ve karanlık modda ters çeviriliyor.
 */
@Composable
fun KasaSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data: SnackbarData ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .shadow(6.dp, RoundedCornerShape(KasaRadius.s), clip = false)
                .clip(RoundedCornerShape(KasaRadius.s))
                .background(KasaTheme.colors.snackbar)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.onSnackbar,
                modifier = Modifier.weight(1f)
            )
            val actionLabel = data.visuals.actionLabel
            if (actionLabel != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = KasaTheme.colors.snackbarAction,
                    modifier = Modifier.clickableNoRipple { data.performAction() }
                )
            }
        }
    }
}

/** Alttan yaylanarak giren içerik sarmalayıcısı. */
@Composable
fun SpringSlideIn(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = KasaMotion.medium()
        ) + fadeIn(KasaMotion.effect()),
        exit = slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = KasaMotion.exit()
        ) + fadeOut(KasaMotion.exit()),
        content = { content() }
    )
}
