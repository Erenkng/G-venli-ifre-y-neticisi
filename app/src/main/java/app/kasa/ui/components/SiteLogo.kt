package app.kasa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Kaydın ait olduğu sitenin işareti.
 *
 * ### Neden favicon indirilmiyor
 *
 * Akla ilk gelen çözüm, her kaydın alan adından `favicon.ico` çekmek. O yol
 * bu uygulamada kapalı ve sebebi teknik değil:
 *
 * **Favicon istemek, kasanın içindekini dışarı söylemek.** Telefon
 * `bankam.com.tr/favicon.ico` istediğinde o sunucu "bu IP'de bizim sitemizin
 * kaydını tutan biri var" bilgisini alıyor. Yüz kayıtlık bir kasa açıldığında
 * yüz farklı sunucuya aynı anda gidilecekti; ortadaki ağ da (kafe Wi-Fi'ı,
 * operatör) bu isteklerin listesini görecekti. Kullanıcının hangi bankada
 * hesabı olduğu, kasanın koruduğu bilginin ta kendisi.
 *
 * Aracı bir servis (Google favicon API gibi) daha da kötü: liste tek bir
 * şirkete, düzenli olarak gidiyor.
 *
 * ### Onun yerine: gömülü tablo
 *
 * Sık kullanılan siteler alan adından tanınıyor ve işaret **uygulamanın
 * içinden** geliyor: sitenin bilinen rengi ve o siteyi anlatan bir simge. Ağ
 * yok, gecikme yok, önbellek yok.
 *
 * Bedeli açık ve söylenmeli: tablo eksik kalır. Tanınmayan site, alan adından
 * türetilen sabit bir renkle ve baş harfiyle gösteriliyor — eskisi gibi düz
 * bir rozet değil, en azından siteye özel bir renk. Aynı site her zaman aynı
 * rengi alıyor, çünkü renk alan adının özetinden geliyor.
 */
