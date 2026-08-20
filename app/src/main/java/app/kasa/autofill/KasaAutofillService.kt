package app.kasa.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import app.kasa.KasaApplication
import app.kasa.R
import app.kasa.core.crypto.SecretText
import app.kasa.core.util.Totp
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Kasa'nın otomatik doldurma servisi.
 *
 * Kilitli kasa doldurma yapmaz — yapamaz da, çünkü kayıtlar bellekte
 * çözülmüş hâlde durmaz. Bu durumda sistem menüsünde "Kasa kilitli, açmak
 * için dokun" satırı gösterilir; dokunulduğunda kimlik doğrulama akışı
 * ([AutofillUnlockActivity]) çalışır ve ancak ondan sonra gerçek öneriler
 * gelir. Bu, "kolaylık olsun diye kasayı açık tutalım" tuzağına düşmeden
 * otomatik doldurma sunmanın tek doğru yolu.
 *
 * ### Yanıt neden bazen gecikmeli
 *
 * Eşleştirme çoğu istekte anında bitiyor: bağı kurulmuş uygulama ya da
 * tarayıcıdaki alan adı. Yalnızca ikisi de tutmadığında ve kullanıcı alan adı
 * doğrulamasını açık bıraktıysa [DigitalAssetLinks] devreye giriyor ve o bir
 * ağ isteği. O yüzden yanıt bir eşyordamda kuruluyor; sistemin iptal işareti
 * de ona bağlı, kullanıcı ekranı kapattığında istek boşuna sürmüyor.
 */
class KasaAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val links: DigitalAssetLinks by lazy { DigitalAssetLinks(this) }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val context = request.fillContexts.lastOrNull()
        if (context == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = StructureParser(context.structure).parse()
        if (!parsed.usable) {
            callback.onSuccess(null)
            return
        }

        // Kasanın kendi ekranlarını doldurmaya çalışma.
        if (parsed.packageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val container = KasaApplication.container(this)
        val repository = container.vaultRepository
        val caller = CallerIdentity.of(this, parsed.packageName, parsed.webDomain, parsed.isBrowser)

        if (!repository.isUnlocked) {
            callback.onSuccess(lockedResponse(request, parsed))
            return
        }

        val job = scope.launch {
            val response = runCatching { buildResponse(request, parsed, caller, repository, container) }
                .getOrNull()
            callback.onSuccess(response)
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    private suspend fun buildResponse(
        request: FillRequest,
        parsed: StructureParser.Result,
        caller: CallerIdentity,
        repository: VaultRepository,
        container: app.kasa.AppContainer
    ): FillResponse {
        val candidates = repository.autofillCandidates()

        var matches = AutofillMatcher.offline(candidates, caller)

        if (matches.isEmpty() && !caller.isBrowser) {
            val verify = container.settingsStore.settings.first().autofillVerifyDomains
            if (verify) matches = AutofillMatcher.delegated(candidates, caller, links)
        }

        // Eşleşme yoksa kasadan rastgele kayıtlar **sunulmuyor**.
        //
        // Parola alanı olan herhangi bir uygulamanın kullanıcının hesaplarını
        // menüde görmesi, doldurmanın en geniş kapısıydı. Artık tek bir
        // "Kasa'dan seç" satırı dönüyor; o satır kimlik doğrulamasından geçiyor
        // ve seçim Kasa'nın kendi ekranında yapılıyor. Kullanıcının orada
        // seçtiği kayıt uygulamayla kalıcı olarak eşleşiyor, yani bu yol
        // yalnızca bir kez yürünüyor.
        if (matches.isEmpty()) return browseResponse(request, parsed)

        val builder = FillResponse.Builder()
        var index = 0
        matches.forEach { match ->
            val dataset = fillDataset(request, parsed, match.item, index) ?: return@forEach
            builder.addDataset(dataset)
            index++
        }
        if (index == 0) return browseResponse(request, parsed)

        builder.setSaveInfo(buildSaveInfo(parsed))
        return builder.build()
    }

    /**
     * Bir kayıttan veri kümesi.
     *
     * ### Tek kullanımlık kod
     *
     * Form bir kod alanı istiyorsa ve kayıtta TOTP anahtarı varsa kod burada
     * üretilip alana yazılıyor. Günlük kullanımda en çok zaman kazandıran
     * ekleme bu: kullanıcı parolayı doldurduktan sonra uygulamayı açıp kodu
     * okumak, ezberlemek ve öteki uygulamaya yazmak zorunda kalmıyor.
     *
     * Kod, kümenin kurulduğu anda üretiliyor ve otuz saniyelik pencereye
     * bağlı. Kullanıcı öneriyi geç seçerse kod geçersiz olabilir; bunun
     * alternatifi kodu hiç önermemekti.
     *
     * Yalnızca kod isteyen ekranda ([StructureParser.Result.otpOnly]) TOTP
     * anahtarı olmayan kayıtlar elenir — orada gösterecek bir şeyleri yok.
     */
    private fun fillDataset(
        request: FillRequest,
        parsed: StructureParser.Result,
        item: VaultItem,
        index: Int
    ): Dataset? {
        val code = if (parsed.otpId != null && item.totpSecret.isNotBlank()) {
            Totp.code(item.totpSecret, item.totpDigits, item.totpPeriod, item.totpAlgorithm)
        } else null

        if (parsed.otpOnly && code == null) return null

        val subtitle = when {
            parsed.otpOnly -> getString(R.string.af_otp_subtitle)
            else -> item.username.ifBlank { item.host().orEmpty() }
        }

        val menu = Presentations.menu(this, item.name, subtitle)
        val inline = Presentations.inline(this, request, index, item.name, subtitle)

        var wrote = false
        val builder = Dataset.Builder()

        fun put(id: AutofillId?, value: String?) {
            if (id == null || value == null) return
            builder.setValueCompat(id, AutofillValue.forText(value), menu, inline)
            wrote = true
        }

        // Yalnızca kod isteyen ekranda kullanıcı adı ve parola yazılmıyor:
        // ikinci adımda o alanlar zaten yok, olsaydı da doldurmak yanlış olurdu.
        if (!parsed.otpOnly) {
            put(parsed.usernameId, item.username.takeIf { it.isNotBlank() })
            put(parsed.passwordId, item.password.reveal().takeIf { it.isNotBlank() })
        }
        put(parsed.otpId, code)

        return if (wrote) builder.build() else null
    }

    /** Kilitliyken: tek bir "kilidi aç" satırı, kimlik doğrulamaya bağlı. */
    private fun lockedResponse(request: FillRequest, parsed: StructureParser.Result): FillResponse =
        authenticatedResponse(
            request = request,
            parsed = parsed,
            title = getString(R.string.af_locked_entry),
            requestCode = REQUEST_UNLOCK,
            browse = false
        )

    /**
     * Eşleşme yokken: tek bir kimlik doğrulamalı "Kasa'dan seç" satırı.
     *
     * Kasa açık olsa bile o ekran ayrıca doğrulama istiyor: eşleşmeyen bir
     * uygulamaya kimlik bilgisi vermek, eşleşen birine vermekten farklı bir
     * karar.
     */
    private fun browseResponse(request: FillRequest, parsed: StructureParser.Result): FillResponse =
        authenticatedResponse(
            request = request,
            parsed = parsed,
            title = getString(R.string.af_browse_entry),
            requestCode = REQUEST_BROWSE,
            browse = true
        )

    private fun authenticatedResponse(
        request: FillRequest,
        parsed: StructureParser.Result,
        title: String,
        requestCode: Int,
        browse: Boolean
    ): FillResponse {
        val ids = listOfNotNull(parsed.usernameId, parsed.passwordId, parsed.otpId)

        val menu = Presentations.menu(this, title, getString(R.string.app_name))
        val inline = Presentations.inline(this, request, 0, title, getString(R.string.app_name))

        val intent = Intent(this, AutofillUnlockActivity::class.java)
            .putExtra(AutofillUnlockActivity.EXTRA_BROWSE, browse)
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val datasetBuilder = Dataset.Builder()
        // Kimlik doğrulamalı veri kümesinde değerler yer tutucudur; gerçek
        // değerleri doğrulama sonrası dönen yanıt taşır.
        ids.forEach { id -> datasetBuilder.setValueCompat(id, null, menu, inline) }
        datasetBuilder.setAuthentication(pendingIntent.intentSender)

        return FillResponse.Builder()
            .addDataset(datasetBuilder.build())
            .setSaveInfo(buildSaveInfo(parsed))
            .build()
    }

    private fun buildSaveInfo(parsed: StructureParser.Result): SaveInfo {
        val required = listOfNotNull(parsed.passwordId).toTypedArray()
        val optional = listOfNotNull(parsed.usernameId).toTypedArray()

        val type = when {
            parsed.passwordId != null && parsed.usernameId != null ->
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
            parsed.passwordId != null -> SaveInfo.SAVE_DATA_TYPE_PASSWORD
            else -> SaveInfo.SAVE_DATA_TYPE_USERNAME
        }

        val ids: Array<AutofillId> = if (required.isNotEmpty()) required else optional
        return SaveInfo.Builder(type, ids)
            .apply { if (required.isNotEmpty() && optional.isNotEmpty()) setOptionalIds(optional) }
            .build()
    }

    /**
     * Kullanıcı bir uygulamada yeni parola girdiğinde sistem burayı çağırır.
     *
     * Kasa kilitliyse kaydetmeyi reddederiz — parolayı geçici olarak bir yerde
     * tutup sonra yazmak, tam da kaçındığımız şey olurdu.
     *
     * ### Güncelleme mi, yeni kayıt mı
     *
     * Karar [VaultRepository.saveFromAutofill] içinde ve gerekçesi orada
     * yazılı. Buradaki iş yalnızca değerleri toplamak ve sonucu kullanıcıya
     * doğru sözcükle söylemek: "güncellendi" ile "eklendi" farklı şeyler ve
     * ikisini aynı iletiyle geçmek, kasasında kaç kayıt olduğunu bilmeyen bir
     * kullanıcı bırakır.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val context = request.fillContexts.lastOrNull()
        if (context == null) {
            callback.onFailure(getString(R.string.imp_failed))
            return
        }

        val container = KasaApplication.container(this)
        val repository = container.vaultRepository
        if (!repository.isUnlocked) {
            callback.onFailure(getString(R.string.af_unlock_prompt))
            return
        }

        val parsed = StructureParser(context.structure).parse()
        val username = parsed.usernameId?.let { findValue(context.structure, it) }.orEmpty()
        val password = parsed.passwordId?.let { findValue(context.structure, it) }.orEmpty()

        if (password.isBlank()) {
            callback.onFailure(getString(R.string.imp_failed))
            return
        }

        val caller = CallerIdentity.of(this, parsed.packageName, parsed.webDomain, parsed.isBrowser)
        val name = parsed.webDomain
            ?: parsed.packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
            ?: getString(R.string.app_name)

        scope.launch {
            repository.saveFromAutofill(
                name = name,
                username = username,
                password = SecretText.of(password),
                url = parsed.webDomain.orEmpty(),
                linkToken = caller.linkToken()
            )
        }
        callback.onSuccess()
    }

    /**
     * Değeri ve iki sunumu birlikte yazar.
     *
     * Satır içi sunum yalnızca klavye destekliyorsa üretiliyor; olmadığında
     * dört argümanlı aşırı yüklemeyi `null` ile çağırmak yerine üç argümanlı
     * olan kullanılıyor, çünkü sistem orada `null` kabul etmiyor.
     */
    private fun Dataset.Builder.setValueCompat(
        id: AutofillId,
        value: AutofillValue?,
        menu: android.widget.RemoteViews,
        inline: android.service.autofill.InlinePresentation?
    ) {
        if (inline != null) setValue(id, value, menu, inline) else setValue(id, value, menu)
    }

    /** Yapı ağacında belirli bir alanın kullanıcı tarafından girilmiş değerini bulur. */
    private fun findValue(structure: android.app.assist.AssistStructure, id: AutofillId): String? {
        for (i in 0 until structure.windowNodeCount) {
            val found = findValue(structure.getWindowNodeAt(i).rootViewNode, id)
            if (found != null) return found
        }
        return null
    }

    private fun findValue(node: android.app.assist.AssistStructure.ViewNode, id: AutofillId): String? {
        if (node.autofillId == id) {
            val value = node.autofillValue
            if (value != null && value.isText) return value.textValue.toString()
            node.text?.let { return it.toString() }
        }
        for (i in 0 until node.childCount) {
            val found = findValue(node.getChildAt(i), id)
            if (found != null) return found
        }
        return null
    }

    private companion object {
        const val REQUEST_UNLOCK = 9021
        const val REQUEST_BROWSE = 9022
    }
}
