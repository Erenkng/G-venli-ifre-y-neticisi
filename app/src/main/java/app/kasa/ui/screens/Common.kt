package app.kasa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.core.util.PasswordStrength
import app.kasa.data.model.Category
import app.kasa.data.model.SmartFolder
import app.kasa.data.model.VaultItem
import app.kasa.ui.components.CardThumb
import app.kasa.ui.components.KasaBadge
import app.kasa.ui.theme.KasaTheme

/** Ekranların tepesindeki dev başlık ve alt satırı. */
@Composable
fun HeroHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = KasaTheme.text.hero,
                color = KasaTheme.colors.ink
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink3
                )
            }
        }
        trailing?.invoke()
    }
}

/** Bir kaydın parola gücü tonu. Parolası olmayan kayıtlar güçlü sayılır. */
fun toneOf(item: VaultItem): PasswordStrength.Tone {
    val secret = item.primarySecret
    if (secret.isBlank()) return PasswordStrength.Tone.STRONG
    if (item.breached) return PasswordStrength.Tone.WEAK
    return PasswordStrength.evaluate(secret).tone
}

/** Rozet renkleri: gücü renkle anlatır, dinamik renk açıkken bile sabit kalır. */
@Composable
fun badgeColors(tone: PasswordStrength.Tone): Pair<Color, Color> {
    val colors = KasaTheme.colors
    return when (tone) {
        PasswordStrength.Tone.WEAK -> colors.badgeWeakBg to colors.badgeWeakFg
        PasswordStrength.Tone.MID -> colors.badgeMidBg to colors.badgeMidFg
        PasswordStrength.Tone.STRONG -> colors.badgeStrongBg to colors.badgeStrongFg
    }
}

@Composable
fun toneLabel(tone: PasswordStrength.Tone): String = stringResource(
    when (tone) {
        PasswordStrength.Tone.WEAK -> R.string.tone_weak
        PasswordStrength.Tone.MID -> R.string.tone_mid
        PasswordStrength.Tone.STRONG -> R.string.tone_strong
    }
)

@Composable
fun strengthColor(tone: PasswordStrength.Tone): Color = when (tone) {
    PasswordStrength.Tone.WEAK -> KasaTheme.colors.strengthWeak
    PasswordStrength.Tone.MID -> KasaTheme.colors.strengthMid
    PasswordStrength.Tone.STRONG -> KasaTheme.colors.strengthStrong
}

/**
 * Kayıt türünün kendi rengi.
 *
 * Eskiden her rozet parola gücüne göre renkleniyordu. Bu, giriş kayıtlarında
 * doğru bir sinyal — ama kimlik, ehliyet ya da Wi-Fi kaydında "parola gücü"
 * diye ölçülen şey aslında bir kimlik numarasının entropisi oluyordu ve
 * kullanıcıya anlamsız bir renk gösteriyordu. Artık gücü olan türde güç,
 * olmayan türde tür gösteriliyor.
 *
 * Renkler tema simgelerinden geliyor; dinamik renk açıkken de tutarlı kalsın
 * diye yeni sabit renk eklenmedi.
 */
