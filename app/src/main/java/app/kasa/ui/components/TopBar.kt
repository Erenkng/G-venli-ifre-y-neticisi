package app.kasa.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.ui.theme.KasaTheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Kayan içeriğin üstünde duran cam başlık çubuğu.
 *
 * ### Neden var
 *
 * Her ekranın başlığı listenin ilk öğesi ve kaydırınca gidiyor. Uzun bir
 * listenin ortasında ekranın hangi ekran olduğunu söyleyen tek şey gezinme
 * çubuğundaki seçili simge kalıyor — ve o simge ekranın altında, gözün
 * bulunduğu yerin tam tersinde.
 *
 * Bu çubuk başlığı geri getiriyor ama yalnızca **gerektiğinde**: büyük başlık
 * görünürken çubuk yok, çünkü aynı kelimeyi iki kez yazmak bilgi değil
 * gürültü. Büyük başlık yukarı çıkarken çubuk onun yerini alıyor.
 *
 * ### Neden ayrı bir pencere katmanında değil de burada
 *
 * Çubuğun altındaki içeriğin bulanıklaşması gerekiyor ve bulanıklık ekranın
 * kaydedilmiş kopyasından geliyor. Kopya içerik çizilirken alınıyor; çubuk o
 * kopyanın **içinde** olsaydı kendi bulanık hâlini kaydeder ve her karede bir
 * önceki karenin bulanıklığını yeniden bulanıklaştırırdı. Bu yüzden çubuk
 * içeriğin kardeşi olarak, kayıttan sonra çiziliyor.
 *
 * ### Alt kenar
 *
 * Bulanıklık sert bir çizgiyle bitmiyor, son üçte birinde sönüyor. Sert bir
 * kenar çubuğu içeriğin üstüne yapıştırılmış bir şerit gibi gösteriyor;
 * sönerek biten kenar, altındaki içeriğin çubuğun altına doğru **devam
 * ettiğini** söylüyor — ki gerçek olan da bu.
 *
 * ### Oran neden işlev olarak geliyor
 *
 * Kaydırma her karede değişiyor. Oranı doğrudan bir `Float` olarak almak,
 * çağıran tarafın onu **beste sırasında** okuması demek: kullanıcı listeyi
 * kaydırdığı sürece bütün ekran iskeleti her karede yeniden birleşiyor.
 *
 * İşlev olarak gelince değer yalnızca `graphicsLayer` içinde, yani çizim
 * sırasında okunuyor. Kaydırma boyunca yeniden birleşen hiçbir şey yok;
 * yalnızca bu katmanın saydamlığı ve kayması güncelleniyor.
 *
 * Görünürlük ayrı bir bayrak olarak geliyor çünkü o **seyrek** değişiyor —
 * eşiği geçerken bir kez — ve beste sırasında okunması bir sorun değil.
 * İkisini tek bir sayıya sıkıştırmak, ya her karede yeniden birleşme ya da
 * görünmezken de bulanıklık hesaplayan bir çubuk demekti.
 *
 * ### Geri düğmesi
 *
 * Bir kategorinin ya da süzülmüş bir görünümün içindeyken geri dönme yolu
 * listenin başındaki çubuktaydı ve o da kaydırınca gidiyordu. Sistem geri
 * hareketi çalışmaya devam ediyordu ama görünür hiçbir çıkış kalmıyordu —
 * ve bir arayüzde yalnızca bilenlerin bulabildiği bir çıkış, çıkış sayılmaz.
 *
 * Düğme kendi eylemini taşımıyor, sistemin geri gönderisini tetikliyor.
 * Ekranların zaten kayıtlı `BackHandler`'ları var ve hangisinin çalışacağına
 * onlar karar veriyor; buraya ikinci bir karar tablosu yazmak, ikisinin
 * zamanla ayrışmasına açık kapı bırakırdı.
 *
 * @param visible çubuk beste ağacında var mı
 * @param progress 0 çubuk yok, 1 tamamen yerinde; yalnızca çizimde okunuyor
 * @param onBack verilirse başta bir geri oku çıkıyor
 */
