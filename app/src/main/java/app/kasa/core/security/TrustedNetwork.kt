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
import app.kasa.core.crypto.KeystoreKeys

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
 * Ağ adı ham hâlde **hiç saklanmıyor**; saklanan şey bir özet ve o özet
 * cihazdan çıkmıyor.
 *
 * ### Neden anahtarsız özet yetmiyordu
 *
 * Burada `SHA-256(ağ adı ‖ ağ donanım kimliği)` vardı ve bu belgede "tersine
 * çevrilip hangi ağa bağlandığı çıkarılamaz" yazıyordu. Doğru değildi.
 * SHA-256 tersine çevrilemez, ama buradaki girdi yüksek entropili değil:
 * ağ adı ve donanım kimliği havada açıkça yayınlanıyor ve dünyadaki
 * erişim noktalarını konumlarıyla birlikte listeleyen herkese açık veri
 * tabanları var. Yani özet, tek yönlü bir mühür değil, halka açık bir
 * sözlüğe karşı denenebilecek bir **arama anahtarı**ydı: ayarlar dosyasını
 * eline geçiren biri, aday ağları tek tek özetleyip tutturduğunda
 * kullanıcının evinin nerede olduğunu öğrenirdi.
 *
 * Özet artık [KeystoreKeys.deviceMac] ile alınıyor: anahtar Keystore'da,
 * dışa aktarılamıyor. Aday listesi kurmak için cihazın kendisi gerekiyor,
 * dolayısıyla dosyayı okumak tek başına bir şey söylemiyor.
 *
 * ### Eski biçim
 *
 * Kayıtlı özetler `v2:` önekiyle işaretleniyor. Öneksiz bir değer eski
 * biçimdir ve hiçbir şeyle eşleşmiyor; [SettingsStore] onu okurken boş
 * sayıyor ve açılışta diskten siliyor. Yükseltme yapan kullanıcı için
 * sonuç, güvenilen ağı bir kez yeniden seçmek — ve bu arada kilit her
 * yerde kısa süreli, yani yanlış tarafa değil güvenli tarafa düşülüyor.
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

    /** Cihaza bağlı özet biçiminin işareti. Öneksiz değerler eski biçim. */
    private const val PREFIX = "v2:"

    /** Kayıtlı değer bugünkü biçimde mi? Boş değer "ayarlanmamış" sayılıyor. */
    fun isCurrentFormat(stored: String): Boolean =
        stored.isBlank() || stored.startsWith(PREFIX)

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

        // Anahtar üretilemiyorsa özellik sessizce kapanıyor: anahtarsız bir
        // özete düşmek, tam da bu yolun kapatmak için var olduğu şey olurdu.
        val mac = KeystoreKeys.deviceMac("$ssid|$bssid".toByteArray(Charsets.UTF_8)) ?: return null
        return PREFIX + Crypto.hex(mac.copyOfRange(0, DIGEST_BYTES))
    }

    /** Kayıtlı güvenilen ağda mıyız? */
    fun isTrusted(context: Context, storedFingerprint: String): Boolean {
        if (storedFingerprint.isBlank()) return false
        // Eski biçim hiçbir şeyle eşleşmiyor; karşılaştırmaya hiç girmiyor.
        if (!storedFingerprint.startsWith(PREFIX)) return false
        val current = currentFingerprint(context) ?: return false
        return current == storedFingerprint
    }
}
