package app.kasa.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────── açık tema ────────────────────────────────
// Kaynak renk: jade #00695C · vurgu: kehribar · uyarı: kiremit

val Ink = Color(0xFF0D1B18)
val Ink2 = Color(0xFF3B4C47)
val Ink3 = Color(0xFF6B7E78)

val Jade = Color(0xFF00695C)
val OnJade = Color(0xFFFFFFFF)
val JadeContainer = Color(0xFFA6F0DE)
val OnJadeContainer = Color(0xFF00201A)
val SecondaryContainerLight = Color(0xFFD5E7E0)
val OnSecondaryContainerLight = Color(0xFF0B2E27)
val Amber = Color(0xFF7A4E00)
val AmberContainer = Color(0xFFFFDEA8)
val OnAmberContainer = Color(0xFF4A2E00)
val Brick = Color(0xFF8E2119)
val BrickContainer = Color(0xFFFFDAD4)
val OnBrickContainer = Color(0xFF5C130E)
val BlueContainer = Color(0xFFD6E6F5)
val OnBlueContainer = Color(0xFF123B5C)

val SurfaceLight = Color(0xFFFAFDFA)
val SurfaceContainerLowLight = Color(0xFFF2F7F3)
val SurfaceContainerLight = Color(0xFFEAF2EC)
val SurfaceContainerHighLight = Color(0xFFE1EBE4)
val OutlineLight = Color(0xFFBFD0C9)
val OutlineVariantLight = Color(0xFFDCE7E1)

val StrengthWeakLight = Color(0xFFC0362C)
val StrengthMidLight = Color(0xFFC88A0E)
val StrengthStrongLight = Color(0xFF0E8A6E)

// ─────────────────────────────── karanlık tema ────────────────────────────
// Aynı jade ailesinin gece hâli. Yüzeyler nötr gri değil hafif yeşil-mavi
// çalar; böylece karanlık modda da tasarımın kimliği korunur.

val InkDark = Color(0xFFE2EFE9)
val Ink2Dark = Color(0xFFB6C7C1)
val Ink3Dark = Color(0xFF8AA098)

val JadeDark = Color(0xFF6FDDC6)
val OnJadeDark = Color(0xFF00382F)
val JadeContainerDark = Color(0xFF00504A)
val OnJadeContainerDark = Color(0xFFA6F0DE)
val SecondaryContainerDark = Color(0xFF26433C)
val OnSecondaryContainerDark = Color(0xFFCDE9E0)
val AmberDark = Color(0xFFFFB95C)
val AmberContainerDark = Color(0xFF5C3D00)
val OnAmberContainerDark = Color(0xFFFFDEA8)
val BrickDark = Color(0xFFFFB4A8)
val BrickContainerDark = Color(0xFF6E1F17)
val OnBrickContainerDark = Color(0xFFFFDAD4)
val BlueContainerDark = Color(0xFF1E3A52)
val OnBlueContainerDark = Color(0xFFC6E0F8)

val SurfaceDark = Color(0xFF0B1512)
val SurfaceContainerLowDark = Color(0xFF101A17)
val SurfaceContainerDark = Color(0xFF16211E)
val SurfaceContainerHighDark = Color(0xFF1C2825)
val OutlineDark = Color(0xFF7D918A)
val OutlineVariantDark = Color(0xFF2C3936)

val StrengthWeakDark = Color(0xFFFF8A7A)
val StrengthMidDark = Color(0xFFF2C14E)
val StrengthStrongDark = Color(0xFF3FD9B4)

/**
 * Material'ın renk şemasında karşılığı olmayan, tasarıma özgü renkler.
 *
 * Kasa listesindeki kartların rengi (`tile`), arka plandaki gradyanın üç
 * durağı ve parola gücü tonları burada. Bunları [androidx.compose.material3.ColorScheme]
 * içine sıkıştırmak yerine ayrı tutmak, dinamik renk (Material You) açıkken bile
 * güç göstergelerinin anlamını korumasını sağlıyor: "zayıf" her zaman kırmızı
 * kalıyor, duvar kâğıdı ne olursa olsun.
 */
