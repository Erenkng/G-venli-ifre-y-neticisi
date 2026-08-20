**Kasa**, cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok,
eşitleme yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç kayıt
olduğu ve hangi kategorilerin kullanıldığı dâhil her şey o dosyanın içinde.

Android 16 (API 36) ve üstü, 64 bit cihaz.

---

## 1.1'de yenilikler

### Otomatik doldurma yeniden yazıldı

- **Klavye şeridinde satır içi öneri.** Menü açmadan, yazarken doğrudan
  klavyenin üstünde.
- **Doğrulanmış eşleştirme.** Önceki sürümde eşleşme, paket adının son
  parçasının kayıt adına benzemesine bakıyordu: `com.kötü.garanti` adlı bir
  uygulama yayımlayan biri "Garanti" kaydının parolasını isteyebilirdi. Yerine
  üç doğrulanmış katman geldi — kullanıcının elle bağladığı uygulama, sitenin
  `assetlinks.json` dosyasıyla doğrulanan alan adı, tanınan tarayıcıda alan adı
  eşleşmesi.
- **2FA kodunu da doldurma.** Tek kullanımlık kod alanı tanınıyor ve kasadaki
  TOTP anahtarından üretilen kod oraya yazılıyor.
- **Kaydetme akışı ayırt ediyor.** Var olan bir kaydın parolası mı değişti,
  yoksa yeni bir hesap mı açıldı — ikisi artık ayrı sorular.

### Yeni özellikler

- **CSV içe aktarma** — Chrome, Firefox, Bitwarden, 1Password, LastPass. Biçim
  başlık satırından kendiliğinden tanınıyor. Ayrıştırıcı RFC 4180'e uyuyor:
  içinde virgül ya da satır sonu geçen parolalar bozulmuyor.
- **Wi-Fi karekodu** — misafir parolayı hiç görmeden ağa bağlanıyor. Karekod
  yalnızca bellekte üretiliyor, diske yazılmıyor.
- **Çöp kutusu kendi ekranında.** Geri al ve kalıcı sil ayrı ayrı.
- **Basılı tutunca hızlı bakış** ve satır eylemleri: kopyala, çoğalt, çöpe at.
- **Kayıt çoğaltma** — aynı sitede ikinci hesap için.
- **Parola yenileme hatırlatıcısı** — kayıt başına gün sayısı. Genel "bir
  yıldan eski" ölçütü her kayda uymuyordu.
- **Söylenebilir parola** üretici modu: telefonda okunup karşıya söylenmesi
  gereken parolalar için. Entropi dürüstçe hesaplanıyor, rastgele parolanınkiyle
  aynıymış gibi gösterilmiyor.
- **Kasa özeti** güvenlik ekranında: kayıt sayısı, 2FA oranı, ortalama parola
  uzunluğu, en eski parolanın yaşı. Önceden yalnızca yanlış olan gösteriliyordu.
- **Son kullanılan kayıtlar için başlatıcı kısayolları.** Kasa kilitlendiğinde
  siliniyorlar ve ek kilitli kayıtlar hiç kısayol almıyor — kısayolun adı
  başlatıcıda görünen bir bilgi.
- **Ekleme akışı kayıt türüne göre**: en sık kullanılan beş tür artı düğmesinde,
  kalanlar "Diğer" listesinde.

### Görünüm ve hareket

- Açılış animasyonu uygulamaya elle yazılmış bir çıkışla bağlanıyor; parola
  alanı anında belirmek yerine bulanıklıktan ve dağılan noktalardan toplanıyor.
- Bütün animasyonlar tek bir hareket sözlüğünden geliyor (`Motion.kt`): beş
  belirteç, alınan yola göre ayrılmış. Öncesinde beş ayrı sönümleme oranı ve
  altı ayrı süre vardı ve hiçbiri ötekine bakılarak seçilmemişti.
- Gezinti çubuğunun altındaki kademeli bulanıklık düzeltildi: konum yanlış
  ölçüldüğü için ekranın sol üst köşesi bulanıklaştırılıyordu.
