package app.kasa.data

import app.kasa.core.crypto.SecretText
import app.kasa.core.util.Totp
import app.kasa.data.model.Category
import app.kasa.data.model.VaultItem

/**
 * Başka parola yöneticilerinden CSV içe aktarma.
 *
 * ### Neden gerekli
 *
 * Kasaya geçmenin önündeki tek gerçek engel, hâlihazırda başka bir yerde duran
 * yüz parola. Elle taşımak kimsenin yapmayacağı bir iş; taşınamayan kasa da
 * kurulmuyor. Chrome, Firefox ve yaygın yöneticilerin hepsi CSV veriyor.
 *
 * ### Biçim tanıma
 *
 * Sütun adları yöneticiden yöneticiye değişiyor ama hepsinde tanınabilir bir
 * başlık satırı var. Kullanıcıya "hangi yöneticiden geliyor" diye sormak yerine
 * başlık okunuyor: seçim yaptırmak, kullanıcının bilmek zorunda olmadığı bir
 * şeyi sormak olurdu ve yanlış seçimde içe aktarma sessizce bozuk kayıtlar
 * üretirdi.
 *
 * Tanınmayan başlıkta da vazgeçilmiyor: sütunlar adlarına bakılarak
 * eşleştiriliyor ("username", "user", "login", "kullanici"...). Hiçbiri
 * tutmazsa dosya reddediliyor — tahminle kayıt oluşturmak, kullanıcının
 * kasasına çöp doldurmak demek.
 *
 * ### CSV ayrıştırıcısı neden elde
 *
 * Parolalar virgül, tırnak ve satır sonu içerebiliyor ve gerçek CSV bunları
 * tırnak içinde kaçışlarla taşıyor. Satırı `split(",")` ile bölmek, içinde
 * virgül olan her parolayı bozardı — ve bozulduğu ancak o hesaba girilmeye
 * çalışıldığında anlaşılırdı. Ayrıştırıcı RFC 4180'e uyuyor: tırnaklı alan,
 * içinde çift tırnak (`""`), alan içinde satır sonu.
 */
object CsvImport {

    /** Ayrıştırma sonucu. */
    data class Result(
        val items: List<VaultItem>,
        /** Atlanan satır sayısı: parolası ve kullanıcı adı olmayanlar. */
        val skipped: Int,
        val source: Source
    )

    enum class Source { CHROME, FIREFOX, BITWARDEN, ONEPASSWORD, LASTPASS, GENERIC }

    /**
     * Metni kayıtlara çevirir.
     *
     * @return başlık okunamadıysa ya da hiç kullanılabilir satır yoksa `null`
     */
    fun parse(text: String): Result? {
        val rows = parseCsv(text)
        if (rows.size < 2) return null

        // Bayt sırası işareti (BOM) yalnızca **baştan** atılıyor.
        //
        // Öncesinde `removeSurrounding` kullanılıyordu ve o, karakteri iki
        // uçta birden arıyor: yalnızca başta duran BOM olduğu gibi kalıyordu.
        // Chrome'un ve Excel'in ürettiği dosyalarda BOM her zaman yalnızca
        // başta ve sonucu ilk sütun adının `\uFEFFname` olarak okunması —
        // yani "name" sütununun hiç bulunamaması.
        val header = rows.first().map { cell ->
            cell.removePrefix("\uFEFF").trim().trim('"').lowercase()
        }
        val columns = Columns.from(header) ?: return null

        var skipped = 0
        val items = mutableListOf<VaultItem>()

        rows.drop(1).forEach { row ->
            if (row.all { it.isBlank() }) return@forEach

            fun cell(index: Int?): String =
                if (index == null || index >= row.size) "" else row[index].trim()

            val password = cell(columns.password)
            val username = cell(columns.username)
            val url = cell(columns.url)
            val totp = cell(columns.totp)

            // Ne parola ne kullanıcı adı ne de 2FA anahtarı varsa taşınacak bir
            // şey yok; bu satırlar genellikle dışa aktarmanın kendi başlıkları
            // ya da boş klasör girdileri.
            if (password.isBlank() && username.isBlank() && totp.isBlank()) {
                skipped++
                return@forEach
            }

            val name = cell(columns.name).ifBlank { hostOf(url) }.ifBlank { username }
            if (name.isBlank()) {
                skipped++
                return@forEach
            }

            items += VaultItem(
                name = name,
                category = if (password.isBlank() && totp.isNotBlank()) Category.OTP else Category.LOGIN,
                username = username,
                password = SecretText.of(password),
                url = url,
                notes = cell(columns.notes),
                totpSecret = normalizeTotp(totp)
            )
        }

        if (items.isEmpty()) return null
        return Result(items, skipped, columns.source)
    }

