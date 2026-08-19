package app.kasa.data.model

import androidx.annotation.StringRes
import app.kasa.R

/** Bir alanın nasıl gösterileceği ve klavyenin nasıl açılacağı. */
enum class FieldKind {
    TEXT,
    EMAIL,
    NUMBER,
    SECRET,
    MULTILINE,
    SECRET_MULTILINE,
    DATE
}

/**
 * Şema tabanlı bir türün tek alanı.
 *
 * @param key [VaultItem.extras] içindeki anahtar. Bir kez yazıldıktan sonra
 *        asla değiştirilmemeli — değişirse eski kayıtların verisi görünmez olur;
 *        değişmesi gerekirse şema göçüyle taşınmalı.
 */
data class FieldDef(
    val key: String,
    @StringRes val labelRes: Int,
    val kind: FieldKind,
    /** Listede alt satırda gösterilecek alan. */
    val subtitle: Boolean = false,
    /** Kopyala düğmesinin ve güç ölçümünün baktığı alan. */
    val primary: Boolean = false
)

/**
 * Kayıt türlerinin alan şeması.
 *
 * Yeni bir tür eklemek artık arayüz işi değil veri işi: buraya bir liste ve
 * dizelere birkaç etiket eklemek yetiyor; düzenleyici, ayrıntı sayfası, arama
 * ve kopyalama kendiliğinden çalışıyor.
 *
 * "Güvenli not"a her şeyi tıkıştırmanın maliyeti buydu — not içindeki IBAN
 * aranamıyor, kopyalanamıyor, gücü ölçülemiyordu.
 */
object CategorySchema {

    private val IDENTITY = listOf(
        FieldDef("full_name", R.string.f_full_name, FieldKind.TEXT, subtitle = true),
        FieldDef("national_id", R.string.f_national_id, FieldKind.SECRET, primary = true),
        FieldDef("birth_date", R.string.f_birth_date, FieldKind.DATE),
        FieldDef("passport_no", R.string.f_passport, FieldKind.SECRET),
        FieldDef("license_no", R.string.f_driver_license, FieldKind.SECRET),
        FieldDef("serial_no", R.string.f_serial, FieldKind.TEXT)
    )

    private val BANK = listOf(
        FieldDef("bank_name", R.string.f_bank_name, FieldKind.TEXT, subtitle = true),
        FieldDef("account_holder", R.string.f_account_holder, FieldKind.TEXT),
        FieldDef("iban", R.string.f_iban, FieldKind.SECRET, primary = true),
        FieldDef("account_no", R.string.f_account_no, FieldKind.SECRET),
        FieldDef("branch", R.string.f_branch, FieldKind.TEXT),
        FieldDef("swift", R.string.f_swift, FieldKind.TEXT)
    )

    private val SSH_KEY = listOf(
        FieldDef("host", R.string.f_host, FieldKind.TEXT, subtitle = true),
        FieldDef("username", R.string.field_username, FieldKind.TEXT),
        FieldDef("private_key", R.string.f_private_key, FieldKind.SECRET_MULTILINE, primary = true),
        FieldDef("public_key", R.string.f_public_key, FieldKind.MULTILINE),
        FieldDef("passphrase", R.string.f_key_passphrase, FieldKind.SECRET),
        FieldDef("fingerprint", R.string.f_fingerprint, FieldKind.TEXT)
    )

    private val LICENSE = listOf(
        FieldDef("product", R.string.f_product, FieldKind.TEXT, subtitle = true),
        FieldDef("version", R.string.f_version, FieldKind.TEXT),
        FieldDef("license_key", R.string.f_license_key, FieldKind.SECRET, primary = true),
        FieldDef("licensee", R.string.f_licensee, FieldKind.TEXT),
        FieldDef("purchased_at", R.string.f_purchase_date, FieldKind.DATE)
    )

    private val WIFI = listOf(
        FieldDef("ssid", R.string.f_ssid, FieldKind.TEXT, subtitle = true),
        FieldDef("wifi_password", R.string.f_wifi_password, FieldKind.SECRET, primary = true),
        FieldDef("security", R.string.f_security, FieldKind.TEXT),
        FieldDef("hidden", R.string.f_hidden, FieldKind.TEXT)
    )

    fun fieldsFor(category: Category): List<FieldDef> = when (category) {
        Category.IDENTITY -> IDENTITY
        Category.BANK -> BANK
        Category.SSH_KEY -> SSH_KEY
        Category.LICENSE -> LICENSE
        Category.WIFI -> WIFI
        else -> emptyList()
    }

    /** Kopyalanacak / gücü ölçülecek değer. */
    fun primaryValue(item: VaultItem): String {
        val def = fieldsFor(item.category).firstOrNull { it.primary } ?: return ""
        return item.extras[def.key].orEmpty()
    }

    /** Listede adın altında görünecek satır. */
    fun subtitle(item: VaultItem): String {
        val fields = fieldsFor(item.category)
        val def = fields.firstOrNull { it.subtitle } ?: fields.firstOrNull { it.kind != FieldKind.SECRET }
        return def?.let { item.extras[it.key] }.orEmpty()
    }

    /** Aramanın tarayacağı, gizli olmayan değerler. */
    fun searchableValues(item: VaultItem): List<String> =
        fieldsFor(item.category)
            .filter { it.kind != FieldKind.SECRET && it.kind != FieldKind.SECRET_MULTILINE }
            .mapNotNull { item.extras[it.key] }
            .filter { it.isNotBlank() }

    fun isSecret(kind: FieldKind): Boolean =
        kind == FieldKind.SECRET || kind == FieldKind.SECRET_MULTILINE
}