- Üretici kaydırıcısı yeniden yazıldı — 4dp'lik çerçeve 6dp'lik tutamağı
  bütünüyle yiyordu.
- Arama kutucuğunun üç noktası artık gerçek bir menü: sıralama ve satır
  yoğunluğu.
- **Yatay mod kaldırıldı.** Yatayda içerik alanı yarıya iniyor ve bir parola
  yöneticisinde okunacak şey zaten uzun bir liste.

### Kare bütçesi (60 ve 120 Hz)

- Model sınıflarına `@Immutable` eklendi. Öncesinde `List` alanları yüzünden
  hiçbir model kararlı sayılmıyordu ve tek bir kaydın değişmesi ekrandaki bütün
  satırların yeniden bestelenmesine yol açıyordu.
- Temel profil (baseline profile) eklendi: açılış ve ilk kaydırma yolları
  kurulumda önceden derleniyor.
- Kare başına ayırma azaltıldı: kadran yolu ve köşe tamponu yeniden
  kullanılıyor, parola gücü kayıt başına bir kez ölçülüyor.
- Bulanıklık için tam ekran katman kaydı artık yalnızca gerektiğinde yapılıyor.
- R8 tam kipi açıldı.

> Bu değişikliklerin hepsi Compose'un belgelenmiş davranışına dayanıyor ama
> gerçek kare süreleri bir cihazda ölçülmedi.

---

## Kasa ne yapıyor

**Şifreleme.** Anahtar türetme cihaza göre ölçülüyor (Argon2id, ~800 ms hedefi)
ve ölçülen parametre kasa başlığına yazılıyor, böylece kasa başka cihaza
taşındığında da doğru açılıyor. İki şifreleme paketi var — AES-256-GCM ve
XChaCha20-Poly1305 — hangisinin daha hızlı olduğu kurulumda ölçülüp seçiliyor.
XChaCha20 kullanılmadan önce kendini sınıyor (RFC vektörü, mühürleme turu,
kurcalanmış etiketin reddi); biri tutmazsa paket hiç sunulmuyor.

**Kilit.** Biyometri (Class 3) ya da cihaz ekran kilidi; 4–6 haneli hızlı PIN
(beş hatalı denemede düşüyor, ana parola isteniyor); kayıt bazlı ek kilit;
güvenilen Wi-Fi ağında daha uzun kilit gecikmesi (ağ adı ham hâlde saklanmıyor).

**Zorlama parolası.** İkinci bir ana parola tuzak kayıtlarla dolu sahte bir kasa
açıyor. İki bölme aynı dosyada, aynı başlığı paylaşıyor ve 4 KiB'lik bloklara
tamamlanıyor — dosyaya bakarak kurulu olup olmadığı anlaşılmıyor.

**Kasa.** Dokuz kayıt türü, klasörler, kurala göre kendini dolduran
koleksiyonlar, 30 günlük çöp kutusu, kayıt başına şifreli ek dosyaları, parola
geçmişi. Kart kayıtları cüzdandaki gibi görünüyor: ödeme kartı oranında yüz,
EMV yongası, ağın renk ailesi, maskeli numara ve Luhn sağlaması.

**Telefon.** Passkey (FIDO2/WebAuthn) saklama ve sistem çapında Credential
Manager sağlayıcısı, kamera ile 2FA karekod okuma, hızlı ayarlar döşemesi, ana
ekran araç birimi, Türkçe ve İngilizce.

**Ağa çıkan tek şey** parola sızıntısı denetimi (Have I Been Pwned), k-anonimlik
ile: parolanın SHA-1 özetinin yalnızca ilk beş hanesi gönderiliyor. Kapatılabilir.

### Bilinen sınırlar

Sınırların tamamı [README](https://github.com/Erenkng/G-venli-ifre-y-neticisi#açıkça-söylenen-sınırlar)
içinde yazılı. Kısaca: deneme sayacı dosyasını silen bir saldırgan sayacı
sıfırlar (asıl koruma ana parolanın entropisi ve ölçülmüş Argon2id maliyeti);
cihaz kök erişimine açıksa kasa açıkken bellekten okunabilir; ekran görüntüsü
engeli kök erişimini durdurmaz.
