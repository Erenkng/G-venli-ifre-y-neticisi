package app.kasa.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.kasa.data.GradientTheme
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Zeminin üç radyal durağı ve taban rengi.
 *
 * [KasaColors] içindeki `gradient*` alanlarının aynısı; ayrı bir tür olmasının
 * sebebi bu dördünün birlikte değişmesi ve tek başlarına anlam taşımaması.
 */
@Immutable
data class GradientStops(
    val topLeft: Color,
    val topRight: Color,
    val bottom: Color,
    val base: Color
)

/**
 * Günün saatine göre kayan zemin gradyanları.
 *
 * ### Neden saate bağlı
 *
 * Bir parola yöneticisi günde on kez, hep aynı görünen bir ekranla açılıyor.
 * Zeminin sabahla akşam arasında yer değiştirmesi, uygulamayı "canlı"
 * göstermeye çalışan bir süs değil: kullanıcının hangi bağlamda olduğuna dair
 * sessiz bir işaret ve ekrana her açılışta biraz farklı bir yüz veriyor.
 *
 * Değişim **çok yavaş** olmak zorunda. Kullanıcı bunu fark ederse etki
 * kaybolur ve "ekran neden renk değiştirdi" sorusuna dönüşür; iki komşu dilim
 * arasında geçiş sürekli olduğu için hiçbir anda bir sıçrama yok.
 *
 * ### Neden üç aile
 *
 * Tek bir renk ailesini herkese dayatmak, temanın kendisini bir tercih
 * olmaktan çıkarıyor. Üç aile üç farklı sıcaklık sunuyor — jade serin ve
 * yeşil, gün batımı sıcak, derin soğuk ve mavi — ve her biri kendi içinde
 * günün beş dilimine göre kayıyor. Aile kullanıcının seçimi; dilim saatin.
 *
 * ### Karanlık tema ayrı
 *
 * Karanlıkta aynı renkleri koyulaştırarak kullanmak çamur üretiyor: doygun bir
 * kehribar %12 parlaklıkta kahverengi bir lekeye dönüşüyor. Karanlık için ayrı
 * ve daha az doygun duraklar tanımlı.
 */
object KasaGradients {

    /** Günün dilimleri. Sınırlar Türkiye'deki tipik gün ritmine göre. */
    private val SLOT_HOURS = listOf(5, 9, 13, 18, 22)

    /**
     * Aile × dilim tablosu.
     *
     * Her aile beş duraklı: şafak, sabah, öğle, akşam, gece. Dilimler dairesel
     * — gecenin ardından şafak geliyor — bu yüzden son ve ilk durak arasında da
     * geçiş yapılıyor.
     */
    private val LIGHT: Map<GradientTheme, List<GradientStops>> = mapOf(
        GradientTheme.JADE to listOf(
            // şafak: soğuk yeşil, ısınan sağ üst
            GradientStops(Color(0xFFC8EFE4), Color(0xFFFFE0D4), Color(0xFFDDE6F6), Color(0xFFFFFFFF)),
            // sabah: uygulamanın imza gradyanı
            GradientStops(Color(0xFFCFF3E7), Color(0xFFFFE7C6), Color(0xFFDCEAF4), Color(0xFFFFFFFF)),
            // öğle: en açık, en az doygun — güçlü ışıkta ekran zaten yıkanıyor
            GradientStops(Color(0xFFD8F5EC), Color(0xFFFFF0D8), Color(0xFFE4F0F8), Color(0xFFFFFFFF)),
            // akşam: kehribar öne çıkıyor
            GradientStops(Color(0xFFCBEEE0), Color(0xFFFFDCB0), Color(0xFFE2E2F4), Color(0xFFFFFDFB)),
            // gece: mavi ağır basıyor
            GradientStops(Color(0xFFC4E9E2), Color(0xFFEFE2D6), Color(0xFFD2E1F2), Color(0xFFFCFDFE))
        ),
        GradientTheme.SUNSET to listOf(
            GradientStops(Color(0xFFFFDDD6), Color(0xFFFFE9C9), Color(0xFFEDDCF2), Color(0xFFFFFDFC)),
            GradientStops(Color(0xFFFFD9CC), Color(0xFFFFE3BC), Color(0xFFF3DCEE), Color(0xFFFFFCFA)),
            GradientStops(Color(0xFFFFE5DC), Color(0xFFFFF0D4), Color(0xFFF6E6F5), Color(0xFFFFFFFF)),
            GradientStops(Color(0xFFFFCFC0), Color(0xFFFFD9A6), Color(0xFFECD5F0), Color(0xFFFFFBF8)),
            GradientStops(Color(0xFFF6D3CE), Color(0xFFF6DCC2), Color(0xFFE2D6EE), Color(0xFFFDFBFC))
        ),
        GradientTheme.DEEP to listOf(
            GradientStops(Color(0xFFD2E2F7), Color(0xFFD5EEF2), Color(0xFFCFDCF4), Color(0xFFFDFEFF)),
            GradientStops(Color(0xFFD8E7F9), Color(0xFFD2F0F4), Color(0xFFD6E2F6), Color(0xFFFFFFFF)),
            GradientStops(Color(0xFFE0EDFB), Color(0xFFDCF3F6), Color(0xFFE0EAF9), Color(0xFFFFFFFF)),
            GradientStops(Color(0xFFCCDCF4), Color(0xFFCBE9F0), Color(0xFFCEDCF2), Color(0xFFFCFDFF)),
            GradientStops(Color(0xFFC3D5EE), Color(0xFFC4E1EA), Color(0xFFC7D6EE), Color(0xFFFAFCFE))
        )
    )

