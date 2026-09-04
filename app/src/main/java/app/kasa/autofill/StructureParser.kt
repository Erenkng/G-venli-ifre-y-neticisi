package app.kasa.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.autofill.HintConstants

/**
 * Doldurulacak formu bulur.
 *
 * Android'in otomatik doldurma ipuçları (`autofillHints`) teoride yeterlidir
 * ama pratikte uygulamaların çoğu onları hiç koymaz. Bu yüzden üç aşamalı
 * bir arama yapılıyor:
 *
 *  1. Resmi ipuçları (`AUTOFILL_HINT_USERNAME`, `AUTOFILL_HINT_PASSWORD`,
 *     kart ipuçları…).
 *  2. HTML `autocomplete` belirteci — web formlarında ipuçlarından daha sık
 *     doğru yazılıyor (`cc-number`, `new-password`, `one-time-code`).
 *  3. Giriş türü (`InputType`) ve alanın kimliği/ipucu metnindeki anahtar
 *     sözcükler, hem Türkçe hem İngilizce.
 *
 * Web görünümlerinde (`WebView`, Chrome) alan adı yerine `webDomain` üzerinden
 * eşleştirme yapılır; bu, kasadaki kaydın URL'siyle karşılaştırılır.
 *
 * ### Formun ne istediği
 *
 * Aynı ağaçta hem kart hem giriş alanı bulunabiliyor (ödeme sayfasında hesap
 * girişi de olabiliyor). [Result.kind] bunlardan **hangisinin** o formun asıl
 * işi olduğunu söylüyor ve kasadan hangi türde kayıt önerileceğini o belirliyor.
 */
class StructureParser(private val structure: AssistStructure) {

    /** Formun asıl işi. Kasadan hangi türde kayıt önerileceğini bu belirliyor. */
    enum class Kind { LOGIN, CARD, OTP }

    data class CardFields(
        val number: AutofillId? = null,
        val holder: AutofillId? = null,
        val expiryMonth: AutofillId? = null,
        val expiryYear: AutofillId? = null,
        /** Ay ve yılı tek alanda isteyen formlar ("AA/YY"). */
        val expiryDate: AutofillId? = null,
        val cvv: AutofillId? = null,
        /**
         * Yıl alanının kaç hane istediği.
         *
         * Kasa yılı iki hane saklıyor ama formların bir kısmı dört hane
         * istiyor. Alanın uzunluk sınırı bunu söylüyor; sınır yoksa iki hane
         * varsayılıyor, çünkü kart üzerinde de öyle yazıyor.
         */
        val yearDigits: Int = 2
    ) {
        val any: Boolean
            get() = number != null || holder != null || expiryMonth != null ||
                expiryYear != null || expiryDate != null || cvv != null

        /** Kart numarası ya da son kullanma tarihi var mı? Bkz. [Result.kind]. */
        val strong: Boolean
            get() = number != null || expiryMonth != null || expiryYear != null ||
                expiryDate != null

        fun ids(): List<AutofillId> =
            listOfNotNull(number, holder, expiryMonth, expiryYear, expiryDate, cvv)
    }

    data class Result(
        val usernameId: AutofillId?,
        /**
         * Formdaki bütün parola alanları, ağaçtaki sıralarıyla.
         *
         * Kayıt formlarında iki tane oluyor (parola + tekrarı) ve ikisini de
         * doldurmak gerekiyor: yalnızca ilkini doldurmak kullanıcıyı ikinciyi
         * elle yazmaya bırakıyor, o da otomatik doldurmanın kazandırdığı şeyi
         * geri alıyor.
         */
        val passwordIds: List<AutofillId>,
        /** Tek kullanımlık kod alanı; iki adımlı doğrulamanın ikinci ekranı. */
        val otpId: AutofillId?,
        val card: CardFields,
        /**
         * Bu bir kayıt (üye ol) formu mu?
         *
         * Açık beyandan (`newPassword`, `autocomplete="new-password"`) ya da
         * birden çok parola alanı bulunmasından anlaşılıyor. Kayıt formunda
         * kasadaki bir paroladan çok, **yeni bir parola üretmek** isteniyor.
         */
        val registration: Boolean,
        val packageName: String?,
        val webDomain: String?,
        val isBrowser: Boolean
    ) {
        val passwordId: AutofillId? get() = passwordIds.firstOrNull()

        val usable: Boolean
            get() = usernameId != null || passwordIds.isNotEmpty() || otpId != null || card.any

        /** Yalnızca kod isteyen ekran: parola alanı yok, kod alanı var. */
        val otpOnly: Boolean get() = otpId != null && passwordIds.isEmpty() && !card.any

        /**
         * Formun asıl işi.
         *
         * Kart, giriş alanlarının önüne geçiyor ama yalnızca **güçlü** bir kart
         * kanıtı varsa: tek başına bir "güvenlik kodu" alanı, ödeme formu
         * olduğunu söylemeye yetmiyor.
         */
        val kind: Kind
            get() = when {
                card.strong -> Kind.CARD
                otpOnly -> Kind.OTP
                usernameId != null || passwordIds.isNotEmpty() -> Kind.LOGIN
                card.any -> Kind.CARD
                else -> Kind.LOGIN
            }

        /** Doldurulabilecek bütün alanlar; kaydetme bilgisi bunlara dayanıyor. */
        fun allIds(): List<AutofillId> =
            listOfNotNull(usernameId) + passwordIds + listOfNotNull(otpId) + card.ids()
    }

