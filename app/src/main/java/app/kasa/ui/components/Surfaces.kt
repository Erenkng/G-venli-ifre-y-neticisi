package app.kasa.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasa.core.util.PasswordStrength
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/** Gruplanmış listede bir öğenin konumu; köşe yarıçapını bu belirler. */
enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }

fun groupPositionOf(index: Int, size: Int): GroupPosition = when {
    size == 1 -> GroupPosition.ONLY
    index == 0 -> GroupPosition.FIRST
    index == size - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

private fun groupShape(position: GroupPosition, tight: Dp, loose: Dp): Shape = when (position) {
    GroupPosition.ONLY -> RoundedCornerShape(loose)
    GroupPosition.FIRST -> RoundedCornerShape(topStart = loose, topEnd = loose, bottomStart = tight, bottomEnd = tight)
    GroupPosition.LAST -> RoundedCornerShape(topStart = tight, topEnd = tight, bottomStart = loose, bottomEnd = loose)
    GroupPosition.MIDDLE -> RoundedCornerShape(tight)
}

/**
 * Gruplanmış liste satırı.
 *
 * Grubun ilk ve son öğesinin dış köşeleri yuvarlak, iç köşeleri keskindir;
 * basılınca satır küçülür ve tüm köşeleri yuvarlanır — satır gruptan
 * "kopup" öne çıkmış gibi olur. Tasarımın en çok tekrarlanan hareketi bu.
 */
@Composable
fun KasaTile(
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Basılı tutma.
     *
     * Bir kaydı açmadan işlem yapmak — parolayı kopyalamak, sık kullanılana
     * eklemek, silmek — listeden çıkmayı gerektiriyordu. Basılı tutma bu işleri
     * satırın olduğu yerde açıyor; öğrenilmesi gereken bir hareket değil,
     * zaten denenen bir hareket.
     */
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.965f else 1f, KasaMotion.small(), label = "tileScale")
    // Yarıçap animasyonları [animatedCorner] üzerinden: yaylı hareket hedefi
    // aşıyor ve gölgenin kullandığı platform Outline'ı negatif yarıçapta
    // istisna atıyor.
    val loose = animatedCorner(KasaRadius.l, label = "tileLoose")
    val tight = animatedCorner(if (pressed) KasaRadius.l else KasaRadius.xs, label = "tileTight")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(scale)
            // ── cam levha kalktı ─────────────────────────────────────────
            //
            // Burada `glassSurface` vardı: her satır, grup köşeleriyle
            // birbirine eklenen yarı saydam bir levhaydı. Kasa listesi
            // dizine dönüşürken o levhanın iki bedeli görüldü —
            // arkadaki gradyanı örtüyordu ve boşluğun yapabileceği bir
            // ayırma işini yüzey harcayarak yapıyordu. Aynı gerekçe bu
            // ortak satır için de geçerli: ayarlar, üretici ve güvenlik
            // listeleri de aynı dili konuşmalı.
            //
            // Geriye kalan tek yüzey basınç tonu ve o yalnızca parmak
            // basılıyken var. Köşe grubu (`position`) duruyor çünkü o tonun
            // şeklini hâlâ o belirliyor: grubun ortasındaki satır keskin,
            // uçları yuvarlak — dolayısıyla dokunulan satır grubun neresinde
            // olduğunu söylemeye devam ediyor.
            .background(
                color = KasaTheme.colors.tilePressed.copy(alpha = if (pressed) 0.55f else 0f),
                shape = groupShape(position, tight, loose)
            )
            // Dokunulan yere en yakın kenarın parlaması. Yüzeyin içine
            // yayılan bir ışık, satırdaki adın ve alt satırın kontrastını
            // düşürüyordu; gerekçesi pressRim üzerinde yazılı.
            // Şekil, yüzeyin **kendi** şekli — sabit bir yarıçap değil. Sabit
            // yarıçapla çizilen çerçeve, grubun ortasındaki keskin köşeli
            // satırın üzerine yuvarlak bir dikdörtgen bindiriyordu; iki şekil
            // örtüşmediği için aradaki fark iç dolguda keskin köşeli bir
            // dikdörtgen olarak görünüyordu.
            .pressRim(
                shape = groupShape(position, tight, loose),
                color = MaterialTheme.colorScheme.primary
            )
            // Uzun basış eşiğine doğru dolan yay. Yalnızca uzun basışı olan
            // satırlarda: olmayan bir beklemeyi göstermek, kullanıcıya
            // olmayan bir şeyi vaat etmek olurdu.
            .then(
                if (onLongClick == null) Modifier
                else Modifier.holdCharge(
                    interactionSource = interaction,
                    shape = groupShape(position, tight, loose),
                    color = MaterialTheme.colorScheme.primary
                )
            )
            .combinedClickableNoRipple(
                interactionSource = interaction,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
    }
}

