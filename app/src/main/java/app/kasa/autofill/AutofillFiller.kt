package app.kasa.autofill

import android.content.Context
import android.view.autofill.AutofillId
import app.kasa.R
import app.kasa.core.util.Totp
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem

/**
 * Bir kayıttan bir forma **ne yazılacağı**.
 *
 * ### Neden ayrı bir yer
 *
 * Bu mantık iki yerde birden yazılıydı: doldurma servisinde ve kimlik
 * doğrulama penceresinde. İkisi aynı kararı vermek zorunda — kullanıcı için
 * "önerilen kayıt" ile "seçtiğim kayıt" arasında bir fark yok — ama ayrı ayrı
 * yazıldıkları için çoktan ayrışmışlardı: servis satır içi öneri kuruyordu,
 * pencere kurmuyordu. Kart desteği eklenince aynı ayrışma ikinci kez olacaktı.
 *
 * Sunum burada değil, çünkü iki çağıranın sunum ihtiyacı gerçekten farklı:
 * servis satır içi öneri de üretiyor, pencere yalnızca menü satırı. Ortak olan
 * şey hangi alana ne yazılacağı ve o burada.
 */
object AutofillFiller {

    /** Tek bir alana yazılacak değer. */
    data class Field(val id: AutofillId, val value: String)

    /**
     * Bu kayıt bu forma sunulabilir mi?
     *
     * Tür uyumu: ödeme formuna giriş kaydı, giriş formuna kart sunmak
     * kullanıcıya seçemeyeceği bir liste göstermek olurdu.
     */
    fun offers(parsed: StructureParser.Result, item: VaultItem): Boolean =
        when (parsed.kind) {
            StructureParser.Kind.CARD ->
                item.category == Category.CARD && item.cardNumber.isNotBlank()
            StructureParser.Kind.OTP ->
                item.totpSecret.isNotBlank()
            StructureParser.Kind.LOGIN ->
                item.category == Category.LOGIN || item.category == Category.OTP
        }

    /**
     * Bu kayıttan bu forma yazılacak alanlar. Liste boşsa kayıt sunulmuyor.
     *
     * ### Tek kullanımlık kod
     *
     * Form bir kod alanı istiyorsa ve kayıtta TOTP anahtarı varsa kod burada
     * üretilip alana yazılıyor. Günlük kullanımda en çok zaman kazandıran
     * ekleme bu: kullanıcı parolayı doldurduktan sonra uygulamayı açıp kodu
     * okumak, ezberlemek ve öteki uygulamaya yazmak zorunda kalmıyor.
     *
     * Kod, kümenin kurulduğu anda üretiliyor ve otuz saniyelik pencereye
     * bağlı. Kullanıcı öneriyi geç seçerse kod geçersiz olabilir; bunun
     * alternatifi kodu hiç önermemekti.
     *
     * ### Kayıt formunda iki parola alanı
     *
     * Parolanın tekrarı da dolduruluyor. Yalnızca ilkini doldurmak kullanıcıyı
     * yirmi karakterlik bir parolayı elle yazmaya bırakıyor ve otomatik
     * doldurmanın kazandırdığı şeyi olduğu gibi geri alıyordu.
     */
    fun values(parsed: StructureParser.Result, item: VaultItem): List<Field> {
        if (!offers(parsed, item)) return emptyList()

        val fields = mutableListOf<Field>()
        fun put(id: AutofillId?, value: String?) {
            if (id == null || value.isNullOrBlank()) return
            fields += Field(id, value)
        }

        when (parsed.kind) {
            StructureParser.Kind.CARD -> {
                val card = parsed.card
                put(card.number, item.cardNumber.filter { it.isDigit() })
                put(card.holder, item.cardHolder)
                put(card.cvv, item.cardCvv)

                // Kasa son kullanma tarihini dört hane saklıyor: AAYY.
                val expiry = item.cardExpiry.filter { it.isDigit() }
                if (expiry.length == 4) {
                    val month = expiry.take(2)
                    val year = expiry.takeLast(2)
                    put(card.expiryMonth, month)
                    put(card.expiryYear, if (card.yearDigits == 4) "20$year" else year)
                    put(card.expiryDate, "$month/$year")
                }
            }

            StructureParser.Kind.OTP -> {
                put(parsed.otpId, code(item))
            }

            StructureParser.Kind.LOGIN -> {
                put(parsed.usernameId, item.username)
                val password = item.password.reveal()
                // Kayıt formunda parolanın tekrarı da aynı değeri alıyor.
                parsed.passwordIds.forEach { put(it, password) }
                put(parsed.otpId, code(item))
            }
        }

        return fields
    }

    /** Menüde ve satır içi öneride adın altında görünen satır. */
    fun subtitle(context: Context, parsed: StructureParser.Result, item: VaultItem): String =
        when (parsed.kind) {
            StructureParser.Kind.CARD -> item.maskedCard()
            StructureParser.Kind.OTP -> context.getString(R.string.af_otp_subtitle)
            StructureParser.Kind.LOGIN -> item.username.ifBlank { item.host().orEmpty() }
        }

    private fun code(item: VaultItem): String? =
        if (item.totpSecret.isNotBlank()) {
            Totp.code(item.totpSecret, item.totpDigits, item.totpPeriod, item.totpAlgorithm)
        } else null
}
