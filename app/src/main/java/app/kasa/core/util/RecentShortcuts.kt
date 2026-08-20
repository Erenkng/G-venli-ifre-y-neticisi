package app.kasa.core.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.kasa.MainActivity
import app.kasa.R
import app.kasa.data.model.VaultItem

/**
 * Son kullanılan kayıtlar için başlatıcı kısayolları.
 *
 * ### Ne kazandırıyor
 *
 * Günlük kullanımda aynı üç dört kayıt açılıyor. Uygulamayı açmak, kilidi
 * açmak, listede aramak ve dokunmak — dört adım. Simgeye basılı tutup kaydın
 * adına dokunmak bunu ikiye indiriyor; kilit yine soruluyor, atlanan tek şey
 * arama.
 *
 * ### Kısayolun adı bir sızıntı
 *
 * Kısayollar sistem başlatıcısında, kasanın **dışında** duruyor ve kilit
 * kapalıyken de görünüyorlar. Yani "Garanti" adlı bir kısayol, telefonu eline
 * alan herkese o bankada hesabın olduğunu söylüyor.
 *
 * Bu yüzden iki koruma var:
 *
 *  - Kayıt bazlı ek kilidi olan kayıtlar hiç kısayol almıyor. Kullanıcı o
 *    kayıtları özellikle ayırmış; adlarının başlatıcıda durması o kararla
 *    çelişir.
 *  - Kasa kilitlendiğinde bütün kısayollar siliniyor ([clear]). Kilitli bir
 *    kasanın dışarıya isim sızdırmaması gerekiyor.
 *
 * Kalan risk açık: kasa açıkken telefonu alan biri kısayolları görebilir. Bunu
 * tamamen kapatmanın tek yolu özelliği hiç sunmamaktı; ara çözüm, kullanıcının
 * en değerli kayıtlarını (ek kilitli olanlar) hiç göstermemek.
 */
object RecentShortcuts {

    /** Kayıt kimliğini taşıyan eylem; [MainActivity] bunu açılışta okuyor. */
    const val ACTION_OPEN_ITEM = "app.kasa.action.OPEN_ITEM"
    const val EXTRA_ITEM_ID = "app.kasa.extra.ITEM_ID"

    private const val PREFIX = "recent_"
    private const val MAX = 3

    /**
     * Kısayolları son kullanılan kayıtlara göre tazeler.
     *
     * Statik kısayollar (üret, ara, güvenlik) manifest'ten geliyor ve
     * dokunulmuyor; burada yalnızca dinamik olanlar değişiyor.
     */
    fun refresh(context: Context, items: List<VaultItem>) {
        runCatching {
            val candidates = items
                .filter { !it.inTrash && !it.requireAuth && it.lastUsedAt > 0 }
                .sortedByDescending { it.lastUsedAt }
                .take(MAX)

            if (candidates.isEmpty()) {
                clear(context)
                return@runCatching
            }

            val shortcuts = candidates.mapIndexed { index, item ->
                ShortcutInfoCompat.Builder(context, PREFIX + item.id)
                    .setShortLabel(item.name.take(20))
                    .setLongLabel(item.name.take(40))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_search))
                    .setRank(index)
                    .setIntent(
                        Intent(context, MainActivity::class.java).apply {
                            action = ACTION_OPEN_ITEM
                            putExtra(EXTRA_ITEM_ID, item.id)
                        }
                    )
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    /** Kasa kilitlendiğinde çağrılıyor: dışarıda isim kalmasın. */
    fun clear(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }
}
