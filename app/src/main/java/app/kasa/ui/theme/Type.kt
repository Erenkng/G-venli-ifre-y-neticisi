package app.kasa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kasa.R

/**
 * Roboto Flex, değişken eksenleriyle (wght, wdth, opsz, GRAD) kullanılıyor.
 *
 * Tasarımdaki başlıklar yalnızca kalın değil, aynı zamanda **geniş** (`wdth 132`)
 * ve yüksek dereceli (`GRAD 40`); bu, sabit ağırlıklı bir yazı tipiyle
 * taklit edilemeyen bir doku veriyor. Tek bir ttf dosyası tüm ağırlıkları
 * karşıladığı için APK'ya da yalnızca bir dosya giriyor.
 */
@OptIn(ExperimentalTextApi::class)
private fun flex(
    weight: Int,
    width: Float = 100f,
    grade: Int = 0
) = Font(
    resId = R.font.robotoflex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.grade(grade)
    )
)

val RobotoFlex = FontFamily(
    flex(300),
    flex(400),
    flex(500),
    flex(560),
    flex(600),
    flex(620),
    flex(700),
    flex(780)
)

val RobotoMono = FontFamily(
    Font(R.font.robotomono, FontWeight.Normal),
    Font(R.font.robotomono, FontWeight.Medium),
    Font(R.font.robotomono, FontWeight.SemiBold),
    Font(R.font.robotomono, FontWeight.Bold)
)

/** Ekranlarda kullanılan dev başlık: 46sp, dar satır aralığı, negatif izleme. */
@OptIn(ExperimentalTextApi::class)
val HeroTextStyle = TextStyle(
    fontFamily = FontFamily(flex(780, width = 132f, grade = 40)),
    fontSize = 46.sp,
    lineHeight = 43.sp,
    letterSpacing = (-1.6).sp
)

@OptIn(ExperimentalTextApi::class)
val SheetTitleStyle = TextStyle(
    fontFamily = FontFamily(flex(720, width = 118f)),
    fontSize = 25.sp,
    lineHeight = 29.sp,
    letterSpacing = (-0.7).sp
)

@OptIn(ExperimentalTextApi::class)
val ScoreTextStyle = TextStyle(
    fontFamily = FontFamily(flex(760, width = 128f)),
    fontSize = 40.sp,
    lineHeight = 40.sp,
    letterSpacing = (-1.5).sp
)

/** Bölüm etiketleri: küçük, aralıklı, tümü büyük harf. */
@OptIn(ExperimentalTextApi::class)
val SectionLabelStyle = TextStyle(
    fontFamily = FontFamily(flex(700, width = 108f)),
    fontSize = 12.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.4.sp
)

@OptIn(ExperimentalTextApi::class)
val FieldLabelStyle = TextStyle(
    fontFamily = FontFamily(flex(700, width = 104f)),
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.2.sp
)

@OptIn(ExperimentalTextApi::class)
val TileNameStyle = TextStyle(
    fontFamily = FontFamily(flex(620, width = 102f)),
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.1).sp
)

@OptIn(ExperimentalTextApi::class)
val NavLabelStyle = TextStyle(
    fontFamily = FontFamily(flex(620, width = 104f)),
    fontSize = 11.5.sp,
    lineHeight = 14.sp
)

@OptIn(ExperimentalTextApi::class)
val ButtonTextStyle = TextStyle(
    fontFamily = FontFamily(flex(640, width = 104f)),
    fontSize = 15.sp,
    lineHeight = 18.sp
)

val MonoStyle = TextStyle(
    fontFamily = RobotoMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    letterSpacing = 0.4.sp
)

val GeneratedPasswordStyle = TextStyle(
    fontFamily = RobotoMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.6.sp
)

val KasaTypography = Typography(
    displayLarge = HeroTextStyle,
    displayMedium = SheetTitleStyle,
    headlineSmall = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(660), fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(660), fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleMedium = TileNameStyle,
    titleSmall = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(620), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(400), fontSize = 15.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(400), fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(400), fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = ButtonTextStyle,
    labelMedium = TextStyle(fontFamily = RobotoFlex, fontWeight = FontWeight(600), fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = NavLabelStyle
)

/** Tasarıma özgü, Material'da karşılığı olmayan yazı biçimleri. */
@Immutable
data class KasaTextStyles(
    val hero: TextStyle = HeroTextStyle,
    val sheetTitle: TextStyle = SheetTitleStyle,
    val score: TextStyle = ScoreTextStyle,
    val sectionLabel: TextStyle = SectionLabelStyle,
    val fieldLabel: TextStyle = FieldLabelStyle,
    val tileName: TextStyle = TileNameStyle,
    val navLabel: TextStyle = NavLabelStyle,
    val mono: TextStyle = MonoStyle,
    val generatedPassword: TextStyle = GeneratedPasswordStyle
)

val LocalKasaTextStyles = staticCompositionLocalOf { KasaTextStyles() }