/** Kayıt rozeti: baş harf ya da simge. Basılınca kareden daireye döner. */
/**
 * Kasa listesinin satırı.
 *
 * ### Dizin denemesi ve geri dönüş
 *
 * Bir tur önce burası elle yazılmış, kutusuz, 60dp yüksekliğinde bir
 * "dizin satırı"ydı: ayırmayı tipografi ve ritim yapıyordu. Tez tutarlıydı,
 * cihazda kötü çıktı. Sebebi de öğretici — kutu yalnızca süs değil, ölçü
 * veriyor: satırın nerede başlayıp bittiğini, dokunma alanının nereye kadar
 * uzandığını ve metnin hangi hizada durduğunu kutu söylüyordu. Kaldırılınca
 * geriye hizası belirsiz, dokunma alanı tahmine kalmış satırlar kaldı.
 *
 * Şimdiki hâli Material 3'ün kendi [ListItem]'ı. Elle yazılmış bir satır
 * yerine standart bileşeni kullanmanın karşılığı somut: iki satırlık liste
 * öğesi için doğru yükseklik (72dp), baş/son yuva hizaları, metin stilleri
 * ve erişilebilirlik davranışı bileşenin kendisinden geliyor — hepsi elle
 * tutulduğunda yanlış tutulan şeyler.
 *
 * Kap saydam bırakılıyor: arkadaki zemin görünmeye devam ediyor, yani dizin
 * denemesinden kalan **iyi** taraf korunuyor. Dokunulan satır yuvarlatılmış
 * bir tonla işaretleniyor.
 *
 * ### Güç işareti neden sol marjdan sağ uca taşındı
 *
 * Burada baş yuvanın içinde 3dp'lik dikey bir tırnak vardı ve gerekçesi
 * "dikey taramada marj tek bir sütun oluşturuyor" idi. Doğruydu ama bedeli
 * vardı: tırnak, baş yuvayı 7dp daraltıp rozeti sağa itiyordu, yani listenin
 * sol hizası güç göstergesine kiralanmıştı. Rozet 56dp'ye çıkınca bu
 * kiranın bedeli görünür oldu.
 *
 * Sağ uçtaki nokta aynı sütunu kuruyor — sağ marj da bir sütun — ve kimseyi
 * itmiyor. Gücü olmayan türlerde (not, kart) hiç çizilmiyor: nötr bir işaret
 * koymak, ölçülmemiş bir şeyi ölçülmüş gibi göstermek olurdu.
 */
