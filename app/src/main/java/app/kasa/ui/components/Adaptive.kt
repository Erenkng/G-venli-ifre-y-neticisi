package app.kasa.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * İçeriği okunabilir bir genişlikte tutup ortalar.
 *
 * ### Neden gerekli
 *
 * Uygulama listelerden oluşuyor ve liste satırı ekran kadar genişleyince
 * okunmuyor: bin piksel genişliğinde bir satırda ad solda, tarih sağda kalıyor
 * ve göz ikisini birleştirmek için ekranı baştan sona tarıyor. Satır uzunluğu
 * okunabilirliğin bilinen sınırlarından biri ve bir tablette ekranı doldurmak
 * o sınırı üçe katlıyor.
 *
 * ### Neden kenar boşluğu, kap değil
 *
 * Genişliği sınırlamanın olağan yolu içeriği bir kaba koymak ama bu, her
 * ekranın iskeletini değiştirmeyi gerektiriyor. Kenar boşluğu aynı sonucu
 * hiçbir yapıyı bozmadan veriyor: kaydırma yine ekranın tamamında çalışıyor,
 * yalnızca içerik ortada duruyor.
 *
 * Dar pencerede hiçbir şey yapmıyor — telefonda içerik zaten kenardan kenara.
 */
@Composable
fun Modifier.readablePane(max: Dp = READABLE_MAX): Modifier {
    val width = LocalConfiguration.current.screenWidthDp.dp
    if (width <= max) return this
    return padding(horizontal = (width - max) / 2)
}

/**
 * Ekranın altında yüzen çubuklar için genişlik sınırı.
 *
 * Bunlar ortalanmış olarak duruyor ve kenar boşluğu vermek yerine doğrudan
 * genişlikleri sınırlanıyor: içlerindeki eylemler zaten sabit boyutlu ve
 * çubuğun uzaması aralarındaki boşluğu büyütmekten başka bir şey yapmıyor.
 */
fun Modifier.floatingBarWidth(max: Dp = FLOATING_BAR_MAX): Modifier = widthIn(max = max)

/**
 * Okunabilir sütun genişliği.
 *
 * Tabletin tamamını doldurmuyor ama boş da bırakmıyor: liste satırının iki ucu
 * tek bakışta görülüyor ve ekranın kalanı zeminin kendi gradyanına kalıyor.
 */
val READABLE_MAX: Dp = 700.dp

private val FLOATING_BAR_MAX: Dp = 560.dp
