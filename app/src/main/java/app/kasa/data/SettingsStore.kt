package app.kasa.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Zeminin gradyan ailesi.
 *
 * Renk şeması (Material You, açık/karanlık) ayrı bir ayar; bu yalnızca arka
 * plandaki üç radyal durağın hangi renk ailesinden geleceğini seçiyor.
 * İkisini tek ayarda toplamak, "karanlık tema" isteyen kullanıcıya aynı anda
 * bir renk kimliği dayatmak olurdu.
 */
enum class GradientTheme { JADE, SUNSET, DEEP }

/**
 * Üreticinin ne ürettiği.
 *
 * Önceden üç ayrı boole vardı (`passphrase`, `pronounceable` ve örtük olarak
 * "hiçbiri = parola") ve üçünün aynı anda açılmaması elle korunuyordu. Birbirini
 * dışlayan durumları boole ile taşımak, geçersiz bileşimleri temsil edilebilir
 * bırakıyor: iki bayrak da açıkken hangisinin kazanacağı okuyan koda kalıyordu.
 */
enum class GeneratorMode {
    /** Rastgele karakter dizisi. */
    PASSWORD,
    /** Türkçe sözcüklerden oluşan dizi. */
    PASSPHRASE,
    /** Sesletilebilir hece dizisi: telefonda okunup karşıya söylenmek için. */
    PRONOUNCEABLE,
    /** Yalnızca rakam: kart PIN'i, kapı kodu, telefon kilidi. */
    PIN,
    /** Rastgele kullanıcı adı: kayıt olurken gerçek adı vermemek için. */
    USERNAME,
    /** Onaltılık anahtar: API anahtarı, şifreleme anahtarı, tuz. */
    HEX,
    /** UUID v4: yapılandırma, veritabanı kaydı, istemci kimliği. */
    UUID,
    /** Yedek giriş kodu seti: iki adımlı doğrulama kuranlar için. */
    RECOVERY
}

