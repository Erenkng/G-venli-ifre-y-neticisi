package app.kasa.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasa.R
import app.kasa.core.util.PasswordStrength
import app.kasa.ui.AuthViewModel
import app.kasa.ui.LocalBiometricGate
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaPasswordField
import app.kasa.ui.components.KasaReveal
import app.kasa.ui.components.MorphDial
import app.kasa.ui.components.WavyProgress
import app.kasa.ui.theme.KasaMotion
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme

/**
 * İlk kurulum: tanıtım, ana parola, kurtarma anahtarı, biyometri.
 *
 * Üç adım da atlanamaz sırayla gösteriliyor. Kurtarma anahtarı adımı
 * özellikle "sonra bakarım" denemeyecek şekilde kurgulandı: kod bir daha
 * gösterilmiyor ve devam etmek için kullanıcı açıkça "kaydettim" demek
 * zorunda. Bir parola yöneticisinde en sık yaşanan felaket veri sızıntısı
 * değil, kullanıcının kendi kasasından kilitlenmesidir.
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
 * Bu yüzden tanıtımın "bitti mi" bilgisi burada, ekranın kendi durumunda.
 * Kullanıcı kurulumu yarıda bırakıp uygulamayı kapatırsa tanıtımı yeniden
 * görüyor; kurulum da zaten baştan başlıyor, yani ikisi tutarlı.
 */
@Composable
fun OnboardingScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.setup.collectAsStateWithLifecycle()
    var introDone by remember { mutableStateOf(false) }

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
                Column(Modifier.fillMaxSize()) {
                    SetupRail(state.stage)

                    // Adımlar arası geçiş.
                    //
                    // Eskiden burada düz bir `when` vardı: parola adımı bir
                    // karede gidiyor, kurtarma anahtarı bir sonrakinde
                    // geliyordu. Kurulumun en kritik anı — kasanın yaratıldığı
                    // an — hiçbir şey olmamış gibi geçiyordu.
                    //
                    // Yön ileri: yeni adım alttan yükseliyor, eski yukarı
                    // çekiliyor. Kurulum tek yönlü bir yol ve hareketin yönü
                    // bunu söylüyor; geri dönülemeyen bir adımın iki yöne de
                    // kayabildiğini göstermek yanlış bilgi olurdu.
                    AnimatedContent(
                        targetState = state.stage,
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
                            AuthViewModel.Stage.SETUP -> SetupStep(viewModel, state)
                            AuthViewModel.Stage.RECOVERY_SHOWN -> RecoveryStep(viewModel, state)
                            AuthViewModel.Stage.BIOMETRIC_OFFER -> BiometricStep(viewModel)
                            AuthViewModel.Stage.DONE -> Unit
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kurulumun üç adımını gösteren ray.
 *
 * ### Neden gerekli
 *
 * Kurulum üç ekran sürüyor ve hiçbiri kaçıncı adımda olunduğunu söylemiyordu.
 * Bir parola yöneticisinin ilk kurulumu, kullanıcının uygulamaya en az
 * güvendiği an: ne kadar sürdüğünü bilmemek "bu daha ne kadar devam edecek"
 * sorusunu doğuruyor ve o soru yarıda bırakmaya en yakın yer.
 *
 * ### Neden dolan bir ray, nokta değil
 *
 * Noktalar kaç adım kaldığını söylüyor ama bulunulan adımın **ne kadarının**
 * bittiğini söylemiyor. Ray geçmiş adımları dolu, bulunulanı yarı dolu
 * bırakıyor: üç bilgi (kaç adım, kaçıncısı, ne kadar ilerlendi) tek bir
 * biçimde.
 */
@Composable
private fun SetupRail(stage: AuthViewModel.Stage) {
    val steps = listOf(
        AuthViewModel.Stage.SETUP,
        AuthViewModel.Stage.RECOVERY_SHOWN,
        AuthViewModel.Stage.BIOMETRIC_OFFER
    )
    // DONE listede yok: `indexOf` orada -1 döndürüyor ve ray, kurulum
    // biterken bir kare için ilk adıma geri dönüyordu. Bitmiş kurulum dolu
    // bir ray demek.
    val current = when (stage) {
        AuthViewModel.Stage.DONE -> steps.size
        else -> steps.indexOf(stage).coerceAtLeast(0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { index, _ ->
            // Geçmiş adım dolu, bulunulan yarı dolu, gelecek boş. Yarı dolu
            // olan, adımın içinde bir yerde olunduğunu söylüyor; tam dolu
            // olsaydı bitmiş görünürdü.
            val target = when {
                index < current -> 1f
                index == current -> 0.5f
                else -> 0f
            }
            val fill by animateFloatAsState(target, KasaMotion.large(), label = "rail$index")

            Box(
                Modifier
                    .weight(1f)
                    .height(RAIL_HEIGHT)
                    .clip(RoundedCornerShape(KasaRadius.full))
                    .background(KasaTheme.colors.ink3.copy(alpha = 0.20f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(RoundedCornerShape(KasaRadius.full))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private val RAIL_HEIGHT = 4.dp

@Composable
private fun SetupStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
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
        // Sırayla beliren dört öğe. Aynı anda gelen bir ekran tek bir blok
        // olarak okunuyor ve gözün nereden başlayacağı belli olmuyor; sırayla
        // gelince okuma yönü hareketin kendisinden çıkıyor. Gecikmeler
        // KasaMotion'ın 26 ms'lik adımının katları — burada daha uzun, çünkü
        // bunlar bir menünün öğeleri değil, bir ekranın bölümleri.
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
                stringResource(R.string.onb_sub),
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
            imeAction = ImeAction.Next,
            supportingText = stringResource(R.string.onb_master_hint)
        )

        if (evaluation != null) {
            Spacer(Modifier.height(10.dp))
            WavyProgress(
                progress = evaluation.score,
                color = strengthColor(evaluation.tone),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.gen_entropy, evaluation.entropyBits.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        KasaPasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = stringResource(R.string.onb_master_again),
            revealed = revealed,
            onRevealToggle = { revealed = !revealed },
            isError = state.error != null,
            supportingText = state.error?.let { stringResource(it) }
        )

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

        Spacer(Modifier.height(24.dp))
        KasaButton(
            text = stringResource(if (state.busy) R.string.onb_creating else R.string.onb_create),
            onClick = {
                viewModel.createVault(password.toCharArray(), confirm.toCharArray())
            },
            enabled = !state.busy && password.isNotEmpty() && confirm.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RecoveryStep(viewModel: AuthViewModel, state: AuthViewModel.SetupState) {
    var acknowledged by remember { mutableStateOf(false) }

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
        Spacer(Modifier.height(28.dp))

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
                    .padding(24.dp)
            ) {
                Text(
                    state.recoveryCode.orEmpty(),
                    style = KasaTheme.text.mono,
                    color = KasaTheme.colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        ToggleRow(
            title = stringResource(R.string.onb_recovery_saved),
            checked = acknowledged,
            onCheckedChange = { acknowledged = it },
            first = true
        )
        Spacer(Modifier.height(16.dp))
        KasaButton(
            text = stringResource(R.string.continue_),
            onClick = viewModel::onRecoveryAcknowledged,
            enabled = acknowledged,
            modifier = Modifier.fillMaxWidth()
        )
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
