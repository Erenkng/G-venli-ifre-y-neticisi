package app.kasa.passkey

import androidx.credentials.provider.CallingAppInfo
import app.kasa.core.webauthn.WebAuthn

/**
 * "Bu isteği kim yaptı?" sorusunun cevabı.
 *
 * WebAuthn imzasının altına giren `origin` alanı, kimlik bilgisinin hangi
 * kaynağa ait olduğunu söylüyor ve doğrulayıcı taraf onu kendi beklediğiyle
 * karşılaştırıyor. Yanlış üretilen bir kaynak, imzanın sessizce reddedilmesi
 * demek — ve bu, kimlik avına karşı asıl korumanın çalıştığı yer: kullanıcı
 * sahte bir uygulamada passkey'ini kullanmaya ikna edilse bile imza gerçek
 * sitede geçerli olmuyor.
 *
 * İki durum var:
 *
 *  - **Ayrıcalıklı çağıran** (tarayıcı): kendi `clientDataHash` değerini
 *    veriyor, kaynağı da kendisi belirliyor. Bu durumda karışmıyoruz.
 *  - **Sıradan uygulama**: kaynak `android:apk-key-hash:<imza özeti>`
 *    biçiminde üretiliyor. Doğrulayıcı taraf bunu `assetlinks.json` ile
 *    eşleştiriyor, yani uygulamanın gerçekten o alan adına ait olduğunu
 *    Google'ın altyapısı değil, sitenin kendi beyanı doğruluyor.
 */
object CallerIdentity {

    /**
     * Çağıranın WebAuthn kaynağı.
     *
     * İmza bilgisi okunamazsa `null` dönüyor ve işlem iptal ediliyor —
     * doğrulanamayan bir çağıran için passkey imzalamak, kimlik avına karşı
     * korumayı gönüllü olarak kapatmak olurdu.
     */
    fun origin(info: CallingAppInfo): String? {
        val certificate = signingCertificate(info) ?: return null
        return WebAuthn.androidOrigin(WebAuthn.sha256(certificate))
    }

    private fun signingCertificate(info: CallingAppInfo): ByteArray? = runCatching {
        val signingInfo = info.signingInfo
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        signatures?.firstOrNull()?.toByteArray()
    }.getOrNull()

    /**
     * Kullanıcıya gösterilecek çağıran adı. Paket adının son parçası, ilk
     * harfi büyük: "com.example.shop" → "Shop".
     */
    fun displayName(info: CallingAppInfo): String =
        info.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
