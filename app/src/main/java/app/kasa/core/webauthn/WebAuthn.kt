package app.kasa.core.webauthn

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Passkey (FIDO2 / WebAuthn) kimlik bilgisinin kriptografik çekirdeği.
 *
 * ### Anahtar nerede duruyor
 *
 * Özel anahtar Android Keystore'da değil, **kasanın içinde** üretilip
 * saklanıyor. Bu bilinçli bir seçim ve bedeli var, karşılığı da:
 *
 *  - *Bedel:* anahtar bir noktada uygulama belleğine iniyor; Keystore'daki bir
 *    anahtar hiç inmezdi.
 *  - *Karşılık:* passkey kasayla birlikte yedekleniyor, dışa aktarılıyor ve
 *    yeni cihaza taşınıyor. Keystore'a bağlı bir passkey telefonla birlikte
 *    kaybolur; kullanıcının o hesaba girme yolu da onunla birlikte. Parola
 *    yöneticisinin varlık sebebi tam olarak bunu engellemek.
 *
 * Bellekteki risk [app.kasa.core.crypto.SecretText] ile sınırlanıyor: özel
 * anahtar silinebilir bir `CharArray` içinde duruyor ve kasa kilitlendiğinde
 * sıfırlanıyor.
 *
 * ### Attestation
 *
 * `none` biçimi kullanılıyor ve AAGUID on altı sıfır bayt. Yazılım
 * kimlik doğrulayıcısının kendisi hakkında doğrulanabilir bir şey söylemesi
 * mümkün değil; sahte bir donanım kimliği üretmek yerine hiçbir şey iddia
 * etmemek doğru olan.
 */
object WebAuthn {

    /** COSE algoritma kimliği: ECDSA + SHA-256, P-256 eğrisi. */
    const val ALG_ES256 = -7

    private const val FLAG_USER_PRESENT = 0x01
    private const val FLAG_USER_VERIFIED = 0x04
    private const val FLAG_BACKUP_ELIGIBLE = 0x08
    private const val FLAG_BACKED_UP = 0x10
    private const val FLAG_ATTESTED_DATA = 0x40

    /** `none` attestation ile AAGUID sıfırlanır: cihaz hakkında iddia yok. */
    private val ZERO_AAGUID = ByteArray(16)

    private const val COORDINATE_BYTES = 32

