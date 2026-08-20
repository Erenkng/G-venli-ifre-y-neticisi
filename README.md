# Kasa — Android parola yöneticisi

`kasasifreyoneticisi.html` tasarımının çalışan Android uygulaması.
Kotlin + Jetpack Compose, Material 3 Expressive, tamamen çevrimdışı ve
uçtan uca şifreli bir yerel kasa.

---

## Derleme

```bash
# Android Studio ile: klasörü aç, Gradle senkronizasyonunu bekle, Run.
# Komut satırıyla:
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/
./gradlew installDebug           # bağlı cihaza kur
./gradlew assembleRelease        # R8 + kaynak küçültme ile sürüm derlemesi
```

Gereksinimler: JDK 17, Android SDK 36.

**Kasa yalnızca Android 16 (API 36) ve üstünde çalışır.** `minSdk = targetSdk =
compileSdk = 36`. Android 17'de çalışmak için yükseltme gerekmez — Android
sürümleri geriye dönük uyumludur; `targetSdk` yalnızca hangi davranış
değişikliklerine katıldığını belirler. API 37 SDK'sı kurulduğunda
`app/build.gradle.kts` içindeki iki sayıyı değiştirmek yeterli, çünkü kodda
başka hiçbir yerde sürüm dalı yok.

Tek hedefe inmenin somut karşılığı: Keystore, titreşim, pano, döşeme,
bildirim ve dinamik renk yollarındaki tüm `Build.VERSION` kontrolleri
kaldırıldı. Argon2Kt 1.6.0'ın dört ABI'sinin de 16 KB sayfa hizalı olduğu
ELF program başlıklarından doğrulandı; 32 bit ABI'ler paketlenmiyor.

### Sürekli tümleştirme

Depo yazılırken kullanılan ortamda Android SDK indirmesi engelliydi, bu yüzden
derleme doğrulaması **GitHub Actions**'a taşındı: `.github/workflows/android.yml`
her itmede `assembleDebug` ve `lintDebug` çalıştırıp APK'yı yapı çıktısı olarak
yüklüyor. Derleme başarısız olduğunda Kotlin hataları günlüğün sonunda ayrıca
özetleniyor — Gradle'ın yığın izi altında kaybolmasınlar diye.

---

## Güvenlik mimarisi

Kasanın tamamı **tek bir şifreli dosyadır**. Kayıt adları, kaç kayıt olduğu,
kategoriler — hiçbiri diskte açıkta durmaz. SQLite kullanılmamasının nedeni
budur: bir veritabanı, satır sayısını ve şema meta verisini gizleyemez.

```
ana parola ──Argon2id(64 MiB, t=3, p=2)──┐
kurtarma anahtarı (120 bit) ──Argon2id──┤──► AES-256-GCM ile sarmalanmış
biyometri ──Keystore/StrongBox──────────┘    KASA ANAHTARI (32 bayt, rastgele)
                                                      │
                                                      ▼
                                        AES-256-GCM(kasa JSON'u)
```

