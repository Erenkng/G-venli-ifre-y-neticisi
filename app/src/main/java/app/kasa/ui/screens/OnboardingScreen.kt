package app.kasa.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.core.util.PasswordGenerator
import app.kasa.core.util.PasswordStrength
import app.kasa.core.util.QrCodes
import app.kasa.data.SettingsStore
import app.kasa.ui.AuthViewModel
import app.kasa.ui.CrackTime
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.animatedCorner
import app.kasa.ui.components.GroupPosition
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.components.KasaReveal
import app.kasa.ui.components.KasaTextField
import app.kasa.ui.components.KasaTile
import app.kasa.ui.components.MorphDial
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.components.readablePane
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * İlk kurulum.
 *
 * ### Adımlar ve neden bu sırada
 *
 * Tanıtım → çatal → ana parola → kurtarma anahtarı → **anahtarı geri yaz** →
 * **parolayı hafızadan yaz** → biyometri → sıkılık → devir.
 *
 * Ölçüt her adım için aynıydı: bu adım ya kasayı kurtarıyor ya da uygulamayı
 * kullanılır kılıyor. Hiçbiri "hoş geldin" demek için orada değil ve
 * kurulumun uzunluğu bedava değil — insanlar uzun kurulumları yarıda
 * bırakıyor, o yüzden çatal, sıkılık ve devir adımları tek dokunuşla
 * geçilebiliyor.
 *
 * ### Tanıtım neden burada, ViewModel'de değil
 *
 * [IntroPager] kasa durumuna hiç dokunmuyor: ne anahtar üretiyor ne dosya
 * yazıyor, yalnızca üç sayfa gösterip kenara çekiliyor. Onu
 * [AuthViewModel.Stage] içine bir aşama olarak eklemek, sunum katmanına ait
 * bir kararı kurulum durum makinesine taşımak olurdu — ve o durum makinesi
 * kasanın hangi dosyalarının yazıldığını izliyor, hangi tanıtım sayfasının
 * açık olduğunu değil.
 *
 * Aynı gerekçe çatal için geçerli değil: çatalın seçimi ([AuthViewModel.SetupIntent])
 * son adımın hangi seçeneği öne çıkaracağını belirliyor, yani kurulumun
 * kendisine ait bir bilgi.
 */