@Immutable
data class KasaColors(
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val tile: Color,
    val tilePressed: Color,
    val card: Color,
    val strengthWeak: Color,
    val strengthMid: Color,
    val strengthStrong: Color,
    val badgeWeakBg: Color,
    val badgeWeakFg: Color,
    val badgeMidBg: Color,
    val badgeMidFg: Color,
    val badgeStrongBg: Color,
    val badgeStrongFg: Color,
    val badgeBlueBg: Color,
    val badgeBlueFg: Color,
    val gradientTopLeft: Color,
    val gradientTopRight: Color,
    val gradientBottom: Color,
    val gradientBase: Color,
    val grainAlpha: Float,
    val navScrim: Color,
    val snackbar: Color,
    val onSnackbar: Color,
    val snackbarAction: Color,
    val isDark: Boolean
)

val LightKasaColors = KasaColors(
    ink = Ink,
    ink2 = Ink2,
    ink3 = Ink3,
    tile = Color(0xEEFFFFFF),
    tilePressed = SurfaceContainerLowLight,
    card = Color(0xEEFFFFFF),
    strengthWeak = StrengthWeakLight,
    strengthMid = StrengthMidLight,
    strengthStrong = StrengthStrongLight,
    badgeWeakBg = BrickContainer,
    badgeWeakFg = OnBrickContainer,
    badgeMidBg = AmberContainer,
    badgeMidFg = OnAmberContainer,
    badgeStrongBg = JadeContainer,
    badgeStrongFg = OnJadeContainer,
    badgeBlueBg = BlueContainer,
    badgeBlueFg = OnBlueContainer,
    gradientTopLeft = Color(0xFFCFF3E7),
    gradientTopRight = Color(0xFFFFE7C6),
    gradientBottom = Color(0xFFDCEAF4),
    gradientBase = Color(0xFFFFFFFF),
    grainAlpha = 0.055f,
    navScrim = Color(0xFFF6FAF7),
    snackbar = Ink,
    onSnackbar = Color(0xFFEAF3EF),
    snackbarAction = Color(0xFF8FE3CE),
    isDark = false
)

val DarkKasaColors = KasaColors(
    ink = InkDark,
    ink2 = Ink2Dark,
    ink3 = Ink3Dark,
    tile = Color(0xFF16211E),
    tilePressed = Color(0xFF1E2B27),
    card = Color(0xFF141F1C),
    strengthWeak = StrengthWeakDark,
    strengthMid = StrengthMidDark,
    strengthStrong = StrengthStrongDark,
    badgeWeakBg = BrickContainerDark,
    badgeWeakFg = OnBrickContainerDark,
    badgeMidBg = AmberContainerDark,
    badgeMidFg = OnAmberContainerDark,
    badgeStrongBg = JadeContainerDark,
    badgeStrongFg = OnJadeContainerDark,
    badgeBlueBg = BlueContainerDark,
    badgeBlueFg = OnBlueContainerDark,
    gradientTopLeft = Color(0xFF10403A),
    gradientTopRight = Color(0xFF3A2C10),
    gradientBottom = Color(0xFF12283A),
    gradientBase = Color(0xFF0B1512),
    grainAlpha = 0.045f,
    navScrim = Color(0xFF0E1815),
    snackbar = Color(0xFFE2EFE9),
    onSnackbar = Color(0xFF0B1512),
    snackbarAction = Color(0xFF00695C),
    isDark = true
)

/** AMOLED ekranlarda gerçek siyah: piksel tamamen sönük kalır. */
val PureBlackKasaColors = DarkKasaColors.copy(
    tile = Color(0xFF0C0F0E),
    tilePressed = Color(0xFF141817),
    card = Color(0xFF0A0D0C),
    gradientTopLeft = Color(0xFF06231F),
    gradientTopRight = Color(0xFF221A08),
    gradientBottom = Color(0xFF07161F),
    gradientBase = Color(0xFF000000),
    navScrim = Color(0xFF000000),
    grainAlpha = 0.03f
)

val LocalKasaColors = staticCompositionLocalOf { LightKasaColors }
