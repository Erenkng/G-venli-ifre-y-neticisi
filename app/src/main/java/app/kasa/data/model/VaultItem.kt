package app.kasa.data.model

import app.kasa.core.crypto.Crypto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Category {
    @SerialName("login") LOGIN,
    @SerialName("card") CARD,
    @SerialName("note") NOTE,
    @SerialName("otp") OTP;

    companion object {
        fun fromKey(key: String): Category =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: LOGIN
    }
}

@Serializable
data class CustomField(
    val key: String,
    val value: String,
    val secret: Boolean = false
)

@Serializable
data class PasswordHistoryEntry(
    val password: String,
    val changedAt: Long
)

/**
 * Kasadaki tek bir kayıt.
 *
 * Bu nesne yalnızca kasa açıkken bellekte bulunur; diske her zaman
 * kasa anahtarıyla şifrelenmiş JSON olarak yazılır. Alanların hiçbiri
 * ayrı ayrı şifrelenmez, çünkü tüm kasa tek bir şifreli blob'dur:
 * böylece kaç kayıt olduğu, adları ve kategorileri gibi meta veriler de
 * diskte görünmez.
 */
@Serializable
data class VaultItem(
    val id: String = randomId(),
    val name: String,
    val category: Category = Category.LOGIN,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    /** RFC 4648 Base32 TOTP gizli anahtarı. */
    val totpSecret: String = "",
    val totpDigits: Int = 6,
    val totpPeriod: Int = 30,
    val totpAlgorithm: String = "SHA1",
    val cardNumber: String = "",
    val cardHolder: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val tags: List<String> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val passwordChangedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
    val history: List<PasswordHistoryEntry> = emptyList(),
    /** Son sızıntı denetiminin sonucu. 0 = temiz, >0 = kaç sızıntıda görüldü. */
    val breachCount: Int = 0,
    val breachCheckedAt: Long = 0L
) {
    val breached: Boolean get() = breachCount > 0

    /** Listede ve rozette gösterilen baş harf. */
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    /** Kaydın "gizli değeri": kopyalama ve güç ölçümü bunu kullanır. */
    val primarySecret: String
        get() = when (category) {
            Category.CARD -> cardNumber.ifBlank { password }
            Category.NOTE -> notes.ifBlank { password }
            else -> password
        }

    /** Kayıt listesinde adın altında görünen ikincil satır. */
    fun subtitle(): String = when (category) {
        Category.CARD -> maskedCard()
        Category.NOTE -> notes.lineSequence().firstOrNull()?.take(48).orEmpty()
        Category.OTP -> if (totpSecret.isNotBlank()) "TOTP · $totpPeriod sn" else username
        Category.LOGIN -> username.ifBlank { url }
    }

    fun maskedCard(): String {
        val digits = cardNumber.filter { it.isDigit() }
        if (digits.length < 4) return cardHolder
        return "•••• " + digits.takeLast(4)
    }

    /** Otomatik doldurma eşleştirmesi için kaydın ana alan adı. */
    fun host(): String? {
        val raw = url.trim()
        if (raw.isBlank()) return null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        return runCatching { java.net.URI(withScheme).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }

    companion object {
        fun randomId(): String = Crypto.hex(Crypto.randomBytes(16))
    }
}

/** Kasanın diske yazılan tüm içeriği. */
@Serializable
data class VaultData(
    val schema: Int = SCHEMA,
    val items: List<VaultItem> = emptyList(),
    /** Üreticide son üretilenler; parolalar burada da şifreli blob içindedir. */
    val generatorHistory: List<String> = emptyList(),
    val lastScanAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCHEMA = 1
    }
}
