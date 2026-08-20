package app.kasa.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/**
 * Doldurulacak formu bulur.
 *
 * Android'in otomatik doldurma ipuçları (`autofillHints`) teoride yeterlidir
 * ama pratikte uygulamaların çoğu onları hiç koymaz. Bu yüzden üç aşamalı
 * bir arama yapılıyor:
 *
 *  1. Resmi ipuçları (`AUTOFILL_HINT_USERNAME`, `AUTOFILL_HINT_PASSWORD`).
 *  2. Giriş türü (`InputType`) — parola maskesi olan alanlar.
 *  3. Alanın kimliği/ipucu metnindeki anahtar sözcükler ("kullanıcı", "eposta",
 *     "sifre", "password"…), hem Türkçe hem İngilizce.
 *
 * Web görünümlerinde (`WebView`, Chrome) alan adı yerine `webDomain` üzerinden
 * eşleştirme yapılır; bu, kasadaki kaydın URL'siyle karşılaştırılır.
 */
class StructureParser(private val structure: AssistStructure) {

    data class Result(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        /** Tek kullanımlık kod alanı; iki adımlı doğrulamanın ikinci ekranı. */
        val otpId: AutofillId?,
        val packageName: String?,
        val webDomain: String?,
        val isBrowser: Boolean
    ) {
        val usable: Boolean get() = usernameId != null || passwordId != null || otpId != null

        /** Yalnızca kod isteyen ekran: parola alanı yok, kod alanı var. */
        val otpOnly: Boolean get() = otpId != null && passwordId == null
    }

    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null
    private var otpId: AutofillId? = null
    private var webDomain: String? = null

    fun parse(): Result {
        val packageName = structure.activityComponent?.packageName
        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode)
        }
        val browser = isBrowser(packageName)
        return Result(
            usernameId = usernameId,
            passwordId = passwordId,
            otpId = otpId,
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
            when {
                // Sıra önemli: parola sınaması önce geliyor, çünkü bir parola
                // alanının ipucu metninde "kod" geçebiliyor ("güvenlik kodu").
                isPassword(node) -> if (passwordId == null) passwordId = id
                isOneTimeCode(node) -> if (otpId == null) otpId = id
                isUsername(node) -> if (usernameId == null) usernameId = id
            }
        }

        for (i in 0 until node.childCount) traverse(node.getChildAt(i))
    }

    private fun isPassword(node: AssistStructure.ViewNode): Boolean {
        node.autofillHints?.forEach { hint ->
            if (hint.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true)) return true
        }
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

    private fun isUsername(node: AssistStructure.ViewNode): Boolean {
        node.autofillHints?.forEach { hint ->
            if (hint.equals(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                hint.equals(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true) ||
                hint.equals(View.AUTOFILL_HINT_PHONE, ignoreCase = true)
            ) return true
        }
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
        node.autofillHints?.forEach { hint ->
            if (hint.equals(View.AUTOFILL_HINT_SMS_OTP, ignoreCase = true)) return true
        }
        val autocomplete = node.htmlInfo?.attributes
            ?.firstOrNull { it.first.equals("autocomplete", ignoreCase = true) }
            ?.second
            ?.lowercase()
        if (autocomplete != null && autocomplete.contains("one-time-code")) return true

        val length = node.maxTextLength
        if (length !in OTP_LENGTHS) return false
        return matchesKeyword(node, OTP_KEYWORDS)
    }

    private fun matchesKeyword(node: AssistStructure.ViewNode, keywords: Array<String>): Boolean {
        val haystack = buildString {
            node.idEntry?.let { append(it).append(' ') }
            node.hint?.let { append(it).append(' ') }
            node.contentDescription?.let { append(it).append(' ') }
            node.htmlInfo?.attributes?.forEach { attribute ->
                if (attribute.first == "name" || attribute.first == "id" || attribute.first == "placeholder") {
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
        /**
         * Tahmin yoluyla kod alanı sayılacak uzunluk sınırı aralığı.
         * Altı hane yaygın, sekiz de var; on üstü artık kod değil.
         */
        val OTP_LENGTHS = 4..10

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
    }
}
