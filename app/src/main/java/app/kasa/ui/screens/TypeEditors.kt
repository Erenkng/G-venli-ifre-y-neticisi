package app.kasa.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kasa.R
import app.kasa.data.model.CardBrand
import app.kasa.data.model.VaultItem
import app.kasa.ui.components.BrandMark
import app.kasa.ui.components.ButtonTone
import app.kasa.ui.components.CardFace
import app.kasa.ui.components.ExpiryTransformation
import app.kasa.ui.components.GroupedDigitsTransformation
import app.kasa.ui.components.KasaButton
import app.kasa.ui.components.KasaTextField
import app.kasa.ui.components.SiteLogo
import app.kasa.ui.components.TotpDisplay
import app.kasa.ui.components.digitsOnly
import app.kasa.ui.theme.KasaRadius
import app.kasa.ui.theme.KasaTheme
import java.util.Calendar

/**
 * Türe özel düzenleyici başlıkları ve alanları.
 *
 * ### Neden tür başına ayrı arayüz
 *
 * Önceki düzenleyici tek bir formdu: üstte tür seçen bir düğme grubu, altında
 * o türün alanları. İşliyordu ama iki şeyi birden kaybediyordu.
 *
 * Birincisi **tanıma**. Kart eklerken ekranda kart yoktu, üç metin kutusu
 * vardı; kullanıcı yazdığı numaranın doğru kartı ürettiğini ancak kaydedip
 * ayrıntıya girince görüyordu. İkincisi **doğru veri**: son kullanma tarihi
 * alanı serbest metindi ve kullanıcı `12/26`, `1226`, `Aralık 2026` yazıp
 * geçiyordu; üçü de kaydediliyordu, üçü de farklı görünüyordu.
 *
 * Şimdi her tür kendi ekranıyla geliyor: kart yazarken kart görünüyor, 2FA
 * yazarken kod dönüyor, kimlik yazarken belge duruyor. Yazılan şey anında
 * göründüğü için yanlış veri kaydedilmeden önce fark ediliyor.
 *
 * ### Tür artık düzenleyicide değişmiyor
 *
 * Eskiden üstteki düğme grubuyla kaydın türü değiştirilebiliyordu. Kulağa
 * esnek geliyor ama pratikte her tür farklı alanlar kullanıyor: kart
 * numarasını `cardNumber`'a yazmış bir kaydı 2FA'ya çevirmek, girilen verinin
 * hiçbirini taşımadan boş bir form gösteriyordu. Tür artık ekleme anında,
 * [TypePickerSheet] üzerinden seçiliyor — yani sorunun sorulduğu tek yer,
 * cevabın hâlâ ücretsiz olduğu yer.
 */

/**
 * Giriş kaydının başlığı: sitenin işareti ve adresi.
 *
 * Adres yazıldıkça işaret değişiyor. Bu bir süs değil, geri bildirim:
 * kullanıcı `garantibbva.com.tr` yerine `garantibvva.com.tr` yazdığında
 * işaret bilinen renge dönmüyor ve yazım hatası kaydedilmeden görülüyor.
 */
@Composable
fun LoginHero(item: VaultItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SiteLogo(
            url = item.url,
            fallbackText = item.initial,
            size = 56.dp,
            cornerRadius = 18.dp
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.name.ifBlank { stringResource(R.string.editor_new_title) },
                style = MaterialTheme.typography.titleMedium,
                color = KasaTheme.colors.ink,
                maxLines = 1
            )
            Text(
                item.url.ifBlank { stringResource(R.string.editor_url_hint) },
                style = MaterialTheme.typography.bodySmall,
                color = KasaTheme.colors.ink3,
                maxLines = 1
            )
        }
    }
}

