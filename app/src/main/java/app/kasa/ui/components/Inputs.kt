package app.kasa.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Kasa ekranındaki arama çubuğu — aslında bir düğme.
 *
 * Dokununca tam ekran arama görünümü açılır. Bunun yerine satır içinde yazmaya
 * başlamak, klavye açıldığında listeyi yarıya düşürürdü; tam ekran arama hem
 * daha fazla sonuç gösteriyor hem de tek elle kullanımda daha rahat.
 */
private val SEARCH_HEIGHT = 58.dp

@Composable
fun SearchBarButton(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "searchScale")
    // Tam yuvarlak = yüksekliğin yarısı. 999dp hedefiyle yay, basış bırakılınca
    // negatif yarıçapa iniyordu ve gölgesi olan bu bileşende çökme yapıyordu.
    val radius = animatedCorner(if (pressed) 22.dp else SEARCH_HEIGHT / 2, label = "searchRadius")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SEARCH_HEIGHT)
            .scale(scale)
            .shadow(1.dp, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(radius))
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = KasaTheme.colors.ink3,
            modifier = Modifier.size(21.dp)
        )
        Text(
            placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = KasaTheme.colors.ink3,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(KasaRadius.full))
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

/**
 * Tasarımın alan (field) bloğu: küçük büyük harf etiket, altında değer ve
 * sağda eylem düğmeleri.
 */
@Composable
fun FieldBlock(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.m))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            label.uppercase(java.util.Locale("tr", "TR")),
            style = KasaTheme.text.fieldLabel,
            color = KasaTheme.colors.ink3
        )
        Spacer(Modifier.height(7.dp))
        content()
    }
}

/**
 * Metin girişi.
 *
 * Material'ın `OutlinedTextField`'ı yerine `BasicTextField` üzerine kurulu:
 * tasarımın alan blokları (dolgulu, geniş yarıçaplı, üstte küçük etiket)
 * Material'ın kendi çerçevesiyle uyuşmuyor ve iç boşlukları zorlamak
 * bileşeni bozuyordu.
 */
@Composable
fun KasaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    trailing: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    val style = (textStyle ?: MaterialTheme.typography.bodyLarge).copy(color = KasaTheme.colors.ink)

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KasaRadius.m))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = if (isError) 1.5.dp else 0.dp,
                    color = if (isError) MaterialTheme.colorScheme.error else Color.Transparent,
                    shape = RoundedCornerShape(KasaRadius.m)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                label.uppercase(java.util.Locale("tr", "TR")),
                style = KasaTheme.text.fieldLabel,
                color = if (isError) MaterialTheme.colorScheme.error else KasaTheme.colors.ink3
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = style.copy(color = KasaTheme.colors.ink3))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = style,
                        singleLine = singleLine,
                        minLines = minLines,
                        enabled = enabled,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = visualTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp)
                    )
                }
                trailing?.invoke()
            }
        }
        if (supportingText != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else KasaTheme.colors.ink3,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

/** Gizleme/gösterme düğmesi taşıyan parola alanı. */
@Composable
fun KasaPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    revealed: Boolean,
    onRevealToggle: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingExtra: @Composable (() -> Unit)? = null
) {
    KasaTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation('•'),
        textStyle = KasaTheme.text.mono,
        isError = isError,
        supportingText = supportingText,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                trailingExtra?.invoke()
                RevealButton(revealed = revealed, onClick = onRevealToggle)
            }
        }
    )
}

/**
 * Yalnızca rakam kabul eden, gizlenmiş PIN alanı.
 *
 * Klavye türü [KeyboardType.NumberPassword]: hem tuş takımı sayısal geliyor
 * hem de sistem alanı parola alanı sayıp önerileri ve panoyu devre dışı
 * bırakıyor. Rakam dışındaki her karakter girişte eleniyor — kullanıcıya hata
 * göstermek yerine yazılamaz kılmak, dört haneli bir alanda daha az sürtünme.
 */
@Composable
fun KasaPinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    KasaTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }) },
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
        visualTransformation = PasswordVisualTransformation('\u2022'),
        textStyle = KasaTheme.text.mono,
        isError = isError,
        supportingText = supportingText
    )
}

@Composable
fun RevealButton(revealed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KasaIconButton(onClick = onClick, modifier = modifier, size = 36.dp) {
        Icon(
            imageVector = if (revealed) Icons.Rounded.VisibilityOff
            else Icons.Rounded.Visibility,
            contentDescription = null,
            tint = KasaTheme.colors.ink2,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Tam ekran arama görünümündeki üst çubuk. */
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KasaIconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = KasaTheme.colors.ink2,
                modifier = Modifier.size(22.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KasaTheme.colors.ink3
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = KasaTheme.colors.ink)
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }
    }
}
