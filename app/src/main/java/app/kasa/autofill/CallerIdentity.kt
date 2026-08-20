package app.kasa.autofill

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
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
 * @param isBrowser [StructureParser] tarafından tanınan bir tarayıcı mı
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
            isBrowser = isBrowser
        )

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
