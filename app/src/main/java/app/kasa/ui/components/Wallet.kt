package app.kasa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasa.data.model.CardBrand
import app.kasa.data.model.VaultItem
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * Kart kaydının cüzdandaki gibi görünen yüzü.
 *
 * ### Neden liste satırı değil de kart
 *
 * Bir ödeme kartını metin satırı olarak göstermek, kullanıcıyı adını okuyup
 * hangi kart olduğunu hatırlamaya zorluyor. Oysa insanlar kartlarını **renk
 * ve şekilden** tanıyor; cüzdanı açtığında numarayı okumuyor, mavi olanı
 * çekiyor. Kart yüzü bu tanımayı ekrana taşıyor.
 *
 * ### Numara neden maskeli
 *
 * Açık numara ekranda dururken omzunun üstünden bakan biri için kart doğrudan
 * kullanılabilir hâle geliyor: numara, son kullanma ve ad aynı karede. Bu
 * yüzden varsayılan maskeli ve açmak ayrı bir dokunuş — "gördüm" ile
 * "kullanılabilir hâle getirdim" iki farklı karar.
 *
 * ### Kopyalama düğmeleri kartın üzerinde
 *
 * Kart bilgisi neredeyse her zaman **yapıştırılmak** için açılıyor: ödeme
 * formuna numara, sonra son kullanma, sonra güvenlik kodu. Önceden bunların
 * her biri kartın altındaki ayrı alan bloklarındaydı ve kullanıcı kartı
 * görüp aşağı kaydırmak zorundaydı — üç değer için üç kez.
 *
 * Düğmeler artık değerin kendi yanında. Kart bir tanıtım resmi değil, üzerinde
 * çalışılan yüzey.
 *
 * Düğmeler yalnızca [onCopy] verildiğinde çiziliyor: liste satırındaki ve
 * düzenleyicideki önizlemede kopyalanacak bir şey yok, orada kart yalnızca
 * bakılan bir nesne.
 *
 * @param revealed numara açık mı. Kart yüzü kendi durumunu tutmuyor; ayrıntı
 *        sayfasındaki tek "göster" düğmesiyle aynı durumu paylaşıyor ki
 *        kullanıcı iki ayrı yerde iki farklı görünüm bulmasın.
 * @param onCopy kopyalanacak değeri alan geri çağırım; `null` ise düğmeler yok
 */