@Composable
fun categoryTint(category: Category): Pair<Color, Color> {
    val colors = KasaTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (category) {
        Category.LOGIN, Category.CARD -> colors.badgeStrongBg to colors.badgeStrongFg
        Category.NOTE -> scheme.surfaceContainerHigh to colors.ink2
        Category.OTP -> colors.badgeMidBg to colors.badgeMidFg
        Category.IDENTITY -> colors.badgeBlueBg to colors.badgeBlueFg
        Category.BANK -> colors.badgeStrongBg to colors.badgeStrongFg
        // Uçbirim çağrışımı: koyu zemin, açık simge.
        Category.SSH_KEY -> colors.ink to scheme.surface
        Category.LICENSE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Category.WIFI -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
}

/**
 * Kaydın listedeki ve başlıktaki görsel kimliği.
 *
 * Üç ayrı davranış var ve her biri o türün nasıl hatırlandığına dayanıyor:
 *
 *  - **Kart** → minyatür kart yüzü. İnsanlar kartlarını renginden tanıyor;
 *    "Ziraat Bankası Kart 2" yazısını okumaktan hızlı.
 *  - **Giriş** → sitenin baş harfi, parola gücü renginde. Giriş kayıtları
 *    isimden tanınıyor ve güç burada gerçek bir sinyal.
 *  - **Diğerleri** → türün simgesi, türün renginde. Bir kimlik kaydının baş
 *    harfi hiçbir şey söylemiyordu.
 */
@Composable
fun EntryBadge(
    item: VaultItem,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 16.dp
) {
    if (item.category == Category.CARD) {
        CardThumb(item = item, modifier = modifier, size = size)
        return
    }

    if (item.category == Category.LOGIN) {
        val (background, foreground) = badgeColors(toneOf(item))
        KasaBadge(
            text = item.initial,
            background = background,
            foreground = foreground,
            modifier = modifier,
            size = size,
            cornerRadius = cornerRadius
        )
        return
    }

    val (background, foreground) = categoryTint(item.category)
    KasaBadge(
        background = background,
        foreground = foreground,
        modifier = modifier,
        size = size,
        cornerRadius = cornerRadius
    ) {
        Icon(
            imageVector = categoryIcon(item.category),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.LOGIN -> Icons.Rounded.Password
    Category.CARD -> Icons.Rounded.CreditCard
    Category.NOTE -> Icons.Rounded.Notes
    Category.OTP -> Icons.Rounded.Timer
    Category.IDENTITY -> Icons.Rounded.Badge
    Category.BANK -> Icons.Rounded.AccountBalance
    Category.SSH_KEY -> Icons.Rounded.Terminal
    Category.LICENSE -> Icons.Rounded.Verified
    Category.WIFI -> Icons.Rounded.Wifi
}

/** Süzgeç çubuğundaki kısa kategori adı. */
@Composable
fun categoryFilterLabel(category: Category?): String = stringResource(
    when (category) {
        null -> R.string.cat_all
        Category.LOGIN -> R.string.cat_login
        Category.CARD -> R.string.cat_card
        Category.NOTE -> R.string.cat_note
        Category.OTP -> R.string.cat_otp
        Category.IDENTITY -> R.string.cat_identity
        Category.BANK -> R.string.cat_bank
        Category.SSH_KEY -> R.string.cat_ssh
        Category.LICENSE -> R.string.cat_license
        Category.WIFI -> R.string.cat_wifi
    }
)

@Composable
fun smartFolderLabel(kind: SmartFolder): String = stringResource(
    when (kind) {
        SmartFolder.FAVORITES -> R.string.smart_favorites
        SmartFolder.PASSKEYS -> R.string.smart_passkeys
        SmartFolder.LEAKED -> R.string.smart_leaked
        SmartFolder.REUSED -> R.string.smart_reused
        SmartFolder.WEAK -> R.string.smart_weak
        SmartFolder.OLD -> R.string.smart_old
        SmartFolder.NO_2FA -> R.string.smart_no2fa
        SmartFolder.TRASH -> R.string.smart_trash
    }
)

fun smartFolderIcon(kind: SmartFolder): ImageVector = when (kind) {
    SmartFolder.FAVORITES -> Icons.Rounded.Star
    SmartFolder.PASSKEYS -> Icons.Rounded.Fingerprint
    SmartFolder.LEAKED -> Icons.Rounded.Warning
    SmartFolder.REUSED -> Icons.Rounded.Repeat
    SmartFolder.WEAK -> Icons.Rounded.Warning
    SmartFolder.OLD -> Icons.Rounded.History
    SmartFolder.NO_2FA -> Icons.Rounded.Shield
    SmartFolder.TRASH -> Icons.Rounded.Delete
}

@Composable
fun categoryLabel(category: Category): String = stringResource(
    when (category) {
        Category.LOGIN -> R.string.item_login
        Category.CARD -> R.string.item_card
        Category.NOTE -> R.string.item_note
        Category.OTP -> R.string.item_otp
        Category.IDENTITY -> R.string.item_identity
        Category.BANK -> R.string.item_bank
        Category.SSH_KEY -> R.string.item_ssh
        Category.LICENSE -> R.string.item_license
        Category.WIFI -> R.string.item_wifi
    }
)

/**
 * "2 ay önce" biçiminde göreli zaman.
 *
 * Kesin tarih yerine göreli süre kullanılıyor: kullanıcı için önemli olan
 * "ne zaman değiştirdim" değil "ne kadar zaman geçti".
 */
@Composable
fun relativeTime(millis: Long): String {
    if (millis <= 0L) return stringResource(R.string.dur_never)
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    val months = days / 30
    val years = days / 365

    return when {
        minutes < 2 -> stringResource(R.string.ago_now)
        minutes < 60 -> stringResource(R.string.ago_minutes, minutes.toInt())
        hours < 24 -> stringResource(R.string.ago_hours, hours.toInt())
        days < 30 -> stringResource(R.string.ago_days, days.toInt())
        months < 12 -> stringResource(R.string.ago_months, months.toInt())
        else -> stringResource(R.string.ago_years, years.toInt())
    }
}

/** Saniye cinsinden süreyi okunur metne çevirir (otomatik kilit ayarı için). */
@Composable
fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.dur_immediately)
    seconds < 60 -> stringResource(R.string.dur_seconds, seconds)
    seconds == 60 -> stringResource(R.string.dur_minute)
    else -> stringResource(R.string.dur_minutes, seconds / 60)
}

/**
 * Kaydırılan listelerin gezinme yüzeyine göre boşluğu.
 *
 * Gezinme yüzeyi içeriğin üzerinde durduğu için son kayıt kendi başına onun
 * altında kalırdı. Sabit bir sayı da yetmiyor, iki sebeple:
 *
 *  - **Sistem çubuğu değişken.** Hareket çubuğunda ~24dp, üç tuşlu gezinmede
 *    ~48dp yer kaplıyor ve fark doğrudan son kaydın okunabilirliğine yansıyor.
 *  - **Yön değişiyor.** Yatayda gezinme alt çubuk değil yan ray; boşluk aşağıda
 *    değil solda gerekiyor. Dikeydeki 100dp'yi yatayda da uygulamak, zaten
 *    yarıya inmiş dikey alandan bir de boşuna yer yemek olurdu.
 *
 * @param extraBottom ekranın kendi ihtiyacı (örneğin kasa ekranındaki eylem düğmesi).
 */
@Composable
fun listContentPadding(extraBottom: Dp = 0.dp): PaddingValues {
    // Sistem çubuğu hareket çubuğunda ~24dp, üç tuşlu gezinmede ~48dp yer
    // kaplıyor ve fark doğrudan son kaydın okunabilirliğine yansıyor. Çentik de
    // birleşime katılıyor: bazı cihazlarda ekranın altında da kesik var.
    val system = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()

    return PaddingValues(
        start = SIDE_PADDING,
        end = SIDE_PADDING,
        bottom = NAV_BAR_HEIGHT + extraBottom + system.calculateBottomPadding()
    )
}

private val SIDE_PADDING = 16.dp

/** Gezinme çubuğunun iç boşluk hariç yüksekliği. */
private val NAV_BAR_HEIGHT = 108.dp