    // ------------------------------------------------------------- anahtarlar

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    fun privateKeyFrom(pkcs8: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    fun publicKeyFrom(spki: ByteArray): ECPublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki)) as ECPublicKey

    /**
     * Açık anahtarın COSE_Key gösterimi (RFC 8152).
     *
     * Harita kurallı sırada: 1 (kty=EC2), 3 (alg=ES256), -1 (crv=P-256),
     * -2 (x), -3 (y). Kodlanmış anahtar baytları artan sırada olduğu için bu
     * sıra aynı zamanda CTAP2 kurallı sırası.
     */
    fun coseKey(publicKey: ECPublicKey): ByteArray {
        val point = publicKey.w
        val x = fixedWidth(point.affineX.toByteArray())
        val y = fixedWidth(point.affineY.toByteArray())
        return Cbor.writer()
            .mapHeader(5)
            .int(1).int(2)              // kty: EC2
            .int(3).int(ALG_ES256)      // alg: ES256
            .int(-1).int(1)             // crv: P-256
            .int(-2).bytes(x)
            .int(-3).bytes(y)
            .build()
    }

    /**
     * `BigInteger.toByteArray` işaret baytı ekleyebilir ya da baştaki sıfırları
     * atabilir; COSE koordinatı tam 32 bayt olmak zorunda.
     */
    private fun fixedWidth(raw: ByteArray): ByteArray {
        if (raw.size == COORDINATE_BYTES) return raw
        val out = ByteArray(COORDINATE_BYTES)
        if (raw.size > COORDINATE_BYTES) {
            System.arraycopy(raw, raw.size - COORDINATE_BYTES, out, 0, COORDINATE_BYTES)
        } else {
            System.arraycopy(raw, 0, out, COORDINATE_BYTES - raw.size, raw.size)
        }
        return out
    }

    // ------------------------------------------------------- authenticatorData

    /**
     * Kayıt için authenticatorData: rpIdHash ‖ bayraklar ‖ sayaç ‖ kimlik bilgisi.
     *
     * Sayaç sıfır gönderiliyor. Çok cihazlı (yedeklenebilir) kimlik bilgilerinde
     * WebAuthn L3 bunu öneriyor: kasa iki cihazda birden açıkken artan bir
     * sayaç, doğrulayıcı tarafta "kimlik bilgisi klonlandı" alarmını boşuna
     * çaldırırdı.
     */
    fun registrationAuthenticatorData(
        rpId: String,
        credentialId: ByteArray,
        coseKey: ByteArray
    ): ByteArray {
        val attested = ByteArray(16 + 2 + credentialId.size + coseKey.size)
        System.arraycopy(ZERO_AAGUID, 0, attested, 0, 16)
        attested[16] = ((credentialId.size shr 8) and 0xFF).toByte()
        attested[17] = (credentialId.size and 0xFF).toByte()
        System.arraycopy(credentialId, 0, attested, 18, credentialId.size)
        System.arraycopy(coseKey, 0, attested, 18 + credentialId.size, coseKey.size)

        val flags = FLAG_USER_PRESENT or FLAG_USER_VERIFIED or
            FLAG_BACKUP_ELIGIBLE or FLAG_BACKED_UP or FLAG_ATTESTED_DATA
        return authenticatorData(rpId, flags, attested)
    }

    /** Oturum açma için authenticatorData: kimlik bilgisi bölümü yok. */
    fun assertionAuthenticatorData(rpId: String): ByteArray {
        val flags = FLAG_USER_PRESENT or FLAG_USER_VERIFIED or
            FLAG_BACKUP_ELIGIBLE or FLAG_BACKED_UP
        return authenticatorData(rpId, flags, null)
    }

    private fun authenticatorData(rpId: String, flags: Int, attested: ByteArray?): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        val size = 32 + 1 + 4 + (attested?.size ?: 0)
        val out = ByteArray(size)
        System.arraycopy(rpIdHash, 0, out, 0, 32)
        out[32] = flags.toByte()
        // out[33..36] sayaç: sıfır kalıyor.
        attested?.let { System.arraycopy(it, 0, out, 37, it.size) }
        return out
    }

    /** `{"fmt":"none","attStmt":{},"authData":...}` — kurallı anahtar sırasıyla. */
    fun attestationObject(authenticatorData: ByteArray): ByteArray =
        Cbor.writer()
            .mapHeader(3)
            .text("fmt").text("none")
            .text("attStmt").mapHeader(0)
            .text("authData").bytes(authenticatorData)
            .build()

    // ------------------------------------------------------------------ imza

    /**
     * WebAuthn imzası: authenticatorData ‖ SHA-256(clientDataJSON) üzerinden
     * ECDSA/SHA-256. Java'nın ürettiği DER kodlaması zaten beklenen biçim.
     */
    fun sign(privateKey: PrivateKey, authenticatorData: ByteArray, clientDataHash: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(authenticatorData)
        signature.update(clientDataHash)
        return signature.sign()
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    // --------------------------------------------------------------- yardımcı

    fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun fromBase64Url(text: String): ByteArray =
        Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /**
     * Yerel uygulamalar için WebAuthn kaynağı (origin).
     *
     * Tarayıcıda kaynak `https://site` biçimindedir; Android uygulamalarında
     * `android:apk-key-hash:<imza özeti>` kullanılır. Doğrulayıcı taraf bu
     * değeri `assetlinks.json` ile eşleştirdiği için imza özetini doğru
     * üretmek şart: kaynak yanlışsa kayıt sessizce reddedilir.
     */
    fun androidOrigin(signatureSha256: ByteArray): String =
        "android:apk-key-hash:" + base64Url(signatureSha256)

    /** Rastgele, 32 baytlık kimlik bilgisi tanımlayıcısı. */
    fun newCredentialId(): ByteArray = app.kasa.core.crypto.Crypto.randomBytes(32)
}
