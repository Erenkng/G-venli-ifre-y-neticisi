package app.kasa.core.webauthn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Credential Manager ile aramızdaki JSON sözleşmesi.
 *
 * Sistem, doğrulayıcı tarafın (web sitesinin) gönderdiği seçenekleri bize
 * olduğu gibi bir JSON dizgesi olarak veriyor ve cevabı da aynı biçimde
 * bekliyor. Buradaki iş bu iki ucu birbirine bağlamak: gelen seçenekleri
 * okunabilir bir nesneye çevirmek ve imzalanmış sonucu tarayıcının anlayacağı
 * biçimde geri paketlemek.
 *
 * Ayrıştırma bilerek bağışlayıcı: bilinmeyen alanlar yok sayılıyor, çünkü
 * WebAuthn seçenekleri sürekli genişliyor ve tanımadığımız bir uzantı yüzünden
 * kaydı reddetmek kullanıcıyı hiçbir şey kazandırmadan engellerdi. Buna karşın
 * **anlamı olan** eksiklikler (rp kimliği, meydan okuma) sessizce
 * tamamlanmıyor; onlarda `null` dönüp işlem iptal ediliyor.
 */
object PasskeyProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------- kayıt

    class CreationOptions(
        val rpId: String,
        val rpName: String,
        val userId: ByteArray,
        val userName: String,
        val userDisplayName: String,
        val challenge: ByteArray,
        val algorithms: List<Int>,
        val excludedCredentialIds: List<String>
    ) {
        /** ES256 desteklenmiyorsa üretemeyiz; başka eğri uygulamıyoruz. */
        val supported: Boolean get() = algorithms.isEmpty() || algorithms.contains(WebAuthn.ALG_ES256)
    }

    fun parseCreationOptions(requestJson: String): CreationOptions? = runCatching {
        val root = json.parseToJsonElement(requestJson).jsonObject
        val rp = root["rp"]?.jsonObject
        val user = root["user"]?.jsonObject ?: return@runCatching null
        val rpId = rp?.get("id")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val challenge = root["challenge"]?.jsonPrimitive?.content?.let(WebAuthn::fromBase64Url)
            ?: return@runCatching null
        val userId = user["id"]?.jsonPrimitive?.content?.let(WebAuthn::fromBase64Url)
            ?: return@runCatching null

        CreationOptions(
            rpId = rpId,
            rpName = rp?.get("name")?.jsonPrimitive?.content.orEmpty().ifBlank { rpId },
            userId = userId,
            userName = user["name"]?.jsonPrimitive?.content.orEmpty(),
            userDisplayName = user["displayName"]?.jsonPrimitive?.content.orEmpty(),
            challenge = challenge,
            algorithms = (root["pubKeyCredParams"] as? JsonArray).orEmpty()
                .mapNotNull { it["alg"]?.jsonPrimitive?.content?.toIntOrNull() },
            excludedCredentialIds = (root["excludeCredentials"] as? JsonArray).orEmpty()
                .mapNotNull { it["id"]?.jsonPrimitive?.content }
        )
    }.getOrNull()

    // -------------------------------------------------------- oturum açma

    class RequestOptions(
        val rpId: String,
        val challenge: ByteArray,
        val allowedCredentialIds: List<String>
    )

    fun parseRequestOptions(requestJson: String): RequestOptions? = runCatching {
        val root = json.parseToJsonElement(requestJson).jsonObject
        val rpId = root["rpId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val challenge = root["challenge"]?.jsonPrimitive?.content?.let(WebAuthn::fromBase64Url)
            ?: return@runCatching null
        RequestOptions(
            rpId = rpId,
            challenge = challenge,
            allowedCredentialIds = (root["allowCredentials"] as? JsonArray).orEmpty()
                .mapNotNull { it["id"]?.jsonPrimitive?.content }
        )
    }.getOrNull()

    // ------------------------------------------------------------ clientData

    /**
     * `clientDataJSON`: imzanın altına giren, "hangi istek için imzaladım"
     * beyanı. Alan sırası önemli değil (doğrulayıcı taraf ayrıştırıp bakıyor),
     * ama `type`, `challenge` ve `origin` üçlüsü şart — biri eksikse ya da
     * yanlışsa doğrulama başarısız olur, ki zaten olması gereken de bu.
     */
    fun clientDataJson(type: String, challenge: ByteArray, origin: String, packageName: String?): String =
        buildJsonObject {
            put("type", type)
            put("challenge", WebAuthn.base64Url(challenge))
            put("origin", origin)
            put("crossOrigin", false)
            if (packageName != null) put("androidPackageName", packageName)
        }.toString()

    // ------------------------------------------------------------- cevaplar

    fun registrationResponseJson(
        credentialId: ByteArray,
        clientDataJson: String,
        attestationObject: ByteArray,
        authenticatorData: ByteArray,
        publicKeySpki: ByteArray
    ): String {
        val id = WebAuthn.base64Url(credentialId)
        return buildJsonObject {
            put("id", id)
            put("rawId", id)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put("clientExtensionResults", buildJsonObject { })
            put(
                "response",
                buildJsonObject {
                    put("clientDataJSON", WebAuthn.base64Url(clientDataJson.toByteArray(Charsets.UTF_8)))
                    put("attestationObject", WebAuthn.base64Url(attestationObject))
                    put("authenticatorData", WebAuthn.base64Url(authenticatorData))
                    put("publicKeyAlgorithm", WebAuthn.ALG_ES256)
                    put("publicKey", WebAuthn.base64Url(publicKeySpki))
                    put(
                        "transports",
                        buildJsonArray {
                            // "internal": anahtar bu cihazda. "hybrid": kasa başka
                            // bir cihaza taşındığında oradan da kullanılabilir.
                            add("internal")
                            add("hybrid")
                        }
                    )
                }
            )
        }.toString()
    }

    fun assertionResponseJson(
        credentialId: ByteArray,
        clientDataJson: String,
        authenticatorData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray
    ): String {
        val id = WebAuthn.base64Url(credentialId)
        return buildJsonObject {
            put("id", id)
            put("rawId", id)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put("clientExtensionResults", buildJsonObject { })
            put(
                "response",
                buildJsonObject {
                    put("clientDataJSON", WebAuthn.base64Url(clientDataJson.toByteArray(Charsets.UTF_8)))
                    put("authenticatorData", WebAuthn.base64Url(authenticatorData))
                    put("signature", WebAuthn.base64Url(signature))
                    put("userHandle", WebAuthn.base64Url(userHandle))
                }
            )
        }.toString()
    }

    private fun JsonArray?.orEmpty(): List<JsonObject> =
        this?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: emptyList()
}
