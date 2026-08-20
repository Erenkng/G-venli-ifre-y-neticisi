package app.kasa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasa.data.model.CardBrand

/**
 * Ödeme ağının işareti.
 *
 * ### Neden artık çiziliyor
 *
 * Önceki sürüm yalnızca ağın **adını** yazıyordu ve gerekçesi şuydu: bu
 * işaretler tescilli marka. Gerekçe eksikti. Bir kartı sahibine tanıtmak için
 * ağın işaretini göstermek, markayı sahiplenmek değil; kullanıcının elindeki
 * kartla ekrandaki kaydı eşleştirmesini sağlayan tanıtıcı kullanım. Cüzdan
 * uygulamalarının tamamı bunu böyle yapıyor ve kullanıcı da kartını renkten
 * ve işaretten tanıyor, yazıdan değil.
 *
 * Yine de iki sınır bilerek korunuyor:
 *
 *  - İşaretler **yeniden çiziliyor**, markanın kendi dosyaları paketlenmiyor.
 *    Geometrik olanlar (iç içe iki daire, üç şerit) zaten geometri; kelime
 *    işareti olanlar uygulamanın kendi yazı tipiyle yazılıyor.
 *  - Hiçbir yerde ağın onayladığı ya da desteklediği ima edilmiyor; işaret
 *    yalnızca kartın kendi yüzünde, tanıtıcı boyutta duruyor.
 *
 * ### Boy neden sabit yükseklik üzerinden
 *
 * İşaretlerin en-boy oranı birbirinden çok farklı: iki daire neredeyse kare,
 * kelime işaretleri uzun. Ortak ölçü yükseklik olduğunda hepsi kartın
 * üzerinde aynı görsel ağırlıkta duruyor; ortak ölçü genişlik olsaydı iki
 * daire devleşir, kelime işaretleri okunamaz hâle gelirdi.
 */
@Composable
fun BrandMark(
    brand: CardBrand,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    /** Kelime işaretlerinin rengi. Kart yüzünde beyaz, açık zeminde mürekkep. */
    wordColor: Color = Color.White
) {
    when (brand) {
        CardBrand.MASTERCARD -> InterlockingCircles(
            left = Color(0xFFEB001B),
            right = Color(0xFFF79E1B),
            overlap = Color(0xFFFF5F00),
            modifier = modifier,
            height = height
        )

        CardBrand.MAESTRO -> InterlockingCircles(
            left = Color(0xFF0099DF),
            right = Color(0xFFED0006),
            overlap = Color(0xFF6C2C6E),
            modifier = modifier,
            height = height
        )

        CardBrand.JCB -> JcbBars(modifier, height)
        CardBrand.UNIONPAY -> UnionPayBars(modifier, height)
        CardBrand.DINERS -> DinersDisc(modifier, height)
        CardBrand.DISCOVER -> DiscoverMark(modifier, height, wordColor)

        // Kelime işaretleri. Visa eğik ve ağır, Amex kutu içinde, Troy küçük
        // harf — üçü de kendi tipografik karakteriyle tanınıyor.
        CardBrand.VISA -> WordMark("VISA", modifier, height, wordColor, italic = true, tracking = 1.2f)
        CardBrand.AMEX -> AmexBox(modifier, height)
        CardBrand.TROY -> WordMark("troy", modifier, height, wordColor, tracking = 0.4f)
        CardBrand.UNKNOWN -> WordMark(
            brand.displayName.uppercase(),
            modifier,
            height,
            wordColor.copy(alpha = 0.75f),
            tracking = 1.6f,
            weight = FontWeight.SemiBold
        )
    }
}

/**
 * İç içe geçmiş iki daire (Mastercard, Maestro).
 *
 * Kesişim ayrı bir renkle dolduruluyor ve bu kesişim gerçek bir yol
 * kesişimi ([PathOperation.Intersect]) — üstteki daireyi yarı saydam çizmek
 * gibi bir yaklaşıklık, iki dairenin kendi renklerini de soldururdu.
 */
