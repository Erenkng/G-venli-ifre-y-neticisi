package app.kasa.passkey

import android.util.Log
import app.kasa.core.crypto.Crypto
import app.kasa.core.crypto.SecretText
import app.kasa.core.webauthn.PasskeyProtocol
import app.kasa.core.webauthn.WebAuthn
import app.kasa.data.model.Passkey
import java.security.interfaces.ECPublicKey

/**
 * Passkey üretme ve imzalama.
 *
 * Bu katman kasayı, arayüzü ya da Credential Manager'ı tanımıyor: içeri
 * ayrıştırılmış istek girer, dışarı imzalanmış cevap çıkar. Kriptografiyi
 * sistemle konuşan koddan ayırmanın sebebi, doğruluğunun tek başına
 * incelenebilmesi — WebAuthn'da yanlış bir bayt sessizce "giriş başarısız"
 * demek, hata mesajı vermez.
 */
object PasskeyVault {

    private const val TAG = "PasskeyVault"

    class Created(val passkey: Passkey, val responseJson: String)

    /**
     * Yeni bir kimlik bilgisi üretir ve kayıt cevabını döndürür.
     *
     * @param clientDataHash ayrıcalıklı çağıran (tarayıcı) kendi
     *        `clientDataJSON`'unu ürettiyse onun özeti. Kayıtta `none`
     *        attestation kullandığımız için imzaya girmiyor; yine de cevapta
     *        tutarlı kalmak adına akış aynı.
     */
    fun create(
        options: PasskeyProtocol.CreationOptions,
        origin: String,
        packageName: String?
    ): Created? = try {
        val keyPair = WebAuthn.generateKeyPair()
        val publicKey = keyPair.public as ECPublicKey
        val credentialId = WebAuthn.newCredentialId()
        val coseKey = WebAuthn.coseKey(publicKey)

        val authenticatorData = WebAuthn.registrationAuthenticatorData(options.rpId, credentialId, coseKey)
        val clientDataJson = PasskeyProtocol.clientDataJson(
            type = "webauthn.create",
            challenge = options.challenge,
            origin = origin,
            packageName = packageName
        )
        val attestationObject = WebAuthn.attestationObject(authenticatorData)

        val passkey = Passkey(
            credentialId = WebAuthn.base64Url(credentialId),
            rpId = options.rpId,
            rpName = options.rpName,
            userHandle = WebAuthn.base64Url(options.userId),
            userName = options.userName,
            userDisplayName = options.userDisplayName,
            // Özel anahtar silinebilir bir kapta: kasa kilitlenince sıfırlanıyor.
            privateKey = SecretText.of(Crypto.hex(keyPair.private.encoded)),
            publicKeySpki = Crypto.hex(publicKey.encoded),
            algorithm = WebAuthn.ALG_ES256
        )

        Created(
            passkey = passkey,
            responseJson = PasskeyProtocol.registrationResponseJson(
                credentialId = credentialId,
                clientDataJson = clientDataJson,
                attestationObject = attestationObject,
                authenticatorData = authenticatorData,
                publicKeySpki = publicKey.encoded
            )
        )
    } catch (t: Throwable) {
        Log.w(TAG, "Passkey üretilemedi")
        null
    }

    /**
     * Var olan bir passkey ile oturum açma iddiasını imzalar.
     *
     * İmza `authenticatorData ‖ SHA-256(clientDataJSON)` üzerinden atılıyor.
     * Ayrıcalıklı çağıran kendi özetini verdiyse ([clientDataHash]) bizimkini
     * değil onu imzalıyoruz — tarayıcının ürettiği `clientDataJSON` ile
     * bizimki bayt bayt aynı olmak zorunda değil ve doğrulayıcı taraf
     * tarayıcınınkini görüyor.
     */
    fun assert(
        passkey: Passkey,
        options: PasskeyProtocol.RequestOptions,
        origin: String,
        packageName: String?,
        clientDataHash: ByteArray?
    ): String? = try {
        val privateKeyBytes = Crypto.fromHex(passkey.privateKey.reveal())
            ?: throw IllegalStateException("Passkey özel anahtarı okunamadı")

        val authenticatorData = WebAuthn.assertionAuthenticatorData(options.rpId)
        val clientDataJson = PasskeyProtocol.clientDataJson(
            type = "webauthn.get",
            challenge = options.challenge,
            origin = origin,
            packageName = packageName
        )
        val hash = clientDataHash
            ?: WebAuthn.sha256(clientDataJson.toByteArray(Charsets.UTF_8))

        val signature = try {
            WebAuthn.sign(WebAuthn.privateKeyFrom(privateKeyBytes), authenticatorData, hash)
        } finally {
            privateKeyBytes.fill(0)
        }

        PasskeyProtocol.assertionResponseJson(
            credentialId = WebAuthn.fromBase64Url(passkey.credentialId),
            clientDataJson = clientDataJson,
            authenticatorData = authenticatorData,
            signature = signature,
            userHandle = WebAuthn.fromBase64Url(passkey.userHandle)
        )
    } catch (t: Throwable) {
        Log.w(TAG, "Passkey imzası üretilemedi")
        null
    }
}
