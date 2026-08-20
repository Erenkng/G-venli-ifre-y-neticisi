package app.kasa.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.autofill.InlinePresentation
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.graphics.drawable.IconCompat
import app.kasa.MainActivity
import app.kasa.R

/**
 * Bir veri kümesinin nasıl görüneceği.
 *
 * ### İki ayrı sunum, tek veri kümesi
 *
 * Otomatik doldurmanın iki yüzü var. **Açılır menü** alanın altında açılan
 * listedir; her zaman çalışır. **Satır içi öneri** ise klavyenin üst şeridinde
 * çıkar ve otomatik doldurmanın hızlı hissedilmesinin tek sebebi odur:
 * kullanıcı zaten klavyeye bakıyor, öneri oradaysa tek dokunuş yeter. Açılır
 * menü her seferinde iki dokunuş — önce alana, sonra öneriye.
 *
 * Manifestte `supportsInlineSuggestions="true"` yazıyordu ama servis satır içi
 * sunum üretmediği için şeritte hiçbir şey çıkmıyordu; bildirim tek başına
 * hiçbir şey yapmıyor.
 *
 * ### Klavye kabul etmezse
 *
 * Satır içi öneri klavyenin desteğine bağlı. Sistem her istekte ne kadar
 * öneriye yer olduğunu ve hangi görsel sürümü anladığını bildiriyor; ikisi de
 * uymazsa satır içi sunum eklenmiyor ve veri kümesi açılır menüde görünmeye
 * devam ediyor. Bu yüzden açılır menü sunumu her zaman kuruluyor — satır içi
 * olan ona ek, alternatifi değil.
 */
object Presentations {

    /** Açılır menüdeki satır. */
    fun menu(context: Context, title: String, subtitle: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
        }

    /**
     * Klavye şeridindeki öneri.
     *
     * @param index kaçıncı öneri; sistem her sıra için ayrı bir ölçü veriyor
     * @return klavye satır içi öneriyi kabul etmiyorsa `null`
     */
    fun inline(
        context: Context,
        request: android.service.autofill.FillRequest,
        index: Int,
        title: String,
        subtitle: String?
    ): InlinePresentation? {
        val inlineRequest = request.inlineSuggestionsRequest ?: return null
        if (index >= inlineRequest.maxSuggestionCount) return null

        val specs = inlineRequest.inlinePresentationSpecs
        if (specs.isEmpty()) return null
        // Sistem daha az ölçü verdiyse sonuncusu tekrarlanıyor; belgelenen
        // davranış bu.
        val spec: InlinePresentationSpec = specs.getOrNull(index) ?: specs.last()

        // Klavyenin anladığı görsel sürüm bizimkini kapsıyor mu?
        if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null

        return runCatching {
            // Uzun basıldığında açılan ekran. Sistem burada boş geçilmesine izin
            // vermiyor; uygulamanın kendi ekranı en anlamlı hedef.
            val attribution = PendingIntent.getActivity(
                context,
                REQUEST_ATTRIBUTION,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val content = InlineSuggestionUi.newContentBuilder(attribution)
                .setTitle(title)
                .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
                .setStartIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_monochrome))
                .build()

            InlinePresentation(content.slice, spec, /* pinned = */ false)
        }.getOrNull()
    }

    private const val REQUEST_ATTRIBUTION = 9030
}