    /**
     * Sütun eşlemesi.
     *
     * Her yönetici kendi adlarını kullanıyor; burada hem bilinen biçimler hem
     * de ada göre genel eşleştirme var.
     */
    private data class Columns(
        val name: Int?,
        val url: Int?,
        val username: Int?,
        val password: Int?,
        val notes: Int?,
        val totp: Int?,
        val source: Source
    ) {
        companion object {
            fun from(header: List<String>): Columns? {
                fun index(vararg names: String): Int? =
                    names.firstNotNullOfOrNull { name ->
                        header.indexOf(name).takeIf { it >= 0 }
                    }

                val source = when {
                    header.containsAll(listOf("name", "url", "username", "password")) -> Source.CHROME
                    header.contains("httprealm") || header.contains("formactionorigin") -> Source.FIREFOX
                    header.contains("login_password") -> Source.BITWARDEN
                    header.contains("grouping") && header.contains("fav") -> Source.LASTPASS
                    header.contains("otpauth") -> Source.ONEPASSWORD
                    else -> Source.GENERIC
                }

                val password = index("password", "login_password", "parola", "sifre", "şifre")
                val username = index("username", "login_username", "user", "login", "kullanici", "kullanıcı", "email")
                val totp = index("totp", "login_totp", "otpauth", "otp", "2fa")

                // Parola da kullanıcı adı da bulunamadıysa bu bir parola dışa
                // aktarımı değil; tahminle kayıt üretmek kasaya çöp doldurmak.
                if (password == null && username == null && totp == null) return null

                return Columns(
                    name = index("name", "title", "account", "ad", "baslik", "başlık"),
                    url = index("url", "uri", "login_uri", "website", "site", "adres"),
                    username = username,
                    password = password,
                    notes = index("note", "notes", "extra", "comment", "not", "notlar"),
                    totp = totp,
                    source = source
                )
            }
        }
    }

    /**
     * TOTP alanı bazen çıplak anahtar, bazen tam `otpauth://` adresi.
     * Kasa Base32 anahtar bekliyor; adresten anahtarı çıkarmak, kullanıcıyı
     * kaydı elle düzeltmekten kurtarıyor.
     */
    private fun normalizeTotp(value: String): String = when (val read = Totp.read(value)) {
        is Totp.Input.Uri -> read.config.secret
        is Totp.Input.Secret -> read.text
        else -> ""
    }

    private fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        val withScheme = if (url.contains("://")) url else "https://$url"
        return runCatching {
            java.net.URI(withScheme).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")
    }

    /**
     * RFC 4180 CSV ayrıştırıcısı.
     *
     * Tırnaklı alan, alan içinde çift tırnak (`""` → `"`), alan içinde satır
     * sonu ve hem `\n` hem `\r\n` satır sonu destekleniyor. Kütüphane
     * eklememenin sebebi: iş bu kadarla bitiyor ve bir parola yöneticisine
     * yalnızca CSV okumak için üçüncü taraf bağımlılık girmesi, saldırı
     * yüzeyini bedava genişletmek olurdu.
     */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < text.length) {
            val ch = text[index]
            when {
                inQuotes && ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    // \r\n tek satır sonu sayılıyor.
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(ch)
            }
            index++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { it.isNotEmpty() }
    }
}
