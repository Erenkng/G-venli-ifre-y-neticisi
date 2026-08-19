package app.kasa.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
import android.widget.RemoteViews
import app.kasa.KasaApplication
import app.kasa.R
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 */
class KasaAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        val response = if (!repository.isUnlocked) {
            lockedResponse(parsed)
        } else {
            val matches = repository.matchesFor(parsed.packageName, parsed.webDomain)
            val fallback = if (matches.isEmpty()) {
                repository.data.value.items.filter { it.category == Category.LOGIN }.take(5)
            } else matches
            unlockedResponse(parsed, fallback)
        }

        callback.onSuccess(response)
    }

    /** Kilitliyken: tek bir "kilidi aç" satırı, kimlik doğrulamaya bağlı. */
    private fun lockedResponse(parsed: StructureParser.Result): FillResponse {
        val ids = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.autofill_title, getString(R.string.af_locked_entry))
            setTextViewText(R.id.autofill_subtitle, getString(R.string.app_name))
        }

        val intent = Intent(this, AutofillUnlockActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_UNLOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val datasetBuilder = Dataset.Builder()
        // Kimlik doğrulamalı veri kümesinde değerler yer tutucudur; gerçek
        // değerleri doğrulama sonrası dönen yanıt taşır.
        ids.forEach { id -> datasetBuilder.setValue(id, null, presentation) }
        datasetBuilder.setAuthentication(pendingIntent.intentSender)

        return FillResponse.Builder()
            .addDataset(datasetBuilder.build())
            .setSaveInfo(buildSaveInfo(parsed))
            .build()
    }

    /** Açıkken: eşleşen her kayıt için bir satır. */
    private fun unlockedResponse(parsed: StructureParser.Result, items: List<VaultItem>): FillResponse? {
        if (items.isEmpty()) return FillResponse.Builder().setSaveInfo(buildSaveInfo(parsed)).build()

        val builder = FillResponse.Builder()
        items.forEach { item ->
            val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
                setTextViewText(R.id.autofill_title, item.name)
                setTextViewText(
                    R.id.autofill_subtitle,
                    item.username.ifBlank { item.host().orEmpty() }
                )
            }
            val dataset = Dataset.Builder().apply {
                parsed.usernameId?.let { setValue(it, AutofillValue.forText(item.username), presentation) }
                parsed.passwordId?.let { setValue(it, AutofillValue.forText(item.password), presentation) }
            }.build()
            builder.addDataset(dataset)
        }
        builder.setSaveInfo(buildSaveInfo(parsed))
        return builder.build()
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
     * Kasa kilitliyse kaydetmeyi reddederiz — parolayı geçici olarak bir yerde
     * tutup sonra yazmak, tam da kaçındığımız şey olurdu.
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

        val name = parsed.webDomain
            ?: parsed.packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
            ?: getString(R.string.app_name)

        scope.launch {
            repository.upsert(
                VaultItem(
                    name = name,
                    category = Category.LOGIN,
                    username = username,
                    password = password,
                    url = parsed.webDomain.orEmpty(),
                    tags = listOfNotNull(parsed.packageName)
                )
            )
        }
        callback.onSuccess()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.text?.let { return it.toString() }
            }
        }
        for (i in 0 until node.childCount) {
            val found = findValue(node.getChildAt(i), id)
            if (found != null) return found
        }
        return null
    }

    private companion object {
        const val REQUEST_UNLOCK = 9021
    }
}
