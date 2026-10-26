package app.kasa.ui.theme

import androidx.compose.runtime.Composable
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
 * Günün saatine ve seçilen aileye göre zemin rengi.
 *
 * ### Neden tek renk kaldı
 *
 * Burada üç radyal durak ve bir taban rengi vardı: sol üstte jade, sağ üstte
 * kehribar, altta mavi bir ışık kaynağı. Düzgün çalışıyordu ve kötü de
 * görünmüyordu; sorun şuydu ki o görünüm 2022'nin görünümü. Renkli ışıkların
 * sızdığı buzlu cam yüzeyler bir dönemin imzasıydı ve o dönem geçti. Bugünün
 * Android'i tersini yapıyor: zemin sessiz ve düz, renk ise bileşenlerin kendi
 * üstünde — düğmede, rozette, seçili sekmede.
 *
 * Düz zemin bedava gelmiyor, bir şey karşılığında alınıyor: kartların görünür
 * olması artık zeminin işi. Gradyanlı hâlde taban saf beyazdı ve beyaz kartlar
 * zemine karışıyordu; kartı ayıran şey altından geçen renk lekesiydi. Şimdi
 * zemin beyazdan bir tık koyu ve kart beyaz kalıyor, yani ayrımı kartın kendi
 * parlaklığı veriyor. Material'ın katman mantığı da bu.
 *
 * ### Neden hâlâ saate bağlı
 *
 * Bir parola yöneticisi günde on kez, hep aynı görünen bir ekranla açılıyor.
 * Zeminin sabahla akşam arasında yer değiştirmesi, uygulamayı "canlı"
 * göstermeye çalışan bir süs değil: ekrana her açılışta biraz farklı bir yüz
 * veriyor. Gradyanlar gitti ama bunun gradyanla bir ilgisi yoktu, o yüzden
 * kaldı — yalnızca artık dört renk yerine bir renk kayıyor.
 *
 * Değişim **çok yavaş** olmak zorunda. Kullanıcı bunu fark ederse etki
 * kaybolur ve "ekran neden renk değiştirdi" sorusuna dönüşür; iki komşu dilim
 * arasında geçiş sürekli olduğu için hiçbir anda bir sıçrama yok.
 *
 * ### Neden üç aile
 *
 * Tek bir renk ailesini herkese dayatmak, temanın kendisini bir tercih
 * olmaktan çıkarıyor. Üç aile üç farklı sıcaklık sunuyor — jade serin ve
 * yeşil, gün batımı sıcak, derin soğuk ve mavi. Aile kullanıcının seçimi;
 * dilim saatin.
 *
 * ### Doygunluk neden bu kadar düşük
 *
 * Aileler bir renk değil bir **eğilim** veriyor: jade zemini yeşile, gün
 * batımı pembeye, derin maviye çekiyor ama hiçbiri "yeşil ekran" değil.
 * Doygun bir zemin, üstündeki her rengi — rozetleri, güç göstergesini, site
 * logolarını — kendi tarafına çekerdi. Zeminin işi görünmek değil, üstündeki
 * şeyin görünmesine izin vermek.
 */
object KasaCanvas {

    /** Günün dilimleri. Sınırlar Türkiye'deki tipik gün ritmine göre. */
    private val SLOT_HOURS = listOf(5, 9, 13, 18, 22)

    /**
     * Aile × dilim tablosu: şafak, sabah, öğle, akşam, gece.
     *
     * Dilimler dairesel — gecenin ardından şafak geliyor — bu yüzden son ve
     * ilk durak arasında da geçiş yapılıyor.
     *
     * Dilimlerin yönü her ailede aynı: öğlen en açık (güçlü ışıkta ekran
     * zaten yıkanıyor, zeminin oraya karşı koymasının anlamı yok), akşam
     * ısınıyor, gece hem koyulaşıyor hem soğuyor.
     */
    private val LIGHT: Map<GradientTheme, List<Color>> = mapOf(
        GradientTheme.JADE to listOf(
            Color(0xFFEFF6F2), Color(0xFFF1F7F4), Color(0xFFF6FAF8), Color(0xFFF5F4EF), Color(0xFFEDF2F2)
        ),
        GradientTheme.SUNSET to listOf(
            Color(0xFFFAF3F0), Color(0xFFFCF5F1), Color(0xFFFEF9F6), Color(0xFFFAF0EA), Color(0xFFF5F1F0)
        ),
        GradientTheme.DEEP to listOf(
            Color(0xFFF0F4FA), Color(0xFFF2F6FB), Color(0xFFF7FAFD), Color(0xFFF1F3FA), Color(0xFFECF1F7)
        )
    )

