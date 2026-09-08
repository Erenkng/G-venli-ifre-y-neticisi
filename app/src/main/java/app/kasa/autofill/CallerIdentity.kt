package app.kasa.autofill

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import java.security.MessageDigest

/**
 * Doldurma isteğini yapanın kimliği.
 *
 * ### Neden paket adı tek başına yetmiyor
 *
 * Paket adı cihazda benzersiz ve taklit edilemez — bu doğru. Ama zamanla
 * benzersiz değil: bir uygulama kaldırıldıktan sonra aynı paket adıyla
 * **başka bir imzayla** yeniden kurulabilir. Mağaza dışından kurulumda bu
 * sıradan bir senaryo. Parmak izi de saklandığında bağ zamanda da sabitleniyor.
 *
 * @param packageName isteği yapan uygulama
 * @param certSha256 imza sertifikasının SHA-256 parmak izi (büyük harf,
 *        iki nokta üst üste ile ayrılmış) ya da okunamadıysa `null`
 * @param webDomain tarayıcıysa görüntülenen sayfanın alan adı
 * @param isBrowser çağıran, alan adı beyanına güvenilebilecek gerçek bir
 *        tarayıcı mı — [trustedBrowser] kararı
 */
data class CallerIdentity(
    val packageName: String?,
    val certSha256: String?,
    val webDomain: String?,
    val isBrowser: Boolean
) {

    /**
     * Kayıtta saklanan bağ dizgesi: `paket|parmak izi`.
     *
     * Parmak izi okunamadıysa bağ **kurulmuyor**. Yalnızca paket adıyla bir
     * bağ yazmak, ileride imzası değişmiş bir uygulamaya da açık kapı
     * bırakırdı; eksik bilgiyle kurulan bir güven bağı, hiç kurulmamış bir
     * bağdan kötüdür çünkü sorgulanmaz.
     */
    fun linkToken(): String? {
        val pkg = packageName ?: return null
        val cert = certSha256 ?: return null
        return "$pkg|$cert"
    }

    companion object {

        /**
         * İsteği yapanın kimliğini toplar.
         *
         * Paket görünürlüğü (Android 11+) yüzünden imza bilgisi her zaman
         * okunamıyor. Manifestte başlatıcı simgesi olan uygulamaları görecek
         * bir `<queries>` bildirimi var — otomatik doldurma isteyebilen her
         * uygulama bu kümede. Yine de okunamazsa parmak izi `null` kalıyor ve
         * o uygulamayla bağ kurulmuyor; eşleşme yalnızca kullanıcının elle
         * seçimiyle oluyor.
         */
        fun of(
            context: Context,
            packageName: String?,
            webDomain: String?,
            isBrowser: Boolean
        ): CallerIdentity = CallerIdentity(
            packageName = packageName,
            certSha256 = packageName?.let { signingFingerprint(context, it) },
            webDomain = webDomain,
            // [StructureParser] yalnızca paket adına bakıyor; asıl karar burada.
            isBrowser = isBrowser && packageName != null && trustedBrowser(context, packageName)
        )

        /**
         * Paket adı tanıdık bir tarayıcıya ait olsa bile, gerçekten o tarayıcı mı?
         *
         * ### Açık neredeydi
         *
         * Tarayıcı tespiti yalnızca paket adına bakıyordu. Paket adı cihazda
         * benzersiz ama **sahipsiz**: adı taşıyan uygulama kurulu değilse o adı
         * herkes alabilir. Listedeki yirmi iki addan tipik bir telefonda bir
         * ikisi kurulu; kalanı boşta.
         *
         * Bunun bedeli, bu dosyanın en başında kapatıldığı söylenen açığın ta
         * kendisiydi. Tarayıcı sayılan çağıranın bildirdiği alan adına
         * güveniliyor ([AutofillMatcher.Tier.DOMAIN]) ve o alan adını
         * **uygulamanın kendisi** yazıyor, sistem doğrulamıyor. Yani
         * `com.android.browser` adıyla kurulan bir uygulama "sayfa
         * bankam.com.tr" deyip kasadaki gerçek banka kaydının önerilmesini
         * sağlayabiliyordu. Kullanıcı listede doğru kaydın adını gördüğü için
         * dokunuyor ve parola saldırgana gidiyordu.
         *
         * ### Neden imza parmak izi değil
         *
         * Doğrusu her tarayıcının imzasını sabitlemek olurdu. Yirmi iki
         * tarayıcının parmak izini doğru kaynaktan almadan gömmek, yanlış bir
         * değerin gerçek tarayıcıda doldurmayı sessizce bozması demek — açığın
         * kendisinden daha çok kullanıcıyı etkileyen bir sonuç.
         *
         * Onun yerine iki doğrulanabilir koşuldan biri aranıyor:
         *
         *  - **Sistem uygulaması**: ürün yazılımıyla gelmiş. Bir saldırgan
         *    ROM'a yazamadan bunu taklit edemiyor. Chrome ve Samsung Internet
         *    gibi baskın durumlar zaten buraya düşüyor.
         *  - **Kullanıcının varsayılan tarayıcısı**: kullanıcı o uygulamayı
         *    bilerek tarayıcısı olarak seçmiş. Taklit eden bir uygulama bunu
         *    kullanıcı ona dokunmadan elde edemiyor.
         *
         * Bunları sağlamayan bir tarayıcı yalnızca alan adı kademesini
         * kaybediyor; kullanıcı kaydı bir kez elle seçiyor ve o seçim imzaya
         * bağlı kalıcı bağı ([Tier.LINKED]) kuruyor. Yani bedel bir kerelik bir
         * dokunuş, kazanç ise beyanı doğrulanamayan bir çağıranın kasadan
         * kayıt isteyememesi.
         */
        fun trustedBrowser(context: Context, packageName: String): Boolean =
            isSystemApp(context, packageName) || isDefaultBrowser(context, packageName)

        private fun isSystemApp(context: Context, packageName: String): Boolean = runCatching {
            val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
            val system = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            (flags and system) != 0
        }.getOrDefault(false)

        /**
         * Bu paket, kullanıcının https bağlantılarını açmak için seçtiği
         * uygulama mı?
         *
         * Varsayılan seçilmemişse sistem seçim penceresini döndürüyor ve o da
         * hiçbir tarayıcı paketiyle eşleşmiyor — yani cevap doğal olarak
         * "hayır" oluyor. Sorgunun görünürlüğü manifestteki `<queries>`
         * bildirimiyle zaten açık.
         */
        private fun isDefaultBrowser(context: Context, packageName: String): Boolean = runCatching {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            context.packageManager
                .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == packageName
        }.getOrDefault(false)

        /**
         * Kurulu bir uygulamanın imza sertifikasının SHA-256 parmak izi.
         *
         * Biçim `assetlinks.json` ile aynı: büyük harf onaltılık, baytlar
         * iki nokta üst üste ile ayrılmış. Karşılaştırma öncesi dönüştürme
         * yapmamak için üretim anında bu biçime çevriliyor.
         *
         * Birden çok imzalayan varsa `null` dönüyor. Çok imzalı bir APK'da
         * "hangi imza bu uygulamayı temsil ediyor" sorusunun tek doğru cevabı
         * yok; birini seçmek keyfî olurdu.
         */
        fun signingFingerprint(context: Context, packageName: String): String? = runCatching {
            val info = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signing = info.signingInfo ?: return null
            val signatures: Array<Signature> = if (signing.hasMultipleSigners()) {
                return null
            } else {
                // İmza döndürme (key rotation) yapılmışsa geçmişteki ilk
                // sertifika kullanılıyor: alan adı sahibinin assetlinks.json
                // dosyasına yazdığı parmak izi genellikle odur.
                signing.signingCertificateHistory ?: signing.apkContentsSigners
            }
            val first = signatures.firstOrNull() ?: return null
            hex(MessageDigest.getInstance("SHA-256").digest(first.toByteArray()))
        }.getOrNull()

        private fun hex(bytes: ByteArray): String =
            bytes.joinToString(":") { byte -> "%02X".format(byte) }
    }
}
