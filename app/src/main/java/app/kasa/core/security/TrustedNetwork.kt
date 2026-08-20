package app.kasa.core.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import app.kasa.core.crypto.Crypto

/**
 * "Bu, ev ağım mı?" sorusunun cevabı.
 *
 * ### Ne işe yarıyor
 *
 * Otomatik kilit süresi her yerde aynı olmak zorunda değil. Evde ya da işte,
 * telefonun kaybolma ihtimalinin düşük olduğu bir yerde beş dakika makul;
 * metroda ya da kafede ise uygulamadan çıkar çıkmaz kilitlenmesi doğru.
 * Tek bir süre seçmek zorunda kalan kullanıcı, ikisinin de yanlış olduğu bir
 * orta yol seçiyor — genellikle de rahat olanı.
 *
 * ### Verinin nereye gittiği: hiçbir yere
 *
 * Ağ adı ham hâlde **hiç saklanmıyor**. Saklanan tek şey
 * `SHA-256(ağ adı ‖ ağ donanım kimliği)` özetinin ilk 16 baytı. Özet
 * karşılaştırma dışında hiçbir işe yaramıyor; tersine çevrilip "kullanıcı
 * hangi ağa bağlanıyor" bilgisi çıkarılamıyor ve zaten cihazdan çıkmıyor.
 *
 * ### Neden konum izni isteniyor
 *
 * Android 10'dan beri bağlı olunan Wi-Fi ağının adını okumak konum izni
 * gerektiriyor: ağ adı, konum belirlemeye yarayabildiği için konum verisi
 * sayılıyor. Bu yüzden özellik **tamamen isteğe bağlı** ve izin verilmediğinde
 * sessizce devre dışı kalıyor — kullanıcıyı izin vermeye zorlayan hiçbir akış
 * yok, çünkü kazanç (birkaç dakika rahatlık) bedeli (sürekli konum izni)
 * herkes için karşılamıyor.
 */
object TrustedNetwork {

    /** Özetin saklanan uzunluğu. Çakışma olasılığı yok denecek kadar düşük. */
    private const val DIGEST_BYTES = 16

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Şu an bağlı olunan Wi-Fi ağının özeti; Wi-Fi yoksa, izin yoksa ya da ağ
     * adı okunamıyorsa `null`.
     *
     * `null` dönmesi "güvenilmeyen ağ" anlamına geliyor ve bu, güvenli olan
     * varsayılan: bilinmeyen bir ortamda kısa süre kullanılıyor.
     */
    fun currentFingerprint(context: Context): String? {
        if (!hasPermission(context)) return null

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        // Android 12'den beri doğru yol bu: bağlı ağın bilgisi, ağ
        // yeteneklerinin içinden geliyor. WifiManager.connectionInfo eskidi ve
        // hangi ağın sorulduğunu belirsiz bırakıyordu.
        val info = capabilities.transportInfo as? WifiInfo
            ?: (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.connectionInfo
            ?: return null

        return fingerprintOf(info)
    }

    private fun fingerprintOf(info: WifiInfo): String? {
        val ssid = info.ssid?.trim('"').orEmpty()
        val bssid = info.bssid.orEmpty()

        // İzin yokken sistem bu alanları maskeliyor; maskeli değerle özet
        // üretmek her ağı "aynı ağ" gibi gösterirdi.
        if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) return null
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return null

        val digest = Crypto.sha256("$ssid|$bssid".toByteArray(Charsets.UTF_8))
        return Crypto.hex(digest.copyOfRange(0, DIGEST_BYTES))
    }

    /** Kayıtlı güvenilen ağda mıyız? */
    fun isTrusted(context: Context, storedFingerprint: String): Boolean {
        if (storedFingerprint.isBlank()) return false
        val current = currentFingerprint(context) ?: return false
        return current == storedFingerprint
    }
}