/**
 * Kartın canlı önizlemesi ve alanları.
 *
 * ### Önizleme neden açık numarayla
 *
 * Ayrıntı sayfasında numara maskeli duruyor ve açmak ayrı bir dokunuş. Burada
 * maskelemek anlamsız: kullanıcı numarayı zaten o anda kendi yazıyor,
 * maskelemek yalnızca yazdığını doğrulamasını engellerdi.
 *
 * ### Neden ayrı bir "biçimlendirilmiş değer" tutulmuyor
 *
 * Kaydedilen değer yalnızca rakam; boşluk ve eğik çizgi çizim sırasında
 * ekleniyor ([GroupedDigitsTransformation]). Öbeklenmiş metni kaydetmek, Luhn
 * sağlamasını, aramayı ve dışa aktarmayı ayrıca temizlemeye zorlardı.
 */
@Composable
fun CardEditorFields(
    item: VaultItem,
    onChange: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val brand = remember(item.cardNumber) { CardBrand.detect(item.cardNumber) }
    val luhn = remember(item.cardNumber) { CardBrand.luhnValid(item.cardNumber) }
    val now = remember { Calendar.getInstance() }
    val expired = remember(item.cardExpiry) {
        app.kasa.ui.components.expiryExpired(
            item.cardExpiry,
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1
        )
    }

    Column(modifier) {
        // Yazdıkça değişen kart. Numaranın ilk iki hanesi girildiği anda ağ
        // tanınıyor ve kartın rengiyle işareti yerine oturuyor.
        CardFace(item = item, revealed = true, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        KasaTextField(
            value = item.cardNumber,
            onValueChange = { onChange(item.copy(cardNumber = digitsOnly(it, MAX_CARD_DIGITS))) },
            label = stringResource(R.string.field_card_number),
            keyboardType = KeyboardType.Number,
            textStyle = KasaTheme.text.mono,
            visualTransformation = remember(brand) { GroupedDigitsTransformation(brand.grouping) },
            isError = luhn == false,
            supportingText = if (luhn == false) stringResource(R.string.card_luhn_warning) else null,
            trailing = { BrandMark(brand = brand, height = 22.dp, wordColor = KasaTheme.colors.ink2) }
        )

        Spacer(Modifier.height(8.dp))
        KasaTextField(
            value = item.cardHolder,
            onValueChange = { onChange(item.copy(cardHolder = it)) },
            label = stringResource(R.string.field_card_holder)
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                KasaTextField(
                    value = item.cardExpiry,
                    onValueChange = { onChange(item.copy(cardExpiry = digitsOnly(it, 4))) },
                    label = stringResource(R.string.field_card_expiry),
                    placeholder = stringResource(R.string.editor_expiry_hint),
                    keyboardType = KeyboardType.Number,
                    textStyle = KasaTheme.text.mono,
                    // Eğik çizgiyi kullanıcı yazmıyor; iki hane girildiği anda
                    // araya kendisi giriyor.
                    visualTransformation = ExpiryTransformation,
                    isError = expired == true,
                    supportingText = if (expired == true) stringResource(R.string.card_expired) else null
                )
            }
            Box(Modifier.weight(1f)) {
                KasaTextField(
                    value = item.cardCvv,
                    // Hane sayısı ağa bağlı: Amex 4, ötekiler 3. Sabit üç hane,
                    // Amex kullanıcısının son hanesini sessizce yutuyordu.
                    onValueChange = { onChange(item.copy(cardCvv = digitsOnly(it, brand.cvvLength))) },
                    label = stringResource(R.string.field_card_cvv),
                    keyboardType = KeyboardType.NumberPassword,
                    textStyle = KasaTheme.text.mono
                )
            }
        }
    }
}