/**
 * Kullanıcı tercihleri.
 *
 * Burada gizli hiçbir şey tutulmaz — parola, anahtar ya da kayıt adı yok.
 * Yalnızca davranış anahtarları olduğu için düz DataStore yeterlidir; gizli
 * veri [VaultStore] tarafında şifreli blob'da durur.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kasa_settings")

class SettingsStore(private val context: Context) {

    /**
     * Kasa listesinin sıralaması.
     *
     * Varsayılan son kullanım: bir parola yöneticisinde en olası sonraki kayıt,
     * en son açılan kayıttır. Ada göre sıralama alfabeyi bilenler için;
     * eklenme sırası "az önce ne kaydettim" sorusu için; güç sırası ise
     * temizlik yaparken en zayıftan başlamak için.
     */
    enum class SortOrder { LAST_USED, NAME, NEWEST, WEAKEST }

    /**
     * Satır yoğunluğu.
     *
     * Kasası yüz kaydı geçen bir kullanıcıda rahat yerleşim ekrana altı satır
     * sığdırıyor ve liste sonsuz görünüyor. Sıkışık yerleşim ikincil satırı
     * kaldırıp yüksekliği düşürüyor: aynı ekranda on bir satır.
     */
    enum class ListDensity { COMFORTABLE, COMPACT }

    /**
     * Ayarların hepsi ilkel değer ve numaralandırma; yine de işaret açıkça
     * konuyor. Bileşen imzalarında en sık geçen tür bu ve kararlılığının
     * derleyicinin çıkarımına bırakılması, ileride bir liste alanı
     * eklendiğinde sessizce her ekranı yeniden bestelemeye başlardı.
     */
    @Immutable
    data class Settings(
        val theme: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val pureBlack: Boolean = false,
        val haptics: Boolean = true,
        val biometricUnlock: Boolean = false,
        /** Biyometri yerine/yanında telefonun kendi ekran kilidi de kabul edilsin. */
        val deviceCredentialUnlock: Boolean = false,
        /**
         * Güvenilen ağdayken otomatik kilit gecikmesi.
         *
         * Ağ kimliği yalnızca özet olarak saklanıyor ([trustedNetworkHash]) ve
         * hiçbir yere gönderilmiyor; ne SSID ne konum cihazdan çıkıyor.
         */
        val contextLockEnabled: Boolean = false,
        val trustedNetworkHash: String = "",
        val contextLockSeconds: Int = 300,
        val blockScreenshots: Boolean = true,
        /**
         * Ekran kapanınca beklemeden kilitle.
         *
         * Otomatik kilit süresi cebe konan telefon için doğru bir ölçü ama
         * ekranı kapatmak bilinçli bir hareket: kullanıcı işini bitirdiğini
         * söylüyor. Süreyi orada da beklemek, telefonu masada bırakılan
         * dakikaları savunmasız geçirmek demek.
         */
        val lockOnScreenOff: Boolean = true,
        val clipboardClearSeconds: Int = 30,
        val autoLockSeconds: Int = 60,
        val wipeAfterAttempts: Int = 0,
        val onlineBreachCheck: Boolean = true,
        /**
         * Otomatik doldurmada uygulama–alan adı bağını doğrula.
         *
         * Açıkken, yerli bir uygulama kasadaki bir alan adının kimlik
         * bilgisini isteyince alan adının `assetlinks.json` beyanına
         * bakılıyor. Bu bir ağ isteği ve bedeli açık: alan adının sunucusuna
         * "bu IP'de sizin için kayıtlı bir kimlik bilgisi var" diyor. Giden
         * bilgi, kimlik bilgisinin zaten ait olduğu tarafa gidiyor ve sonuç
         * bir hafta önbelleklendiği için her doldurmada tekrarlanmıyor.
         *
         * Kapatıldığında uygulama eşleşmesi yalnızca kullanıcının elle kurduğu
         * bağlarla çalışıyor; hiçbir zaman ad benzerliğiyle değil.
         */
        val autofillVerifyDomains: Boolean = true,
        val sortOrder: SortOrder = SortOrder.LAST_USED,
        val listDensity: ListDensity = ListDensity.COMFORTABLE,
        val onboardingDone: Boolean = false,
        val lastScanAt: Long = 0L,
        val integrityWarningShown: Boolean = false,
        val generatorLength: Int = 20,
        val generatorUpper: Boolean = true,
        val generatorDigits: Boolean = true,
        val generatorSymbols: Boolean = true,
        val generatorAvoidLookalikes: Boolean = false,
        /**
         * Deneysel yüzey efektleri.
         *
         * Eğim parlaması, basınç çiçeklenmesi, parıltı şeridi ve kenar
         * derinliği. Varsayılan açık: bunlar uygulamanın görünmek istediği
         * hâli. Kapatıldığında kod yolları hiç çalışmıyor — sensör
         * dinleyicisi kaydedilmiyor, sonsuz animasyon başlamıyor.
         */
        val experimentalEffects: Boolean = true,
        val gradientTheme: GradientTheme = GradientTheme.JADE,
        /**
         * Gradyan günün saatine göre kaysın mı.
         *
         * Seçilen aile aynı kalıyor; içindeki tonlar sabahtan geceye doğru
         * yer değiştiriyor. Kapatıldığında ailenin gündüz tonları sabitleniyor.
         */
        val gradientFollowsTime: Boolean = true,
        val generatorMode: GeneratorMode = GeneratorMode.PASSWORD,
        val generatorPinLength: Int = 6,
        val generatorHexBits: Int = 256,
        /**
         * Aynı anda birden çok seçenek üret.
         *
         * Tek bir öneriyi beğenmeyen kullanıcı düğmeye basıp yeniden üretiyordu
         * ve beğendiği bir önceki artık yoktu. Toplu üretimde seçenekler aynı
         * anda duruyor, karşılaştırılabiliyor.
         */
        val generatorBatch: Boolean = false,
        /**
         * İstenen en az entropi (bit). Sıfır = kapalı.
         *
         * Açıkken uzunluk kullanıcıdan değil hedeften geliyor: seçilen karakter
         * kümesiyle o güce ulaşmak için kaç karakter gerekiyorsa o kadar.
         * "20 karakter" bir güç ölçüsü değil — sembol kapatıldığında aynı
         * uzunluk belirgin şekilde zayıflıyor ve kullanıcı bunu görmüyordu.
         */
        val generatorEntropyTarget: Int = 0,
        /**
         * Telaffuz edilebilir mod.
         *
         * Parola dizesi ve sözcük dizisiyle aynı düzeyde üçüncü bir seçenek;
         * ayrı bir bayrak olmasının sebebi ikisiyle birlikte kullanılamaması.
         */
        val generatorSyllables: Int = 6,
        val generatorWordCount: Int = 5,
        val generatorSeparator: String = "-",
        val generatorCapitalize: Boolean = true,
        /**
         * Kapatılmış sızıntı uyarısının parmak izi.
         *
         * Boş = kapatılmış bir uyarı yok. Değer, uyarının kapatıldığı andaki
         * sızmış kayıt kümesinin özeti; küme değişince eşleşme bozuluyor ve
         * uyarı geri geliyor. Bir boole olsaydı sonradan sızan parola da aynı
         * "kapatıldı" bayrağının altında kalırdı.
         */
        val dismissedLeakAlert: String = ""
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            pureBlack = prefs[KEY_PURE_BLACK] ?: false,
            haptics = prefs[KEY_HAPTICS] ?: true,
            biometricUnlock = prefs[KEY_BIOMETRIC] ?: false,
            deviceCredentialUnlock = prefs[KEY_DEVICE_CRED] ?: false,
            contextLockEnabled = prefs[KEY_CONTEXT_LOCK] ?: false,
            trustedNetworkHash = prefs[KEY_TRUSTED_NETWORK] ?: "",
            contextLockSeconds = prefs[KEY_CONTEXT_SECONDS] ?: 300,
            blockScreenshots = prefs[KEY_BLOCK_SHOTS] ?: true,
            clipboardClearSeconds = prefs[KEY_CLIP_SECONDS] ?: 30,
            autoLockSeconds = prefs[KEY_AUTOLOCK] ?: 60,
            wipeAfterAttempts = prefs[KEY_WIPE_ATTEMPTS] ?: 0,
            onlineBreachCheck = prefs[KEY_ONLINE_CHECK] ?: true,
            autofillVerifyDomains = prefs[KEY_AF_VERIFY] ?: true,
            lockOnScreenOff = prefs[KEY_LOCK_SCREEN_OFF] ?: true,
            generatorSyllables = prefs[KEY_GEN_SYLLABLES] ?: 6,
            sortOrder = runCatching { SortOrder.valueOf(prefs[KEY_SORT] ?: SortOrder.LAST_USED.name) }
                .getOrDefault(SortOrder.LAST_USED),
            listDensity = runCatching { ListDensity.valueOf(prefs[KEY_DENSITY] ?: ListDensity.COMFORTABLE.name) }
                .getOrDefault(ListDensity.COMFORTABLE),
            onboardingDone = prefs[KEY_ONBOARDING] ?: false,
            lastScanAt = prefs[KEY_LAST_SCAN] ?: 0L,
            integrityWarningShown = prefs[KEY_INTEGRITY_SHOWN] ?: false,
            generatorLength = prefs[KEY_GEN_LENGTH] ?: 20,
            generatorUpper = prefs[KEY_GEN_UPPER] ?: true,
            generatorDigits = prefs[KEY_GEN_DIGITS] ?: true,
            generatorSymbols = prefs[KEY_GEN_SYMBOLS] ?: true,
            generatorAvoidLookalikes = prefs[KEY_GEN_CLEAR] ?: false,
            experimentalEffects = prefs[KEY_EXPERIMENTAL] ?: true,
            gradientTheme = runCatching { GradientTheme.valueOf(prefs[KEY_GRADIENT] ?: GradientTheme.JADE.name) }
                .getOrDefault(GradientTheme.JADE),
            gradientFollowsTime = prefs[KEY_GRADIENT_TIME] ?: true,
            // Kip anahtarı yoksa eski boole çiftinden türetiliyor: 1.1'den
            // yükselen kullanıcı seçtiği modu kaybetmiyor.
            generatorMode = runCatching {
                prefs[KEY_GEN_MODE]?.let { GeneratorMode.valueOf(it) }
                    ?: when {
                        prefs[KEY_GEN_PASSPHRASE] == true -> GeneratorMode.PASSPHRASE
                        prefs[KEY_GEN_PRONOUNCE] == true -> GeneratorMode.PRONOUNCEABLE
                        else -> GeneratorMode.PASSWORD
                    }
            }.getOrDefault(GeneratorMode.PASSWORD),
            generatorPinLength = prefs[KEY_GEN_PIN_LEN] ?: 6,
            generatorHexBits = prefs[KEY_GEN_HEX_BITS] ?: 256,
            generatorBatch = prefs[KEY_GEN_BATCH] ?: false,
            generatorEntropyTarget = prefs[KEY_GEN_ENTROPY] ?: 0,
            generatorWordCount = prefs[KEY_GEN_WORDS] ?: 5,
            generatorSeparator = prefs[KEY_GEN_SEPARATOR] ?: "-",
            generatorCapitalize = prefs[KEY_GEN_CAPITALIZE] ?: true,
            dismissedLeakAlert = prefs[KEY_LEAK_DISMISSED] ?: ""
        )
    }

    suspend fun setTheme(mode: ThemeMode) = put(KEY_THEME, mode.name)
    suspend fun setDynamicColor(value: Boolean) = put(KEY_DYNAMIC, value)
    suspend fun setPureBlack(value: Boolean) = put(KEY_PURE_BLACK, value)
    suspend fun setHaptics(value: Boolean) = put(KEY_HAPTICS, value)
    suspend fun setBiometricUnlock(value: Boolean) = put(KEY_BIOMETRIC, value)
    suspend fun setDeviceCredentialUnlock(value: Boolean) = put(KEY_DEVICE_CRED, value)
    suspend fun setContextLockEnabled(value: Boolean) = put(KEY_CONTEXT_LOCK, value)
    suspend fun setTrustedNetworkHash(value: String) = put(KEY_TRUSTED_NETWORK, value)
    suspend fun setContextLockSeconds(value: Int) = put(KEY_CONTEXT_SECONDS, value)
    suspend fun setBlockScreenshots(value: Boolean) = put(KEY_BLOCK_SHOTS, value)
    suspend fun setAutofillVerifyDomains(value: Boolean) = put(KEY_AF_VERIFY, value)
    suspend fun setLockOnScreenOff(value: Boolean) = put(KEY_LOCK_SCREEN_OFF, value)
    suspend fun setGeneratorSyllables(value: Int) = put(KEY_GEN_SYLLABLES, value)
    suspend fun setSortOrder(value: SortOrder) = put(KEY_SORT, value.name)
    suspend fun setListDensity(value: ListDensity) = put(KEY_DENSITY, value.name)
    suspend fun setClipboardClearSeconds(value: Int) = put(KEY_CLIP_SECONDS, value)
    suspend fun setAutoLockSeconds(value: Int) = put(KEY_AUTOLOCK, value)
    suspend fun setWipeAfterAttempts(value: Int) = put(KEY_WIPE_ATTEMPTS, value)
    suspend fun setOnlineBreachCheck(value: Boolean) = put(KEY_ONLINE_CHECK, value)
    suspend fun setOnboardingDone(value: Boolean) = put(KEY_ONBOARDING, value)
    suspend fun setLastScanAt(value: Long) = put(KEY_LAST_SCAN, value)
    suspend fun setIntegrityWarningShown(value: Boolean) = put(KEY_INTEGRITY_SHOWN, value)

    suspend fun setGeneratorLength(value: Int) = put(KEY_GEN_LENGTH, value)
    suspend fun setGeneratorUpper(value: Boolean) = put(KEY_GEN_UPPER, value)
    suspend fun setGeneratorDigits(value: Boolean) = put(KEY_GEN_DIGITS, value)
    suspend fun setGeneratorSymbols(value: Boolean) = put(KEY_GEN_SYMBOLS, value)
    suspend fun setGeneratorAvoidLookalikes(value: Boolean) = put(KEY_GEN_CLEAR, value)
    suspend fun setGeneratorWordCount(value: Int) = put(KEY_GEN_WORDS, value)
    suspend fun setGeneratorSeparator(value: String) = put(KEY_GEN_SEPARATOR, value)
    suspend fun setExperimentalEffects(value: Boolean) = put(KEY_EXPERIMENTAL, value)
    suspend fun setGradientTheme(value: GradientTheme) = put(KEY_GRADIENT, value.name)
    suspend fun setGradientFollowsTime(value: Boolean) = put(KEY_GRADIENT_TIME, value)
    suspend fun setGeneratorMode(value: GeneratorMode) = put(KEY_GEN_MODE, value.name)
    suspend fun setGeneratorPinLength(value: Int) = put(KEY_GEN_PIN_LEN, value)
    suspend fun setGeneratorHexBits(value: Int) = put(KEY_GEN_HEX_BITS, value)
    suspend fun setGeneratorBatch(value: Boolean) = put(KEY_GEN_BATCH, value)
    suspend fun setGeneratorEntropyTarget(value: Int) = put(KEY_GEN_ENTROPY, value)
    suspend fun setGeneratorCapitalize(value: Boolean) = put(KEY_GEN_CAPITALIZE, value)
    suspend fun setDismissedLeakAlert(value: String) = put(KEY_LEAK_DISMISSED, value)

    /** Kasa silindiğinde tercihler de sıfırlanır. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun put(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_BIOMETRIC = booleanPreferencesKey("biometric")
        val KEY_DEVICE_CRED = booleanPreferencesKey("device_credential")
        val KEY_CONTEXT_LOCK = booleanPreferencesKey("context_lock")
        val KEY_TRUSTED_NETWORK = stringPreferencesKey("trusted_network")
        val KEY_CONTEXT_SECONDS = intPreferencesKey("context_lock_seconds")
        val KEY_BLOCK_SHOTS = booleanPreferencesKey("block_screenshots")
        val KEY_CLIP_SECONDS = intPreferencesKey("clipboard_seconds")
        val KEY_AUTOLOCK = intPreferencesKey("autolock_seconds")
        val KEY_WIPE_ATTEMPTS = intPreferencesKey("wipe_attempts")
        val KEY_ONLINE_CHECK = booleanPreferencesKey("online_breach_check")
        val KEY_AF_VERIFY = booleanPreferencesKey("autofill_verify_domains")
        val KEY_LOCK_SCREEN_OFF = booleanPreferencesKey("lock_on_screen_off")
        val KEY_GEN_PRONOUNCE = booleanPreferencesKey("gen_pronounceable")
        val KEY_GEN_SYLLABLES = intPreferencesKey("gen_syllables")
        val KEY_SORT = stringPreferencesKey("sort_order")
        val KEY_DENSITY = stringPreferencesKey("list_density")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        val KEY_LAST_SCAN = longPreferencesKey("last_scan_at")
        val KEY_INTEGRITY_SHOWN = booleanPreferencesKey("integrity_warning_shown")
        val KEY_GEN_LENGTH = intPreferencesKey("gen_length")
        val KEY_GEN_UPPER = booleanPreferencesKey("gen_upper")
        val KEY_GEN_DIGITS = booleanPreferencesKey("gen_digits")
        val KEY_GEN_SYMBOLS = booleanPreferencesKey("gen_symbols")
        val KEY_GEN_CLEAR = booleanPreferencesKey("gen_clear")
        val KEY_GEN_PASSPHRASE = booleanPreferencesKey("gen_passphrase")
        val KEY_GEN_WORDS = intPreferencesKey("gen_words")
        val KEY_GEN_SEPARATOR = stringPreferencesKey("gen_separator")
        val KEY_GEN_CAPITALIZE = booleanPreferencesKey("gen_capitalize")
        val KEY_EXPERIMENTAL = booleanPreferencesKey("experimental_effects")
        val KEY_GRADIENT = stringPreferencesKey("gradient_theme")
        val KEY_GRADIENT_TIME = booleanPreferencesKey("gradient_follows_time")
        val KEY_GEN_MODE = stringPreferencesKey("gen_mode")
        val KEY_LEAK_DISMISSED = stringPreferencesKey("dismissed_leak_alert")
        val KEY_GEN_PIN_LEN = intPreferencesKey("gen_pin_length")
        val KEY_GEN_HEX_BITS = intPreferencesKey("gen_hex_bits")
        val KEY_GEN_BATCH = booleanPreferencesKey("gen_batch")
        val KEY_GEN_ENTROPY = intPreferencesKey("gen_entropy_target")
    }
}
