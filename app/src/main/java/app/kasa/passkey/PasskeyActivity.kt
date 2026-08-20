package app.kasa.passkey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.kasa.KasaApplication
import app.kasa.R
import app.kasa.core.webauthn.PasskeyProtocol
import app.kasa.data.SettingsStore
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
 * Passkey akışının kullanıcıyla konuşan tarafı.
 *
 * [KasaCredentialProviderService] sisteme yalnızca "şu seçenekler var" diyor;
 * gerçek iş — kilidi açmak, kullanıcıya sormak, imzalamak — burada oluyor.
 * Sistem bu Activity'yi sağlayıcının döndürdüğü [android.app.PendingIntent]
 * üzerinden başlatıyor ve sonucu `setResult` ile geri alıyor.
 *
 * Üç mod var:
 *
 *  - [MODE_UNLOCK] — kilitli kasada hesap listesi gösterilemiyordu; kullanıcı
 *    "kilidi aç"a dokundu. Kilit açıldıktan sonra gerçek liste üretilip
 *    sisteme geri veriliyor ve seçici yeniden çiziliyor.
 *  - [MODE_CREATE] — yeni passkey kaydı. Kullanıcıya hangi siteye, hangi
 *    hesapla kayıt olduğu gösteriliyor ve onayı alınıyor.
 *  - [MODE_GET] — kullanıcı seçicide bir hesap seçti; o passkey ile oturum
 *    açma iddiası imzalanıyor.
 *
 * Pencere `FLAG_SECURE`: burada ana parola yazılabiliyor ve hangi sitede hangi
 * hesabın olduğu görünüyor; ikisi de ekran görüntüsüne düşmemeli.
 */
class PasskeyActivity : FragmentActivity() {

    private val repository: VaultRepository
        get() = KasaApplication.container(this).vaultRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK

