package app.kasa.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Kasa şemasının sürüm zinciri.
 *
 * Bir parola yöneticisinde en pahalı hata, kullanıcıyı kendi kasasından
 * kilitlemektir. Veri modeline alan eklemek kaçınılmaz; eklendiğinde iki yönde
 * de güvenli olmak gerekiyor:
 *
 *  - **Eski dosya, yeni uygulama** → [migrate] adım adım yükseltir.
 *    Yalnızca alan eklendiği durumlarda serileştiricinin varsayılan değerleri
 *    yeterlidir; sıra ya da anlam değiştiğinde burada gerçek dönüşüm yazılır.
 *
 *  - **Yeni dosya, eski uygulama** → [VaultTooNewException] ile açıkça
 *    reddedilir. Bu, sessizce açmaktan çok daha önemli: `ignoreUnknownKeys`
 *    açık olduğu için eski uygulama yeni alanları tanımaz, okur gibi yapar ve
 *    ilk kaydetmede onları **kalıcı olarak siler**. Kullanıcı uygulamayı geri
 *    aldığında klasörlerini ve eklerini kaybetmiş olur. Bu yüzden okuma
 *    aşamasında durduruluyor.
 */
object VaultMigrations {

    /** Uygulamanın yazdığı şema sürümü. */
    const val CURRENT = 4

    /** Şema alanı olmayan en eski kasalar sürüm 1 sayılır. */
    const val OLDEST_SUPPORTED = 1

    fun schemaOf(root: JsonObject): Int =
        root["schema"]?.jsonPrimitive?.intOrNull ?: OLDEST_SUPPORTED

    /**
     * [root] JSON'unu [CURRENT] sürümüne yükseltir.
     *
     * @throws VaultTooNewException dosya bu uygulamadan yeniyse
     * @throws VaultTooOldException dosya artık desteklenmeyen bir sürümdeyse
     */
    fun migrate(root: JsonObject): JsonObject {
        var version = schemaOf(root)

        if (version > CURRENT) throw VaultTooNewException(version, CURRENT)
        if (version < OLDEST_SUPPORTED) throw VaultTooOldException(version)

        var current = root
        while (version < CURRENT) {
            val step = STEPS[version]
                ?: throw VaultTooOldException(version)
            current = step(current)
            version++
            current = current.withSchema(version)
        }
        return current
    }

    /**
     * `sürüm → (o sürümden bir sonrakine dönüştüren adım)`.
     *
     * Her adım saf bir fonksiyon: girdi JSON'unu değiştirmez, yeni bir tane
     * döndürür. Böylece zincir test edilebilir ve yarım kalan bir göç diskteki
     * dosyaya dokunmamış olur.
     */
    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        1 to ::migrate1to2,
        2 to ::migrate2to3,
        3 to ::migrate3to4
    )

    /**
     * 1 → 2: klasörler, çöp kutusu, ekler ve şema tabanlı tür alanları.
     *
     * Bu adım alan taşımıyor, çünkü eklenen alanların hepsi yeni ve hepsinin
     * serileştiricide varsayılanı var: `folders: []`, `folderId: null`,
     * `deletedAt: 0`, `extras: {}`, `attachments: []`. Eski dosya olduğu gibi
     * okunduğunda doğru sonucu veriyor.
     *
     * Yine de adım açıkça yazılı ve zincire bağlı: bir sonraki göç gerçek bir
     * dönüşüm gerektirdiğinde eklenecek yer belli ve sürüm numarası zaten
     * ilerlemiş oluyor. Boş bırakmak yerine burada durmasının nedeni bu.
     */
    private fun migrate1to2(root: JsonObject): JsonObject = root

    /**
     * 2 → 3: passkey saklama.
     *
     * `VaultItem.passkeys` eklendi; varsayılanı boş liste olduğu için eski
     * dosya olduğu gibi okunuyor. Bu adımın asıl işi, sürüm numarasını
     * ilerleterek **eski uygulamanın bu dosyayı açmasını engellemek**: passkey
     * taşıyan bir kasa, passkey'i tanımayan bir sürümde açılıp kaydedilseydi
     * özel anahtarlar sessizce silinir ve kullanıcı o hesaplara bir daha
     * giremezdi. Parolanın aksine passkey'in yedeği yok — geri alınamaz.
     */
    private fun migrate2to3(root: JsonObject): JsonObject = root

    /**
     * 3 → 4: kayıt bazlı ek kilit.
     *
     * `VaultItem.requireAuth` eklendi, varsayılanı `false`. Alan taşınmıyor
     * ama sürüm ilerliyor ve bunun bir bedeli var: işaretli kaydı olan bir
     * kasa, alanı tanımayan eski bir sürümde açılıp kaydedilseydi işaret
     * sessizce düşerdi. Kullanıcı en değerli kaydını korumaya aldığını
     * sanırken koruma kalkmış olurdu — sessiz kalmanın kabul edilemez olduğu
     * durum tam olarak bu.
     */
    private fun migrate3to4(root: JsonObject): JsonObject = root

    private fun JsonObject.withSchema(version: Int): JsonObject = buildJsonObject {
        this@withSchema.forEach { (key, value) -> if (key != "schema") put(key, value) }
        put("schema", version)
    }
}

/** Kasa dosyası bu uygulamanın anladığından daha yeni bir şemada. */
class VaultTooNewException(val fileSchema: Int, val appSchema: Int) :
    Exception("Kasa şeması $fileSchema, bu sürüm en fazla $appSchema okuyabiliyor")

/** Kasa dosyası artık desteklenmeyen bir şemada. */
class VaultTooOldException(val fileSchema: Int) :
    Exception("Kasa şeması $fileSchema artık desteklenmiyor")
