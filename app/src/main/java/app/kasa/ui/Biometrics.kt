package app.kasa.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Biyometrik doğrulama kapısı.
 *
 * Yalnızca **Class 3 (STRONG)** biyometri kabul edilir. Zayıf sınıf (yüz
 * tanımanın 2D varyantları gibi) kripto anahtarına bağlanamaz zaten; bu yüzden
 * "biyometri var" demekle "biyometriyle anahtar açılabilir" demek aynı şey
 * değildir ve burada ikincisi aranır.
 *
 * Doğrulama [Cipher] ile yapılır: parmak izi yalnızca bir bayrağı `true`
 * yapmaz, doğrudan Keystore'daki anahtarın kullanımını serbest bırakır.
 * Böylece uygulamanın belleğine müdahale ederek "doğrulandı" demek işe yaramaz.
 */
class BiometricGate(private val activity: FragmentActivity) {

    private val manager = BiometricManager.from(activity)

    fun status(): Int = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

    val available: Boolean get() = status() == BiometricManager.BIOMETRIC_SUCCESS

    /** Cihaz destekliyor ama kullanıcı henüz parmak izi kaydetmemiş. */
    val notEnrolled: Boolean
        get() = status() == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    fun authenticate(
        title: String,
        subtitle: String,
        negativeButton: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (Int, CharSequence) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated != null) onSuccess(authenticated)
                    else onError(-1, "Şifreleyici doğrulanamadı")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButton)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        runCatching { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }
            .onFailure { onError(-2, it.message ?: "Biyometrik istem açılamadı") }
    }

    /**
     * "Ekranın başındaki kişi hâlâ sen misin?" sorusu.
     *
     * Kripto nesnesi **yok** ve bu bilinçli. Kasa anahtarı zaten bellekte —
     * kilit açılmış durumda — dolayısıyla burada açılacak bir anahtar yok;
     * sorulan şey erişim değil, o anda telefonu kimin tuttuğu. Kayıt bazlı ek
     * kilidin tehdit modeli tam olarak bu: açık bırakılmış telefonu eline alan
     * biri, banka kaydını Spotify kaydı kadar kolay görememeli.
     *
     * Bunun bedeli açık: kripto bağlı doğrulamanın aksine, uygulamanın
     * belleğine müdahale edebilen bir saldırgan bu kontrolü atlayabilir. Ama o
     * saldırgan kasa anahtarına zaten erişmiş demektir; bu kapının kimseye
     * söz vermediği koruma o.
     *
     * Cihazın ekran kilidi de kabul ediliyor: parmak izi okuyucusu olmayan
     * kullanıcıda tek seçenek "hiç doğrulama yok" olmasın diye.
     */
    fun authenticatePresence(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        // Kullanılabilir hiçbir yol yoksa kapıyı kilitli bırakmak, kullanıcıyı
        // kendi kaydından kalıcı olarak dışarıda bırakmak olurdu.
        if (manager.canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onCancel()
                }
            }
        )

        // DEVICE_CREDENTIAL açıkken olumsuz düğme konulamaz; sistem "PIN kullan"
        // seçeneğini kendisi koyuyor.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)
            .setConfirmationRequired(false)
            .build()

        runCatching { prompt.authenticate(info) }.onFailure { onCancel() }
    }
}

val LocalBiometricGate = staticCompositionLocalOf<BiometricGate?> { null }
