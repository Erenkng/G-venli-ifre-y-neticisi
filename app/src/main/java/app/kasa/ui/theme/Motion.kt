package app.kasa.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Sistemde animasyonlar kapalı mı.
 *
 * Kurulumda bir kez okunuyor: `ANIMATOR_DURATION_SCALE` ayarı değiştiğinde
 * Android zaten süreçleri yeniden başlatmıyor ama bu ayarı değiştiren kullanıcı
 * uygulamayı da kapatıp açıyor. Her kareyi `Settings.Global` sorgusuyla
 * yüklemek bunun karşılığında ödenecek bir bedel değil.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Sistemde animasyonlar kapalıysa (erişilebilirlik ya da pil tasarrufu) hareket
 * bütünüyle kalkar. Hareketi kapatan bir kullanıcıya ekranın ortasında dönen bir
 * kadran göstermek, tasarımın niyetini bozmaktan da öte rahatsız edici.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Uygulamanın hareket sözlüğü.
 *
 * ### Neden merkezî bir sözlük
 *
 * Bu dosyadan önce uygulamada beş ayrı sönümleme oranı (0,50 / 0,55 / 0,62 /
 * 0,70 / 0,75) ve altı ayrı süre vardı; her biri yazıldığı anda o ekrana iyi
 * gelen bir sayıydı ve hiçbiri ötekine bakılarak seçilmemişti. Sonuç, tek tek
 * bakıldığında kusursuz ama arka arkaya kullanıldığında **aynı uygulama gibi
 * durmayan** ekranlardı: gezinme çubuğu zıplarken ayarlar listesi ağır
 * ilerliyordu.
 *
 * ### Sınıflandırma neye göre
 *
 * Yaylı bir animasyonun algılanan süresi **yalnızca sertliğe** bağlı; alınan
 * yola değil. Aynı sertlik uzun bir yolda kırbaç gibi, kısa bir yolda uyuşuk
 * görünür. Bu yüzden ayrım mesafeye göre yapılıyor — böylece ekrandaki her
 * hareket kabaca aynı **hızda** oluyor ve göz bunu tek bir sistem olarak
 * okuyor:
 *
 * | Belirteç | Yol | Nerede |
 * |---|---|---|
 * | [small]  | ~40dp altı | basış durumu, köşe yarıçapı, anahtar başlığı |
 * | [medium] | bileşen boyu | gezinme göstergesi, eylem menüsü, kart yüzü |
 * | [large]  | ekran boyu | sayfa geçişi, alt sayfa, büyük gösterge |
 *
 * ### Uzamsal ve etkisel ayrımı
 *
 * [small]/[medium]/[large] **konum ve boyut** içindir; sönümleme oranı 1'in
 * altında olduğu için hedefi aşarlar ve hareketi canlı kılan şey budur.
 * Saydamlık ve renkte aşma istenmez: %100'ü aşan bir saydamlık kırpılır, geri
 * dönerken de titreme olarak görünür. [effect] bu yüzden hiç aşmıyor.
 *
 * ### Giriş ve çıkış neden eşit değil
 *
 * [ENTER_MILLIS] > [EXIT_MILLIS]. Gelen yüzey dikkati üstüne çeker, gidenin
 * ise oyalanmaya hakkı yok: kullanıcı kapatma kararını çoktan vermiş.
 */
object KasaMotion {

    /** Küçük yollar: basış, köşe, anahtar başlığı. */
    @Composable
    fun <T> small(): FiniteAnimationSpec<T> = spec(DAMPING_SPATIAL, Spring.StiffnessMedium)

    /** Bileşen boyu yollar: gösterge, menü, satır. */
    @Composable
    fun <T> medium(): FiniteAnimationSpec<T> = spec(DAMPING_SPATIAL, Spring.StiffnessMediumLow)

    /** Ekran boyu yollar: sayfa geçişi, alt sayfa, gösterge kadranı. */
    @Composable
    fun <T> large(): FiniteAnimationSpec<T> = spec(DAMPING_SPATIAL_LARGE, Spring.StiffnessLow)

    /** Yalnızca görünüm değişiyor: saydamlık, renk, yükseklik. Aşma yok. */
    @Composable
    fun <T> effect(): FiniteAnimationSpec<T> = spec(DAMPING_EFFECT, Spring.StiffnessMedium)

    /** Belirli süreli giriş. Yayla ölçülemeyen yerlerde (sıralı geçişler). */
    @Composable
    fun <T> enter(): FiniteAnimationSpec<T> = timed(ENTER_MILLIS)

    /** Belirli süreli çıkış. */
    @Composable
    fun <T> exit(): FiniteAnimationSpec<T> = timed(EXIT_MILLIS)

    /**
     * Sıralı beliriş: listedeki her öğe bir öncekinden [STAGGER_MILLIS] sonra
     * geliyor.
     *
     * Aynı anda beliren dört öğe tek bir blok olarak okunuyor ve aralarındaki
     * sıra kayboluyor; sırayla gelince göz onları ayrı ayrı görüyor ve menünün
     * hangi uçtan açıldığı anlaşılıyor. Gecikme bilerek küçük: 26 ms fark
     * edilmiyor ama toplamı hissediliyor.
     *
     * @param step kaçıncı sırada belireceği. Sıfır beklemeden gelir.
     */
    @Composable
    fun <T> stagger(step: Int, millis: Int = ENTER_MILLIS): FiniteAnimationSpec<T> {
        val reduced = LocalReducedMotion.current
        return remember(reduced, step, millis) {
            if (reduced) snap()
            else tween(durationMillis = millis, delayMillis = step * STAGGER_MILLIS)
        }
    }

    @Composable
    private fun <T> spec(damping: Float, stiffness: Float): FiniteAnimationSpec<T> {
        val reduced = LocalReducedMotion.current
        return remember(reduced, damping, stiffness) {
            if (reduced) snap() else spring(dampingRatio = damping, stiffness = stiffness)
        }
    }

    @Composable
    private fun <T> timed(millis: Int): FiniteAnimationSpec<T> {
        val reduced = LocalReducedMotion.current
        return remember(reduced, millis) {
            if (reduced) snap() else tween(durationMillis = millis)
        }
    }

    const val ENTER_MILLIS = 240
    const val STAGGER_MILLIS = 26
    const val EXIT_MILLIS = 160

    private const val DAMPING_SPATIAL = 0.62f
    private const val DAMPING_SPATIAL_LARGE = 0.78f
    private const val DAMPING_EFFECT = 1f
}

/**
 * Deneysel yüzey efektleri açık mı.
 *
 * ### Neden CompositionLocal
 *
 * Efektler `Modifier` uzantıları olarak yazıldı ve bileşen ağacının her
 * yerinde kullanılıyorlar. Bayrağı parametre olarak taşımak, aradaki her
 * bileşene ilgilenmediği bir alan eklemek olurdu: kart yüzü, liste satırı,
 * döşeme, başlık — hiçbirinin bu kararla işi yok, yalnızca içinden geçiyor.
 *
 * Varsayılan `false`: bir efekt, açık olduğu açıkça sağlanmadıkça
 * çalışmamalı. Ters varsayılan, önizlemelerde ve testlerde sensör
 * dinleyicisi kuran bir arayüz üretirdi.
 */
val LocalExperimentalEffects = staticCompositionLocalOf { false }
