**Kasa**, cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok,
eşitleme yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç kayıt
olduğu ve hangi kategorilerin kullanıldığı dâhil her şey o dosyanın içinde.

**Gereksinim:** Android 16 (API 36) ve üstü, 64 bit cihaz.

---

## 1.1'de yenilikler

### Otomatik doldurma yeniden yazıldı

- **Satır içi öneriler.** Kayıtlar artık klavyenin üstündeki şeritte çıkıyor;
  ayrı bir menü açılmıyor.
- **Doğrulanmış eşleştirme.** Önceki sürüm paket adının son parçasını kayıt
  adıyla karşılaştırıyordu — `com.kötü.garanti` adlı bir uygulama yayımlayan
  biri "Garanti" kaydının parolasını isteyebilirdi. Yerine üç doğrulanmış güven
  katmanı geldi: kullanıcının elle bağladığı uygulama, sitenin
  `assetlinks.json` dosyasıyla doğrulanan alan adı, tanınan tarayıcıda alan adı
  eşleşmesi.
- **2FA kodunu da dolduruyor.** Tek kullanımlık kod alanı tanındığında TOTP
  kodu üretilip doğrudan yazılıyor.
- **Kaydetme akışı ayırt ediyor.** "Yeni kayıt mı, var olanın parolası mı
  değişti" sorusu artık kullanıcıya sorulmuyor; eşleşen kayıt bulunursa
  güncelleme öneriliyor ve eski parola geçmişe yazılıyor.

### Yeni ekranlar ve akışlar

- **Tür seçimi.** Artı tuşu en çok kullanılan beş türü doğrudan veriyor,
  kalanlar "Diğer" altında.
- **Çöp kutusu kendi ekranında.** Geri al ve kalıcı sil ayrı ayrı.
- **Basılı tutma işlemleri.** Kopyala, hızlı bakış, çoğalt, çöpe at — listeden
  çıkmadan.
- **CSV içe aktarma.** Chrome, Firefox, Bitwarden, 1Password ve LastPass
  dosyaları başlık satırından tanınıyor. Ayrıştırıcı RFC 4180'e uyuyor:
  içinde virgül, tırnak ya da satır sonu geçen parolalar bozulmuyor.
- **Wi-Fi karekodu.** Misafir, parolayı hiç görmeden ağa bağlanıyor.
- **Başlatıcı kısayolları.** Son kullanılan kayıtlar simgeye basılı tutunca
  çıkıyor; kasa kilitlenince siliniyor ve ek kilitli kayıtlar hiç görünmüyor.
- **Kasa özeti.** Güvenlik ekranı yalnızca yanlış olanı gösteriyordu; artık
  kayıt sayısı, 2FA oranı, ortalama parola uzunluğu ve en eski parola da var.
- **Parola yenileme hatırlatıcısı.** Genel "bir yıldan eski" ölçütü yerine
  kayıt başına gün sayısı.
- **Söylenebilir parola.** Telefonda okunup karşıya söylenmesi gereken
  parolalar için üçüncü üretici modu.
- **Kaydı çoğalt.** Aynı sitede ikinci hesap için.

### Görünüm ve hareket

- Açılış işareti animasyonlu: kadranın çemberi çiziliyor, sonra kadran ters
  yönden dönüp yerine oturuyor. Çıkışı da elle yazıldı — sistemin varsayılanı
  işareti olduğu yerde kesiyordu.
- Kilit ekranında parola alanı anında belirmiyor; bulanıklıktan ve dağılan
  noktalardan toplanıyor.
- Bütün animasyonlar tek bir hareket sözlüğünden geliyor. Ayrım alınan yola
  göre: aynı yay sertliği uzun bir yolda kırbaç gibi, kısa bir yolda uyuşuk
  görünüyordu.
- Gezinti çubuğunun kademeli bulanıklığı düzeltildi: bulanıklık ekranın sol üst
  köşesini örnekliyordu (`positionInParent` yerine `positionInRoot`).
- Üreteç kaydırıcısı düzeltildi: 6dp'lik tutamağa uygulanan 4dp'lik çerçeve
  tutamağın tamamını yiyordu.
- Kadranın uçlarına doğru bulanıklaşan kenar parıltısı.
- Arama kutusundan arama ekranına geçiş artık animasyonlu; kutunun yanındaki üç
  nokta gerçek bir sıralama ve yoğunluk menüsü açıyor.
