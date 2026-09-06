package app.kasa.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kasa.core.util.Haptics
import app.kasa.core.util.rememberHapticPlayer
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.LocalReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Kaydırınca çalışan tek bir eylem.
 *
 * @param icon kaydırırken arkada beliren işaret.
 * @param label ekran okuyucu için; işaretin kendi açıklaması yok.
 * @param tint işaretin rengi. Yıkıcı eylemler güç renginden, ötekiler
 *   mürekkepten alıyor — parmak eşiği geçmeden önce ne olacağını renk söylüyor.
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onAction: () -> Unit
)

/**
 * Satırı yana çekerek eylem çalıştırma.
 *
 * ### Neden
 *
 * Bir kaydı sık kullanılana eklemek ya da çöpe atmak, satırı basılı tutup
 * açılan sayfadan seçmeyi gerektiriyordu: iki hareket ve arada bir pencere.
 * Oysa ikisi de listenin en sık yapılan iki işi ve ikisi de yönlü — biri
 * kayda bir şey **ekliyor**, öteki onu listeden **çıkarıyor**. Yön, hareketin
 * kendisinde duruyor.
 *
 * ### Neden satır kaybolmuyor
 *
 * Kaydırma eylemi **tetikliyor**, sonucu kendisi çizmiyor. Çöpe atılan kayıt
 * listeden düştüğü için `animateItem` onu zaten kayarak çıkarıyor; sık
 * kullanılana eklenen kayıt ise yerinde kalmalı ve yalnızca rozeti
 * değişmeli. Kaydırmanın satırı ekrandan atması, ikinci durumda kaydın
 * silindiği izlenimini verirdi.
 *
 * Bu yüzden satır eşiği geçtikten sonra da yerine dönüyor: hareket bir düğme
 * gibi davranıyor, bir çöp kutusu gibi değil.
 *
 * ### Eşik neden ekranın oranı
 *
 * Sabit bir mesafe (örneğin 96dp) dar ve geniş ekranlarda iki farklı çabaya
 * denk geliyor. Oran, hareketin ekranın neresinde yapıldığından bağımsız
 * olarak aynı hissi veriyor.
 *
 * ### Dikey kaydırmayla çakışmıyor
 *
 * [detectHorizontalDragGestures] yalnızca yatay eşiği aşan dokunuşları
 * tüketiyor; parmak aşağı gidiyorsa olay listeye geçiyor ve liste normal
 * kayıyor. Ayrı bir "önce hangisi" hakemliği gerekmiyor.
 */