@Composable
fun SiteLogo(
    url: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    cornerRadius: Dp = 16.dp
) {
    val host = remember(url) { hostOf(url) }
    val brand = remember(host) { SiteBrands.match(host) }
    val background = brand?.color ?: remember(host) { derivedColor(host.ifBlank { fallbackText }) }
    val foreground = remember(background) { readableInk(background) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (brand != null) {
            Icon(
                imageVector = brand.icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            Text(
                text = fallbackText,
                color = foreground,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/** Kasadaki bir kaydın bilinen bir siteye ait olup olmadığı. */
fun isKnownSite(url: String): Boolean = SiteBrands.match(hostOf(url)) != null

private data class SiteBrand(val color: Color, val icon: ImageVector)

/**
 * Alan adı → renk ve simge.
 *
 * Anahtarlar alan adının **ayırt edici parçası**: `google` yazıyor,
 * `google.com` değil. Böylece `google.com.tr`, `accounts.google.com` ve
 * `mail.google.co.uk` aynı işareti alıyor; ülke uzantısı başına satır yazmak
 * tabloyu üçe katlar ve yine eksik kalırdı.
 */
private object SiteBrands {

    private val TABLE: Map<String, SiteBrand> = mapOf(
        // ── genel ──────────────────────────────────────────────────────────
        "google" to SiteBrand(Color(0xFF4285F4), Icons.Rounded.Search),
        "gmail" to SiteBrand(Color(0xFFEA4335), Icons.Rounded.Mail),
        "youtube" to SiteBrand(Color(0xFFFF0000), Icons.Rounded.PlayArrow),
        "instagram" to SiteBrand(Color(0xFFE1306C), Icons.Rounded.PhotoCamera),
        "facebook" to SiteBrand(Color(0xFF1877F2), Icons.Rounded.ThumbUp),
        "whatsapp" to SiteBrand(Color(0xFF25D366), Icons.Rounded.Chat),
        "telegram" to SiteBrand(Color(0xFF229ED9), Icons.Rounded.Send),
        "discord" to SiteBrand(Color(0xFF5865F2), Icons.Rounded.Forum),
        "reddit" to SiteBrand(Color(0xFFFF4500), Icons.Rounded.Forum),
        "linkedin" to SiteBrand(Color(0xFF0A66C2), Icons.Rounded.Work),
        "github" to SiteBrand(Color(0xFF24292F), Icons.Rounded.Code),
        "gitlab" to SiteBrand(Color(0xFFFC6D26), Icons.Rounded.Code),
        "bitbucket" to SiteBrand(Color(0xFF0052CC), Icons.Rounded.Code),
        "microsoft" to SiteBrand(Color(0xFF0078D4), Icons.Rounded.Description),
        "outlook" to SiteBrand(Color(0xFF0078D4), Icons.Rounded.Mail),
        "hotmail" to SiteBrand(Color(0xFF0078D4), Icons.Rounded.Mail),
        "yahoo" to SiteBrand(Color(0xFF6001D2), Icons.Rounded.Mail),
        "proton" to SiteBrand(Color(0xFF6D4AFF), Icons.Rounded.Mail),
        "icloud" to SiteBrand(Color(0xFF3E7BFA), Icons.Rounded.Cloud),
        "apple" to SiteBrand(Color(0xFF444448), Icons.Rounded.Cloud),
        "dropbox" to SiteBrand(Color(0xFF0061FF), Icons.Rounded.Cloud),
        "amazon" to SiteBrand(Color(0xFFFF9900), Icons.Rounded.ShoppingCart),
        "netflix" to SiteBrand(Color(0xFFE50914), Icons.Rounded.Movie),
        "spotify" to SiteBrand(Color(0xFF1DB954), Icons.Rounded.MusicNote),
        "twitch" to SiteBrand(Color(0xFF9146FF), Icons.Rounded.Videocam),
        "steampowered" to SiteBrand(Color(0xFF1B2838), Icons.Rounded.SportsEsports),
        "steamcommunity" to SiteBrand(Color(0xFF1B2838), Icons.Rounded.SportsEsports),
        "epicgames" to SiteBrand(Color(0xFF2A2A2A), Icons.Rounded.SportsEsports),
        "playstation" to SiteBrand(Color(0xFF0070D1), Icons.Rounded.SportsEsports),
        "xbox" to SiteBrand(Color(0xFF107C10), Icons.Rounded.SportsEsports),
        "nintendo" to SiteBrand(Color(0xFFE60012), Icons.Rounded.SportsEsports),
        "paypal" to SiteBrand(Color(0xFF003087), Icons.Rounded.Payments),
        "ebay" to SiteBrand(Color(0xFFE53238), Icons.Rounded.ShoppingBag),
        "slack" to SiteBrand(Color(0xFF4A154B), Icons.Rounded.Forum),
        "zoom" to SiteBrand(Color(0xFF2D8CFF), Icons.Rounded.Videocam),
        "tiktok" to SiteBrand(Color(0xFF010101), Icons.Rounded.MusicNote),
        "snapchat" to SiteBrand(Color(0xFFFFC800), Icons.Rounded.Chat),
        "pinterest" to SiteBrand(Color(0xFFE60023), Icons.Rounded.PushPin),
        "adobe" to SiteBrand(Color(0xFFFA0F00), Icons.Rounded.Brush),
        "canva" to SiteBrand(Color(0xFF00C4CC), Icons.Rounded.Brush),
        "figma" to SiteBrand(Color(0xFFF24E1E), Icons.Rounded.Brush),
        "notion" to SiteBrand(Color(0xFF2F2F2F), Icons.Rounded.Description),
        "openai" to SiteBrand(Color(0xFF10A37F), Icons.Rounded.AutoAwesome),
        "chatgpt" to SiteBrand(Color(0xFF10A37F), Icons.Rounded.AutoAwesome),
        "claude" to SiteBrand(Color(0xFFD97757), Icons.Rounded.AutoAwesome),
        "anthropic" to SiteBrand(Color(0xFFD97757), Icons.Rounded.AutoAwesome),
        "wikipedia" to SiteBrand(Color(0xFF54595D), Icons.Rounded.MenuBook),
        "stackoverflow" to SiteBrand(Color(0xFFF48024), Icons.Rounded.QuestionAnswer),
        "booking" to SiteBrand(Color(0xFF003580), Icons.Rounded.Hotel),
        "airbnb" to SiteBrand(Color(0xFFFF5A5F), Icons.Rounded.Hotel),

        // ── Türkiye ────────────────────────────────────────────────────────
        "trendyol" to SiteBrand(Color(0xFFF27A1A), Icons.Rounded.ShoppingBag),
        "hepsiburada" to SiteBrand(Color(0xFFFF6000), Icons.Rounded.ShoppingCart),
        "n11" to SiteBrand(Color(0xFFEC008C), Icons.Rounded.ShoppingCart),
        "sahibinden" to SiteBrand(Color(0xFFFFE800), Icons.Rounded.Storefront),
        "getir" to SiteBrand(Color(0xFF5D3EBC), Icons.Rounded.DeliveryDining),
        "yemeksepeti" to SiteBrand(Color(0xFFFA0050), Icons.Rounded.Restaurant),
        "migros" to SiteBrand(Color(0xFFF58220), Icons.Rounded.LocalGroceryStore),
        "a101" to SiteBrand(Color(0xFF004B93), Icons.Rounded.LocalGroceryStore),
        "garanti" to SiteBrand(Color(0xFF00A94F), Icons.Rounded.AccountBalance),
        "isbank" to SiteBrand(Color(0xFF00539F), Icons.Rounded.AccountBalance),
        "akbank" to SiteBrand(Color(0xFFE1051E), Icons.Rounded.AccountBalance),
        "ziraatbank" to SiteBrand(Color(0xFFE01F26), Icons.Rounded.AccountBalance),
        "yapikredi" to SiteBrand(Color(0xFF00447C), Icons.Rounded.AccountBalance),
        "vakifbank" to SiteBrand(Color(0xFFF9C606), Icons.Rounded.AccountBalance),
        "denizbank" to SiteBrand(Color(0xFF003D7D), Icons.Rounded.AccountBalance),
        "kuveytturk" to SiteBrand(Color(0xFF009B3A), Icons.Rounded.AccountBalance),
        "enpara" to SiteBrand(Color(0xFF7F3F98), Icons.Rounded.AccountBalance),
        "papara" to SiteBrand(Color(0xFF1E1E5A), Icons.Rounded.Payments),
        "turkcell" to SiteBrand(Color(0xFFFFC800), Icons.Rounded.SignalCellularAlt),
        "vodafone" to SiteBrand(Color(0xFFE60000), Icons.Rounded.SignalCellularAlt),
        "turktelekom" to SiteBrand(Color(0xFF00A0DF), Icons.Rounded.SignalCellularAlt),
        "turkishairlines" to SiteBrand(Color(0xFFC70A0C), Icons.Rounded.Flight),
        "pegasus" to SiteBrand(Color(0xFFFEC20E), Icons.Rounded.Flight),
        "turkiye.gov" to SiteBrand(Color(0xFFC8102E), Icons.Rounded.AccountBalance),
        "ptt" to SiteBrand(Color(0xFFFFD400), Icons.Rounded.LocalPostOffice)
    )

    /**
     * Alan adında tablo anahtarlarından biri geçiyor mu.
     *
     * Eşleşme alan adının **etiketleri** üzerinde yapılıyor, düz metin araması
     * değil: `x.com` için düz arama `sahibinden-x.com` gibi bir alan adını da
     * yakalardı. `turkiye.gov` gibi iki etiketli anahtarlar ayrıca sınanıyor.
     */
    fun match(host: String): SiteBrand? {
        if (host.isBlank()) return null
        val labels = host.split('.')
        labels.forEach { label ->
            TABLE[label]?.let { return it }
        }
        // İki etiketli anahtarlar (turkiye.gov gibi)
        for (index in 0 until labels.size - 1) {
            TABLE["${labels[index]}.${labels[index + 1]}"]?.let { return it }
        }
        return null
    }
}

/** Adresten yalnızca ana makine adını çıkarır; şema, yol ve `www.` atılıyor. */
fun hostOf(url: String): String {
    if (url.isBlank()) return ""
    val withScheme = if (url.contains("://")) url else "https://$url"
    return runCatching {
        java.net.URI(withScheme).host.orEmpty().removePrefix("www.").lowercase()
    }.getOrDefault("")
}

/**
 * Tanınmayan site için alan adından türetilen renk.
 *
 * Ton alan adının özetinden geliyor; doygunluk ve parlaklık sabit tutuluyor
 * ki hiçbir kayıt okunamayacak kadar soluk ya da göz alacak kadar parlak
 * çıkmasın. Aynı alan adı her zaman aynı rengi alıyor — liste kaydırıldığında
 * ya da uygulama yeniden açıldığında rozet renginin değişmesi, kullanıcının
 * kurduğu görsel hafızayı bozardı.
 */
private fun derivedColor(seed: String): Color {
    if (seed.isBlank()) return Color(0xFF5B6B73)
    val hash = seed.fold(0) { acc, ch -> acc * 31 + ch.code }
    val hue = abs(hash % 360).toFloat()
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.52f, 0.62f)))
}

/**
 * Zemine göre okunur mürekkep.
 *
 * Sabit beyaz kullanmak Snapchat sarısı ya da sahibinden sarısı gibi parlak
 * zeminlerde simgeyi görünmez yapıyordu. Eşik, algılanan parlaklığa
 * (WCAG'in bağıl parlaklık yaklaşımı) göre.
 */
private fun readableInk(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.6f) Color(0xFF14211E) else Color.White
}