- **Yatay mod kaldırıldı.** İçerik alanı yarıya iniyordu ve bir parola
  yöneticisinde okunacak şey zaten uzun bir liste.

### Kare bütçesi (60 / 120 Hz)

- Model sınıflarına `@Immutable` eklendi. Öncesinde tek bir kaydın değişmesi
  ekrandaki bütün satırların yeniden bestelenmesine yol açıyordu.
- Temel profil (baseline profile): açılış ve ilk kaydırma yolları kurulumda
  önceden derleniyor.
- Kadran çizimi kare başına yeni `Path` ve kutulanmış dizi ayırmıyor; güç
  ölçümü sıralama karşılaştırıcısının içinden çıkarıldı.
- Bulanıklık için tam ekran katman kaydı yalnızca gerçekten çizilecekse
  yapılıyor.
- R8 tam kipi açıldı.

> Bu değişikliklerin hepsi Compose'un belgelenmiş davranışına dayanıyor ama
> gerçek kare süreleri bir cihazda ölçülmedi.

### Düzeltmeler

- Artı tuşuna basıp kapatınca çökme: köşe yarıçapı animasyonu geçici olarak
  negatif değere düşebiliyordu.
- Eksik dizeler, deneysel API imleri ve içe aktarımlar.

---

## Kasa nedir

### Şifreleme

- Anahtar türetme cihaza göre **ölçülüyor** (Argon2id, ~800 ms hedefi); ölçülen
  parametre kasa başlığına yazılıyor, kasa başka cihaza taşınsa da doğru açılıyor
- İki şifreleme paketi: AES-256-GCM ve XChaCha20-Poly1305; hangisinin daha hızlı
  olduğu kurulumda ölçülüp seçiliyor
- XChaCha20 kullanılmadan önce kendini sınıyor (RFC vektörü + mühürleme turu +
  kurcalanmış etiketin reddi); biri tutmazsa paket hiç sunulmuyor
- Android Keystore / StrongBox sarmalayıcıları, kasa anahtarı rotasyonu

### Kilit

- Biyometri (Class 3) ya da cihaz ekran kilidi
- 4–6 haneli hızlı PIN; beş hatalı denemede PIN düşüyor, ana parola isteniyor
- Kayıt bazlı ek kilit: "bu kaydı açarken her seferinde doğrula"
- Zorlama parolası: ikinci bir ana parola tuzak kayıtlarla dolu sahte bir kasa
  açıyor. İki bölme aynı dosyada, aynı başlığı paylaşıyor ve 4 KiB'lik bloklara
  tamamlanıyor — dosyaya bakarak kurulu olup olmadığı anlaşılmıyor
- Bağlama duyarlı kilit süresi: güvenilen Wi-Fi ağında daha uzun. Ağ adı ham
  hâlde saklanmıyor, SHA-256 özetinin ilk 16 baytı tutuluyor

### Kasa

- Dokuz kayıt türü: giriş, kart, güvenli not, 2FA, kimlik, banka hesabı,
  SSH/API anahtarı, yazılım lisansı, Wi-Fi ağı
- Klasörler ve kurala göre kendini dolduran koleksiyonlar
- 30 günlük çöp kutusu, kayıt başına şifreli ek dosyaları, parola geçmişi
- Kart kayıtları cüzdandaki gibi görünüyor: ödeme kartı oranında yüz, EMV
  yongası, ağın renk ailesi, maskeli numara ve Luhn sağlaması

### Ağa çıkan tek şey

Parola sızıntısı denetimi (Have I Been Pwned), k-anonimlik ile: parolanın SHA-1
özetinin yalnızca ilk beş hanesi gönderiliyor. Kapatılabilir.

### Bilinen sınırlar

Sınırların tamamı [README](https://github.com/Erenkng/G-venli-ifre-y-neticisi#açıkça-söylenen-sınırlar)
içinde yazılı. Kısaca: deneme sayacı dosyasını silen bir saldırgan sayacı
sıfırlar (asıl koruma ana parolanın entropisi ve ölçülmüş Argon2id maliyeti);
cihaz kök erişimine açıksa kasa açıkken bellekten okunabilir; ekran görüntüsü
engeli kök erişimini durdurmaz.