    private val DARK: Map<GradientTheme, List<GradientStops>> = mapOf(
        GradientTheme.JADE to listOf(
            GradientStops(Color(0xFF0E3B36), Color(0xFF3A2612), Color(0xFF12283A), Color(0xFF0B1512)),
            GradientStops(Color(0xFF10403A), Color(0xFF3A2C10), Color(0xFF12283A), Color(0xFF0B1512)),
            GradientStops(Color(0xFF134840), Color(0xFF433313), Color(0xFF152E42), Color(0xFF0C1714)),
            GradientStops(Color(0xFF0F3A34), Color(0xFF46300F), Color(0xFF16263C), Color(0xFF0A1411)),
            GradientStops(Color(0xFF0B2E2A), Color(0xFF2E2410), Color(0xFF102138), Color(0xFF08110F))
        ),
        GradientTheme.SUNSET to listOf(
            GradientStops(Color(0xFF422019), Color(0xFF46301A), Color(0xFF2B1B3C), Color(0xFF150C0E)),
            GradientStops(Color(0xFF4A241B), Color(0xFF4E351A), Color(0xFF301D42), Color(0xFF160D0F)),
            GradientStops(Color(0xFF52291F), Color(0xFF573B1D), Color(0xFF352048), Color(0xFF180E10)),
            GradientStops(Color(0xFF5A2A1C), Color(0xFF5E3A16), Color(0xFF2F1B41), Color(0xFF170C0E)),
            GradientStops(Color(0xFF3A1C16), Color(0xFF3A2714), Color(0xFF241634), Color(0xFF110A0C))
        ),
        GradientTheme.DEEP to listOf(
            GradientStops(Color(0xFF16294A), Color(0xFF123A44), Color(0xFF13233F), Color(0xFF090F17)),
            GradientStops(Color(0xFF1A2F52), Color(0xFF14404C), Color(0xFF162846), Color(0xFF0A1119)),
            GradientStops(Color(0xFF1E3559), Color(0xFF174754), Color(0xFF1A2D4D), Color(0xFF0B131C)),
            GradientStops(Color(0xFF152847), Color(0xFF11373F), Color(0xFF141F3C), Color(0xFF080E15)),
            GradientStops(Color(0xFF101F3A), Color(0xFF0D2B33), Color(0xFF0F1930), Color(0xFF060A11))
        )
    )

    /**
     * Saate karşılık gelen duraklar.
     *
     * İki komşu dilim arasında doğrusal karışım yapılıyor: saat 11'de sabah ve
     * öğle yarı yarıya. Dilimden dilime atlamak, günde beş kez fark edilir bir
     * renk sıçraması demek olurdu.
     *
     * @param minutesOfDay gece yarısından itibaren geçen dakika
     * @param followTime kapalıysa sabahın (imza) durağı sabitleniyor
     */
    fun stopsAt(
        theme: GradientTheme,
        dark: Boolean,
        minutesOfDay: Int,
        followTime: Boolean
    ): GradientStops {
        val table = (if (dark) DARK else LIGHT)[theme] ?: (if (dark) DARK else LIGHT).getValue(GradientTheme.JADE)
        if (!followTime) return table[1]

        val minutes = minutesOfDay.coerceIn(0, 24 * 60 - 1)
        val boundaries = SLOT_HOURS.map { it * 60 }

        // Hangi iki durak arasındayız: sınırlar dairesel, gece yarısı gecenin
        // içinde kalıyor.
        var index = boundaries.indexOfLast { it <= minutes }
        if (index < 0) index = boundaries.lastIndex // gece yarısı–şafak arası

        val from = boundaries[index]
        val to = boundaries[(index + 1) % boundaries.size]
        val span = if (to > from) to - from else (24 * 60 - from) + to
        val elapsed = if (minutes >= from) minutes - from else (24 * 60 - from) + minutes
        val fraction = (elapsed.toFloat() / span).coerceIn(0f, 1f)

        return blend(table[index], table[(index + 1) % table.size], fraction)
    }

    private fun blend(from: GradientStops, to: GradientStops, fraction: Float) = GradientStops(
        topLeft = mix(from.topLeft, to.topLeft, fraction),
        topRight = mix(from.topRight, to.topRight, fraction),
        bottom = mix(from.bottom, to.bottom, fraction),
        base = mix(from.base, to.base, fraction)
    )

    private fun mix(from: Color, to: Color, fraction: Float) = Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = from.alpha + (to.alpha - from.alpha) * fraction
    )
}

/**
 * O anki gradyan duraklarını veren, saat ilerledikçe kendini tazeleyen durum.
 *
 * ### Tazeleme aralığı neden on dakika
 *
 * Renkler dört saatlik bir dilim boyunca kayıyor; on dakikada bir örneklemek,
 * en hızlı geçişte bile gözle görülemeyecek bir adım demek. Her dakika
 * tazelemek aynı görüntüyü üretip uygulamayı gereksiz uyandırırdı; saatte bir
 * tazelemek ise adımı fark edilir kılardı.
 *
 * Zamanlayıcı uygulama ön plandayken çalışıyor; arka planda beste zaten
 * durduğu için ayrıca durdurmak gerekmiyor.
 */
@Composable
fun rememberGradientStops(
    theme: GradientTheme,
    dark: Boolean,
    followTime: Boolean
): GradientStops {
    var minutes by remember { mutableIntStateOf(minutesOfDayNow()) }

    LaunchedEffect(followTime) {
        if (!followTime) return@LaunchedEffect
        while (true) {
            minutes = minutesOfDayNow()
            delay(REFRESH_MILLIS)
        }
    }

    return remember(theme, dark, followTime, minutes) {
        KasaGradients.stopsAt(theme, dark, minutes, followTime)
    }
}

private fun minutesOfDayNow(): Int = Calendar.getInstance().let {
    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
}

private const val REFRESH_MILLIS = 10 * 60 * 1000L