    private var usernameId: AutofillId? = null
    private val passwordIds = mutableListOf<AutofillId>()
    private var otpId: AutofillId? = null
    private var webDomain: String? = null
    private var registration = false

    private var cardNumber: AutofillId? = null
    private var cardHolder: AutofillId? = null
    private var cardMonth: AutofillId? = null
    private var cardYear: AutofillId? = null
    private var cardDate: AutofillId? = null
    private var cardCvv: AutofillId? = null
    private var cardYearDigits = 2

    /**
     * Güvenlik kodu alanı yalnızca anahtar sözcükten bulunduysa true.
     *
     * "Güvenlik kodu" hem kartın CVV'si hem de SMS ile gelen doğrulama kodu
     * anlamına geliyor ve ikisi Türkçede birebir aynı sözcük. Ayrım formun
     * geri kalanından çıkıyor: ortada kart numarası ya da son kullanma tarihi
     * yoksa o alan CVV değil, doğrulama kodudur. Karar ağaç gezildikten sonra
     * veriliyor, çünkü kart numarası o alandan **sonra** da gelebiliyor.
     */
    private var cvvGuessed = false

    fun parse(): Result {
        val packageName = structure.activityComponent?.packageName
        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode)
        }

        // Tahminle bulunmuş bir CVV, ortada kart kanıtı yoksa doğrulama kodudur.
        if (cvvGuessed && cardNumber == null && cardMonth == null && cardYear == null && cardDate == null) {
            if (otpId == null) otpId = cardCvv
            cardCvv = null
        }

        val browser = isBrowser(packageName)
        return Result(
            usernameId = usernameId,
            passwordIds = passwordIds.toList(),
            otpId = otpId,
            card = CardFields(
                number = cardNumber,
                holder = cardHolder,
                expiryMonth = cardMonth,
                expiryYear = cardYear,
                expiryDate = cardDate,
                cvv = cardCvv,
                yearDigits = cardYearDigits
            ),
            // İki parola alanı da kayıt formu demek: giriş ekranında parolanın
            // tekrarı sorulmuyor.
            registration = registration || passwordIds.size >= 2,
            packageName = packageName,
            // Alan adı yalnızca tarayıcıdan geliyorsa kabul ediliyor; gerekçesi
            // [isBrowser] üzerinde yazılı.
            webDomain = if (browser) webDomain else null,
            isBrowser = browser
        )
    }

    private fun traverse(node: AssistStructure.ViewNode) {
        if (webDomain == null) {
            val domain = node.webDomain?.trim()?.lowercase()
            val scheme = node.webScheme?.lowercase()
            // Şema varsa https/http olmalı. `javascript:` ya da `data:` gibi bir
            // şemayla gelen "alan adı" gerçek bir kaynak değildir.
            if (!domain.isNullOrBlank() && (scheme == null || scheme == "https" || scheme == "http")) {
                webDomain = domain.removePrefix("www.")
            }
        }

        val id = node.autofillId
        if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
            // Sıra önemli ve her adımın gerekçesi var:
            //
            //  - Kart alanları en başta, çünkü kart numarası bir "kullanıcı
            //    adı" gibi de okunabiliyor (rakam, uzun, kimliğe benziyor).
            //  - Parola, koddan önce: bir parola alanının ipucunda "kod"
            //    geçebiliyor ("güvenlik kodu").
            //  - Kullanıcı adı en sonda, çünkü sınaması en geniş olan o.
            when {
                isCardNumber(node) -> if (cardNumber == null) cardNumber = id
                isCardExpiry(node, MONTH_KEYWORDS, View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH, "cc-exp-month") ->
                    if (cardMonth == null) cardMonth = id
                isCardExpiry(node, YEAR_KEYWORDS, View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR, "cc-exp-year") ->
                    if (cardYear == null) {
                        cardYear = id
                        if (node.maxTextLength == 4) cardYearDigits = 4
                    }
                isCardExpiryDate(node) -> if (cardDate == null) cardDate = id
                isCardSecurityCode(node) -> if (cardCvv == null) cardCvv = id
                isCardHolder(node) -> if (cardHolder == null) cardHolder = id
                isPassword(node) -> {
                    if (isNewPassword(node)) registration = true
                    if (id !in passwordIds) passwordIds += id
                }
                isOneTimeCode(node) -> if (otpId == null) otpId = id
                isUsername(node) -> if (usernameId == null) usernameId = id
            }
        }

        for (i in 0 until node.childCount) traverse(node.getChildAt(i))
    }

    // ── kart alanları ────────────────────────────────────────────────────

    private fun isCardNumber(node: AssistStructure.ViewNode): Boolean {
        if (hasHint(node, View.AUTOFILL_HINT_CREDIT_CARD_NUMBER)) return true
        if (hasAutocomplete(node, "cc-number")) return true
        // Anahtar sözcük yolunda alanın sayısal olması aranıyor: "kart" sözcüğü
        // "kart sahibi" alanında da geçiyor ve oraya numara yazmak yanlış olur.
        return matchesKeyword(node, CARD_NUMBER_KEYWORDS) && isNumeric(node)
    }

    private fun isCardExpiry(
        node: AssistStructure.ViewNode,
        keywords: Array<String>,
        hint: String,
        autocomplete: String
    ): Boolean {
        if (hasHint(node, hint)) return true
        if (hasAutocomplete(node, autocomplete)) return true
        return matchesKeyword(node, keywords)
    }

    private fun isCardExpiryDate(node: AssistStructure.ViewNode): Boolean {
        if (hasHint(node, View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE)) return true
        if (hasAutocomplete(node, "cc-exp")) return true
        return matchesKeyword(node, EXPIRY_KEYWORDS)
    }

    /**
     * Kartın güvenlik kodu.
     *
     * Açık beyan yoksa uzunluk sınırı isteniyor: CVV üç ya da dört hane. Sınırı
     * olmayan bir alana kart güvenlik kodu yazmak, oranın gerçekten CVV olduğu
     * bilgisi olmadan yapılan bir tahmin olurdu.
     */
    private fun isCardSecurityCode(node: AssistStructure.ViewNode): Boolean {
        if (hasHint(node, View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE)) return true
        if (hasAutocomplete(node, "cc-csc")) return true
        if (node.maxTextLength !in CVV_LENGTHS) return false
        if (!matchesKeyword(node, CVV_KEYWORDS)) return false
        cvvGuessed = true
        return true
    }

    private fun isCardHolder(node: AssistStructure.ViewNode): Boolean {
        if (hasAutocomplete(node, "cc-name")) return true
        // `AUTOFILL_HINT_NAME` tek başına yetmiyor: her formda ad alanı olabilir
        // ve hepsine kart sahibinin adını yazmak yanlış. Kart bağlamı anahtar
        // sözcükten geliyor ("kart sahibi", "name on card").
        return matchesKeyword(node, CARD_HOLDER_KEYWORDS)
    }

    // ── giriş alanları ───────────────────────────────────────────────────

    private fun isPassword(node: AssistStructure.ViewNode): Boolean {
        if (hasHint(node, View.AUTOFILL_HINT_PASSWORD)) return true
        if (hasHint(node, HintConstants.AUTOFILL_HINT_NEW_PASSWORD)) return true
        if (hasAutocomplete(node, "current-password") || hasAutocomplete(node, "new-password")) return true

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val isTextClass = (node.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        if (isTextClass && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )
        ) return true

        return matchesKeyword(node, PASSWORD_KEYWORDS)
    }

    /** Yeni parola mı isteniyor (kayıt formu), var olan mı (giriş formu)? */
    private fun isNewPassword(node: AssistStructure.ViewNode): Boolean {
        if (hasHint(node, HintConstants.AUTOFILL_HINT_NEW_PASSWORD)) return true
        if (hasAutocomplete(node, "new-password")) return true
        return matchesKeyword(node, NEW_PASSWORD_KEYWORDS)
    }

    private fun isUsername(node: AssistStructure.ViewNode): Boolean {
        if (hasHint(node, View.AUTOFILL_HINT_USERNAME) ||
            hasHint(node, View.AUTOFILL_HINT_EMAIL_ADDRESS) ||
            hasHint(node, View.AUTOFILL_HINT_PHONE)
        ) return true
        if (hasAutocomplete(node, "username") || hasAutocomplete(node, "email")) return true

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) return true

        return matchesKeyword(node, USERNAME_KEYWORDS)
    }

    /**
     * Tek kullanımlık kod alanı mı?
     *
     * Buradaki asıl zorluk yanlış pozitif. "code" sözcüğü posta kodunda, ülke
     * kodunda, indirim kodunda da geçiyor ve oraya TOTP kodu önermek en hafif
     * tabirle şaşırtıcı olurdu. Bu yüzden iki ayrı yol var:
     *
     *  - **Açık beyan** — `AUTOFILL_HINT_SMS_OTP` ya da HTML'de
     *    `autocomplete="one-time-code"`. Uygulama/sayfa ne istediğini
     *    söylemiş; başka sınamaya gerek yok.
     *  - **Tahmin** — anahtar sözcük eşleşmesi, ama yalnızca alan gerçekten
     *    kısa bir koda benziyorsa: uzunluk sınırı 4-10 arası. Sınırı olmayan
     *    bir metin alanına tek kullanımlık kod önerilmiyor.
     */
    private fun isOneTimeCode(node: AssistStructure.ViewNode): Boolean {
        // Tek kullanımlık kod ipucu android.view.View içinde yok;
        // androidx.autofill kitaplığının sabit listesinde ("smsOTPCode").
        if (hasHint(node, HintConstants.AUTOFILL_HINT_SMS_OTP)) return true
        if (hasAutocomplete(node, "one-time-code")) return true

        val length = node.maxTextLength
        if (length !in OTP_LENGTHS) return false
        return matchesKeyword(node, OTP_KEYWORDS)
    }

    // ── ortak sınamalar ──────────────────────────────────────────────────

    private fun hasHint(node: AssistStructure.ViewNode, hint: String): Boolean =
        node.autofillHints?.any { it.equals(hint, ignoreCase = true) } == true

    /**
     * HTML `autocomplete` belirteci.
     *
     * Belirteç birden çok parçadan oluşabiliyor (`"shipping cc-number"`), o
     * yüzden içerik araması yapılıyor. Web formlarında bu alan, Android'in
     * kendi ipuçlarından belirgin biçimde daha sık ve daha doğru yazılıyor.
     */
    private fun hasAutocomplete(node: AssistStructure.ViewNode, token: String): Boolean {
        val value = node.htmlInfo?.attributes
            ?.firstOrNull { it.first.equals("autocomplete", ignoreCase = true) }
            ?.second
            ?.lowercase()
            ?: return false
        return value.contains(token)
    }

    private fun isNumeric(node: AssistStructure.ViewNode): Boolean {
        val klass = node.inputType and InputType.TYPE_MASK_CLASS
        if (klass == InputType.TYPE_CLASS_NUMBER || klass == InputType.TYPE_CLASS_PHONE) return true
        // Web formlarında sayı sınıfı çoğu zaman yok; uzunluk sınırı kart
        // numarası aralığındaysa (boşluklu yazım dâhil) sayısal sayılıyor.
        return node.maxTextLength in CARD_NUMBER_LENGTHS
    }

    private fun matchesKeyword(node: AssistStructure.ViewNode, keywords: Array<String>): Boolean {
        val haystack = buildString {
            node.idEntry?.let { append(it).append(' ') }
            node.hint?.let { append(it).append(' ') }
            node.contentDescription?.let { append(it).append(' ') }
            node.htmlInfo?.attributes?.forEach { attribute ->
                if (attribute.first == "name" || attribute.first == "id" ||
                    attribute.first == "placeholder" || attribute.first == "aria-label"
                ) {
                    append(attribute.second).append(' ')
                }
            }
        }.lowercase()

        if (haystack.isBlank()) return false
        return keywords.any { haystack.contains(it) }
    }

    /**
     * Çağıran bir tarayıcı mı?
     *
     * Bu ayrım güvenlik açısından belirleyici. `ViewNode.webDomain` alanını
     * **uygulamanın kendisi** dolduruyor; sistem doğrulamıyor. Yani kötü niyetli
     * bir uygulama, kendi giriş formundaki bir alana `webDomain = "bankam.com"`
     * yazıp kasadan o bankanın parolasını isteyebilir — kullanıcı da doğru
     * kaydı gördüğü için gönül rahatlığıyla dokunur.
     *
     * Tarayıcılarda bu alan gerçekten görüntülenen sayfanın adresidir ve
     * tarayıcının kendisi güvenilir bir aracıdır. Bu yüzden alan adı eşleşmesi
     * yalnızca tarayıcılarda kullanılıyor; başka her uygulamada eşleştirme
     * paket adı üzerinden yapılıyor ve paket adını taklit etmek imza
     * doğrulaması yüzünden mümkün değil.
     *
     * Liste kaçınılmaz olarak eksik: tanınmayan bir tarayıcıda alan adı
     * eşleşmesi çalışmaz, kullanıcı kaydı elle seçer. Yanlış tarafta kalmanın
     * bedeli budur — kimlik avına açık kalmaktan iyidir.
     */
    private fun isBrowser(packageName: String?): Boolean =
        packageName != null && packageName in BROWSERS

    private companion object {
        /** Alan adı beyanına güvenilen tarayıcılar. */
        val BROWSERS = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "org.chromium.chrome",
            "com.ecosia.android",
            "com.yandex.browser",
            "com.UCMobile.intl",
            "com.android.browser"
        )

        val PASSWORD_KEYWORDS = arrayOf(
            // "pass" tek başına yok: "passport", "passenger", "compass" gibi
            // alanları parola sanıp oraya parola yazdırırdı.
            "password", "passwd", "pwd",
            "sifre", "şifre", "parola"
        )

        /** Kayıt formunu ele veren sözcükler: yeni parola ve tekrarı. */
        val NEW_PASSWORD_KEYWORDS = arrayOf(
            "new-password", "newpassword", "new_password",
            "confirm", "repeat", "retype", "again", "verify password",
            "yeni parola", "yeni sifre", "yeni şifre",
            "tekrar", "yeniden", "dogrula", "doğrula"
        )

        /**
         * Tahmin yoluyla kod alanı sayılacak uzunluk sınırı aralığı.
         * Altı hane yaygın, sekiz de var; on üstü artık kod değil.
         */
        val OTP_LENGTHS = 4..10

        /** CVV üç hane; American Express'te dört. */
        val CVV_LENGTHS = 3..4

        /**
         * Kart numarası alanının uzunluk sınırı aralığı.
         * On üç hane (eski Visa) ile on dokuz hane (bazı ortak markalı kartlar)
         * arasında; boşluklu yazımda yirmi üçe kadar çıkabiliyor.
         */
        val CARD_NUMBER_LENGTHS = 13..23

        val OTP_KEYWORDS = arrayOf(
            "otp", "one-time", "onetime", "totp", "2fa", "mfa", "authenticator",
            "verification", "verify",
            "dogrulama", "doğrulama", "tek kullanimlik", "tek kullanımlık", "guvenlik kodu",
            "güvenlik kodu", "sms kodu", "onay kodu"
        )

        val USERNAME_KEYWORDS = arrayOf(
            "username", "user", "login", "email", "e-mail", "account", "identifier",
            "kullanici", "kullanıcı", "eposta", "e-posta", "hesap", "giris", "giriş", "telefon"
        )

        val CARD_NUMBER_KEYWORDS = arrayOf(
            "cardnumber", "card-number", "card_number", "cardno", "ccnumber", "creditcard",
            "kart numara", "kartnumara", "kart no", "kartno"
        )

        val CARD_HOLDER_KEYWORDS = arrayOf(
            "cardholder", "card-holder", "card_holder", "cardname", "name on card", "ccname",
            "kart sahibi", "kartsahibi", "kart uzerindeki", "kart üzerindeki"
        )

        val EXPIRY_KEYWORDS = arrayOf(
            "expiry", "expiration", "expdate", "exp-date", "exp_date", "valid thru", "validthru",
            "son kullanma", "sonkullanma", "gecerlilik", "geçerlilik", "skt"
        )

        val MONTH_KEYWORDS = arrayOf("expmonth", "exp-month", "exp_month", "expirymonth", "ay")

        val YEAR_KEYWORDS = arrayOf("expyear", "exp-year", "exp_year", "expiryyear", "yil", "yıl")

        val CVV_KEYWORDS = arrayOf(
            "cvv", "cvc", "csc", "cid", "security code", "securitycode",
            "guvenlik kodu", "güvenlik kodu"
        )
    }
}