@Composable
fun CardFace(
    item: VaultItem,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null
) {
    val brand = CardBrand.detect(item.cardNumber)
    val number = if (revealed) CardBrand.group(item.cardNumber, brand)
    else CardBrand.mask(item.cardNumber, brand)
    // Gerçek bir kartın camdan yansıması gibi: telefon çevrildikçe ışık
    // yüzeyde geziniyor. Deneysel efektler kapalıysa hiç hesaplanmıyor.
    val tilt = rememberDeviceTilt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Gerçek bir ödeme kartının oranı (ISO/IEC 7810 ID-1). Uydurma bir
            // oran, tanıdıklık hissini bozan ilk şey olurdu.
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(
                Brush.linearGradient(
                    listOf(Color(brand.startColor), Color(brand.endColor))
                )
            )
            .tiltRim(tilt, shape = RoundedCornerShape(KasaRadius.l))
    ) {
        CardSheen(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EmvChip(Modifier.size(width = 40.dp, height = 30.dp))
                BrandMark(brand = brand, height = 26.dp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = number.ifBlank { "•••• •••• •••• ••••" },
                    color = Color.White,
                    style = KasaTheme.text.mono.copy(fontSize = 19.sp, letterSpacing = 1.4.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onCopy != null && item.cardNumber.isNotBlank()) {
                    OnCardCopyButton { onCopy(item.cardNumber.filter { it.isDigit() }) }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    CardCaption(stringOrDash(item.cardHolder).uppercase())
                }
                Spacer(Modifier.width(14.dp))
                if (onCopy != null && item.cardCvv.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "CVV",
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Güvenlik kodu kartın yüzünde hiçbir zaman açık
                            // yazmıyor: numarayla birlikte görünmesi, kartı tek
                            // bir ekran görüntüsüyle kullanılabilir yapardı.
                            CardCaption("•".repeat(item.cardCvv.length))
                            OnCardCopyButton { onCopy(item.cardCvv) }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "AY/YIL",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardCaption(stringOrDash(formatExpiry(item.cardExpiry)))
                        if (onCopy != null && item.cardExpiry.isNotBlank()) {
                            OnCardCopyButton { onCopy(formatExpiry(item.cardExpiry)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartın üzerindeki kopyalama düğmesi.
 *
 * Zemini kartın kendi degradesi olduğu için düğme yarı saydam beyaz bir daire:
 * her ağ renginde okunuyor ve hiçbirinde kartın rengiyle çakışmıyor. Dolu bir
 * yüzey kullanmak, dokuz farklı kart rengi için dokuz farklı düğme rengi
 * seçmeyi gerektirirdi.
 */
@Composable
private fun OnCardCopyButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(Color.White.copy(alpha = 0.18f))
            .clickableNoRipple(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun CardCaption(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.92f),
        style = KasaTheme.text.mono.copy(fontSize = 13.sp, letterSpacing = 1.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun stringOrDash(value: String): String = value.ifBlank { "—" }

/**
 * Liste satırındaki minyatür kart.
 *
 * Rozetin yerini alıyor: baş harf yerine kartın kendi rengi duruyor, böylece
 * listede hangi satırın hangi kart olduğu okumadan anlaşılıyor. Son dört hane
 * de burada; bir listede iki kartı ayırmaya yeten tek bilgi genellikle bu.
 */
@Composable
fun CardThumb(
    item: VaultItem,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val brand = CardBrand.detect(item.cardNumber)
    val last4 = item.cardNumber.filter { it.isDigit() }.takeLast(4)

    Box(
        modifier = modifier
            .size(width = size, height = size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(brand.startColor), Color(brand.endColor))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        CardSheen(Modifier.fillMaxSize())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            EmvChip(Modifier.size(width = 17.dp, height = 13.dp))
            if (last4.isNotEmpty()) {
                Text(
                    text = last4,
                    color = Color.White.copy(alpha = 0.95f),
                    style = KasaTheme.text.mono.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Kart yüzeyindeki ışık kırılması.
 *
 * Düz bir degrade plastik hissi vermiyor; köşeden geçen bu ikinci, çok zayıf
 * beyaz eğim yüzeye hacim veriyor. Şeffaflık bilerek düşük — fark edilmesi
 * gereken bir efekt değil, yokluğu fark edilen bir doku.
 */
@Composable
private fun CardSheen(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.02f),
                    Color.Black.copy(alpha = 0.10f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            size = Size(size.width, size.height)
        )
    }
}

/**
 * EMV yongası.
 *
 * Gerçek bir yonga deseni değil, onun tanınabilir soyutlaması: yuvarlatılmış
 * altın dikdörtgen ve içindeki temas hatları. Hiçbir markaya ait olmadığı için
 * serbestçe çizilebiliyor ve kartı "kart" yapan görsel ipucunu tek başına
 * taşıyor.
 */
@Composable
fun EmvChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE8D9A8), Color(0xFFB9A163))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val line = Color(0x66000000)
            val stroke = size.height * 0.055f
            // Yatay temas hatları
            listOf(0.32f, 0.68f).forEach { fraction ->
                drawRect(
                    color = line,
                    topLeft = Offset(0f, size.height * fraction - stroke / 2f),
                    size = Size(size.width, stroke)
                )
            }
            // Ortadaki dikey ayrım
            drawRect(
                color = line,
                topLeft = Offset(size.width * 0.5f - stroke / 2f, 0f),
                size = Size(stroke, size.height)
            )
            // Orta pencere, hatları kesiyor
            val insetX = size.width * 0.26f
            val insetY = size.height * 0.26f
            drawRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(insetX, insetY),
                size = Size(size.width - insetX * 2, size.height - insetY * 2)
            )
        }
    }
}

/**
 * Kart dışındaki türler için renkli başlık şeridi.
 *
 * Kart yüzünün karşılığı: kayıt açıldığında hangi türde olduğunu okumadan
 * anlatan bir alan. Simge büyük, zemin türün kendi rengi.
 */
@Composable
fun CategoryHeroBand(
    background: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(
                Brush.linearGradient(
                    listOf(
                        background.copy(alpha = 0.95f),
                        background.copy(alpha = 0.62f)
                    )
                )
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}
