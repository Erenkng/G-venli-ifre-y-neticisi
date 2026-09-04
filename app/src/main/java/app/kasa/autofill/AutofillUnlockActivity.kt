package app.kasa.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.kasa.KasaApplication
import app.kasa.R
import app.kasa.core.util.PasswordGenerator
import app.kasa.core.crypto.SecretText
import app.kasa.data.SettingsStore
import app.kasa.data.model.VaultItem
import app.kasa.data.repo.VaultRepository
import app.kasa.ui.BiometricGate
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Otomatik doldurma akışının kimlik doğrulama penceresi.
 *
 * Sistem, kilitli kasadan gelen "doğrulama gerekiyor" veri kümesine
 * dokunulduğunda bu Activity'yi açar. Kasa açıldıktan sonra gerçek
 * [FillResponse] `EXTRA_AUTHENTICATION_RESULT` ile geri verilir ve sistem
 * alanları doldurur.
 *
 * Bu Activity saydam bir pencere olarak açılır ve son uygulamalar listesine
 * girmez (`excludeFromRecents`); ekranı da `FLAG_SECURE` ile korunur, çünkü
 * burada ana parola yazılıyor.
 */
class AutofillUnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val container = KasaApplication.container(this)
        val repository = container.vaultRepository
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK

        if (repository.isUnlocked) {
            // Eşleşmeyen bir uygulamaya kimlik bilgisi vermek ya da yeni bir
            // kayıt açmak ayrı bir karar: kasa açık olsa bile burada bir kez
            // daha kim olduğu soruluyor.
            if (mode == MODE_UNLOCK) {
                finishWithResponse(mode)
            } else {
                BiometricGate(this).authenticatePresence(
                    title = getString(
                        if (mode == MODE_GENERATE) R.string.af_generate_entry
                        else R.string.af_browse_entry
                    ),
                    subtitle = getString(R.string.af_unlock_prompt),
                    onSuccess = { finishWithResponse(mode) },
                    onCancel = { setResult(Activity.RESULT_CANCELED); finish() }
                )
            }
            return
        }

        setContent {
            val settings by container.settingsStore.settings
                .collectAsState(initial = SettingsStore.Settings())

            KasaTheme(
                themeMode = settings.theme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack
            ) {
                UnlockDialog(
                    repository = repository,
                    biometricEnabled = settings.biometricUnlock,
                    onUnlocked = { finishWithResponse(mode) },
                    onCancel = { setResult(Activity.RESULT_CANCELED); finish() }
                )
            }
        }
    }

    /** Kasa açıldı: istenen işi yap ve doldurulacak yanıtı geri ver. */
    private fun finishWithResponse(mode: String) {
        val structure = intent.getParcelableExtra<android.app.assist.AssistStructure>(
            AutofillManager.EXTRA_ASSIST_STRUCTURE
        )
        if (structure == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val parsed = StructureParser(structure).parse()
        val repository = KasaApplication.container(this).vaultRepository
        val caller = CallerIdentity.of(this, parsed.packageName, parsed.webDomain, parsed.isBrowser)

        if (mode == MODE_GENERATE) {
            generateAndFinish(structure, parsed, caller, repository)
            return
        }

        val candidates = repository.autofillCandidates()
        val matched = AutofillMatcher.offline(candidates, caller, parsed.kind).map { it.item }

        // Eşleşme yoksa kullanıcı kendi seçsin diye son kullanılan kayıtlar
        // listeleniyor. Bu liste bir **öneri** değil, bir seçim ekranı: buraya
        // gelinmiş olması zaten hiçbir güvenilir bağın kurulamadığı anlamına
        // geliyor ve kullanıcı hangi uygulamanın istediğini görerek seçiyor.
        //
        // Liste formun türüne göre süzülüyor: ödeme formunda kartlar, giriş
        // formunda girişler. Karışık bir liste, kullanıcıya oraya
        // yazılamayacak kayıtları seçtirmeye çalışırdı.
        val offered = matched.ifEmpty {
            candidates
                .filter { AutofillMatcher.fillable(it, parsed.kind) }
                .sortedByDescending { it.lastUsedAt }
                .take(8)
        }

        val builder = FillResponse.Builder()
        var added = 0
        offered.forEach { item ->
            val dataset = buildDataset(parsed, item) ?: return@forEach
            builder.addDataset(dataset)
            added++
        }

        if (added == 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Kullanıcı bu ekrandan bir kayıt seçtiğinde hangisini seçtiğini sistem
        // bize söylemiyor; seçim doğrudan hedef uygulamaya gidiyor. Bu yüzden
        // bağ, eşleşme bulunamamış olsa bile **sunulan tek kayıt** varsa
        // kuruluyor: orada belirsizlik yok. Birden çok kayıt sunulduğunda bağ
        // kaydetme akışında kuruluyor (saveFromAutofill).
        val token = caller.linkToken()
        if (token != null && matched.isEmpty() && offered.size == 1) {
            val id = offered.first().id
            lifecycleScope.launch { repository.linkApp(id, token) }
        }

        val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, builder.build())
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    /**
     * Üye olma formu: yeni parola üret, kasaya yaz, alanları doldur.
     *
     * ### Neden önce kasaya yazılıyor
     *
     * Kaydetme akışının çalışacağına güvenmek burada yeterli değil. Uygulama
     * kaydetmeyi tetiklemezse — ki bölünmüş formlarda bu sık oluyor —
     * kullanıcı, bir daha hiçbir yerde bulamayacağı bir parolayla üye olmuş
     * oluyor. Kaybedilen şey bir kayıt değil, hesabın kendisi. Kullanıcı üye
     * olmaktan vazgeçerse kasada kullanılmayan bir kayıt kalıyor; iki
     * sonucun bedeli arasında karşılaştırma bile yok.
     *
     * Kullanıcı adı formun kendisinden okunuyor: kullanıcı onu zaten yazmış
     * oluyor ve kayıt o adla açılınca kasada aranabilir hâle geliyor.
     */
    private fun generateAndFinish(
        structure: android.app.assist.AssistStructure,
        parsed: StructureParser.Result,
        caller: CallerIdentity,
        repository: VaultRepository
    ) {
        if (parsed.passwordIds.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val generated = PasswordGenerator.generate(PasswordGenerator.Options())
        val username = parsed.usernameId?.let { readValue(structure, it) }.orEmpty()
        val name = parsed.webDomain
            ?: parsed.packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
            ?: getString(R.string.app_name)

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.autofill_title, getString(R.string.af_generate_entry))
            setTextViewText(R.id.autofill_subtitle, name)
        }

        val builder = Dataset.Builder()
        parsed.passwordIds.forEach { id ->
            builder.setValue(id, AutofillValue.forText(generated.value), presentation)
        }
        val usernameId = parsed.usernameId
        if (usernameId != null && username.isNotBlank()) {
            builder.setValue(usernameId, AutofillValue.forText(username), presentation)
        }

        val response = FillResponse.Builder().addDataset(builder.build()).build()

        lifecycleScope.launch {
            repository.saveFromAutofill(
                name = name,
                username = username,
                password = SecretText.of(generated.value),
                url = parsed.webDomain.orEmpty(),
                linkToken = caller.linkToken()
            )
            val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    /**
     * Tek kayıttan veri kümesi.
     *
     * Hangi alana ne yazılacağına [AutofillFiller] karar veriyor — servisin
     * doldurma yoluyla birebir aynı mantık, çünkü kullanıcı için "önerilen
     * kayıt" ile "seçtiğim kayıt" arasında bir fark yok.
     */
    private fun buildDataset(parsed: StructureParser.Result, item: VaultItem): Dataset? {
        val fields = AutofillFiller.values(parsed, item)
        if (fields.isEmpty()) return null

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.autofill_title, item.name)
            setTextViewText(R.id.autofill_subtitle, AutofillFiller.subtitle(this@AutofillUnlockActivity, parsed, item))
        }

        val builder = Dataset.Builder()
        fields.forEach { field ->
            builder.setValue(field.id, AutofillValue.forText(field.value), presentation)
        }
        return builder.build()
    }

    /** Formda kullanıcının yazdığı değer. */
    private fun readValue(
        structure: android.app.assist.AssistStructure,
        id: android.view.autofill.AutofillId
    ): String? {
        for (i in 0 until structure.windowNodeCount) {
            val found = readValue(structure.getWindowNodeAt(i).rootViewNode, id)
            if (found != null) return found
        }
        return null
    }

    private fun readValue(
        node: android.app.assist.AssistStructure.ViewNode,
        id: android.view.autofill.AutofillId
    ): String? {
        if (node.autofillId == id) {
            val value = node.autofillValue
            if (value != null && value.isText) return value.textValue.toString()
            node.text?.let { return it.toString() }
        }
        for (i in 0 until node.childCount) {
            val found = readValue(node.getChildAt(i), id)
            if (found != null) return found
        }
        return null
    }

    @Composable
    private fun UnlockDialog(
        repository: VaultRepository,
        biometricEnabled: Boolean,
        onUnlocked: () -> Unit,
        onCancel: () -> Unit
    ) {
        var password by remember { mutableStateOf("") }
        var revealed by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        var biometricTried by remember { mutableStateOf(false) }

        val gate = remember { BiometricGate(this) }

        LaunchedEffect(biometricEnabled) {
            if (!biometricTried && biometricEnabled && gate.available) {
                biometricTried = true
                val cipher = repository.biometricCipher()
                if (cipher != null) {
                    gate.authenticate(
                        title = getString(R.string.af_unlock_title),
                        subtitle = getString(R.string.af_unlock_prompt),
                        negativeButton = getString(R.string.biometric_prompt_negative),
                        cipher = cipher,
                        onSuccess = { authenticated ->
                            lifecycleScope.launch {
                                val outcome = repository.unlockWithBiometric(authenticated)
                                if (outcome is VaultRepository.UnlockOutcome.Success) onUnlocked()
                            }
                        }
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(24.dp)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KasaRadius.xl))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(24.dp)
            ) {
                Text(
                    stringResource(R.string.af_unlock_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = KasaTheme.colors.ink
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.af_unlock_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KasaTheme.colors.ink2
                )
                Spacer(Modifier.height(18.dp))
                KasaPasswordField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    label = stringResource(R.string.lock_master),
                    revealed = revealed,
                    onRevealToggle = { revealed = !revealed },
                    imeAction = ImeAction.Done,
                    isError = error,
                    supportingText = if (error) stringResource(R.string.lock_wrong) else null
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    KasaButton(
                        text = stringResource(R.string.cancel),
                        onClick = onCancel,
                        tone = ButtonTone.TONAL,
                        height = 46.dp
                    )
                    KasaButton(
                        text = stringResource(if (busy) R.string.lock_unlocking else R.string.lock_unlock),
                        onClick = {
                            val chars = password.toCharArray()
                            password = ""
                            busy = true
                            lifecycleScope.launch {
                                val wipeAfter = KasaApplication.container(this@AutofillUnlockActivity)
                                    .settingsStore.settings.first().wipeAfterAttempts
                                val outcome = repository.unlockWithPassword(chars, wipeAfter)
                                busy = false
                                if (outcome is VaultRepository.UnlockOutcome.Success) onUnlocked()
                                else error = true
                            }
                        },
                        enabled = !busy && password.isNotEmpty(),
                        height = 46.dp
                    )
                }
            }
        }
    }

    companion object {
        /** Bu ekranın ne yapacağı. Değerleri [MODE_UNLOCK] ve arkadaşları. */
        const val EXTRA_MODE = "app.kasa.autofill.MODE"

        /** Kasa kilitli; açıldıktan sonra eşleşen kayıtlar doldurulacak. */
        const val MODE_UNLOCK = "unlock"

        /**
         * "Kasa'dan seç" akışı. Eşleşme bulunamadığında servis bu kiple
         * geliyor ve burada ek doğrulama isteniyor: eşleşmeyen bir uygulamaya
         * kimlik bilgisi vermek, eşleşen birine vermekten farklı bir karar.
         */
        const val MODE_BROWSE = "browse"

        /**
         * Üye olma formunda yeni parola üret.
         *
         * Üretilen parola aynı anda kasaya da yazılıyor. Yalnızca doldurup
         * kaydetmemek, kullanıcıyı bir daha hiçbir yerde bulamayacağı bir
         * parolayla üye yapmak olurdu — kaydetme akışının çalışacağına
         * güvenmek burada yeterli değil, çünkü çalışmadığında kaybedilen şey
         * hesabın kendisi.
         */
        const val MODE_GENERATE = "generate"
    }
}
