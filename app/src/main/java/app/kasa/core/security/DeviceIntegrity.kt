package app.kasa.core.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import java.io.File

/**
 * Cihaz bütünlüğü sezgileri.
 *
 * Bunların hiçbiri güvenlik sınırı değildir: kararlı bir saldırgan hepsini
 * atlatabilir. Amaç, kullanıcıyı kandırmak değil bilgilendirmek — "bu cihazda
 * parolaların düşündüğün kadar korunmuyor" demek. Bu yüzden uygulama tespit
 * hâlinde kapanmaz, yalnızca bir kez uyarır; aksi hâlde geliştirici ya da
 * bilinçli kullanıcıyı kendi cihazından dışlamış oluruz.
 */
object DeviceIntegrity {

    data class Report(
        val rooted: Boolean,
        val debuggerAttached: Boolean,
        val debuggableBuild: Boolean,
        val emulator: Boolean
    ) {
        val suspicious: Boolean get() = rooted || debuggerAttached || debuggableBuild
    }

    fun check(context: Context): Report = Report(
        rooted = isRooted(),
        debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
        debuggableBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        emulator = isEmulator()
    )

    private val ROOT_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu"
    )

    private val ROOT_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su"
    )

    private fun isRooted(): Boolean =
        ROOT_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            Build.TAGS?.contains("test-keys") == true ||
            runCatching { File("/system").canWrite() }.getOrDefault(false)

    fun rootPackagesPresent(context: Context): Boolean = ROOT_PACKAGES.any { pkg ->
        runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            "google_sdk" == Build.PRODUCT
}
