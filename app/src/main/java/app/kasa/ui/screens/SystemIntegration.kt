package app.kasa.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.core.app.NotificationManagerCompat

/**
 * Kasa'yı sistemin kendi alanlarına bağlayan üç anahtar.
 *
 * ### Neden ayrı bir dosya
 *
 * İlk ikisi Ayarlar ekranının içinde özel (private) fonksiyonlardı ve orada
 * kaldıkları sürece kurulumdan çağrılamıyorlardı. Bu tek başına küçük bir
 * ayrıntı; sonucu değildi: otomatik doldurma, uygulamanın günlük kullanıma
 * girip girmeyeceğini belirleyen şey ve yalnızca Ayarlar'ın derinliğinde
 * duruyordu. Kullanıcı orayı hiç açmazsa Kasa, elle kopyalanan bir kasa
 * olarak kalıyor — yani kullanıcıyı parola tekrarına iten sürtünme aynen
 * yerinde duruyordu.
 *
 * ### Durum okumanın simetrik olmaması
 *
 * Otomatik doldurmanın etkin olup olmadığı resmî API ile sorulabiliyor
 * ([AutofillManager.hasEnabledAutofillServices]). Geçiş anahtarı sağlayıcısı
 * için böyle bir API yok; [isCredentialProviderEnabled] bu yüzden `null`
 * dönebiliyor ve arayüz o durumda "açık/kapalı" rozeti göstermiyor.
 * Bilinmeyeni bilinmiyor diye göstermek, yanlış tahmin etmekten iyi.
 */

/** Kasa, sistemde etkin otomatik doldurma servisi mi? */
fun isAutofillEnabled(context: Context): Boolean = try {
    context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
} catch (t: Throwable) {
    false
}

fun openAutofillSettings(context: Context) {
    val request = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
        .setData(Uri.parse("package:${context.packageName}"))
    openOrFallBack(context, request)
}

/**
 * Kasa, sistemde etkin geçiş anahtarı sağlayıcısı mı?
 *
 * @return okunamıyorsa `null` — resmî bir sorgu API'si yok ve buradaki okuma
 *   belgelenmemiş bir ayar anahtarına dayanıyor. Üretici katmanı anahtarı
 *   değiştirmişse ya da okuma engellenmişse yanlış cevap vermek yerine
 *   susuluyor.
 */
fun isCredentialProviderEnabled(context: Context): Boolean? = runCatching {
    val value = Settings.Secure.getString(context.contentResolver, CREDENTIAL_SERVICE_SETTING)
        ?: return@runCatching null
    value.split(':').any { it.substringBefore('/') == context.packageName }
}.getOrNull()

fun openCredentialProviderSettings(context: Context) {
    openOrFallBack(context, Intent(Settings.ACTION_CREDENTIAL_PROVIDER))
}

/** Bildirimler kullanıcı tarafından açık mı? Sızıntı taraması buna bakıyor. */
fun areNotificationsEnabled(context: Context): Boolean =
    runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
        .getOrDefault(false)

/**
 * Niyeti başlatır; cihaz o ekranı tanımıyorsa sistem ayarlarına düşer.
 *
 * Düşüş sessiz değil işlevsel: hiçbir şey açılmaması, kullanıcının düğmenin
 * bozuk olduğunu düşünmesi demek. Ayarların kökü en azından aramanın
 * yapılabileceği yer.
 */
private fun openOrFallBack(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * `Settings.Secure` içindeki sağlayıcı listesinin anahtarı.
 *
 * Belgelenmiş bir sabit değil; okuma [isCredentialProviderEnabled] içinde
 * `runCatching` ile sarılı ve başarısızlığı bilgi eksikliği olarak ele
 * alınıyor.
 */
private const val CREDENTIAL_SERVICE_SETTING = "credential_service"