        if (repository.isUnlocked) {
            proceed(mode)
            return
        }
        showUnlock(mode)
    }

    // ------------------------------------------------------------ kilit açma

    private fun showUnlock(mode: String) {
        val container = KasaApplication.container(this)
        setContent {
            val settings by container.settingsStore.settings
                .collectAsState(initial = SettingsStore.Settings())

            KasaTheme(
                themeMode = settings.theme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack
            ) {
                UnlockSheet(
                    biometricEnabled = settings.biometricUnlock,
                    onUnlocked = { proceed(mode) },
                    onCancel = { cancel(mode) }
                )
            }
        }
    }

    private fun proceed(mode: String) {
        when (mode) {
            MODE_CREATE -> handleCreate()
            MODE_GET -> handleGet()
            else -> handleUnlockOnly()
        }
    }

    /**
     * Kilit açıldı; sisteme artık gerçek hesap listesi verilebilir.
     *
     * Sistem bu cevabı alınca seçiciyi yeniden çiziyor — kullanıcı kilidi
     * açtığı akışın içinde kalıyor, baştan başlamak zorunda kalmıyor.
     */
    private fun handleUnlockOnly() {
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
        if (request == null) {
            // İstek okunamadıysa gösterecek bir liste de yok; kilidi açmış
            // olmak yine de kazanç, kullanıcı uygulamaya dönebilir.
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        val entries = request.beginGetCredentialOptions
            .filterIsInstance<BeginGetPublicKeyCredentialOption>()
            .flatMap { PasskeyEntries.forOption(this, it) }

        val result = Intent()
        PendingIntentHandler.setBeginGetCredentialResponse(
            result,
            BeginGetCredentialResponse(credentialEntries = entries)
        )
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    // ---------------------------------------------------------- passkey kaydı

    private fun handleCreate() {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val callingRequest = request?.callingRequest as? CreatePublicKeyCredentialRequest
        if (request == null || callingRequest == null) {
            failCreate()
            return
        }

        val options = PasskeyProtocol.parseCreationOptions(callingRequest.requestJson)
        if (options == null || !options.supported) {
            Log.w(TAG, "Desteklenmeyen passkey kayıt isteği")
            failCreate()
            return
        }

        val origin = originFor(request.callingAppInfo)
        if (origin == null) {
            failCreate()
            return
        }

        val container = KasaApplication.container(this)
        setContent {
            val settings by container.settingsStore.settings
                .collectAsState(initial = SettingsStore.Settings())

            KasaTheme(
                themeMode = settings.theme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack
            ) {
                ConfirmSheet(
                    title = stringResource(R.string.passkey_create_title),
                    body = stringResource(
                        R.string.passkey_create_body,
                        options.rpName.ifBlank { options.rpId },
                        options.userName.ifBlank { options.userDisplayName }
                    ),
                    confirmText = stringResource(R.string.passkey_create_confirm),
                    onConfirm = { finishCreate(options, origin, request.callingAppInfo.packageName) },
                    onCancel = { cancel(MODE_CREATE) }
                )
            }
        }
    }

    private fun finishCreate(
        options: PasskeyProtocol.CreationOptions,
        origin: String,
        packageName: String
    ) {
        lifecycleScope.launch {
            val created = PasskeyVault.create(options, origin, packageName)
            if (created == null || !repository.addPasskey(created.passkey)) {
                failCreate()
                return@launch
            }
            val result = Intent()
            PendingIntentHandler.setCreateCredentialResponse(
                result,
                CreatePublicKeyCredentialResponse(created.responseJson)
            )
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    // ------------------------------------------------------- oturum açma

    /**
     * Seçilen passkey ile iddiayı imzalar.
     *
     * Ayrı bir onay ekranı yok ve olmaması doğru: kullanıcı hesabı sistemin
     * kendi seçicisinde zaten seçti, kasanın kilidi de bu akışta açıldı.
     * `authenticatorData` içindeki "kullanıcı doğrulandı" bayrağının arkasında
     * duran şey bu ikisi — üçüncü bir onay, güvenliğe hiçbir şey eklemeden
     * her girişe bir dokunuş ekliyor olurdu.
     */
    private fun handleGet() {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID)
        val option = request?.credentialOptions
            ?.filterIsInstance<GetPublicKeyCredentialOption>()
            ?.firstOrNull()

        if (request == null || credentialId == null || option == null) {
            failGet()
            return
        }

        val options = PasskeyProtocol.parseRequestOptions(option.requestJson)
        val found = repository.findPasskey(credentialId)
        val origin = originFor(request.callingAppInfo)
        if (options == null || found == null || origin == null) {
            failGet()
            return
        }

        lifecycleScope.launch {
            val responseJson = PasskeyVault.assert(
                passkey = found.second,
                options = options,
                origin = origin,
                packageName = request.callingAppInfo.packageName,
                clientDataHash = option.clientDataHash
            )
            if (responseJson == null) {
                failGet()
                return@launch
            }
            repository.touchPasskey(credentialId)

            val result = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                result,
                GetCredentialResponse(PublicKeyCredential(responseJson))
            )
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }

    // ------------------------------------------------------------ yardımcılar

    /**
     * Çağıranın WebAuthn kaynağı.
     *
     * Ayrıcalıklı bir tarayıcı kendi kaynağını bildiriyorsa o kullanılıyor;
     * sıradan uygulamalarda imza özetinden `android:apk-key-hash:` üretiliyor.
     * İkisi de elde edilemiyorsa işlem iptal — kaynağı bilinmeyen bir çağıran
     * için imza atmak, kimlik avına karşı asıl korumayı kapatmak olurdu.
     */
    private fun originFor(info: CallingAppInfo): String? =
        runCatching { info.getOrigin(PRIVILEGED_BROWSERS) }.getOrNull()
            ?: CallerIdentity.origin(info)

    private fun failCreate() {
        val result = Intent()
        PendingIntentHandler.setCreateCredentialException(result, CreateCredentialUnknownException())
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun failGet() {
        val result = Intent()
        PendingIntentHandler.setGetCredentialException(result, GetCredentialUnknownException())
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun cancel(mode: String) {
        when (mode) {
            MODE_CREATE -> failCreate()
            MODE_GET -> failGet()
            else -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    // ------------------------------------------------------------- arayüz

    @Composable
    private fun UnlockSheet(
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

        androidx.compose.runtime.LaunchedEffect(biometricEnabled) {
            if (!biometricTried && biometricEnabled && gate.available) {
                biometricTried = true
                val cipher = repository.biometricCipher()
                if (cipher != null) {
                    gate.authenticate(
                        title = getString(R.string.passkey_unlock_title),
                        subtitle = getString(R.string.passkey_unlock_body),
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

        Sheet {
            Text(
                stringResource(R.string.passkey_unlock_title),
                style = MaterialTheme.typography.titleLarge,
                color = KasaTheme.colors.ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.passkey_unlock_body),
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
                            val wipeAfter = KasaApplication.container(this@PasskeyActivity)
                                .settingsStore.settings.first().wipeAfterAttempts
                            val outcome = repository.unlockWithPassword(chars, wipeAfter)
                            busy = false
                            if (outcome is VaultRepository.UnlockOutcome.Success) onUnlocked() else error = true
                        }
                    },
                    enabled = !busy && password.isNotEmpty(),
                    height = 46.dp
                )
            }
        }
    }

    @Composable
    private fun ConfirmSheet(
        title: String,
        body: String,
        confirmText: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        Sheet {
            Text(title, style = MaterialTheme.typography.titleLarge, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = KasaTheme.colors.ink2)
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
                KasaButton(text = confirmText, onClick = onConfirm, height = 46.dp)
            }
        }
    }

    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
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
                content()
            }
        }
    }

    companion object {
        private const val TAG = "PasskeyActivity"

        const val EXTRA_MODE = "app.kasa.passkey.MODE"
        const val EXTRA_CREDENTIAL_ID = "app.kasa.passkey.CREDENTIAL_ID"

        const val MODE_UNLOCK = "unlock"
        const val MODE_CREATE = "create"
        const val MODE_GET = "get"

        /**
         * Ayrıcalıklı tarayıcı listesi.
         *
         * Boş bırakılıyor: bir tarayıcıya "kendi kaynağını sen belirle" yetkisi
         * vermek, o tarayıcının herhangi bir site adına passkey isteyebilmesi
         * demek. Google'ın yayımladığı listeyi uygulamanın içine gömmek de
         * çözüm değil — liste değiştiğinde uygulama eskimiş olurdu. Sıradan
         * uygulama yolu (imza özeti) her çağıran için çalışıyor ve doğrulaması
         * sitenin kendi `assetlinks.json` beyanına dayanıyor.
         */
        private const val PRIVILEGED_BROWSERS = "{\"apps\":[]}"
    }
}