/**
 * 2FA kaydının kendi ekranı.
 *
 * ### Neden bambaşka
 *
 * 2FA kaydının değeri parolası değil, **şu anda üretilen kod**. Onu bir metin
 * alanının altına sıkıştırmak, kaydın var oluş sebebini forma gömmek olurdu.
 * Burada kod en üstte, büyük ve dönen sayaçla duruyor; anahtar alanı altta,
 * çünkü anahtar bir kez giriliyor ve bir daha bakılmıyor.
 *
 * ### Karekod öncelikli
 *
 * Base32 anahtarı elle yazmak hem yorucu hem hataya açık: tek harf yanlışsa
 * kod üretiliyor ama hiçbir zaman kabul edilmiyor ve kullanıcı hatanın nerede
 * olduğunu anlayamıyor. Bu yüzden karekod okutma birincil eylem; elle giriş
 * onun altında duran seçenek.
 */
@Composable
fun OtpHero(
    item: VaultItem,
    onScanQr: ((String) -> Unit) -> Unit,
    onSecretScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSecret = item.totpSecret.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = hasSecret, enter = fadeIn(), exit = fadeOut()) {
            TotpDisplay(
                secret = item.totpSecret,
                digits = item.totpDigits,
                period = item.totpPeriod,
                algorithm = item.totpAlgorithm
            )
        }
        if (!hasSecret) {
            Text(
                stringResource(R.string.otp_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = KasaTheme.colors.ink2,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        KasaButton(
            text = stringResource(R.string.editor_scan_qr),
            onClick = { onScanQr(onSecretScanned) },
            tone = if (hasSecret) ButtonTone.TONAL else ButtonTone.FILLED,
            leading = {
                Icon(
                    Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Kimlik kaydının başlığı: bir belgenin kendisi.
 *
 * ### Neden belge biçiminde
 *
 * Kimlik bilgileri listede birbirine benziyor — hepsi rakam dizisi. Kullanıcı
 * "TC kimlik mi, pasaport mu, ehliyet mi" ayrımını alan etiketlerini okuyarak
 * yapmak zorunda kalıyordu. Belge görünümü ayrımı okumadan yapıyor: solda
 * fotoğraf yeri, sağda ad, altta seri numarası — herkesin elindeki kimliğin
 * yerleşimi.
 *
 * Fotoğraf **yok** ve olmayacak. Kimlik fotoğrafını kasaya koymak, telefon
 * ele geçtiğinde kimlik hırsızlığını doğrudan mümkün kılan tek parçayı da
 * oraya koymak demek. Yerinde duran şey bir yer tutucu: belgeyi belge yapan
 * yerleşimi kuruyor, içeriği taşımıyor.
 */
@Composable
fun IdentityHero(item: VaultItem, modifier: Modifier = Modifier) {
    val name = item.extras["full_name"].orEmpty().ifBlank { item.name }
    val serial = item.extras["serial_no"].orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KasaRadius.l))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Fotoğraf yeri: belgenin yerleşimini kuruyor, içerik taşımıyor.
            Box(
                Modifier
                    .size(width = 56.dp, height = 70.dp)
                    .clip(RoundedCornerShape(KasaRadius.s))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Badge,
                    contentDescription = null,
                    tint = KasaTheme.colors.ink3.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.f_full_name).uppercase(),
                    style = KasaTheme.text.fieldLabel,
                    color = KasaTheme.colors.ink3
                )
                Text(
                    name.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = KasaTheme.colors.ink,
                    maxLines = 2
                )
                if (serial.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        serial,
                        style = KasaTheme.text.mono,
                        color = KasaTheme.colors.ink2,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // Belgenin alt şeridi: gerçek kimliklerdeki makine okunur alanın
        // soyutlaması. Hiçbir veri taşımıyor, yalnızca biçimi tamamlıyor.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(MRZ_BLOCKS) { index ->
                Box(
                    Modifier
                        .weight(if (index % 3 == 0) 2f else 1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(KasaRadius.full))
                        .background(KasaTheme.colors.ink3.copy(alpha = 0.16f))
                )
            }
        }
    }
}

/** Kart numarasının en uzun hâli (UnionPay). */
private const val MAX_CARD_DIGITS = 19
private const val MRZ_BLOCKS = 9
