package app.kasa.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
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
 * ### Ne doldurulabiliyor
 *
 * | Form | Kaynak | Yazılan alanlar |
 * |---|---|---|
 * | Giriş | giriş kaydı | kullanıcı adı, parola, varsa tek kullanımlık kod |
 * | Üye ol | üretilen parola | parola ve tekrarı |
 * | İki adımlı doğrulama | TOTP anahtarı olan kayıt | kod |
 * | Ödeme | kart kaydı | numara, sahip, son kullanma, güvenlik kodu |
 *
 * Hangisinin geçerli olduğuna [StructureParser.Result.kind] karar veriyor.
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
    ): FillResponse? {
        val candidates = repository.autofillCandidates()

        var matches = AutofillMatcher.offline(candidates, caller, parsed.kind)

        if (matches.isEmpty() && !caller.isBrowser) {
            val verify = container.settingsStore.settings.first().autofillVerifyDomains
            if (verify) matches = AutofillMatcher.delegated(candidates, caller, links, parsed.kind)
        }

        val builder = FillResponse.Builder()
        var index = 0

        matches.forEach { match ->
            val dataset = fillDataset(request, parsed, match.item, index) ?: return@forEach
            builder.addDataset(dataset)
            index++
        }

        // Kayıt formunda parola üretme satırı.
        //
        // Kasada eşleşen bir kayıt olsa bile duruyor: üye olma formunda istenen
        // şey zaten var olan bir parola değil, yenisi. Satır kimlik
        // doğrulamasından geçiyor, çünkü üretilen parola aynı anda kasaya da
        // yazılıyor — doldurulup kaydedilmeyen bir parola, kullanıcının bir
        // daha hiçbir yerde bulamayacağı bir paroladır.
        if (parsed.registration && parsed.passwordIds.isNotEmpty()) {
            builder.addDataset(
                actionDataset(
                    request = request,
                    parsed = parsed,
                    index = index,
                    title = getString(R.string.af_generate_entry),
                    subtitle = getString(R.string.af_generate_subtitle),
                    requestCode = REQUEST_GENERATE,
                    mode = AutofillUnlockActivity.MODE_GENERATE
                )
            )
            index++
        }

        // Eşleşme yoksa kasadan rastgele kayıtlar **sunulmuyor**.
        //
        // Parola alanı olan herhangi bir uygulamanın kullanıcının hesaplarını
        // menüde görmesi, doldurmanın en geniş kapısıydı. Onun yerine tek bir
        // "Kasa'dan seç" satırı dönüyor; o satır kimlik doğrulamasından geçiyor
        // ve seçim Kasa'nın kendi ekranında yapılıyor. Kullanıcının orada
        // seçtiği kayıt uygulamayla kalıcı olarak eşleşiyor, yani bu yol
        // yalnızca bir kez yürünüyor.
        //
        // Kullanıcı menüyü kendisi açtıysa satır eşleşme varken de duruyor: o
        // hareket zaten "önerilenler işime yaramadı" demek.
        val manual = (request.flags and FillRequest.FLAG_MANUAL_REQUEST) != 0
        if (index == 0 || manual) {
            builder.addDataset(
                actionDataset(
                    request = request,
                    parsed = parsed,
                    index = index,
                    title = getString(R.string.af_browse_entry),
                    subtitle = getString(R.string.app_name),
                    requestCode = REQUEST_BROWSE,
                    mode = AutofillUnlockActivity.MODE_BROWSE
                )
            )
            index++
        }

        if (index == 0) return null

        builder.setSaveInfo(buildSaveInfo(parsed))
        return builder.build()
    }

    /**
     * Bir kayıttan veri kümesi.
     *
     * Hangi alana ne yazılacağına [AutofillFiller] karar veriyor; burada
     * kalan tek iş sunum. Aynı kararı kimlik doğrulama penceresi de veriyor ve
     * ikisinin ayrışmaması için mantık orada değil, ortak yerde duruyor.
     */
    private fun fillDataset(
        request: FillRequest,
        parsed: StructureParser.Result,
        item: VaultItem,
        index: Int
    ): Dataset? {
        val fields = AutofillFiller.values(parsed, item)
        if (fields.isEmpty()) return null

        val subtitle = AutofillFiller.subtitle(this, parsed, item)
        val menu = Presentations.menu(this, item.name, subtitle)
        val inline = Presentations.inline(this, request, index, item.name, subtitle)

        val builder = Dataset.Builder()
        fields.forEach { field ->
            builder.setValueCompat(field.id, AutofillValue.forText(field.value), menu, inline)
        }
        return builder.build()
    }

    /** Kilitliyken: tek bir "kilidi aç" satırı, kimlik doğrulamaya bağlı. */
    private fun lockedResponse(request: FillRequest, parsed: StructureParser.Result): FillResponse =
        FillResponse.Builder()
            .addDataset(
                actionDataset(
                    request = request,
                    parsed = parsed,
                    index = 0,
                    title = getString(R.string.af_locked_entry),
                    subtitle = getString(R.string.app_name),
                    requestCode = REQUEST_UNLOCK,
                    mode = AutofillUnlockActivity.MODE_UNLOCK
                )
            )
            .setSaveInfo(buildSaveInfo(parsed))
            .build()

    /**
     * Değer taşımayan, dokununca Kasa'yı açan satır.
     *
     * Kimlik doğrulamalı veri kümesinde değerler yer tutucudur; gerçek
     * değerleri doğrulama sonrası dönen yanıt taşır. [mode] o ekranın ne
     * yapacağını söylüyor: kilidi açmak, kayıt seçtirmek ya da parola üretmek.
     */
    private fun actionDataset(
        request: FillRequest,
        parsed: StructureParser.Result,
        index: Int,
        title: String,
        subtitle: String,
        requestCode: Int,
        mode: String
    ): Dataset {
        val menu = Presentations.menu(this, title, subtitle)
        val inline = Presentations.inline(this, request, index, title, subtitle)

        val intent = Intent(this, AutofillUnlockActivity::class.java)
            .putExtra(AutofillUnlockActivity.EXTRA_MODE, mode)
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val builder = Dataset.Builder()
        parsed.allIds().forEach { id -> builder.setValueCompat(id, null, menu, inline) }
        builder.setAuthentication(pendingIntent.intentSender)
        return builder.build()
    }

    /**
     * Kaydetme penceresinin ne soracağı.
     *
     * ### Neden `SAVE_ON_ALL_VIEWS_INVISIBLE`
     *
     * Giriş akışlarının çoğu iki ekrana bölünmüş durumda: önce kullanıcı adı,
     * sonra parola. Bu bayrak olmadan sistem kaydetmeyi ilk ekran kapanırken
     * soruyor — yani parola daha yazılmamışken — ve kaydetme hiç
     * gerçekleşmiyor. Bayrakla birlikte soru, izlenen alanların hepsi
     * ekrandan çekildiğinde geliyor.
     */
    private fun buildSaveInfo(parsed: StructureParser.Result): SaveInfo {
        val required: Array<AutofillId>
        val optional: Array<AutofillId>
        val type: Int

        if (parsed.kind == StructureParser.Kind.CARD) {
            required = listOfNotNull(parsed.card.number).toTypedArray()
            optional = listOfNotNull(
                parsed.card.holder,
                parsed.card.expiryMonth,
                parsed.card.expiryYear,
                parsed.card.expiryDate,
                parsed.card.cvv
            ).toTypedArray()
            type = SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD
        } else {
            required = parsed.passwordIds.toTypedArray()
            optional = listOfNotNull(parsed.usernameId).toTypedArray()
            type = when {
                parsed.passwordIds.isNotEmpty() && parsed.usernameId != null ->
                    SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
                parsed.passwordIds.isNotEmpty() -> SaveInfo.SAVE_DATA_TYPE_PASSWORD
                else -> SaveInfo.SAVE_DATA_TYPE_USERNAME
            }
        }

        val ids: Array<AutofillId> = if (required.isNotEmpty()) required else optional
        return SaveInfo.Builder(type, ids)
            .apply { if (required.isNotEmpty() && optional.isNotEmpty()) setOptionalIds(optional) }
            .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            .build()
    }

    /**
     * Kullanıcı bir uygulamada yeni parola ya da kart girdiğinde sistem burayı
     * çağırır.
     *
     * Kasa kilitliyse kaydetmeyi reddederiz — girilen değeri geçici olarak bir
     * yerde tutup sonra yazmak, tam da kaçındığımız şey olurdu.
     *
     * ### Neden bütün bağlamlar taranıyor
     *
     * İki ekrana bölünmüş giriş akışlarında kullanıcı adı ilk ekranda, parola
     * ikincisinde yazılıyor ve sistem her ekranı ayrı bir bağlam olarak
     * veriyor. Yalnızca sonuncusuna bakmak kullanıcı adını her seferinde boş
     * bırakıyordu — kayıt açılıyor ama kimin hesabı olduğu yazmıyordu.
     *
     * ### Güncelleme mi, yeni kayıt mı
     *
     * Karar [VaultRepository.saveFromAutofill] ile
     * [VaultRepository.saveCardFromAutofill] içinde ve gerekçeleri orada
     * yazılı. Buradaki iş yalnızca değerleri toplamak.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            callback.onFailure(getString(R.string.imp_failed))
            return
        }

        val container = KasaApplication.container(this)
        val repository = container.vaultRepository
        if (!repository.isUnlocked) {
            callback.onFailure(getString(R.string.af_unlock_prompt))
            return
        }

        val harvest = harvest(contexts)
        val parsed = harvest.parsed
        val caller = CallerIdentity.of(this, parsed.packageName, parsed.webDomain, parsed.isBrowser)
        val name = parsed.webDomain
            ?: parsed.packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
            ?: getString(R.string.app_name)

        if (parsed.kind == StructureParser.Kind.CARD) {
            val number = harvest.value(parsed.card.number).orEmpty()
            if (number.filter { it.isDigit() }.length < 12) {
                callback.onFailure(getString(R.string.imp_failed))
                return
            }
            scope.launch {
                repository.saveCardFromAutofill(
                    name = name,
                    number = number,
                    holder = harvest.value(parsed.card.holder).orEmpty(),
                    expiry = cardExpiry(harvest, parsed.card),
                    cvv = harvest.value(parsed.card.cvv).orEmpty(),
                    linkToken = caller.linkToken()
                )
            }
            callback.onSuccess()
            return
        }

        val username = harvest.value(parsed.usernameId).orEmpty()
        val password = parsed.passwordIds.firstNotNullOfOrNull { harvest.value(it) }.orEmpty()

        if (password.isBlank()) {
            callback.onFailure(getString(R.string.imp_failed))
            return
        }

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
     * Son kullanma tarihi dört haneye indiriliyor: AAYY.
     *
     * Form ay ile yılı ayrı ayrı da isteyebiliyor, tek alanda da. Yıl dört
     * haneyse ("2029") son iki hanesi alınıyor, çünkü kasada kartın üstünde
     * yazdığı gibi iki hane duruyor.
     */
    private fun cardExpiry(harvest: Harvest, card: StructureParser.CardFields): String {
        val combined = harvest.value(card.expiryDate)?.filter { it.isDigit() }
        if (combined != null && combined.length >= 4) {
            return combined.take(2) + combined.takeLast(2)
        }
        val month = harvest.value(card.expiryMonth)?.filter { it.isDigit() }?.padStart(2, '0')
        val year = harvest.value(card.expiryYear)?.filter { it.isDigit() }?.takeLast(2)
        if (month.isNullOrBlank() || year.isNullOrBlank()) return ""
        return month.takeLast(2) + year
    }

    /**
     * Bütün bağlamlardan toplanan alan değerleri.
     *
     * Alan kimlikleri bağlamlar arasında korunuyor, yani ilk ekranda yazılan
     * kullanıcı adı son bağlamın ayrıştırma sonucuyla birlikte kullanılabiliyor.
     */
    private class Harvest(
        val parsed: StructureParser.Result,
        private val values: Map<AutofillId, String>
    ) {
        fun value(id: AutofillId?): String? = id?.let { values[it]?.takeIf { v -> v.isNotBlank() } }
    }

    private fun harvest(contexts: List<FillContext>): Harvest {
        val values = LinkedHashMap<AutofillId, String>()
        var parsed: StructureParser.Result? = null

        contexts.forEach { context ->
            val result = StructureParser(context.structure).parse()
            // Sonuncusu kazanıyor: kullanıcının en son gördüğü ekran, formun ne
            // olduğunu en iyi anlatan ekran.
            if (result.usable) parsed = result
            collect(context.structure, values)
        }

        return Harvest(parsed ?: StructureParser(contexts.last().structure).parse(), values)
    }

    private fun collect(structure: AssistStructure, into: MutableMap<AutofillId, String>) {
        for (i in 0 until structure.windowNodeCount) {
            collect(structure.getWindowNodeAt(i).rootViewNode, into)
        }
    }

    private fun collect(node: AssistStructure.ViewNode, into: MutableMap<AutofillId, String>) {
        val id = node.autofillId
        if (id != null) {
            val value = node.autofillValue
            val text = when {
                value != null && value.isText -> value.textValue.toString()
                else -> node.text?.toString()
            }
            // Boş değer yazılmıyor: sonraki bağlamda dolu gelen aynı alanı
            // ezmesin.
            if (!text.isNullOrBlank()) into[id] = text
        }
        for (i in 0 until node.childCount) collect(node.getChildAt(i), into)
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

    private companion object {
        const val REQUEST_UNLOCK = 9021
        const val REQUEST_BROWSE = 9022
        const val REQUEST_GENERATE = 9023
    }
}
