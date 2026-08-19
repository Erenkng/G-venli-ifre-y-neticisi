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

> **Not:** Bu depo, Android SDK'sına erişimi olmayan bir ortamda yazıldı;
> Gradle derlemesi henüz çalıştırılmadı. Bağımlılık sürümleri ve API
> kullanımları elle doğrulandı, ancak ilk `./gradlew assembleDebug`
> çalıştırmasında küçük düzeltmeler gerekebilir.

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
| Anahtar türetme | **Argon2id** 64 MiB / 3 tur / 2 şerit | Bellek-zor; GPU ve ASIC ile paralel deneme pahalı. Yerel kitaplık yüklenemezse PBKDF2-HMAC-SHA512 / 600.000 tura düşer ve bu, dosya başlığına yazılır. |
| Şifreleme | **AES-256-GCM**, 96 bit rastgele nonce | Bütünlük şifrelemenin içinde. Yanlış ana parola ayrı bir doğrulayıcı alan olmadan, etiket hatasıyla anlaşılır — çevrimdışı saldırgana bedava sağlama sunulmaz. |
| AAD | Dosya başlığı (KDF parametreleri dâhil) | Saldırgan Argon2 maliyetini düşürecek şekilde başlığı kurcalayamaz. |
| Biyometrik kilit | Keystore anahtarı, `setUserAuthenticationRequired(true)`, **StrongBox** varsa donanımda | Parmak izi bir bayrağı `true` yapmaz; doğrudan Keystore anahtarının kullanımını açar. Yeni parmak izi kaydedilirse anahtar otomatik geçersizleşir. |
| Bellek | `SecretBytes` — silinebilir bayt tamponu | Anahtarlar `String` olarak tutulmaz; iş bitince sıfırlanır. |
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
- Material You dinamik renk (Android 12+) — güç renkleri sabit kalır
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