@Composable
fun SwipeActions(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Sağa kaydırınca çalışan eylem; işaret solda beliriyor. */
    start: SwipeAction? = null,
    /** Sola kaydırınca çalışan eylem; işaret sağda beliriyor. */
    end: SwipeAction? = null,
    content: @Composable () -> Unit
) {
    if (!enabled || (start == null && end == null)) {
        Box(modifier) { content() }
        return
    }

    val scope = rememberCoroutineScope()
    val play = rememberHapticPlayer()
    val reduced = LocalReducedMotion.current
    var width by remember { mutableFloatStateOf(0f) }
    val slide = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    // @Composable belirteç üreticileri eşyordam içinde çağrılamıyor; besteden
    // hazır değer olarak alınıyorlar.
    val settle: FiniteAnimationSpec<Float> = KasaMotion.medium()

    Box(
        modifier = modifier.onSizeChanged { width = it.width.toFloat() }
    ) {
        // İşaretler satırın **altında**: satır çekildikçe altından çıkıyorlar.
        // Üstte olsalardı satırın üzerine binerler ve satır hâlâ okunurken
        // ekranda iki katman birden olurdu.
        Row(
            modifier = Modifier.matchParentSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SwipeGlyph(action = start) {
                if (slide.value > 0f) actionFraction(slide.value, width) else 0f
            }
            SwipeGlyph(action = end) {
                if (slide.value < 0f) actionFraction(slide.value, width) else 0f
            }
        }

        // Hareket dinleyicisi içeriğin **kabında**, üstünde ayrı bir katmanda
        // değil. Üstte olsaydı Compose çakışan kardeşlerde yalnızca en üsttekini
        // isabet saydığı için satırın kendi tıklaması hiç çalışmazdı. Kapta
        // olunca ikisi birlikte çalışıyor: yatay eşik aşılana kadar hiçbir olay
        // tüketilmiyor, aşıldığında sürükleme olayı tüketiyor ve alttaki
        // tıklama kendiliğinden iptal oluyor.
        Box(
            Modifier
                // Değer çizim aşamasında okunuyor: bestede okunsaydı parmak
                // satırı sürüklediği sürece satırın iskeleti her karede
                // yeniden kurulurdu.
                .offset { IntOffset(slide.value.roundToInt(), 0) }
                .pointerInput(width, start, end) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val fired = armed
                            val direction = slide.value
                            armed = false
                            scope.launch { slide.animateTo(0f, settle) }
                            if (fired) {
                                if (direction > 0f) start?.onAction() else end?.onAction()
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch { slide.animateTo(0f, settle) }
                        }
                    ) { change, drag ->
                        change.consume()
                        // O yönde eylem yoksa satır kıpırdamıyor: olmayan bir
                        // şeye doğru çekilebilen bir yüzey, kullanıcıya orada
                        // bir şey olduğunu söyler.
                        val raw = slide.value + drag
                        val next = when {
                            raw > 0f && start == null -> 0f
                            raw < 0f && end == null -> 0f
                            else -> raw
                        }
                        // Eşikten sonra direniyor: parmak gitmeye devam ediyor
                        // ama satır yavaşlıyor ve "buraya kadar" diyor.
                        val limit = width * ACTION_FRACTION
                        val damped =
                            if (abs(next) <= limit) next
                            else (limit + (abs(next) - limit) * RUBBER) * (if (next < 0f) -1f else 1f)
                        scope.launch { slide.snapTo(if (reduced) 0f else damped) }

                        val past = width > 0f && abs(next) > limit
                        if (past != armed) {
                            armed = past
                            if (past) play(Haptics.Kind.THRESHOLD)
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/** 0 = dokunulmamış, 1 = eşik geçildi. Çizim anında okunmalı. */
private fun actionFraction(slide: Float, width: Float): Float =
    if (width <= 0f) 0f else (abs(slide) / (width * ACTION_FRACTION)).coerceIn(0f, 1f)

/**
 * Satırın altından çıkan işaret.
 *
 * Saydamlığın yanında ölçek de var: yalnızca solarak gelen bir işaret
 * "yükleniyor" gibi duruyor, büyüyerek gelen bir işaret ise parmağın
 * yaptığı işe yanıt veriyor. Eşikte tam boyuna varıyor, yani hareketin
 * tamamlandığı an gözle de görülüyor.
 */
@Composable
private fun SwipeGlyph(action: SwipeAction?, fraction: () -> Float) {
    if (action == null) {
        Box(Modifier.size(1.dp))
        return
    }
    Icon(
        imageVector = action.icon,
        contentDescription = action.label,
        tint = action.tint,
        modifier = Modifier
            .size(22.dp)
            .graphicsLayer {
                val shown = fraction()
                alpha = shown
                val grow = GLYPH_MIN + (1f - GLYPH_MIN) * shown
                scaleX = grow
                scaleY = grow
            }
    )
}

/** Eylemin çalışması için gereken yol, satır genişliğinin oranı olarak. */
private const val ACTION_FRACTION = 0.28f

/** Eşikten sonra parmağın kat ettiği yolun satıra geçen oranı. */
private const val RUBBER = 0.22f

/** İşaretin başlangıç ölçeği. Sıfırdan büyümek "beliriyor" değil "patlıyor" gibi duruyordu. */
private const val GLYPH_MIN = 0.6f