    /**
     * Karanlıkta aynı renkleri koyulaştırmak çamur üretiyor: doygun bir
     * kehribar %12 parlaklıkta kahverengi bir lekeye dönüşüyor. Karanlık için
     * ayrı ve daha da az doygun duraklar tanımlı.
     */
    private val DARK: Map<GradientTheme, List<Color>> = mapOf(
        GradientTheme.JADE to listOf(
            Color(0xFF0C1512), Color(0xFF0D1714), Color(0xFF0F1A17), Color(0xFF121613), Color(0xFF0A1210)
        ),
        GradientTheme.SUNSET to listOf(
            Color(0xFF16100E), Color(0xFF17120F), Color(0xFF1A1411), Color(0xFF18110D), Color(0xFF130E0C)
        ),
        GradientTheme.DEEP to listOf(
            Color(0xFF0B1017), Color(0xFF0C1219), Color(0xFF0E151D), Color(0xFF0C1118), Color(0xFF090E14)
        )
    )

    /**
     * Saate karşılık gelen renk.
     *
     * İki komşu dilim arasında doğrusal karışım yapılıyor: saat 11'de sabah ve
     * öğle yarı yarıya. Dilimden dilime atlamak, günde beş kez fark edilir bir
     * renk sıçraması demek olurdu.
     *
     * @param minutesOfDay gece yarısından itibaren geçen dakika
     * @param followTime kapalıysa sabahın (imza) durağı sabitleniyor
     */
    fun toneAt(
        theme: GradientTheme,
        dark: Boolean,
        minutesOfDay: Int,
        followTime: Boolean
    ): Color {
        val source = if (dark) DARK else LIGHT
        val table = source[theme] ?: source.getValue(GradientTheme.JADE)
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

        return mix(table[index], table[(index + 1) % table.size], fraction)
    }

    private fun mix(from: Color, to: Color, fraction: Float) = Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = from.alpha + (to.alpha - from.alpha) * fraction
    )
}

/**
 * O anki zemin rengini veren, saat ilerledikçe kendini tazeleyen durum.
 *
 * ### Tazeleme aralığı neden on dakika
 *
 * Renk dört saatlik bir dilim boyunca kayıyor; on dakikada bir örneklemek, en
 * hızlı geçişte bile gözle görülemeyecek bir adım demek. Her dakika tazelemek
 * aynı görüntüyü üretip uygulamayı gereksiz uyandırırdı; saatte bir tazelemek
 * ise adımı fark edilir kılardı.
 *
 * Zamanlayıcı uygulama ön plandayken çalışıyor; arka planda beste zaten
 * durduğu için ayrıca durdurmak gerekmiyor.
 */
@Composable
fun rememberCanvasTone(
    theme: GradientTheme,
    dark: Boolean,
    followTime: Boolean
): Color {
    var minutes by remember { mutableIntStateOf(minutesOfDayNow()) }

    LaunchedEffect(followTime) {
        if (!followTime) return@LaunchedEffect
        while (true) {
            minutes = minutesOfDayNow()
            delay(REFRESH_MILLIS)
        }
    }

    return remember(theme, dark, followTime, minutes) {
        KasaCanvas.toneAt(theme, dark, minutes, followTime)
    }
}

private fun minutesOfDayNow(): Int = Calendar.getInstance().let {
    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
}

private const val REFRESH_MILLIS = 10 * 60 * 1000L