@Composable
fun OnboardingScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.setup.collectAsStateWithLifecycle()
    var introDone by remember { mutableStateOf(false) }
    var intentChosen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
    ) {
        // Tanıtımdan kuruluma geçiş de bir hareket: sayfalar sola çıkıyor,
        // parola adımı alttan yükseliyor. Sert bir takas, tanıtımın sonunda
        // "başka bir uygulamaya düştüm" hissi veriyordu.
        //
        // Belirteçler çağrıdan **önce** çözülüyor: `transitionSpec` bloğu
        // @Composable değil, yani KasaMotion'ın içinden okuduğu
        // CompositionLocal'a orada erişilemiyor.
        val fadeInSpec = KasaMotion.enter<Float>()
        val fadeOutSpec = KasaMotion.exit<Float>()
        val slideSpec = KasaMotion.large<IntOffset>()

        AnimatedContent(
            targetState = introDone,
            transitionSpec = {
                (fadeIn(fadeInSpec) + slideInVertically(slideSpec) { it / 8 })
                    .togetherWith(fadeOut(fadeOutSpec) + slideOutHorizontally(slideSpec) { -it / 6 })
            },
            label = "onboardingStage"
        ) { done ->
            if (!done) {
                IntroPager(onFinish = { introDone = true })
            } else {
                // Kurulum adımları geniş pencerede ortalanıyor. Tanıtım
                // sayfaları ortalanmıyor: oradaki çizimler ekranın
                // tamamını kullanmak üzere kurulmuş.
                Column(Modifier.fillMaxSize().readablePane()) {
                    // Üstteki adım rayı kaldırıldı.
                    //
                    // Tanıtım sayfalarının altında zaten bir gösterge vardı ve
                    // parola adımına geçerken **ekranın öteki ucunda** ikinci
                    // bir gösterge beliriyordu. İkisi de bir şeyler sayıyor ama
                    // farklı şeyler sayıyorlar ve farklı yerlerde duruyorlar;
                    // kullanıcının gördüğü şey ilerleme değil, birden ortaya
                    // çıkan yeni bir nesne oluyordu.
                    //
                    // Kaç adım kaldığını söylemenin bedeli bu değil: her adımın
                    // kendi başlığı zaten nerede olunduğunu söylüyor.

                    // Adımlar arası geçiş.
                    //
                    // Yön ileri: yeni adım alttan yükseliyor, eski yukarı
                    // çekiliyor. Kurulum tek yönlü bir yol ve hareketin yönü
                    // bunu söylüyor; geri dönülemeyen bir adımın iki yöne de
                    // kayabildiğini göstermek yanlış bilgi olurdu.
                    AnimatedContent(
                        targetState = if (intentChosen) state.stage else null,
                        transitionSpec = {
                            (fadeIn(fadeInSpec) + slideInVertically(slideSpec) { it / 7 })
                                .togetherWith(
                                    fadeOut(fadeOutSpec) + slideOutVertically(slideSpec) { -it / 10 }
                                )
                        },
                        label = "setupStage",
                        modifier = Modifier.weight(1f)
                    ) { stage ->
                        when (stage) {
                            null -> IntentStep(
                                onChoose = {
                                    viewModel.onIntentChosen(it)
                                    intentChosen = true
                                }
                            )
                            AuthViewModel.Stage.SETUP -> SetupStep(viewModel, state)
                            AuthViewModel.Stage.RECOVERY_SHOWN -> RecoveryStep(viewModel, state)
                            AuthViewModel.Stage.RECOVERY_VERIFY -> RecoveryVerifyStep(viewModel, state)
                            AuthViewModel.Stage.CONFIRM_MASTER -> ConfirmMasterStep(viewModel, state)
                            AuthViewModel.Stage.BIOMETRIC_OFFER -> BiometricStep(viewModel)
                            AuthViewModel.Stage.POSTURE -> PostureStep(viewModel)
                            AuthViewModel.Stage.HANDOFF -> HandoffStep(viewModel, state)
                            AuthViewModel.Stage.DONE -> Unit
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kurulumun çatalı: boş kasa mı, elindeki yedek mi.
 *
 * ### Neden var
 *
 * Telefon değiştiren ya da uygulamayı silip yeniden kuran kullanıcı için tek
 * yol şuydu: yeni bir kasa yarat, sonra Ayarlar'ın derinliğinde içe aktarmayı
 * bul. Her iki geri yükleme yolu da kodda hazırdı ama ikisi de kasa
 * yaratıldıktan **sonra** erişilebiliyordu.
 *
 * Bedeli yalnızca birkaç dokunuş değildi: geri yükleyeceği kasanın zaten bir
 * ana parolası olan kullanıcı, kurulumda ikinci bir ana parola seçiyor ve
 * hangisinin geçerli olduğu belirsiz kalıyordu. Bu, insanların kasa kaybettiği
 * klasik senaryo.
 *
 * ### Neden yine de ana parola soruluyor
 *
 * Yedek dosyasının kendi parolası var ve o parola dışa aktarma anında
 * seçilmişti; kasanın bu cihazdaki ana parolası ayrı bir şey. İkisini
 * birleştirmek mümkün değil, o yüzden yapılan şey doğruyu söylemek: bu adımı
 * seçen kullanıcı, sonraki ekranda **bu cihaz için** parola seçtiğini okuyor.
 */
@Composable
private fun IntentStep(onChoose: (AuthViewModel.SetupIntent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KasaReveal(visible = true, delayMillis = 0) {
            Text(
                stringResource(R.string.onb_intent_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY, modifier = Modifier.fillMaxWidth()) {
            ChoiceCard(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.onb_intent_fresh),
                body = stringResource(R.string.onb_intent_fresh_sub),
                onClick = { onChoose(AuthViewModel.SetupIntent.FRESH) }
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY * 2, modifier = Modifier.fillMaxWidth()) {
            ChoiceCard(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.onb_intent_restore),
                body = stringResource(R.string.onb_intent_restore_sub),
                onClick = { onChoose(AuthViewModel.SetupIntent.RESTORE) }
            )
        }
    }
}

/**
 * İkonlu, iki satırlı seçim kartı. Çatalda ve sıkılık adımında kullanılıyor.
 */
@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    // Seçim bir durum, bir olay değil.
    //
    // Önceden yalnızca simge onay imiyle takas ediliyordu: kart seçiliyken
    // seçili olmayandan yalnızca o küçük karenin içeriğiyle ayrılıyordu ve
    // takas tek karede olduğu için hangi kartın değiştiği bile gözden
    // kaçıyordu.
    //
    // Şimdi simge kabı seçilince kareden daireye **dönüşüyor** ve rengi
    // oraya doğru yürüyor. Biçimin kendisi seçili olmanın işareti, yani
    // karar ekranda kalıcı olarak duruyor.
    // Belirteçler çağrıdan **önce** çözülüyor: `transitionSpec` bloğu
    // @Composable değil, yani KasaMotion'ın içinden okuduğu
    // CompositionLocal'a orada erişilemiyor.
    val iconIn = KasaMotion.enter<Float>()
    val iconOut = KasaMotion.exit<Float>()
    val iconPop = KasaMotion.small<Float>()

    val radius = animatedCorner(
        if (selected) KasaRadius.full else KasaRadius.m,
        label = "choiceIconShape"
    )
    val background by animateColorAsState(
        if (selected) KasaTheme.colors.badgeStrongBg
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        KasaMotion.effect(),
        label = "choiceBg"
    )
    val foreground by animateColorAsState(
        if (selected) KasaTheme.colors.badgeStrongFg else KasaTheme.colors.ink2,
        KasaMotion.effect(),
        label = "choiceFg"
    )

    KasaTile(
        position = GroupPosition.ONLY,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(radius))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            // Simge de takas edilmiyor, **çapraz geçiyor**: onay imi
            // büyüyerek gelirken eski simge küçülerek çıkıyor.
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(iconIn) + scaleIn(initialScale = 0.6f, animationSpec = iconPop))
                        .togetherWith(fadeOut(iconOut) + scaleOut(targetScale = 0.6f, animationSpec = iconOut))
                },
                label = "choiceIcon"
            ) { on ->
                Icon(
                    if (on) Icons.Rounded.Check else icon,
                    contentDescription = null,
                    tint = foreground
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink3)
        }
    }
}

/**
 * Ana parola adımı.
 *
 * ### Yineleme alanı neden burada değil
 *
 * İki parola alanı yan yana dururken kullanıcının yaptığı şey hatırlamak
 * değil, az önce yazdığını kopyalamaktı — gözü hâlâ ilk alanda. Yineleme
 * artık kurtarma adımından sonra ([ConfirmMasterStep]) soruluyor; arada geçen
 * dakika testi gerçek kılıyor.
 *
 * ### Ekran neden bir öneri sunuyor
 *
 * Kadran, güç çubuğu ve entropi, hepsi **yazdıktan sonra** çalışan şeyler:
 * seçtiğin şeye not veriyorlar ama "ne seçmeliyim" sorusuna cevap
 * vermiyorlar. Sözcük dizisi o sorunun tek dürüst cevabı — akılda kalıyor ve
 * uzunluğu gücü kendiliğinden getiriyor. Üretilen öneri doğrudan alana
 * yazılıyor, kullanıcı üstünde oynayabiliyor.
 *
 * ### Bit yerine süre
 *
 * "78 bit" kimsenin sahip olduğu bir birim değil. Yanına, varsayımı açıkça
 * yazılmış bir çeviri konuyor: saniyede yüz milyar deneme yapan çevrimdışı
 * bir saldırgan. Sayı tek başına dururken bir şey ifade etmiyordu.
 */
@Composable
private fun SetupStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val evaluation = if (password.isEmpty()) null else PasswordStrength.evaluate(password)
    val strength by animateFloatAsState(
        evaluation?.score ?: 0f,
        KasaMotion.large(),
        label = "setupStrength"
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        // Sırayla beliren öğeler. Aynı anda gelen bir ekran tek bir blok
        // olarak okunuyor ve gözün nereden başlayacağı belli olmuyor; sırayla
        // gelince okuma yönü hareketin kendisinden çıkıyor.
        KasaReveal(visible = true, delayMillis = 0) {
            MorphDial(
                strength = strength,
                color = when {
                    strength > 0.5f -> KasaTheme.colors.badgeStrongBg
                    strength > 0.28f -> KasaTheme.colors.badgeMidBg
                    else -> KasaTheme.colors.badgeWeakBg
                },
                modifier = Modifier.size(150.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_title),
                style = KasaTheme.text.hero,
                color = KasaTheme.colors.ink
            )
        }
        Spacer(Modifier.height(10.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY * 2) {
            Text(
                stringResource(
                    if (state.intent == AuthViewModel.SetupIntent.RESTORE) R.string.onb_sub_restore
                    else R.string.onb_sub
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))

        KasaPasswordField(
            value = password,
            onValueChange = {
                password = it
                viewModel.onMasterPasswordTyped(it)
            },
            label = stringResource(R.string.onb_master),
            revealed = revealed,
            onRevealToggle = { revealed = !revealed },
            imeAction = ImeAction.Done,
            isError = state.error != null,
            supportingText = state.error?.let { stringResource(it) }
                ?: stringResource(R.string.onb_master_hint)
        )

        Spacer(Modifier.height(10.dp))
        KasaButton(
            text = stringResource(R.string.onb_suggest),
            onClick = {
                // Öneri açık geliyor: kullanıcının okuyup ezberleyemediği bir
                // şeyi kabul etmesini istemek, ezberleneceğini varsaymak olur.
                val words = PasswordGenerator.words(context)
                password = PasswordGenerator.generatePassphrase(
                    words,
                    PasswordGenerator.PassphraseOptions(words = SUGGEST_WORDS)
                ).value
                revealed = true
                viewModel.onMasterPasswordTyped(password)
            },
            tone = ButtonTone.TONAL,
            height = 44.dp,
            modifier = Modifier.fillMaxWidth(),
            leading = { Icon(Icons.Rounded.Password, contentDescription = null) }
        )

        if (evaluation != null) {
            Spacer(Modifier.height(14.dp))
            WavyProgress(
                progress = evaluation.score,
                color = strengthColor(evaluation.tone),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(6.dp))
            val crack = CrackTime.of(PasswordStrength.crackSeconds(evaluation.entropyBits))
            Text(
                stringResource(
                    R.string.onb_crack,
                    evaluation.entropyBits.toInt(),
                    crack.arg?.let { stringResource(crack.textRes, it) }
                        ?: stringResource(crack.textRes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
        }

        if (state.busy) {
            Spacer(Modifier.height(18.dp))
            WavyProgress(
                progress = state.progress,
                color = KasaTheme.colors.badgeStrongBg,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.calib_running),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
        }

        Spacer(Modifier.height(20.dp))
        // Sonucun kendisi. İpucundaki "unutursan kurtarma anahtarı var"
        // cümlesi bunu yumuşatıyordu: asıl söylenmesi gereken, sıfırlamanın
        // hiç olmadığı.
        NoteRow(stringResource(R.string.onb_no_reset))

        Spacer(Modifier.height(20.dp))
        KasaButton(
            text = stringResource(if (state.busy) R.string.onb_creating else R.string.onb_create),
            onClick = { viewModel.createVault(password.toCharArray()) },
            enabled = !state.busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
    }
}

/** Uyarı satırı: kenarda ince bir şerit, gövdede tek cümle. */
@Composable
private fun NoteRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(KasaTheme.colors.badgeWeakBg)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink2
        )
    }
}

/**
 * Kurtarma anahtarı adımı.
 *
 * ### Onay kutusu neden kaldırıldı
 *
 * Burada "kurtarma anahtarını kaydettim" yazan bir onay kutusu vardı ve
 * niyeti kullanıcının "sonra bakarım" demesini engellemekti. Onay kutusu tam
 * olarak "sonra bakarım"ın kendisidir: işaretlemenin bedeli sıfır, kimse
 * okumadan geçer. Yerine gelen şey [RecoveryVerifyStep] — beyan yerine kanıt.
 *
 * ### Anahtarın gidecek bir yeri
 *
 * Önceden tek seçenek ekrana bakmaktı. Şimdi dosyaya yazılabiliyor ve
 * karekod olarak gösterilebiliyor; karekodu ikinci bir cihazla fotoğraflamak,
 * elle yazmanın en sık başarısız olduğu yeri — 24 karakteri yanlışsız
 * kopyalamayı — tamamen atlıyor.
 *
 * Panoya kopyalama bilerek yok: pano bütün uygulamalara açık ve kasa
 * anahtarının orada bir saniye durmasının gerekçesi yok.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecoveryStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    val code = state.recoveryCode.orEmpty()
    var showQr by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) viewModel.saveRecoveryCode(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        KasaReveal(visible = true, delayMillis = 0) {
            Text(
                stringResource(R.string.onb_recovery_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_recovery_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))

        // Anahtarın kendisi en son ve en güçlü bulanıklıktan çözülüyor.
        //
        // Bu ekranın tek gerçek işi bu koda bakılmasını sağlamak. Kod
        // ötekilerle birlikte belirseydi sayfanın bir parçası olurdu; sonradan
        // ve farklı bir hareketle gelince, hareketin kendisi "asıl mesele bu"
        // diyor. Aynı bulanıklıktan-çözülme, kasa açılırken parola alanında da
        // kullanılıyor — yani öğrenilmiş bir hareket, yeni bir süs değil.
        KasaReveal(
            visible = true,
            delayMillis = STEP_DELAY * 3,
            blurRadius = CODE_BLUR,
            lift = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(KasaRadius.xl))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 18.dp, vertical = 24.dp)
            ) {
                // Kod, grupları sırayla çözülerek beliriyor.
                //
                // Kutu tek parça olarak netleşiyordu ve yirmi dört karakter
                // aynı anda okunur hâle geliyordu; gözün nereden başlayacağına
                // dair hiçbir işaret yoktu. Soldan sağa açılınca okuma yönünü
                // hareketin kendisi veriyor — ve bu ekranın tek işi bu koda
                // gerçekten bakılması.
                //
                // Gruplar ayrı ayrı sarılıyor çünkü aralarındaki gecikme
                // metnin **içinde** olmalı; tek bir Text'e uygulanan gecikme
                // yine tek bir olay olurdu.
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val groups = code.split('-')
                    groups.forEachIndexed { index, group ->
                        KasaReveal(
                            visible = true,
                            delayMillis = STEP_DELAY * 3 + index * CODE_GROUP_STEP,
                            blurRadius = CODE_BLUR,
                            lift = 0.dp
                        ) {
                            Text(
                                if (index == groups.lastIndex) group else "$group-",
                                style = KasaTheme.text.mono,
                                color = KasaTheme.colors.ink
                            )
                        }
                    }
                }
            }
        }

        if (showQr) {
            Spacer(Modifier.height(16.dp))
            RecoveryQr(code)
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            KasaButton(
                text = stringResource(
                    if (state.recoverySaved) R.string.onb_recovery_saved_file
                    else R.string.onb_recovery_save
                ),
                onClick = { saveLauncher.launch(RECOVERY_FILE_NAME) },
                tone = ButtonTone.TONAL,
                height = 46.dp,
                modifier = Modifier.weight(1f),
                leading = {
                    Icon(
                        if (state.recoverySaved) Icons.Rounded.Check else Icons.Rounded.Download,
                        contentDescription = null
                    )
                }
            )
            Spacer(Modifier.width(8.dp))
            KasaButton(
                text = stringResource(if (showQr) R.string.onb_recovery_qr_hide else R.string.onb_recovery_qr),
                onClick = { showQr = !showQr },
                tone = ButtonTone.TONAL,
                height = 46.dp,
                modifier = Modifier.weight(1f),
                leading = { Icon(Icons.Rounded.QrCode2, contentDescription = null) }
            )
        }

        Spacer(Modifier.height(16.dp))
        NoteRow(stringResource(R.string.onb_recovery_note))

        Spacer(Modifier.height(20.dp))
        KasaButton(
            text = stringResource(R.string.onb_recovery_next),
            onClick = viewModel::onRecoverySeen,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Kurtarma anahtarının karekodu.
 *
 * Zemin her temada beyaz: karekod okuyucular koyu modül–açık zemin
 * bekliyor ve karanlık temada tema renklerine uymak, okunamayan ama güzel
 * duran bir kare üretirdi.
 */
@Composable
private fun RecoveryQr(code: String) {
    val density = LocalDensity.current
    val sizePx = with(density) { QR_SIZE.roundToPx() }
    // Kodlama kare başına değil, kod ve boyut değiştiğinde yapılıyor.
    val bitmap: Bitmap? = remember(code, sizePx) { QrCodes.bitmap(code, sizePx) }
    if (bitmap == null) return

    Box(
        Modifier
            .size(QR_SIZE + QR_QUIET * 2)
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(QR_SIZE)
        )
    }
}

/**
 * Kurtarma anahtarının doğrulanması.
 *
 * ### Neden yalnızca birkaç grup
 *
 * Yirmi dört karakterin tamamını yazdırmak kâğıttan kopyalamayı bir işe
 * dönüştürüyor ve insanlar işten kaçmak için en kolay yolu buluyor: ekran
 * görüntüsü. Yani kodu tam da korunması gereken yere, galeriye koyuyor.
 * Üç grup, koda bakmadan geçilemeyecek kadar çok; yazması sıkıcı olmayacak
 * kadar az.
 *
 * ### Neden geri dönüş var
 *
 * "Koda tekrar bak" düğmesi bu adımı zayıflatmıyor: amaç kullanıcıyı sınavda
 * bırakmak değil, kodun elinde olduğundan emin olmak. Kâğıdı yanlış yere
 * koymuş biri geri dönüp bakabilmeli; kâğıdı hiç yazmamış biri zaten dönünce
 * yazacak.
 */
@Composable
private fun RecoveryVerifyStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    val slots = state.verifyIndices
    var typed by remember(slots) { mutableStateOf(List(slots.size) { "" }) }
    val complete = typed.all { it.trim().length >= GROUP_LENGTH }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        KasaReveal(visible = true, delayMillis = 0) {
            Text(
                stringResource(R.string.onb_verify_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_verify_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))

        // Alanlar sırayla iniyor: kaç grup istendiği, saymadan önce
        // hareketten anlaşılıyor.
        slots.forEachIndexed { slot, group ->
            KasaReveal(
                visible = true,
                delayMillis = STEP_DELAY * 2 + slot * FIELD_STEP,
                lift = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
            KasaTextField(
                value = typed[slot],
                onValueChange = { fresh ->
                    // Crockford Base32 büyük harfli; kullanıcı küçük yazsa da
                    // tireyi kendisi koysa da aynı yere varmalı.
                    val cleaned = fresh.uppercase().filter { it.isLetterOrDigit() }.take(GROUP_LENGTH)
                    typed = typed.toMutableList().also { it[slot] = cleaned }
                },
                // Grup numarası kullanıcının gördüğü sayı: koddaki sıra 1'den
                // başlıyor, dizideki sıra 0'dan.
                label = stringResource(R.string.onb_verify_group, group + 1),
                textStyle = KasaTheme.text.mono,
                imeAction = if (slot == slots.lastIndex) ImeAction.Done else ImeAction.Next,
                isError = state.verifyError,
                modifier = Modifier.fillMaxWidth()
            )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (state.verifyError) {
            Text(
                stringResource(R.string.onb_verify_wrong),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.badgeWeakFg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(10.dp))
        KasaButton(
            text = stringResource(R.string.continue_),
            onClick = { viewModel.onRecoveryVerified(typed) },
            enabled = complete,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        KasaButton(
            text = stringResource(R.string.onb_verify_back),
            onClick = viewModel::onRecoveryReview,
            tone = ButtonTone.TEXT,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Ana parolayı hafızadan yazdırma.
 *
 * ### Yanlış yazarsa
 *
 * Üç denemeden sonra ekran, kasanın **şu an boş** olduğunu söyleyip baştan
 * başlamayı öneriyor. Bu öneri yalnızca burada verilebilir ve tam da bu
 * yüzden yineleme buraya konuldu: kurulumun ilerisinde aynı durumun cevabı
 * kurtarma anahtarı olur, kasa doluyken "baştan başla" demek ise kayıp
 * demektir. Şu anda silinen tek şey, açılamayacak bir anahtar.
 *
 * Öneri dayatma değil — kullanıcı denemeye devam edebilir. Parolayı
 * hatırlayan biri dördüncü denemede hatırlıyor; hatırlamayan biri en azından
 * durumu okumuş oluyor.
 */
@Composable
private fun ConfirmMasterStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val exhausted = state.confirmAttempts >= CONFIRM_PATIENCE

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))
        KasaReveal(visible = true, delayMillis = 0) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                tint = KasaTheme.colors.ink3,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_confirm_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY * 2) {
            Text(
                stringResource(R.string.onb_confirm_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))

        KasaPasswordField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.onb_master),
            revealed = revealed,
            onRevealToggle = { revealed = !revealed },
            imeAction = ImeAction.Done,
            isError = state.confirmError,
            supportingText = if (state.confirmError) stringResource(R.string.onb_confirm_wrong) else null
        )

        if (state.busy) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.onb_confirm_checking),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3
            )
        }

        Spacer(Modifier.height(20.dp))
        KasaButton(
            text = stringResource(R.string.continue_),
            onClick = {
                viewModel.confirmMaster(password.toCharArray())
                password = ""
            },
            enabled = !state.busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )

        if (exhausted) {
            Spacer(Modifier.height(24.dp))
            NoteRow(stringResource(R.string.onb_confirm_stuck))
            Spacer(Modifier.height(10.dp))
            KasaButton(
                text = stringResource(R.string.onb_confirm_restart),
                onClick = viewModel::restartSetup,
                tone = ButtonTone.TEXT,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BiometricStep(viewModel: AuthViewModel) {
    val gate = LocalBiometricGate.current
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            // Öteki adımlar kaydırılabiliyordu, bu değildi: kısa bir
            // pencerede (tablette yatay tutulduğunda ya da bölünmüş ekranda)
            // düğmeler ekranın altında kalıyor ve adım tamamlanamıyordu.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KasaReveal(visible = true, delayMillis = 0) {
            MorphDial(
                strength = 1f,
                color = KasaTheme.colors.badgeStrongBg,
                modifier = Modifier.size(140.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_biometric_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY * 2) {
            Text(
                stringResource(R.string.onb_biometric_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(32.dp))
        KasaButton(
            text = stringResource(R.string.onb_biometric_enable),
            onClick = {
                val cipher = viewModel.biometricEncryptCipher()
                if (cipher != null && gate != null) {
                    gate.authenticate(
                        title = context.getString(R.string.onb_biometric_title),
                        subtitle = context.getString(R.string.onb_biometric_sub),
                        negativeButton = context.getString(R.string.onb_skip),
                        cipher = cipher,
                        onSuccess = viewModel::enableBiometric,
                        onError = { _, _ -> viewModel.skipBiometric() }
                    )
                } else {
                    viewModel.skipBiometric()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        KasaButton(
            text = stringResource(R.string.onb_skip),
            onClick = viewModel::skipBiometric,
            tone = ButtonTone.TEXT,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Sıkılık seçimi.
 *
 * ### Neden tek soru
 *
 * Buradaki dört ayarın dördü de Ayarlar'da tek tek duruyor ve orada kalmaya
 * devam ediyor. Sorun bulunabilirlikti: kilit süresini, pano temizleme
 * süresini ve yanlış deneme eşiğini kurcalayan kullanıcı sayısı az,
 * dolayısıyla neredeyse herkes varsayılanla yaşıyor — yani gerçekte seçen
 * taraf uygulama oluyor ama seçtiğini söylemiyor.
 *
 * ### Neden üçüncü bir kademe yok
 *
 * "Gevşek" eklemek, ilk kurulumda kullanıcıya korumasını kendi eliyle
 * indirmeyi teklif etmek olurdu. Gevşetmek mümkün, ama Ayarlar'dan ve tek
 * tek — yani bilerek.
 */
@Composable
private fun PostureStep(viewModel: AuthViewModel) {
    var chosen by remember { mutableStateOf<SettingsStore.SecurityPosture?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KasaReveal(visible = true, delayMillis = 0) {
            Text(
                stringResource(R.string.onb_posture_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_posture_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))

        KasaReveal(visible = true, delayMillis = STEP_DELAY * 2, modifier = Modifier.fillMaxWidth()) {
            ChoiceCard(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.onb_posture_balanced),
                body = stringResource(R.string.onb_posture_balanced_sub),
                selected = chosen == SettingsStore.SecurityPosture.BALANCED,
                onClick = { chosen = SettingsStore.SecurityPosture.BALANCED }
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY * 3, modifier = Modifier.fillMaxWidth()) {
            ChoiceCard(
                icon = Icons.Rounded.Speed,
                title = stringResource(R.string.onb_posture_strict),
                body = stringResource(R.string.onb_posture_strict_sub),
                selected = chosen == SettingsStore.SecurityPosture.STRICT,
                onClick = { chosen = SettingsStore.SecurityPosture.STRICT }
            )
        }

        Spacer(Modifier.height(24.dp))
        KasaButton(
            text = stringResource(R.string.continue_),
            onClick = { chosen?.let(viewModel::choosePosture) },
            enabled = chosen != null,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onb_posture_note),
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink3,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Kurulumun son adımı: Kasa'yı telefona bağlamak ve kasayı doldurmak.
 *
 * ### Neden bu adım var
 *
 * Kurulum eskiden biyometriyle bitiyor ve kullanıcı boş bir kasayla, tek
 * satırlık bir açıklamanın karşısında kalıyordu. Uygulamanın günlük kullanıma
 * girip girmeyeceğini belirleyen iki şey ise o ekranda hiç geçmiyordu:
 *
 * - **Otomatik doldurma.** Servis yazılmış ve alan adı doğrulaması dahil
 *   çalışıyor, ama sistem ayarlarından seçilmezse Kasa "elle kopyalanan bir
 *   kasa" olarak kalıyor — yani insanları parola tekrarına iten sürtünme
 *   aynen yerinde duruyor.
 * - **Mevcut parolalar.** Bir parola yöneticisinin değeri, içine parolalar
 *   girene kadar sıfır. İçe aktarma vardı ama yalnızca Ayarlar'da; kullanıcının
 *   "şunu kurayım" modunda olduğu tek an olan kurulumda sunulmuyordu.
 *
 * ### Neden hepsi atlanabilir
 *
 * Hiçbiri kasanın güvenliğini etkilemiyor; hepsi kolaylık. Zorunlu kılmak,
 * kurulumu uzatıp terk oranını artırmaktan başka bir şey yapmazdı. "Bitir"
 * her zaman etkin.
 */
@Composable
private fun HandoffStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    val context = LocalContext.current

    // Üç anahtarın durumu **uygulamaya her dönüşte** yeniden okunuyor.
    //
    // Üçü de sistemin kendi ekranlarında açılıyor, yani kullanıcı düğmeye
    // bastığında Kasa arka plana geçiyor ve karar başka bir yerde veriliyor.
    // Durumu düğmeye basıldığı anda okumak işe yaramaz: o an henüz hiçbir şey
    // değişmemiştir. `ON_RESUME` ise tam olarak kullanıcının geri geldiği an.
    //
    // Her besteye okumak da yanlış olurdu: bunlar sistem servisi çağrıları ve
    // kaydırma sırasında kare başına tekrarlanmaları için hiçbir sebep yok.
    var autofillOn by remember { mutableStateOf(isAutofillEnabled(context)) }
    var passkeyOn by remember { mutableStateOf(isCredentialProviderEnabled(context)) }
    var notificationsOn by remember { mutableStateOf(areNotificationsEnabled(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        autofillOn = isAutofillEnabled(context)
        passkeyOn = isCredentialProviderEnabled(context)
        notificationsOn = areNotificationsEnabled(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsOn = it }

    var pendingArchive by remember { mutableStateOf<Uri?>(null) }

    val archiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingArchive = uri }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importCsv(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        KasaReveal(visible = true, delayMillis = 0) {
            Text(
                stringResource(R.string.onb_handoff_title),
                style = KasaTheme.text.sheetTitle,
                color = KasaTheme.colors.ink,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        KasaReveal(visible = true, delayMillis = STEP_DELAY) {
            Text(
                stringResource(R.string.onb_handoff_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))

        // Kasayı doldurma.
        //
        // Sıra çatalın seçimine bağlı: "yedeğim var" diyen kullanıcı buraya
        // zaten bunun için geldi, listenin ikinci satırında aramamalı.
        val restoreFirst = state.intent == AuthViewModel.SetupIntent.RESTORE

        SectionCaption(stringResource(R.string.onb_handoff_fill))
        FillRow(
            icon = if (restoreFirst) Icons.Rounded.Download else Icons.Rounded.Password,
            title = stringResource(
                if (restoreFirst) R.string.onb_handoff_archive else R.string.onb_handoff_csv
            ),
            subtitle = stringResource(
                if (restoreFirst) R.string.onb_handoff_archive_sub else R.string.onb_handoff_csv_sub
            ),
            position = GroupPosition.FIRST,
            step = 0,
            onClick = {
                if (restoreFirst) archiveLauncher.launch(arrayOf(ANY_MIME))
                else csvLauncher.launch(arrayOf(ANY_MIME))
            }
        )
        FillRow(
            icon = if (restoreFirst) Icons.Rounded.Password else Icons.Rounded.Download,
            title = stringResource(
                if (restoreFirst) R.string.onb_handoff_csv else R.string.onb_handoff_archive
            ),
            subtitle = stringResource(
                if (restoreFirst) R.string.onb_handoff_csv_sub else R.string.onb_handoff_archive_sub
            ),
            position = GroupPosition.LAST,
            step = 1,
            onClick = {
                if (restoreFirst) csvLauncher.launch(arrayOf(ANY_MIME))
                else archiveLauncher.launch(arrayOf(ANY_MIME))
            }
        )

        state.imported?.let { count ->
            Spacer(Modifier.height(10.dp))
            NoteRow(
                if (count == 0) stringResource(R.string.csv_none)
                else stringResource(R.string.onb_handoff_imported, count)
            )
        }
        state.importError?.let {
            Spacer(Modifier.height(10.dp))
            NoteRow(stringResource(it))
        }

        Spacer(Modifier.height(24.dp))
        SectionCaption(stringResource(R.string.onb_handoff_connect))

        HandoffRow(
            icon = Icons.Rounded.Password,
            title = stringResource(R.string.onb_handoff_autofill),
            subtitle = stringResource(R.string.onb_handoff_autofill_sub),
            position = GroupPosition.FIRST,
            done = autofillOn,
            step = 2,
            onClick = { openAutofillSettings(context) }
        )
        HandoffRow(
            icon = Icons.Rounded.Key,
            title = stringResource(R.string.onb_handoff_passkey),
            subtitle = stringResource(R.string.onb_handoff_passkey_sub),
            position = GroupPosition.MIDDLE,
            done = passkeyOn,
            step = 3,
            onClick = { openCredentialProviderSettings(context) }
        )
        // Bildirim izni.
        //
        // İzin manifestte tanımlıydı ve sızıntı taraması onu kontrol
        // ediyordu, ama hiçbir yerde **istenmiyordu**: Android 13'ten beri
        // izin varsayılan olarak reddedildiği için tarama sonucu hiçbir zaman
        // görünmüyordu. Soğuk bir sistem penceresi yerine burada, ne işe
        // yaradığı yazılı olarak soruluyor.
        HandoffRow(
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.onb_handoff_notify),
            subtitle = stringResource(R.string.onb_handoff_notify_sub),
            position = GroupPosition.LAST,
            done = notificationsOn,
            step = 4,
            onClick = {
                if (!notificationsOn) {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        Spacer(Modifier.height(28.dp))
        KasaButton(
            text = stringResource(R.string.onb_handoff_finish),
            onClick = viewModel::finishOnboarding,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onb_handoff_note),
            style = MaterialTheme.typography.bodySmall,
            color = KasaTheme.colors.ink3,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
    }

    // Yedeğin parolası. Kasa dosyası şifreli ve parolası ana paroladan ayrı;
    // bunu sormadan dosyayı açmanın yolu yok.
    pendingArchive?.let { uri ->
        PasswordPromptDialog(
            title = stringResource(R.string.onb_archive_title),
            description = stringResource(R.string.onb_archive_body),
            label = stringResource(R.string.exp_password),
            confirmText = stringResource(R.string.onb_handoff_archive),
            onConfirm = { password ->
                pendingArchive = null
                viewModel.restoreArchive(uri, password)
            },
            onDismiss = { pendingArchive = null }
        )
    }
}

/**
 * Kasayı dolduran satır.
 *
 * [HandoffRow]'dan ayrı, çünkü burada gösterilecek bir "açık" durumu yok:
 * içe aktarma bir anahtar değil, bir olay. Sonucu satırın kendisinde bir
 * onay imiyle değil, altındaki not satırında kaç kaydın geldiğiyle
 * söyleniyor.
 */
@Composable
private fun FillRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: GroupPosition,
    step: Int,
    onClick: () -> Unit
) {
    HandoffRow(icon, title, subtitle, position, onClick, done = null, step = step)
}

/** Bölüm başlığı: satır grubunun ne olduğunu söyleyen küçük etiket. */
@Composable
private fun SectionCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = KasaTheme.colors.ink3,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * Devir adımının satırı.
 *
 * [done] üç değerli: `true` açık, `false` kapalı, `null` bilinmiyor. Üçüncüsü
 * geçiş anahtarı sağlayıcısı için var — sistemde durumu soracak resmî bir API
 * yok ve tahmin etmektense rozeti hiç göstermemek doğru.
 */
@Composable
private fun HandoffRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: GroupPosition,
    onClick: () -> Unit,
    done: Boolean? = false,
    /**
     * Sıradaki yeri. Satırlar tek blok olarak belirdiğinde hangi eylemin
     * nerede olduğu ancak okununca anlaşılıyordu; sırayla inince liste bir
     * yığın değil bir **sıra** oluyor.
     */
    step: Int = 0
) {
    KasaReveal(
        visible = true,
        delayMillis = STEP_DELAY * 2 + step * ROW_STEP,
        lift = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
    KasaTile(position = position, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = KasaTheme.colors.ink2)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KasaTheme.colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KasaTheme.colors.ink3)
        }
        if (done == true) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = KasaTheme.colors.badgeStrongBg
            )
        }
    }
    }
}

/**
 * Sırayla belirmede iki öğe arasındaki gecikme.
 *
 * Menü öğelerinin 26 ms'inden uzun: orada dört küçük şey aynı hareketin
 * parçası, burada bir ekranın ayrı bölümleri. Aynı değeri kullanmak, bölümleri
 * tek bir blok gibi getiriyordu.
 */
private const val STEP_DELAY = 90

/**
 * Kurtarma anahtarının çözülme bulanıklığı.
 *
 * Ötekilerden yüksek: kodun okunamaz başlayıp okunur hâle gelmesi, ekranın
 * asıl olayının o olduğunu söyleyen şey.
 */
private val CODE_BLUR = 26.dp

/**
 * Sıralı beliren alanlar ve satırlar arasındaki gecikme.
 *
 * [STEP_DELAY]'den kısa: bunlar ekranın ayrı bölümleri değil, tek bir
 * listenin öğeleri. Aynı değeri kullanmak listeyi bölümlere ayırırdı.
 */
private const val FIELD_STEP = 60
private const val ROW_STEP = 45

/** Önerilen sözcük dizisinin uzunluğu: ~9 bit/sözcük ile 50 bitin üzerinde. */
private const val SUGGEST_WORDS = 6

/**
 * Kodun grupları arasındaki çözülme gecikmesi.
 *
 * Adımın bölümleri arasındaki [STEP_DELAY]'den kısa: bunlar ayrı bölümler
 * değil, tek bir şeyin parçaları. Uzun bir gecikme kodu altı ayrı nesneye
 * bölerdi.
 */
private const val CODE_GROUP_STEP = 55

/** Kurtarma anahtarındaki bir grubun karakter sayısı (Crockford Base32). */
private const val GROUP_LENGTH = 4

/** Ana parola kaç kez yanlış yazıldıktan sonra baştan başlamak öneriliyor. */
private const val CONFIRM_PATIENCE = 3

/** Karekodun kenar uzunluğu ve etrafındaki beyaz pay. */
private val QR_SIZE = 190.dp
private val QR_QUIET = 14.dp

/**
 * Dosya seçicinin süzgeci.
 *
 * Daraltılmıyor: dışa aktarılan `.kasa` uzantısının kayıtlı bir MIME türü yok
 * ve CSV dosyaları cihazdan cihaza `text/csv`, `text/comma-separated-values`
 * ya da `application/octet-stream` olarak geliyor. Dar bir süzgeç, kullanıcının
 * gözüyle gördüğü dosyayı seçicide **soluk** gösterir; bu, süzmenin
 * kazandırdığı kolaylıktan çok daha pahalı bir hata.
 */
private const val ANY_MIME = "*/*"

/**
 * Kurtarma anahtarı dosyasının önerilen adı.
 *
 * "kasa" geçiyor ama hangi kasa olduğu yazmıyor: dosya adları yedeklemelerde,
 * bulut eşitlemesinde ve dosya yöneticilerinin önizlemelerinde görünüyor.
 */
private const val RECOVERY_FILE_NAME = "kasa-kurtarma-anahtari.txt"
