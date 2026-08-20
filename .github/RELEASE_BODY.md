**Kasa**, cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok,
senkronizasyon yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç
kayıt olduğu ve hangi kategorilerin kullanıldığı dâhil her şey o dosyanın
içinde.

### Gereksinimler

Android 16 (API 36) ve üstü, 64 bit cihaz. `minSdk = targetSdk = 36`.

### Kasa

- Dokuz kayıt türü: giriş, kart, güvenli not, 2FA, kimlik, banka hesabı,
  SSH/API anahtarı, yazılım lisansı, Wi-Fi ağı
- Klasörler ve kurala göre kendini dolduran koleksiyonlar (sızmış, tekrar
  kullanılan, zayıf, bir yıldan eski, 2FA'sız, sık kullanılan)
- 30 günlük çöp kutusu, kayıt başına şifreli ek dosyaları, parola geçmişi
- Kart kayıtları cüzdandaki gibi görünüyor: ödeme kartı oranında yüz, EMV
  yongası, ağın renk ailesi, maskeli numara ve Luhn sağlaması

### Şifreleme

- Anahtar türetme cihaza göre **ölçülüyor** (Argon2id, ~800 ms hedefi);
  ölçülen parametre kasa başlığına yazılıyor, kasa başka cihaza taşınsa da
  doğru açılıyor
- İki şifreleme paketi: AES-256-GCM ve XChaCha20-Poly1305; hangisinin daha
  hızlı olduğu kurulumda ölçülüp seçiliyor
- XChaCha20 kullanılmadan önce kendini sınıyor (RFC vektörü + mühürleme turu +
  kurcalanmış etiketin reddi); biri tutmazsa paket hiç sunulmuyor
- Android Keystore / StrongBox sarmalayıcıları, kasa anahtarı rotasyonu

### Kilit

- Biyometri (Class 3) ya da cihaz ekran kilidi
- 4–6 haneli hızlı PIN; beş hatalı denemede PIN düşüyor, ana parola isteniyor
- Kayıt bazlı ek kilit: "bu kaydı açarken her seferinde doğrula"
- Zorlama parolası: ikinci bir ana parola tuzak kayıtlarla dolu sahte bir kasa
  açıyor. İki bölme aynı dosyada, aynı başlığı paylaşıyor ve 4 KiB'lik bloklara
  tamamlanıyor — dosyaya bakarak zorlama parolasının kurulu olup olmadığı
  anlaşılmıyor
- Bağlama duyarlı kilit süresi: güvenilen Wi-Fi ağında daha uzun. Ağ adı ham
  hâlde saklanmıyor, SHA-256 özetinin ilk 16 baytı tutuluyor ve hiçbir yere
  gönderilmiyor

### Telefon

- Otomatik doldurma servisi. `webDomain` yalnızca tarayıcılardan kabul
  ediliyor: o alanı uygulamanın kendisi dolduruyor, sistem doğrulamıyor
- Passkey (FIDO2/WebAuthn) saklama, sistem çapında Credential Manager
  sağlayıcısı
- Kamera ile 2FA karekod okuma (çevrimdışı), hızlı ayarlar döşemesi, ana ekran
  araç birimi, uygulama kısayolları
- Türkçe ve İngilizce

### Görünüm

- Açık/karanlık tema, AMOLED tam siyah, Material You dinamik renk
- Gezinti çubuğu içeriğin üzerinde ve altındaki içerik kademeli bulanıklaşıyor:
  iki farklı yarıçapta geçiş iç içe geçiyor, keskin bir başlangıç çizgisi yok
- Yatayda gezinti sol yana taşınıyor, dikey alanın tamamı içeriğe kalıyor
- Açılışta kadran dönerek kasayı açıyor
- Sistemde animasyon kapalıysa bütün geçişler anlık

### Ağa çıkan tek şey

Parola sızıntısı denetimi (Have I Been Pwned), k-anonimlik ile: parolanın
SHA-1 özetinin yalnızca ilk beş hanesi gönderiliyor. Kapatılabilir.

### Bilinen sınırlar

Sınırların tamamı [README](https://github.com/Erenkng/G-venli-ifre-y-neticisi#açıkça-söylenen-sınırlar)
içinde yazılı. Kısaca: deneme sayacı dosyasını silen bir saldırgan sayacı
sıfırlar (asıl koruma ana parolanın entropisi ve ölçülmüş Argon2id maliyeti);
cihaz kök erişimine açıksa kasa açıkken bellekten okunabilir; ekran görüntüsü
engeli kök erişimini durdurmaz.
