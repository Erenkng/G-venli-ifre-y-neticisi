package app.kasa.autofill

import android.app.assist.AssistStructure
import android.os.Build
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
        val packageName: String?,
        val webDomain: String?
    ) {
        val usable: Boolean get() = usernameId != null || passwordId != null
    }

    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null
    private var webDomain: String? = null

    fun parse(): Result {
        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode)
        }
        return Result(
            usernameId = usernameId,
            passwordId = passwordId,
            packageName = structure.activityComponent?.packageName,
            webDomain = webDomain
        )
    }

    private fun traverse(node: AssistStructure.ViewNode) {
        node.webDomain?.takeIf { it.isNotBlank() }?.let { if (webDomain == null) webDomain = it }

        val id = node.autofillId
        if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
            when {
                isPassword(node) -> if (passwordId == null) passwordId = id
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

    private fun matchesKeyword(node: AssistStructure.ViewNode, keywords: Array<String>): Boolean {
        val haystack = buildString {
            node.idEntry?.let { append(it).append(' ') }
            node.hint?.let { append(it).append(' ') }
            node.contentDescription?.let { append(it).append(' ') }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                node.htmlInfo?.attributes?.forEach { attribute ->
                    if (attribute.first == "name" || attribute.first == "id" || attribute.first == "placeholder") {
                        append(attribute.second).append(' ')
                    }
                }
            }
        }.lowercase()

        if (haystack.isBlank()) return false
        return keywords.any { haystack.contains(it) }
    }

    private companion object {
        val PASSWORD_KEYWORDS = arrayOf(
            "password", "passwd", "pwd", "pass",
            "sifre", "şifre", "parola", "gizli"
        )
        val USERNAME_KEYWORDS = arrayOf(
            "username", "user", "login", "email", "e-mail", "account", "identifier",
            "kullanici", "kullanıcı", "eposta", "e-posta", "hesap", "giris", "giriş", "telefon"
        )
    }
}
