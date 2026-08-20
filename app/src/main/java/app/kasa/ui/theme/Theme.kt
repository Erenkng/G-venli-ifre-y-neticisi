package app.kasa.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.kasa.data.GradientTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.kasa.data.ThemeMode

/**
 * Tasarımın köşe yarıçapları. Material'ın varsayılanlarından belirgin biçimde
 * daha yuvarlak: kasa kartları 26dp, kartlar 32dp, düğmeler tam yuvarlak.
 * Basılı tutunca köşelerin değişmesi (shape morph) bu değerler arasında olur.
 */
val KasaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object KasaRadius {
    val xs = 8.dp
    val s = 14.dp
    val m = 20.dp
    val l = 26.dp
    val xl = 32.dp
    val full = 999.dp
}

private val LightScheme = lightColorScheme(
    primary = Jade,
    onPrimary = OnJade,
    primaryContainer = JadeContainer,
    onPrimaryContainer = OnJadeContainer,
    secondary = Jade,
    onSecondary = OnJade,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = Brick,
    onError = Color.White,
    errorContainer = BrickContainer,
    onErrorContainer = OnBrickContainer,
    background = SurfaceLight,
    onBackground = Ink,
    surface = SurfaceLight,
    onSurface = Ink,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = Ink2,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = Ink,
    inverseOnSurface = SurfaceLight,
    inversePrimary = JadeContainer
)

private val DarkScheme = darkColorScheme(
    primary = JadeDark,
    onPrimary = OnJadeDark,
    primaryContainer = JadeContainerDark,
    onPrimaryContainer = OnJadeContainerDark,
    secondary = JadeDark,
    onSecondary = OnJadeDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = AmberDark,
    onTertiary = Color(0xFF3D2800),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = BrickDark,
    onError = Color(0xFF5C130E),
    errorContainer = BrickContainerDark,
    onErrorContainer = OnBrickContainerDark,
    background = SurfaceDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = Ink2Dark,
    surfaceContainerLowest = Color(0xFF060D0B),
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = Color(0xFF23302C),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InkDark,
    inverseOnSurface = SurfaceDark,
    inversePrimary = Jade
)

private val PureBlackScheme = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0D0C),
    surfaceContainer = Color(0xFF0C0F0E),
    surfaceContainerHigh = Color(0xFF121716),
    surfaceContainerHighest = Color(0xFF171D1B)
)

@Composable
fun KasaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    pureBlack: Boolean = false,
    gradientTheme: GradientTheme = GradientTheme.JADE,
    gradientFollowsTime: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    // Duvar kâğıdı renkleri Android 12'den beri var; minSdk 36 olduğu için
    // burada sürüm kontrolüne gerek yok.
    val colorScheme = when {
        dynamicColor && dark ->
            dynamicDarkColorScheme(context).let {
                if (pureBlack) it.copy(background = Color.Black, surface = Color.Black) else it
            }
        dynamicColor -> dynamicLightColorScheme(context)
        dark && pureBlack -> PureBlackScheme
        dark -> DarkScheme
        else -> LightScheme
    }

    val baseColors = when {
        dark && pureBlack -> PureBlackKasaColors
        dark -> DarkKasaColors
        else -> LightKasaColors
    }

    // Zemin gradyanı seçilen aileden ve saatten geliyor; renk şemasının geri
    // kalanı (mürekkep, rozet renkleri) dokunulmadan kalıyor. Tam siyah
    // kipinde gradyan hiç değiştirilmiyor: o kip zaten "hiç ışık olmasın"
    // demek ve AMOLED'de piksel söndürmenin bütün kazancı oradan geliyor.
    val stops = rememberGradientStops(gradientTheme, dark, gradientFollowsTime)
    val kasaColors = if (dark && pureBlack) baseColors else baseColors.copy(
        gradientTopLeft = stops.topLeft,
        gradientTopRight = stops.topRight,
        gradientBottom = stops.bottom,
        gradientBase = stops.base
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalKasaColors provides kasaColors,
        LocalKasaTextStyles provides KasaTextStyles(),
        // Hareket ayarı buradan aşağıya iniyor: her bileşen kendi başına
        // sormak yerine KasaMotion üzerinden hazır cevabı alıyor ve sistemde
        // animasyon kapalıysa bütün geçişler kendiliğinden anlık oluyor.
        LocalReducedMotion provides rememberReducedMotion()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KasaTypography,
            shapes = KasaShapes,
            content = content
        )
    }
}

/** Tasarıma özgü renklere kısa erişim: `KasaTheme.colors.strengthWeak` */
object KasaTheme {
    val colors: KasaColors
        @Composable get() = LocalKasaColors.current

    val text: KasaTextStyles
        @Composable get() = LocalKasaTextStyles.current
}