| Katman | Seçim | Gerekçe |
|---|---|---|
| Anahtar türetme | **Argon2id**, parametreleri cihazda **ölçülerek** bulunur | Sabit 64 MiB / 3 tur amiral gemisinde gereksiz zayıf, dört yıllık orta segment telefonda kullanılamaz yavaştı. Kurulumda ~800 ms hedefine göre ölçülüp bulunan değer kasa başlığına yazılır; kasa başka cihaza taşındığında orada yeniden ölçülmez. Yerel kitaplık yüklenemezse PBKDF2-HMAC-SHA512'ye düşer ve bu da başlığa yazılır. |
| Şifreleme paketi | **AES-256-GCM** ya da **XChaCha20-Poly1305**, ölçümle seçilir | AES komut kümesi olan cihazlarda GCM açık ara önde; olmayanlarda ChaCha öne geçiyor ve yazılımda tasarımı gereği sabit zamanlı. Seçim dosya başlığındaki paket kimliğine yazılır, böylece varsayılan değişse bile eski kasa açılmayı sürdürür. |
| Şifreleme | **AES-256-GCM**, 96 bit rastgele nonce | Bütünlük şifrelemenin içinde. Yanlış ana parola ayrı bir doğrulayıcı alan olmadan, etiket hatasıyla anlaşılır — çevrimdışı saldırgana bedava sağlama sunulmaz. |
| AAD | Dosya başlığı (KDF parametreleri dâhil) | Saldırgan Argon2 maliyetini düşürecek şekilde başlığı kurcalayamaz. |
| Biyometrik kilit | Keystore anahtarı, `setUserAuthenticationRequired(true)`, **StrongBox** varsa donanımda | Parmak izi bir bayrağı `true` yapmaz; doğrudan Keystore anahtarının kullanımını açar. Yeni parmak izi kaydedilirse anahtar otomatik geçersizleşir. |
| Bellek | `SecretBytes` (anahtarlar) ve `SecretText` (parolalar) — silinebilir tamponlar | Kasa kilitlendiği anda uygulamanın elindeki hiçbir nesnede okunabilir parola kalmaz. JSON çözücüsünün ürettiği geçici `String` kaçınılmaz; kazanç "hiç `String` olmasın" değil, **kalıcı kopya olmasın**. |
| Kasa anahtarı rotasyonu | Yeni anahtar, kasa baştan şifrelenir, üç sarmalayıcı da yeniden yazılır | Ana parola değişimi yalnızca sarmalayıcıyı yeniler; eski anahtarı ele geçirmiş biri eski kopyayı sonsuza dek açardı. Rotasyon önce `.new` dosyaları, sonra işaret dosyası, en son devralma sırasıyla yapılır; yarıda kesilirse açılışta tamamlanır. |
| Passkey | FIDO2/WebAuthn kimlik bilgileri kasada; Kasa bir Credential Manager sağlayıcısı | Özel anahtar Keystore'da değil kasada: Keystore'a bağlı bir passkey telefonla birlikte kaybolurdu ve parola yöneticisinin varlık sebebi tam olarak bunu engellemek. `none` attestation, AAGUID sıfır — yazılım kimlik doğrulayıcısı kendisi hakkında doğrulanabilir bir şey söyleyemez, sahte bir donanım kimliği üretmek yerine hiçbir şey iddia etmiyoruz. |
| Hızlı PIN | 4-6 hane; kasa anahtarı önce PIN'den türetilen anahtarla, sonra Keystore anahtarıyla sarmalanır | Dosyayı kopyalayan biri dış katmanı açamadığı için çevrimdışı deneme mümkün değil; cihaz üzerinde beş yanlış denemede PIN düşer ve ana parola istenir. 20 karakterlik ana parolayı günde on kez yazdırmak, pratikte ana parolayı zayıflatan kuraldır. |
| Cihaz kimlik bilgisi | İsteğe bağlı: biyometri **ya da** telefonun ekran kilidi | Parmak izi okuyucusu bozuk cihazda tek seçenek "her açılışta ana parola" olmasın diye. Eşiği bilerek düşürüyor, bu yüzden varsayılan değil. |
| Kayıt bazlı ek kilit | İşaretli kayıtta alanlar doğrulama gelene kadar hiç çizilmez | Banka kaydının Spotify kaydıyla aynı eşiği paylaşması için sebep yok. Maskeleyip "göster"e basılabilir bırakmak, korunanı bir dokunuş uzağa koymak olurdu. |
| Zorlama parolası | İkinci bir ana parola yem kasayı açar; iki bölme aynı dosyada | Kurulu olup olmadığı dosyaya bakılarak anlaşılamaz: sarmalayıcı her kasada aynı boyutta bulunur, yem bölmesi her kasada doludur ve bölmeler 4 KiB'lik bloklara tamamlanır. İki sarmalayıcı eş zamanlı denenir; sırayla denemek yem parolanın iki kat uzun sürmesi demekti. |
| Bağlama duyarlı kilit | Güvenilen Wi-Fi'da daha uzun kilit gecikmesi | Ağ adı ham hâlde saklanmaz; yalnızca `SHA-256(ssid‖bssid)` özetinin ilk 16 baytı, yalnızca cihazda. Konum izni gerektirdiği için tamamen isteğe bağlı ve izin yoksa sessizce devre dışı. |
| Deneme sayacı | Cihaz anahtarıyla şifreli, üstel bekleme (5 sn → 30 dk) | İsteğe bağlı olarak N hatalı denemeden sonra kasayı kalıcı siler. |
| Yedekleme | `allowBackup=false`, buluta ve cihaz aktarımına kapalı | Taşınan bir kopya zaten açılamaz; yalnızca saldırı yüzeyi büyütürdü. |
| Ağ | Tek uç nokta, sistem CA'ları, düz metin HTTP yasak | Kullanıcı tarafından kurulan sertifikalar (araya girme vekilleri) güvenilmez. |
| Sızıntı denetimi | HIBP **k-anonimlik**: yalnızca SHA-1 özetinin ilk 5 hanesi | Parolanın kendisi asla cihazdan çıkmaz. `Add-Padding` ile yanıt uzunluğu da gizlenir. |
| Ekran | `FLAG_SECURE` (varsayılan açık) | Ekran görüntüsü, son uygulamalar önizlemesi ve ekran yansıtma kapalı. |
| Pano | Hassas işaretleme + alarmla otomatik temizleme | Android 13+ pano önizlemesinde içeriği gizler; süre dolunca uygulama öldürülmüş olsa bile alarm panoyu siler. |
| Otomatik kilit | Ekran kapanınca **hemen**, arka planda süreyle | Kilitlendiğinde anahtar bellekten silinir, pano temizlenir. |
| Otomatik doldurma | Kilitliyse doldurma **yok** | "Kolaylık olsun diye kasayı açık tutalım" tuzağına düşmez; önce kimlik doğrulama akışı çalışır. |
| Araç birimi | Gizli veri göstermez | Ana ekran araç birimleri kilit ekranında görünebilir; oraya kod basmak otomatik kilidi anlamsız kılardı. |
| Bildirimler | `VISIBILITY_SECRET` | Hangi hesabın sızdığı da gizli bir bilgidir. |
| Sürüm derlemesi | R8 + tüm `Log` çağrıları çıkarılır, `dependenciesInfo` kapalı | Üretimde kayıt sızıntısı ve APK meta verisi yok. |

