package app.kasa.data.model

import app.kasa.core.crypto.SecretText
import kotlinx.serialization.Serializable

/**
 * Kasada saklanan bir FIDO2 / WebAuthn kimlik bilgisi.
 *
 * Passkey, parolanın yerine geçen bir açık anahtar çifti: doğrulayıcı taraf
 * (site) yalnızca açık anahtarı biliyor, gizli olan hiçbir şey sunucuya
 * gitmiyor. Bu yüzden sızdırılacak bir parola yok, kimlik avıyla çalınacak bir
 * dizge yok — imza yalnızca doğru alan adı için üretiliyor.
 *
 * Bu kayıt bir [VaultItem] içinde duruyor, ayrı bir listede değil: aynı site
 * için hem parola hem passkey bulunması olağan durum, ikisini bir arada
 * göstermek de doğru olan.
 *
 * @param credentialId doğrulayıcı tarafın bize geri göndereceği tanımlayıcı
 *        (base64url). Rastgele 32 bayt; kullanıcı hakkında bilgi taşımıyor.
 * @param userHandle sitenin kullanıcı kimliği (base64url). Oturum açarken
 *        imzayla birlikte geri gönderiliyor.
 * @param privateKey PKCS#8 kodlu özel anahtar, onaltılık. Silinebilir
 *        [SecretText] içinde: kasa kilitlendiğinde bellekten sıfırlanıyor.
 * @param publicKeySpki X.509/SPKI kodlu açık anahtar, onaltılık. Gizli değil;
 *        kayıt cevabında sitenin istediği biçim bu.
 */
@Serializable
data class Passkey(
    val credentialId: String,
    val rpId: String,
    val rpName: String = "",
    val userHandle: String,
    val userName: String = "",
    val userDisplayName: String = "",
    val privateKey: SecretText,
    val publicKeySpki: String,
    val algorithm: Int = -7,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
) {
    /** Seçim ekranında gösterilen ad. */
    val label: String
        get() = userName.ifBlank { userDisplayName }.ifBlank { rpId }
}
