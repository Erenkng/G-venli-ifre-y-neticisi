package app.kasa.passkey

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.PublicKeyCredentialEntry
import app.kasa.KasaApplication
import app.kasa.core.webauthn.PasskeyProtocol
import java.time.Instant

/**
 * Sistem seçicisinde görünen passkey satırlarını kuran ortak kod.
 *
 * İki yerden çağrılıyor: kasa açıkken doğrudan
 * [KasaCredentialProviderService], kilitliyken kilidi açan
 * [PasskeyActivity]. İkisinin de aynı listeyi üretmesi şart — biri
 * ötekinden farklı bir hesap gösterirse kullanıcı kilidi açtıktan sonra
 * aradığını bulamaz.
 */
object PasskeyEntries {

    /**
     * Bir istek seçeneği için gösterilecek hesap satırları.
     *
     * `allowCredentials` doluysa doğrulayıcı taraf belirli kimlik bilgilerini
     * istiyor demektir; listeyi ona göre daraltmak şart, yoksa kullanıcıya
     * seçtirdiğimiz hesap sunucuda kabul edilmez.
     */
    fun forOption(
        context: Context,
        option: BeginGetPublicKeyCredentialOption
    ): List<PublicKeyCredentialEntry> {
        val parsed = PasskeyProtocol.parseRequestOptions(option.requestJson) ?: return emptyList()
        val repository = KasaApplication.container(context).vaultRepository

        return repository.passkeysFor(parsed.rpId)
            .filter { (_, passkey) ->
                parsed.allowedCredentialIds.isEmpty() ||
                    parsed.allowedCredentialIds.contains(passkey.credentialId)
            }
            .map { (item, passkey) ->
                PublicKeyCredentialEntry.Builder(
                    context = context,
                    username = passkey.label,
                    pendingIntent = pendingIntent(
                        context = context,
                        mode = PasskeyActivity.MODE_GET,
                        credentialId = passkey.credentialId
                    ),
                    beginGetPublicKeyCredentialOption = option
                )
                    .setDisplayName(item.name)
                    .apply {
                        if (passkey.lastUsedAt > 0) {
                            setLastUsedTime(Instant.ofEpochMilli(passkey.lastUsedAt))
                        }
                    }
                    .build()
            }
    }

    /**
     * Her giriş için ayrı bir [PendingIntent].
     *
     * İstek kodu her çağrıda farklı: aynı kod yeniden kullanılırsa Android
     * mevcut PendingIntent'i geri veriyor ve iki farklı hesap satırı aynı
     * ekstraları paylaşıyor — kullanıcı A'ya dokunup B ile giriş yapmış oluyor.
     * `FLAG_MUTABLE` ise şart, çünkü isteği intent'e sistem ekliyor.
     */
    fun pendingIntent(context: Context, mode: String, credentialId: String? = null): PendingIntent {
        val intent = Intent(context, PasskeyActivity::class.java)
            .setPackage(context.packageName)
            .putExtra(PasskeyActivity.EXTRA_MODE, mode)
            .apply { credentialId?.let { putExtra(PasskeyActivity.EXTRA_CREDENTIAL_ID, it) } }

        return PendingIntent.getActivity(
            context,
            nextRequestCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @Volatile
    private var requestCounter: Int = 1000

    @Synchronized
    private fun nextRequestCode(): Int {
        requestCounter = (requestCounter + 1) and 0x7FFF
        return requestCounter
    }
}
