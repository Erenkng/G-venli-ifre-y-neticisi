package app.kasa.data.model

import app.kasa.core.crypto.Crypto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Category {
    @SerialName("login") LOGIN,
    @SerialName("card") CARD,
    @SerialName("note") NOTE,
    @SerialName("otp") OTP,
    @SerialName("identity") IDENTITY,
    @SerialName("bank") BANK,
    @SerialName("ssh") SSH_KEY,
    @SerialName("license") LICENSE,
    @SerialName("wifi") WIFI;

    /**
     * Alanları [CategorySchema] tarafından tanımlanan türler.
     *
     * Eski dört tür kendi açık alanlarını (username, password, cardNumber…)
     * kullanmayı sürdürüyor; onları şemaya taşımak var olan her kasayı
     * dönüştürmeyi gerektirirdi ve hiçbir şey kazandırmazdı. Yeni türler
     * baştan şema tabanlı, böylece tür eklemek yalnızca veri işi.
     */
    val schemaDriven: Boolean
        get() = this == IDENTITY || this == BANK || this == SSH_KEY || this == LICENSE || this == WIFI

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
 * Kayda iliştirilen dosya.
 *
 * Dosyanın kendisi kasa blob'unun içinde değil, `kasa/att/<id>.bin` altında
 * ayrı ve şifreli durur; kasa yalnızca referansı ve o dosyaya özel anahtarı
 * taşır. Böylece 4 MB'lık bir kimlik fotoğrafı, her kayıt açılışında çözülmek
 * zorunda kalan kasa blob'unu şişirmiyor.
 *
 * Her ek kendi anahtarıyla şifreleniyor (kasa anahtarıyla değil): ileride tek
 * bir eki paylaşmak ya da anahtarını döndürmek gerektiğinde kasanın tamamına
 * dokunmak gerekmesin diye.
 */
@Serializable
data class Attachment(
    val id: String = Crypto.hex(Crypto.randomBytes(16)),
    val name: String,
    val mime: String = "application/octet-stream",
    val size: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    /** 32 baytlık dosya anahtarı, onaltılık. Zaten şifreli kasa içinde durur. */
    val key: String
)

/** Kullanıcının kendi kurduğu klasör. */
@Serializable
data class Folder(
    val id: String = Crypto.hex(Crypto.randomBytes(8)),
    val name: String,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Kurala göre kendini dolduran görünümler.
 *
 * Saklanmazlar; her açılışta kayıtlardan hesaplanırlar. Güvenlik ekranındaki
 * bulguları gezilebilir kılan şey bunlar: "3 parola sızıntıda" artık bir sayı
 * değil, dokunulabilen bir liste.
 */
enum class SmartFolder { FAVORITES, LEAKED, REUSED, WEAK, OLD, NO_2FA, TRASH }

/** Kasa listesinin etkin görünümü. */
sealed interface VaultFilter {
    data object All : VaultFilter
    data class InFolder(val folderId: String) : VaultFilter
    data class Smart(val kind: SmartFolder) : VaultFilter

    val isTrash: Boolean get() = this is Smart && kind == SmartFolder.TRASH
}

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
    /** Şema tabanlı türlerin alanları: [CategorySchema] anahtarları → değer. */
    val extras: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val folderId: String? = null,
    val favorite: Boolean = false,
    /** 0 = kasada; >0 = çöp kutusunda, silinme anı. */
    val deletedAt: Long = 0L,
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

    val inTrash: Boolean get() = deletedAt > 0L

    /** Listede ve rozette gösterilen baş harf. */
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    /** Kaydın "gizli değeri": kopyalama ve güç ölçümü bunu kullanır. */
    val primarySecret: String
        get() = when {
            category.schemaDriven -> CategorySchema.primaryValue(this)
            category == Category.CARD -> cardNumber.ifBlank { password }
            category == Category.NOTE -> notes.ifBlank { password }
            else -> password
        }

    /** Kayıt listesinde adın altında görünen ikincil satır. */
    fun subtitle(): String = when {
        category.schemaDriven -> CategorySchema.subtitle(this)
        category == Category.CARD -> maskedCard()
        category == Category.NOTE -> notes.lineSequence().firstOrNull()?.take(48).orEmpty()
        category == Category.OTP -> if (totpSecret.isNotBlank()) "TOTP · $totpPeriod sn" else username
        else -> username.ifBlank { url }
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
    val folders: List<Folder> = emptyList(),
    /** Üreticide son üretilenler; parolalar burada da şifreli blob içindedir. */
    val generatorHistory: List<String> = emptyList(),
    val lastScanAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Çöp kutusundakiler hariç, kullanıcıya görünen kayıtlar. */
    val liveItems: List<VaultItem> get() = items.filter { !it.inTrash }

    val trashedItems: List<VaultItem> get() = items.filter { it.inTrash }

    companion object {
        /** Geçerli şema sürümü. Değiştirmeden önce [app.kasa.data.VaultMigrations]. */
        const val SCHEMA = VaultMigrations.CURRENT
    }
}