@Composable
fun KasaTopBar(
    title: String,
    visible: Boolean,
    progress: () -> Float,
    backdrop: GraphicsLayer?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    if (!visible) return

    val colors = KasaTheme.colors
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val height = inset + BAR_HEIGHT + FADE_RUNWAY

    val plate = remember(colors) {
        Brush.verticalGradient(
            0.00f to colors.navScrim.copy(alpha = 0.88f),
            0.62f to colors.navScrim.copy(alpha = 0.78f),
            1.00f to colors.navScrim.copy(alpha = 0f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Saydamlık ve kayma ayrı: çubuk yalnızca solarak gelseydi
            // başlık olduğu yerde belirirdi ve nereden geldiği anlaşılmazdı.
            // Yukarıdan inmesi, büyük başlığın çıktığı yerden geldiğini
            // söylüyor.
            .graphicsLayer {
                // Çubuk büyük başlıkla **aynı** oranı paylaşmıyor.
                //
                // Paylaştığında ikisi de yolun ortasında yarı saydam oluyordu:
                // ekranda aynı sözcük, iki ayrı boyda, iki ayrı yerde, ikisi
                // de yarım — bir geçiş değil, bir hayal görüntüsü. Çubuk artık
                // ancak büyük başlık çubuğun ölçüsüne inmişken belirmeye
                // başlıyor. Kalan küçük örtüşme bilerek: iki yazı o anda aynı
                // boyda ve aynı yerde olduğu için göz tek bir nesne görüyor.
                val fraction = progress().coerceIn(0f, 1f)
                val appear = ((fraction - BAR_FADE_IN) / (1f - BAR_FADE_IN))
                    .coerceIn(0f, 1f)
                alpha = appear
                translationY = -(1f - appear) * BAR_TRAVEL.toPx()
            }
    ) {
        BackdropBlur(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize(),
            radius = BAR_BLUR,
            fadeBottom = FADE_FRACTION
        )
        Box(Modifier.matchParentSize().background(plate))
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(inset + BAR_HEIGHT)
                .padding(top = inset)
                // Geri oku varken sol boşluk daralıyor: okun kendi dokunma
                // alanı zaten o boşluğun yerini tutuyor ve ikisi üst üste
                // gelince başlık ortaya doğru kayıyordu.
                .padding(start = if (onBack == null) 22.dp else 8.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (onBack != null) {
                // Açıklama düğmede, simgede değil: ikisine birden verilirse
                // ekran okuyucu aynı şeyi iki kez söylüyor.
                KasaIconButton(
                    onClick = onBack,
                    contentDescription = stringResource(R.string.back)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = KasaTheme.colors.ink2,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = KasaTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Başlığın ne kadar geçtiğini bildirir.
 *
 * ### Neden ilk öğenin ötesine bakılmıyor
 *
 * İlk öğe ekrandan çıktığı anda oran 1 oluyor ve daha fazla kaydırmak bir şey
 * değiştirmiyor. Çubuk zaten yerinde; kaydırma miktarına bağlı bir değer
 * üretmek, hiçbir şeyi etkilemeyen bir hesabı her karede yapmak olurdu.
 *
 * ### Neden kısa bir mesafe
 *
 * Geçiş büyük başlığın tamamı kadar sürseydi, çubuk listenin yarısı geçtikten
 * sonra hâlâ yarı saydam olurdu. Kısa mesafe, geçişi kullanıcının "kaydırmaya
 * başladım" dediği ana bağlıyor.
 */
@Composable
fun HeaderCollapse(state: LazyListState, onProgress: (Float) -> Unit) {
    val runway = with(LocalDensity.current) { COLLAPSE_RUNWAY.toPx() }

    LaunchedEffect(state, runway) {
        snapshotFlow {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / runway).coerceIn(0f, 1f)
        }
            // Aynı değeri tekrar bildirmek, üst ekranda gereksiz bir yeniden
            // birleştirme demek; kaydırmanın durduğu her karede oluyordu.
            .distinctUntilChanged()
            .collect { onProgress(it) }
    }
}

/**
 * Büyük başlığın cam çubuğa devrederkenki hareketi.
 *
 * ### Neden gerekli
 *
 * Başlık listenin ilk öğesi olduğu için kaydırınca **yukarı kayıp
 * kayboluyordu**, üstteki cam çubuk ise ondan bağımsız olarak beliriyordu.
 * İki hareket aynı anda oluyor ama birbirini görmüyordu; göz iki ayrı şeyin
 * olduğunu okuyordu, oysa olan tek bir şey — başlık yer değiştiriyor.
 *
 * Devir hareketi bunu bağlıyor: büyük başlık kaybolurken hafifçe küçülüp
 * yukarı çekiliyor, yani çubuğa **gidiyor**. Aynı mesafede sönüyorlar,
 * dolayısıyla hiçbir anda ikisi birden tam görünür olmuyor.
 *
 * ### Neden kaydırmadan hızlı sönüyor
 *
 * Başlık kendi yüksekliği kadar yol alana kadar görünür kalsaydı, cam çubuk
 * çoktan gelmiş olacaktı. Sönme mesafesi çubuğun geliş mesafesiyle aynı;
 * ikisi tek bir geçişin iki yarısı.
 *
 * ### Neden çizim aşamasında
 *
 * Kaydırma her karede değişiyor. Oranı beste sırasında okumak, kullanıcı
 * listeyi kaydırdığı sürece başlığı taşıyan öğenin her karede yeniden
 * birleşmesi demek. `graphicsLayer` bloğu yalnızca çizimde çalışıyor.
 */
fun Modifier.headerHandoff(state: LazyListState): Modifier = composed {
    val runway = with(LocalDensity.current) { COLLAPSE_RUNWAY.toPx() }

    graphicsLayer {
        // Yalnızca ilk öğe ekrandayken anlamlı; ötesinde başlık zaten yok.
        val offset = if (state.firstVisibleItemIndex > 0) runway
        else state.firstVisibleItemScrollOffset.toFloat()
        val fraction = (offset / runway).coerceIn(0f, 1f)

        // Dönüşüm devir anında **bitiyor**, pistin sonunda değil: büyük
        // başlık çubuk belirmeden önce çubuğun ölçüsüne inmiş oluyor.
        val morph = (fraction / HANDOFF_CROSSOVER).coerceIn(0f, 1f)

        val shrink = 1f - morph * (1f - HANDOFF_TARGET_SCALE)
        scaleX = shrink
        scaleY = shrink
        // Ölçek merkezi sol üst: başlık kendi ortasına değil, çubuktaki
        // yazının başladığı köşeye doğru toplanıyor.
        transformOrigin = TransformOrigin(0f, 0f)

        // Sönme küçülmeden geç: kare alınca eğri başta yatay, sonda dik.
        // Doğrusal olsaydı başlık daha yarı yoldayken yarı saydam olurdu ve
        // küçülürken **kaybolan** bir şey gibi görünürdü; oysa kaybolmuyor,
        // yer değiştiriyor.
        alpha = 1f - morph * morph

        // Kaydırmanın bir kısmı geri alınıyor.
        //
        // Başlık listeyle birlikte tam hızda yukarı gitseydi, devir anında
        // çubuğun satırını çoktan geçmiş olurdu — 72dp'lik pistin %62'si
        // 45dp, oysa başlığın çubuğa inmesi için gereken yol ~33dp. Aradaki
        // farkı kapatmak için başlık kaydırmaya direniyor: liste akarken o
        // yavaşlayıp çubuğun satırına oturuyor.
        translationY = offset * HANDOFF_HOLD
    }
}

/**
 * Büyük başlığın devir anındaki ölçeği.
 *
 * 16/46: çubuktaki yazının boyunun dev başlığınkine oranı. Başlık devir
 * anında çubuktaki yazıyla **aynı** boyda oluyor ve takas o yüzden
 * görünmüyor. Eski değer %10'luk bir küçülmeydi; başlık uzaklaşıyor gibi
 * duruyordu ama çubuktaki yazıya dönüşmüyordu, çünkü ona hiç benzemiyordu.
 */
private const val HANDOFF_TARGET_SCALE = 0.348f

/** Pistin hangi noktasında büyük başlığın işi bitiyor. */
private const val HANDOFF_CROSSOVER = 0.62f

/** Çubuğun belirmeye başladığı nokta. Devirden biraz önce: ince bir örtüşme. */
private const val BAR_FADE_IN = 0.46f

/**
 * Başlığın kaydırmaya direnme oranı.
 *
 * Kaydırılan yolun bu kadarı geri veriliyor, yani başlık listeden yavaş
 * gidiyor ve devir anında çubuğun satırına oturuyor.
 */
private const val HANDOFF_HOLD = 0.25f

/** Çubuğun içerik yüksekliği; sistem çubuğu bunun üstüne ekleniyor. */
private val BAR_HEIGHT = 44.dp

/** Alt kenardaki sönme mesafesi. */
private val FADE_RUNWAY = 14.dp

/** Bulanıklığın alt kenarda söndüğü bölge, yüksekliğin oranı olarak. */
private const val FADE_FRACTION = 0.34f

/**
 * Çubuğun bulanıklık yarıçapı.
 *
 * Durum çubuğu camınınkinden yüksek, menü örtüsününkinden düşük. Buradaki cam
 * bir yüzey — üzerinde yazı duruyor ve o yazının altındaki içeriğin okunmaması
 * gerekiyor — ama ekranın küçük bir kısmını kaplıyor ve altında ne olduğunun
 * tamamen silinmesi kullanıcıyı listede kaybediyor.
 */
private val BAR_BLUR = 18.dp

/** Çubuğun yukarıdan inme mesafesi. */
private val BAR_TRAVEL = 10.dp

/**
 * Geçişin tamamlandığı kaydırma mesafesi.
 *
 * Büyük başlığın yüksekliğinden kısa: geçişin başlığın tamamen çıkmasını
 * beklemesi, çubuğu kaydırmanın ortasında yarı saydam bırakıyordu.
 */
private val COLLAPSE_RUNWAY = 72.dp
