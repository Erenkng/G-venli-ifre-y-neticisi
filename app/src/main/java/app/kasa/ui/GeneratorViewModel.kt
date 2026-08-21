package app.kasa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kasa.AppContainer
import app.kasa.R
import app.kasa.core.security.SecureClipboard
import app.kasa.core.util.Haptics
import app.kasa.core.util.PasswordGenerator
import app.kasa.core.util.PasswordStrength
import app.kasa.data.GeneratorMode
import app.kasa.data.SettingsStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Parola üreticisi.
 *
 * Ayarlar diske yazılır (kullanıcı her açılışta uzunluğu yeniden ayarlamasın),
 * üretilen parolaların kendisi ise yalnızca şifreli kasa blob'una girer.
 */
class GeneratorViewModel(private val container: AppContainer) : ViewModel() {

    private val settingsStore = container.settingsStore

    data class State(
        val value: String = "",
        val entropyBits: Double = 0.0,
        /**
         * Toplu üretimdeki öteki seçenekler.
         *
         * Boş liste = toplu üretim kapalı. [value] her zaman "seçili olan";
         * toplu kipte listenin ilk öğesiyle aynı, kullanıcı bir seçenek
         * seçtiğinde onunla değişiyor. İki ayrı "şu an geçerli değer"
         * kaynağı tutmak, kopyalanan şeyle ekranda vurgulanan şeyin
         * ayrışmasına açık kapı bırakırdı.
         */
        val alternatives: List<String> = emptyList(),
        val settings: SettingsStore.Settings = SettingsStore.Settings()
    ) {
        /** 0..1 arası, kadranın biçimini belirleyen güç. */
        val strength: Float
            get() = ((entropyBits - 28.0) / 105.0).coerceIn(0.0, 1.0).toFloat()

        val label: Int
            get() = when {
                strength > 0.75f -> R.string.strength_very_strong
                strength > 0.5f -> R.string.strength_strong
                strength > 0.28f -> R.string.strength_fair
                strength > 0.12f -> R.string.strength_weak
                else -> R.string.strength_very_weak
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val messages = Channel<UiMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    // Geçmiş ekranda gösterilecek; silinebilir kaptan çıkışı burada, tek yerde.
    val history: StateFlow<List<String>> = container.vaultRepository.data
        .map { data -> data.generatorHistory.map { it.reveal() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            _state.value = _state.value.copy(settings = settings)
            regenerate()
        }
    }

    fun regenerate() {
        val settings = _state.value.settings
        val generated = generateOnce(settings)

        // Toplu üretim: aynı ayarlarla birkaç seçenek birden.
        //
        // Tek öneriyi beğenmeyen kullanıcı düğmeye basıp yeniden üretiyordu ve
        // beğendiği bir önceki geri gelmiyordu. Seçenekler aynı anda durunca
        // karşılaştırılabiliyor. Entropileri eşit olduğu için ilkinin değeri
        // hepsi için geçerli.
        val extras = if (!settings.generatorBatch) emptyList()
        else List(PasswordGenerator.BATCH_SIZE - 1) { generateOnce(settings).value }

        _state.value = _state.value.copy(
            value = generated.value,
            entropyBits = generated.entropyBits,
            alternatives = if (settings.generatorBatch) listOf(generated.value) + extras else emptyList()
        )
    }

    /**
     * Kipe karşılık gelen üretici.
     *
     * Kip artık tek bir numaralandırma; öncesinde iki boole ve örtük bir
     * "hiçbiri" durumu vardı ve hangisinin kazandığı okuma sırasına bağlıydı.
     */
    private fun generateOnce(settings: SettingsStore.Settings): PasswordGenerator.Generated =
        when (settings.generatorMode) {
            GeneratorMode.PRONOUNCEABLE -> PasswordGenerator.generatePronounceable(
                syllables = settings.generatorSyllables,
                appendDigits = settings.generatorDigits
            )

            GeneratorMode.PASSPHRASE -> PasswordGenerator.generatePassphrase(
                words = PasswordGenerator.words(container.appContext),
                options = PasswordGenerator.PassphraseOptions(
                    words = settings.generatorWordCount,
                    separator = settings.generatorSeparator,
                    capitalize = settings.generatorCapitalize,
                    appendNumber = settings.generatorDigits
                )
            )

            GeneratorMode.PIN -> PasswordGenerator.generatePin(settings.generatorPinLength)

            GeneratorMode.HEX -> PasswordGenerator.generateHexKey(settings.generatorHexBits)

            GeneratorMode.UUID -> PasswordGenerator.generateUuid()

            GeneratorMode.RECOVERY -> PasswordGenerator.generateRecoveryCodes()

            GeneratorMode.USERNAME -> PasswordGenerator.generateUsername(
                PasswordGenerator.words(container.appContext)
            )

            GeneratorMode.PASSWORD -> {
                val options = PasswordGenerator.Options(
                    length = settings.generatorLength,
                    upper = settings.generatorUpper,
                    digits = settings.generatorDigits,
                    symbols = settings.generatorSymbols,
                    avoidLookalikes = settings.generatorAvoidLookalikes
                )
                // Entropi hedefi açıksa uzunluk kullanıcıdan değil hedeften
                // geliyor: "20 karakter" seçilen kümelere göre 94 bit de
                // olabiliyor 68 bit de, ve kullanıcı bunu görmüyordu.
                PasswordGenerator.generate(
                    options.copy(
                        length = PasswordGenerator.lengthForEntropy(
                            settings.generatorEntropyTarget,
                            options
                        )
                    )
                )
            }
        }

    /** Toplu üretimde bir seçeneği geçerli değer yapar. */
    fun selectAlternative(value: String) {
        if (value !in _state.value.alternatives) return
        _state.value = _state.value.copy(value = value)
    }

    private fun update(transform: SettingsStore.Settings.() -> SettingsStore.Settings, persist: suspend () -> Unit) {
        _state.value = _state.value.copy(settings = _state.value.settings.transform())
        regenerate()
        viewModelScope.launch { persist() }
    }

    fun setLength(value: Int) = update({ copy(generatorLength = value) }) {
        settingsStore.setGeneratorLength(value)
    }

    fun setWordCount(value: Int) = update({ copy(generatorWordCount = value) }) {
        settingsStore.setGeneratorWordCount(value)
    }

    fun setUpper(value: Boolean) = update({ copy(generatorUpper = value) }) {
        settingsStore.setGeneratorUpper(value)
    }

    fun setDigits(value: Boolean) = update({ copy(generatorDigits = value) }) {
        settingsStore.setGeneratorDigits(value)
    }

    fun setSymbols(value: Boolean) = update({ copy(generatorSymbols = value) }) {
        settingsStore.setGeneratorSymbols(value)
    }

    fun setAvoidLookalikes(value: Boolean) = update({ copy(generatorAvoidLookalikes = value) }) {
        settingsStore.setGeneratorAvoidLookalikes(value)
    }

    /**
     * Üretilecek şeyin türü.
     *
     * Kipler birbirini dışlıyor ve bu artık tür sisteminde: iki boole yerine
     * tek bir numaralandırma. Geçersiz bileşim (hem sözcük dizisi hem PIN)
     * artık temsil bile edilemiyor.
     */
    fun setMode(value: GeneratorMode) = update({ copy(generatorMode = value) }) {
        settingsStore.setGeneratorMode(value)
    }

    fun setPinLength(value: Int) = update({ copy(generatorPinLength = value) }) {
        settingsStore.setGeneratorPinLength(value)
    }

    fun setHexBits(value: Int) = update({ copy(generatorHexBits = value) }) {
        settingsStore.setGeneratorHexBits(value)
    }

    fun setBatch(value: Boolean) = update({ copy(generatorBatch = value) }) {
        settingsStore.setGeneratorBatch(value)
    }

    fun setEntropyTarget(value: Int) = update({ copy(generatorEntropyTarget = value) }) {
        settingsStore.setGeneratorEntropyTarget(value)
    }

    fun setSyllables(value: Int) = update({ copy(generatorSyllables = value) }) {
        settingsStore.setGeneratorSyllables(value)
    }

    fun setSeparator(value: String) = update({ copy(generatorSeparator = value) }) {
        settingsStore.setGeneratorSeparator(value)
    }

    fun setCapitalize(value: Boolean) = update({ copy(generatorCapitalize = value) }) {
        settingsStore.setGeneratorCapitalize(value)
    }

    fun copy(clearSeconds: Int) {
        val value = _state.value.value
        if (value.isEmpty()) return
        SecureClipboard.copySensitive(container.appContext, value, clearSeconds)
        container.haptics.play(Haptics.Kind.SUCCESS)
        viewModelScope.launch {
            container.vaultRepository.addGeneratorHistory(value)
            if (clearSeconds > 0) messages.send(UiMessage(R.string.copied_clip, listOf(clearSeconds)))
            else messages.send(UiMessage(R.string.copied))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { container.vaultRepository.clearGeneratorHistory() }
    }

    fun haptic(kind: Haptics.Kind) = container.haptics.play(kind)

    /** Kırılma süresini insan diline çeviren yardımcı; ekran metni bunu kullanır. */
    fun crackTime(): CrackTime = CrackTime.of(PasswordStrength.crackSeconds(_state.value.entropyBits))
}

/** Kaba kuvvetle kırma süresinin kabaca sınıflandırılması. */
sealed class CrackTime(val textRes: Int, val arg: String? = null) {
    data object Instant : CrackTime(R.string.crack_instant)
    data object Seconds : CrackTime(R.string.crack_seconds)
    data object Minutes : CrackTime(R.string.crack_minutes)
    data object Hours : CrackTime(R.string.crack_hours)
    data object Days : CrackTime(R.string.crack_days)
    data object Months : CrackTime(R.string.crack_months)
    class Years(val value: String) : CrackTime(R.string.crack_years, value)
    data object Centuries : CrackTime(R.string.crack_centuries)

    companion object {
        fun of(seconds: Double): CrackTime = when {
            seconds < 1 -> Instant
            seconds < 60 -> Seconds
            seconds < 3600 -> Minutes
            seconds < 86_400 -> Hours
            seconds < 2_592_000 -> Days
            seconds < 31_536_000 -> Months
            seconds < 3_153_600_000.0 -> Years(formatYears(seconds / 31_536_000.0))
            else -> Centuries
        }

        private fun formatYears(years: Double): String =
            if (years < 10) years.toInt().toString() else ((years / 10).toInt() * 10).toString()
    }
}