@Composable
fun KasaIndexRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /**
     * Satırın en az yüksekliği.
     *
     * [ListItem] kendi başına iki satırlık öğe için 72dp veriyor; 56dp'lik
     * bir baş yuvayla o yükseklik rozetin üstüne ve altına 8'er dp bırakıyor
     * ve satır sıkışık duruyor. Ferah yerleşimde bu değer yükseltiliyor,
     * sıkışıkta düşürülüyor.
     */
    minHeight: Dp = 84.dp,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    supporting: @Composable (() -> Unit)? = null,
    headline: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = KasaMotion.small(),
        label = "rowWash"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.m))
            .background(KasaTheme.colors.tilePressed.copy(alpha = 0.55f * wash))
            .combinedClickableNoRipple(
                interactionSource = interaction,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ListItem(
            modifier = Modifier.heightIn(min = minHeight),
            headlineContent = headline,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailing,
            // Kap saydam: zemin satırın arkasından geçmeye devam ediyor ve
            // basınç tonu onun üstüne biniyor.
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun KasaBadge(
    text: String? = null,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 16.dp,
    content: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content()
        else Text(
            text = text.orEmpty(),
            color = foreground,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

/** Parola gücü noktası: listenin sağ ucundaki küçük renkli işaret. */
/**
 * Gücü tek bir noktayla anlatan işaret.
 *
 * [tone] `null` ise güç **ölçülemiyor** demek — notta parola yok, kartta
 * ölçülecek bir sır yok. O durumda dolu bir nokta değil, içi boş bir halka
 * çiziliyor: "burada bir şey var ama ölçülmedi". Dolu noktanın üç rengi de
 * bir yargı bildiriyor ve yargı bildirmeyecek yere yeşil koymak, kullanıcının
 * yeşile duyduğu güveni boşaltıyordu.
 */
@Composable
fun StrengthDot(tone: PasswordStrength.Tone?, modifier: Modifier = Modifier) {
    val colors = KasaTheme.colors
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(KasaRadius.full))
            .background(colors.tile),
        contentAlignment = Alignment.Center
    ) {
        if (tone == null) {
            // Halka ölçülemeyeni gösteriyor. Kalınlığı noktanın yarıçapından
            // ince: göz onu "sönük bir nokta" değil, "boş bir yer" olarak
            // okuyor.
            Box(
                Modifier
                    .size(10.dp)
                    .border(1.4.dp, colors.ink3.copy(alpha = 0.55f), RoundedCornerShape(KasaRadius.full))
            )
            return@Box
        }
        val color = when (tone) {
            PasswordStrength.Tone.WEAK -> colors.strengthWeak
            PasswordStrength.Tone.MID -> colors.strengthMid
            PasswordStrength.Tone.STRONG -> colors.strengthStrong
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(KasaRadius.full))
                .background(color)
        )
    }
}

/** Bölüm etiketi: büyük harf başlık + isteğe bağlı sayaç + ince ayırıcı çizgi. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    Row(
        // Yatay dolgu yok: başlık listenin sol kenarına dayanıyor ve satır
        // adlarının solunda, marjda asılı duruyor. Dizinde bölüm işaretinin
        // yeri burası — içerikle aynı hizaya girerse bölüm başlığı olmaktan
        // çıkıp bir satır gibi okunuyor.
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            // Büyük harfe çevirme **etkin yerelle** yapılıyor, sabit Türkçeyle
            // değil.
            //
            // Burada `Locale("tr","TR")` gömülüydü ve Türkçe metin için
            // doğruydu: "i" → "İ". Ama bu bileşen çevrilmiş metin de alıyor
            // ve İngilizcede aynı kural yanlış sonuç veriyordu —
            // "Security" → "SECURİTY", "Settings" → "SETTİNGS". Uygulamanın
            // dili `localeConfig` ile ayarlandığı için varsayılan yerel
            // zaten doğru olanı: Türkçede "İ", İngilizcede "I".
            text = text.uppercase(),
            style = KasaTheme.text.sectionLabel,
            color = KasaTheme.colors.ink3
        )
        if (count != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$count", style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink2)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/**
 * Yüzeyi kalkık kart. [tinted] açıkken tasarımın jade-kehribar geçişini alır.
 *

 * ### Saydam yüzeyler neden gölge atmıyor
 *
 * Bu kart bir zamanlar `Modifier.shadow(2.dp, shape, clip = false)` taşıyordu
 * ve o gölge, kartın **içinde** keskin köşeli bir dikdörtgen olarak
 * görünüyordu — açık temada apaçık, koyu temada zar zor.
 *
 * Sebebi platformun gölge geometrisi. Android yuvarlak dikdörtgenin gölgesini
 * iki parçadan kuruyor: dış hattı köşe yarıçapı kadar içeri çekerek elde
 * ettiği tam koyu çekirdek (umbra) ve oradan kenara kadar açılan yumuşak
 * geçiş (penumbra). Yuvarlak bir dikdörtgeni kendi köşe yarıçapı kadar içeri
 * çekince köşelerin yuvarlaklığı sıfıra iniyor: umbra, kenarlardan yarıçap
 * kadar içeride duran **keskin köşeli** bir dikdörtgen oluyor.
 *
 * Opak bir yüzeyde bunun bir önemi yok, çünkü gölge yüzeyin altında kalıyor.
 * Bu yüzey saydam ([SURFACE_OPACITY]) ve gölge onun **içinden** görünüyor:
 * kenar boyunca yarıçap genişliğinde biraz daha koyu bir bant, ortada keskin
 * köşeli bir sınır. Ölçülen bant genişliği kartın köşe yarıçapının tam
 * kendisiydi.
 *
 * `clip = true` yapmak da çözmüyor: kırpma yüzeyin **içeriğini** kısıtlıyor,
 * altına çizilen gölgeyi değil.
 *
 * Derinlik zaten gölgeden gelmiyordu. Bu tasarım dilinde derinliği taşıyan
 * şey yüzeyin geçirgenliği ve üst kenarındaki ışık: arkadaki zemin
 * görünüyor, kenar yukarıdan aydınlanıyor. Gölge, o dilin üstüne binen ikinci
 * ve yanlış bir eğretilemeydi.
 */
@Composable
fun KasaCard(
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = KasaTheme.colors
    val tilt = rememberDeviceTiltState()
    val background = if (tinted) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            0f to (if (colors.isDark) Color(0xFF13302B) else Color(0xFFE9F8F2)),
            0.55f to colors.card,
            1f to (if (colors.isDark) Color(0xFF2E2413) else Color(0xFFFFF6E6))
        )
    } else {
        androidx.compose.ui.graphics.SolidColor(colors.card)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.xl))
            // Kart kendi degradesini taşıyor (jade–kehribar geçişi), bu yüzden
            // glassSurface'in düz tonu yerine aynı degrade geçirgen çiziliyor.
            .background(background, alpha = SURFACE_OPACITY)
            // Cilalı bir yüzeyin kenarından arada bir geçen yansıma.
            .shimmerRim(
                shape = RoundedCornerShape(KasaRadius.xl),
                color = if (colors.isDark) Color.White else colors.ink,
                alpha = if (colors.isDark) 0.30f else 0.12f
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.90f),
                    0.5f to Color.White.copy(alpha = if (colors.isDark) 0.05f else 0.34f),
                    1f to Color.White.copy(alpha = if (colors.isDark) 0.02f else 0.10f)
                ),
                RoundedCornerShape(KasaRadius.xl)
            )
            .padding(padding)
    ) {
        // İçerik camın **altında** duruyor: cihaz döndükçe yüzeyden biraz
        // geride kalıyor ve aradaki fark kalınlık olarak okunuyor.
        // Gerekçesi tiltDepth üzerinde yazılı.
        Column(Modifier.tiltDepth(tilt), content = content)
    }
}