### Bilinçli olarak yapılmayanlar

- **Bulut eşitleme yok.** Sunucu tarafı, anahtar yönetimi ve hesap kurtarma
  akışı olmadan eşitleme, güvenliği düşüren bir özelliktir. Taşıma için
  şifreli `.kasa` dosyası dışa/içe aktarımı var.
- **Root tespiti bir güvenlik sınırı değildir.** Uygulama root'lu cihazda
  kapanmaz; bir kez uyarır ve çalışmaya devam eder. Kararlı bir saldırgan
  tespiti atlatır; amaç kullanıcıyı bilgilendirmek.
- **Güvenli silme flash bellekte tam garanti değildir.** Dosyanın üzerine
  rastgele veri yazılır ama asıl güvence, dosyanın zaten şifreli olması ve
  anahtarının Keystore'dan silinmesidir.

---

## Özellikler

**Kasa**
- Dokuz kayıt türü: giriş, kart, güvenli not, 2FA, kimlik, banka hesabı,
  SSH/API anahtarı, yazılım lisansı, Wi-Fi ağı — yeni türler `CategorySchema`
  içinde veri olarak tanımlı, arayüz kodu gerektirmiyor
- Klasörler ve kurala göre kendini dolduran koleksiyonlar (sızmış, tekrar
  kullanılan, zayıf, bir yıldan eski, 2FA'sız, sık kullanılan)
- 30 günlük çöp kutusu; süresi dolan kayıtlar kendiliğinden siliniyor
- Kayıt başına şifreli ek dosyaları (her ek kendi anahtarıyla, ayrı dosyada)
- Kategori süzgeci, tam ekran arama, son kullanılanlar şeridi, sık kullanılanlar
- Kayıt ayrıntısı: maskeli parola, ayrı göster/kopyala eylemleri, güç göstergesi
- Parola geçmişi (son 10 parola), özel alanlar, etiketler, silmeyi geri alma

**Üretici**
- Parola ve Türkçe sözcük dizisi (517 ASCII sözcük, sözcük başına ~9 bit)
- 8–64 karakter, büyük harf / rakam / sembol / karışan harfleri ele
- Gerçek entropi hesabı ve çevrimdışı kırma süresi tahmini
- Tüm rastgelelik `SecureRandom` + modulo sapması olmayan reddetme örneklemesi

**Güvenlik merkezi**
- Kasa puanı: ortalama güç eksi yapısal cezalar (sızıntı > tekrar > zayıflık > yaş > 2FA eksikliği)
- Bulgular: sızmış, tekrar kullanılmış, zayıf, bir yıldan eski, 2FA'sız
- Haftalık arka plan taraması ve bildirim (yalnızca kasa açıkken çalışır)

**Telefon özellikleri**
- Biyometrik kilit (Class 3), Android Keystore + StrongBox
- Otomatik doldurma servisi: `autofillHints` + `InputType` + Türkçe/İngilizce anahtar sözcük eşleştirme, kaydetme akışı
- Kamera ile 2FA karekod okuma (ZXing, çevrimdışı)
- Hızlı ayarlar döşemesi (tek dokunuşla kilitle), ana ekran araç birimi, uygulama kısayolları
- Dokunsal geri bildirim desenleri, kenardan kenara düzen, tahminli geri
- Türkçe/İngilizce, uygulama içi dil yapılandırması

**Görünüm**
- Açık ve karanlık tema, sistem takibi, AMOLED tam siyah
- Material You dinamik renk — güç renkleri sabit kalır (zayıf her zaman kırmızı)
- Roboto Flex değişken yazı tipi (wght/wdth/GRAD eksenleri)
- Tasarımın imza hareketleri: güce göre biçim değiştiren kadran, dalgalı ilerleme
  çubukları, basınca sıkışan liste satırları, komşusuna tepki veren düğme grubu

---

## Mimari

```
app/src/main/java/app/kasa/
├── core/
│   ├── crypto/      Crypto, Kdf, KeystoreKeys, SecretBytes, Base32, RecoveryKey
│   ├── security/    AutoLocker, SecureClipboard, DeviceIntegrity
│   └── util/        Totp, PasswordStrength, PasswordGenerator, Haptics
├── data/
│   ├── VaultStore     dosya biçimi, sarmalayıcılar, dışa/içe aktarma
│   ├── SettingsStore  DataStore tercihleri (gizli veri yok)
│   ├── model/         VaultItem, VaultData
│   ├── net/           BreachChecker (HIBP k-anonimlik)
│   └── repo/          VaultRepository (tek doğruluk kaynağı), SecurityAnalyzer
├── ui/
│   ├── theme/       renkler, Roboto Flex tipografi, şekiller
│   ├── components/  tasarımdan birebir bileşenler
│   ├── screens/     kasa, üret, güvenlik, ayarlar, kurulum, kilit, düzenleyici, QR
│   └── *ViewModel   ekran durumları
├── autofill/        AutofillService + yapı ayrıştırıcı + kilit açma penceresi
├── widget/          ana ekran araç birimi
├── tile/            hızlı ayarlar döşemesi
└── work/            haftalık güvenlik taraması
```

Bağımlılık enjeksiyonu için Hilt yerine elle kurulan `AppContainer` kullanılıyor:
bu boyutta bir uygulamada ek açıklama işleyicisi derleme süresini ikiye katlar ve
kasa anahtarını tutan deponun ömrünü tek bir yerde görebilmek güvenlik açısından
daha değerli.

---

## Dosya biçimleri

| Dosya | İçerik |
|---|---|
| `kasa/att/<id>.bin` | `KASAATT1` + AES-GCM(ek dosyası), eke özel anahtarla |
| `kasa/master.key` | `KASAMST1` + KDF parametreleri + AES-GCM(kasa anahtarı) |
| `kasa/recovery.key` | `KASAREC1` + KDF parametreleri + AES-GCM(kasa anahtarı) |
| `kasa/biometric.key` | `KASABIO1` + IV + Keystore-GCM(kasa anahtarı) |
| `kasa/vault.bin` | `KASAVLT1` + AES-GCM(kasa JSON'u) |
| `kasa/attempts.bin` | Cihaz anahtarıyla şifreli deneme sayacı |
| Dışa aktarma `.kasa` | `KASAEXP1` + Argon2id(128 MiB) + AES-GCM — ayrı dışa aktarma parolasıyla |

Ana parola değiştiğinde kasa yeniden şifrelenmez; yalnızca sarmalayıcı yenilenir.
Bu hem hızlıdır hem de değişiklik sırasında veri kaybı penceresi bırakmaz.

### Şema göçü

Kasa JSON'u bir `schema` sürümü taşır ve `VaultMigrations` adım adım yükseltir.
İki yön de kapalı:

- **Eski dosya, yeni uygulama** → zincir sırayla çalışır.
- **Yeni dosya, eski uygulama** → açılmayı reddeder. Bu ikincisi daha önemli:
  `ignoreUnknownKeys` açık olduğu için eski sürüm yeni alanları tanımaz, okur
  gibi yapar ve ilk kaydetmede kalıcı olarak siler.

Aynı gerekçeyle, kasa **herhangi bir nedenle** okunamıyorsa kilit hiç açılmaz.
Boş bir kasayla açılıp ilk yazmada gerçek kayıtların üstüne binmek, okuma
hatasının verebileceği en pahalı sonuçtu.
