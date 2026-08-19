package app.kasa.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * Kullanıcı tercihleri.
 *
 * Burada gizli hiçbir şey tutulmaz — parola, anahtar ya da kayıt adı yok.
 * Yalnızca davranış anahtarları olduğu için düz DataStore yeterlidir; gizli
 * veri [VaultStore] tarafında şifreli blob'da durur.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kasa_settings")

class SettingsStore(private val context: Context) {

    data class Settings(
        val theme: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val pureBlack: Boolean = false,
        val haptics: Boolean = true,
        val biometricUnlock: Boolean = false,
        val blockScreenshots: Boolean = true,
        val clipboardClearSeconds: Int = 30,
        val autoLockSeconds: Int = 60,
        val wipeAfterAttempts: Int = 0,
        val onlineBreachCheck: Boolean = true,
        val onboardingDone: Boolean = false,
        val lastScanAt: Long = 0L,
        val integrityWarningShown: Boolean = false,
        val generatorLength: Int = 20,
        val generatorUpper: Boolean = true,
        val generatorDigits: Boolean = true,
        val generatorSymbols: Boolean = true,
        val generatorAvoidLookalikes: Boolean = false,
        val generatorPassphrase: Boolean = false,
        val generatorWordCount: Int = 5,
        val generatorSeparator: String = "-",
        val generatorCapitalize: Boolean = true
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            theme = runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            pureBlack = prefs[KEY_PURE_BLACK] ?: false,
            haptics = prefs[KEY_HAPTICS] ?: true,
            biometricUnlock = prefs[KEY_BIOMETRIC] ?: false,
            blockScreenshots = prefs[KEY_BLOCK_SHOTS] ?: true,
            clipboardClearSeconds = prefs[KEY_CLIP_SECONDS] ?: 30,
            autoLockSeconds = prefs[KEY_AUTOLOCK] ?: 60,
            wipeAfterAttempts = prefs[KEY_WIPE_ATTEMPTS] ?: 0,
            onlineBreachCheck = prefs[KEY_ONLINE_CHECK] ?: true,
            onboardingDone = prefs[KEY_ONBOARDING] ?: false,
            lastScanAt = prefs[KEY_LAST_SCAN] ?: 0L,
            integrityWarningShown = prefs[KEY_INTEGRITY_SHOWN] ?: false,
            generatorLength = prefs[KEY_GEN_LENGTH] ?: 20,
            generatorUpper = prefs[KEY_GEN_UPPER] ?: true,
            generatorDigits = prefs[KEY_GEN_DIGITS] ?: true,
            generatorSymbols = prefs[KEY_GEN_SYMBOLS] ?: true,
            generatorAvoidLookalikes = prefs[KEY_GEN_CLEAR] ?: false,
            generatorPassphrase = prefs[KEY_GEN_PASSPHRASE] ?: false,
            generatorWordCount = prefs[KEY_GEN_WORDS] ?: 5,
            generatorSeparator = prefs[KEY_GEN_SEPARATOR] ?: "-",
            generatorCapitalize = prefs[KEY_GEN_CAPITALIZE] ?: true
        )
    }

    suspend fun setTheme(mode: ThemeMode) = put(KEY_THEME, mode.name)
    suspend fun setDynamicColor(value: Boolean) = put(KEY_DYNAMIC, value)
    suspend fun setPureBlack(value: Boolean) = put(KEY_PURE_BLACK, value)
    suspend fun setHaptics(value: Boolean) = put(KEY_HAPTICS, value)
    suspend fun setBiometricUnlock(value: Boolean) = put(KEY_BIOMETRIC, value)
    suspend fun setBlockScreenshots(value: Boolean) = put(KEY_BLOCK_SHOTS, value)
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
    suspend fun setGeneratorPassphrase(value: Boolean) = put(KEY_GEN_PASSPHRASE, value)
    suspend fun setGeneratorWordCount(value: Int) = put(KEY_GEN_WORDS, value)
    suspend fun setGeneratorSeparator(value: String) = put(KEY_GEN_SEPARATOR, value)
    suspend fun setGeneratorCapitalize(value: Boolean) = put(KEY_GEN_CAPITALIZE, value)

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
        val KEY_BLOCK_SHOTS = booleanPreferencesKey("block_screenshots")
        val KEY_CLIP_SECONDS = intPreferencesKey("clipboard_seconds")
        val KEY_AUTOLOCK = intPreferencesKey("autolock_seconds")
        val KEY_WIPE_ATTEMPTS = intPreferencesKey("wipe_attempts")
        val KEY_ONLINE_CHECK = booleanPreferencesKey("online_breach_check")
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
    }
}
