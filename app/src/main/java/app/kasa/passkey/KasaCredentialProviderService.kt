package app.kasa.passkey

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import app.kasa.KasaApplication
import app.kasa.R

/**
 * Kasa'yı sistem çapında bir passkey sağlayıcısı yapan servis.
 *
 * Android'in Credential Manager'ı, bir uygulama ya da tarayıcı passkey
 * istediğinde kurulu sağlayıcılara soruyor; bu servis Kasa'nın cevabı.
 * Kullanıcı açısından sonuç şu: herhangi bir uygulamada "passkey ile giriş
 * yap" dendiğinde seçim listesinde Kasa çıkıyor ve hesap Kasa'dan geliyor.
 *
 * ### İş bölümü
 *
 * Bu servis yalnızca **passkey** ile ilgileniyor; parolalar
 * [app.kasa.autofill.KasaAutofillService] üzerinden doldurulmayı sürdürüyor.
 * İkisini ayırmak bilinçli: Otomatik Doldurma her ekrandaki her metin alanını
 * görebiliyor, Credential Manager ise yalnızca kendisine açıkça sorulan
 * isteği. Passkey'i dar olan kapıdan geçirmek doğru olan.
 *
 * ### Kilitli kasa
 *
 * Kasa kilitliyken hangi passkey'lerin bulunduğu **söylenmiyor** — kilitli bir
 * kasanın hesap listesini sızdırması, kilidin yarısını açık bırakmak olurdu.
 * Onun yerine tek bir "Kasa'nın kilidini aç" eylemi dönüyor; kullanıcı kilidi
 * açtıktan sonra gerçek liste üretiliyor.
 */
class KasaCredentialProviderService : CredentialProviderService() {

    // ---------------------------------------------------------- passkey isteği

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        try {
            val options = request.beginGetCredentialOptions
                .filterIsInstance<BeginGetPublicKeyCredentialOption>()

            if (options.isEmpty()) {
                callback.onResult(BeginGetCredentialResponse())
                return
            }

            val repository = KasaApplication.container(this).vaultRepository
            if (!repository.isUnlocked) {
                callback.onResult(
                    BeginGetCredentialResponse(
                        authenticationActions = listOf(
                            AuthenticationAction(
                                title = getString(R.string.passkey_unlock_action),
                                pendingIntent = pendingIntent(PasskeyActivity.MODE_UNLOCK)
                            )
                        )
                    )
                )
                return
            }

            val entries = options.flatMap { option -> PasskeyEntries.forOption(this, option) }
            callback.onResult(BeginGetCredentialResponse(credentialEntries = entries))
        } catch (t: Throwable) {
            Log.w(TAG, "Passkey isteği karşılanamadı")
            callback.onError(GetCredentialUnknownException())
        }
    }

    // ----------------------------------------------------------- passkey kaydı

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        try {
            if (request !is BeginCreatePublicKeyCredentialRequest) {
                callback.onResult(BeginCreateCredentialResponse())
                return
            }
            // Kilitliyken de giriş sunuluyor: kullanıcı dokunduğunda açılış
            // ekranı geliyor. Burada isim göstermek bir şey sızdırmıyor, çünkü
            // gösterilen kasanın kendi adı, içindekiler değil.
            callback.onResult(
                BeginCreateCredentialResponse(
                    createEntries = listOf(
                        CreateEntry(
                            accountName = getString(R.string.app_name),
                            pendingIntent = pendingIntent(PasskeyActivity.MODE_CREATE)
                        )
                    )
                )
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Passkey kaydı başlatılamadı")
            callback.onError(CreateCredentialUnknownException())
        }
    }

    /**
     * Sistem "bu uygulamanın tuttuğu oturum durumunu temizle" diyor.
     *
     * Kasa'da temizlenecek bir oturum yok: passkey'ler kalıcı kayıtlar, geçici
     * bir kimlik durumu tutulmuyor. Kullanıcının passkey'ini burada silmek,
     * istenmeyen bir veri kaybı olurdu — bu yüzden yalnızca başarı bildiriliyor.
     */
    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    // ------------------------------------------------------------- yardımcılar

    private fun pendingIntent(mode: String, credentialId: String? = null) =
        PasskeyEntries.pendingIntent(this, mode, credentialId)

    companion object {
        private const val TAG = "KasaCredentials"
    }
}