@Composable
private fun InterlockingCircles(
    left: Color,
    right: Color,
    overlap: Color,
    modifier: Modifier,
    height: Dp
) {
    Canvas(modifier.size(width = height * 1.55f, height = height)) {
        val radius = size.height / 2f
        val leftCenter = Offset(radius * 1.28f, radius)
        val rightCenter = Offset(size.width - radius * 1.28f, radius)

        drawCircle(left, radius, leftCenter)
        drawCircle(right, radius, rightCenter)

        val lens = Path().apply {
            val a = Path().apply {
                addOval(Rect(leftCenter - Offset(radius, radius), Size(radius * 2, radius * 2)))
            }
            val b = Path().apply {
                addOval(Rect(rightCenter - Offset(radius, radius), Size(radius * 2, radius * 2)))
            }
            op(a, b, PathOperation.Intersect)
        }
        drawPath(lens, overlap)
    }
}

/**
 * JCB: üç dikey şerit.
 *
 * Gerçek işaretteki harfler bu boyutta zaten okunmuyor; tanınan şey üç rengin
 * sırası. Şeritler yuvarlatılmış, aralarında kartın kendi zeminini gösteren
 * ince boşluklar var.
 */
@Composable
private fun JcbBars(modifier: Modifier, height: Dp) {
    val colors = listOf(Color(0xFF0E4C96), Color(0xFFBE0028), Color(0xFF007B40))
    Canvas(modifier.size(width = height * 1.34f, height = height)) {
        val gap = size.width * 0.045f
        val barWidth = (size.width - gap * 2) / 3f
        colors.forEachIndexed { index, color ->
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth * 0.28f)
            )
        }
    }
}

/**
 * UnionPay: üç eğik dilim.
 *
 * Şeritler paralelkenar; dikey olsalardı JCB'den ayırt edilemezlerdi ve
 * kullanıcı iki farklı ağı aynı işaret sanardı.
 */
@Composable
private fun UnionPayBars(modifier: Modifier, height: Dp) {
    val colors = listOf(Color(0xFFE21836), Color(0xFF00447C), Color(0xFF007B40))
    Canvas(modifier.size(width = height * 1.4f, height = height)) {
        val slant = size.height * 0.22f
        val gap = size.width * 0.04f
        val barWidth = (size.width - gap * 2 - slant) / 3f
        colors.forEachIndexed { index, color ->
            val x = index * (barWidth + gap)
            drawPath(
                Path().apply {
                    moveTo(x + slant, 0f)
                    lineTo(x + slant + barWidth, 0f)
                    lineTo(x + barWidth, size.height)
                    lineTo(x, size.height)
                    close()
                },
                color
            )
        }
    }
}

/** Diners Club: mavi disk, içinde beyaz halka. */
@Composable
private fun DinersDisc(modifier: Modifier, height: Dp) {
    Canvas(modifier.size(height)) {
        val radius = size.minDimension / 2f
        val center = Offset(radius, radius)
        drawCircle(Color(0xFF0079BE), radius, center)
        drawCircle(Color.White, radius * 0.62f, center)
        drawCircle(Color(0xFF0079BE), radius * 0.34f, center)
    }
}

/** Discover: turuncu küre ve yanında adı. */
@Composable
private fun DiscoverMark(modifier: Modifier, height: Dp, wordColor: Color) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(height * 0.62f)) {
                drawCircle(Color(0xFFF48120), size.minDimension / 2f)
            }
            Text(
                "DISCOVER",
                color = wordColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                    fontSize = 9.sp
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** American Express: adının etrafındaki mavi kutu. */
@Composable
private fun AmexBox(modifier: Modifier, height: Dp) {
    Box(
        modifier
            .height(height)
            .width(height * 1.5f)
            .background(Color(0xFF006FCF), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "AMEX",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun WordMark(
    text: String,
    modifier: Modifier,
    height: Dp,
    color: Color,
    italic: Boolean = false,
    tracking: Float = 1f,
    weight: FontWeight = FontWeight.Black
) {
    Box(modifier.height(height), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = weight,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                letterSpacing = tracking.sp,
                fontSize = 17.sp
            )
        )
    }
}