/** "Son kullanılan" şeridindeki küçük kart. */
@Composable
fun RecentCard(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Rozet dışarıdan veriliyor: son kullanılanlar şeridinde de kartlar kart
     * yüzüyle, diğer türler kendi simgeleriyle görünsün diye. Rozeti burada
     * kurmak, kart görselini bu dosyaya taşımak anlamına gelirdi.
     */
    badge: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.94f else 1f, KasaMotion.small(), label = "recentScale")
    val radius = animatedCorner(if (pressed) KasaRadius.l else KasaRadius.m, label = "recentRadius")

    Column(
        modifier = modifier
            .width(104.dp)
            .pressScale(scale)
            // Gölge yok: saydam bir yüzeyde platform gölgesinin çekirdeği
            // kartın içinden keskin köşeli bir dikdörtgen olarak görünüyor.
            // Gerekçesi [KasaCard] üzerinde yazılı.
            // Kenar ışığı kısık: 104dp'lik bir kartta tam güçteki ışık
            // yüzeyin görünür bir kısmını kaplıyor ve levha değil çerçeve
            // gibi duruyor.
            .glassSurface(RoundedCornerShape(radius), KasaTheme.colors.tile, edge = 0.65f)
            .clickableNoRipple(interactionSource = interaction, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
    ) {
        badge()
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = KasaTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Boş durum bloğu. */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    /**
     * Boşluğu dolduran işaret.
     *
     * Boş bir ekranda iki satır gri yazı, kullanıcıya bir şeyin **eksik**
     * olduğunu söylüyor; işaret ise o boşluğun beklenen bir durum olduğunu
     * söylüyor. Kasanın kendi kadranı kullanılıyor ve çok soluk çiziliyor:
     * dikkat çekmesi değil, boşluğa bir zemin vermesi gerekiyor.
     */
    icon: ImageVector? = null,
    /**
     * Buradan çıkışın yolu.
     *
     * Boş bir kasada "yeni kayıt ekle" düğmesi ekranın sağ alt köşesinde
     * duruyor ve ilk kez açan kullanıcının oraya bakması için bir sebep yok.
     * Boş durumun kendisi o yolu göstermeli.
     */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(KasaTheme.colors.ink3.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = KasaTheme.colors.ink3.copy(alpha = 0.55f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = KasaTheme.colors.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = KasaTheme.colors.ink3,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            KasaButton(
                text = actionLabel,
                onClick = onAction,
                tone = ButtonTone.TONAL
            )
        }
    }
}
