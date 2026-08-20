**Kasa**, cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok,
eşitleme yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç kayıt
olduğu ve hangi kategorilerin kullanıldığı dâhil her şey o dosyanın içinde.

Android 16 (API 36) ve üstü, 64 bit cihaz.

---

## 1.3'te yenilikler

Bu sürüm 1.2'de yapılanları da içeriyor; 1.2 ayrı yayımlanmadı.

### Arayüz baştan geçti

- **Animasyonlu tanıtım.** Kurulum artık ilk kareden "ana parolanı gir"
  demiyor. Üç sayfa, üç karar: kasa tek bir dosya, her tür kendi alanında,
  hiçbir şey cihazdan çıkmıyor. Grafik sayfadan yavaş, yazı sayfadan hızlı
  kayıyor — tek hızda kayan bir sayfa düz bir slayt, farklı hız derinlik
  veriyor.
- **Türe özel düzenleyiciler.** Genel "yeni kayıt" formu kalktı. Kart
  yazarken kart görünüyor, 2FA yazarken kod dönüyor, kimlik yazarken belge
  duruyor. Yazılan şey anında göründüğü için yanlış veri kaydedilmeden önce
  fark ediliyor.
- **Notlar kaldırıldı.** Not, tür sisteminin kaçış kapısıydı: oraya yazılan
  hiçbir şey aranamıyor, kopyalanamıyor, gücü ölçülemiyordu. Var olan notlar
  silinmiyor — şema göçü onları giriş kaydına çeviriyor, ad, metin, etiket,
  klasör ve ekler olduğu gibi kalıyor.
- **Arama, çubuğun bulunduğu yerden büyüyerek** açılıyor ve geri gidince
  oraya dönüyor.
- **Ayarlar altı kategoriye ayrıldı.** Yetmiş satırlık tek bir listede bir
  ayarı aramanın cevabı "kaydır ve ara" olmuştu.
- **Güvenlik ekranı**: puan artık bir halkanın içinde ve altında bulgu
  şeridi var; dokununca o kuralın listesine gidiyor.

### Kartlar

- Numara **yazarken öbekleniyor**, son kullanma tarihine eğik çizgiyi
  uygulama koyuyor. Kaydedilen değer yalnızca rakam.
- **Ağ işaretleri çizildi**: Mastercard ve Maestro'nun iç içe daireleri
  gerçek yol kesişimiyle, JCB'nin üç şeridi, UnionPay'in eğik dilimleri,
  Diners diski, Visa/Amex/Troy kelime işaretleri.
- **Kopyalama düğmeleri kartın üzerinde**. Güvenlik kodu kartın yüzünde
  hiçbir zaman açık yazmıyor.

### Görünüm

- **Site logoları** baş harfin yerine: ~75 site gömülü tabloyla. Favicon
  indirilmiyor ve indirilmeyecek — o istek, kasanın içindekini tam olarak o
  sunuculara ve aradaki ağa söylerdi.
- **Gradyanlar saate göre kayıyor**, ayarlardan üç aile içinde.
- **Gizli değer açılırken** noktalar bir anda harfe dönmüyor: metin
  bulanıklaşıyor, takas en bulanık karede oluyor, sonra netleşiyor.
- **Uygulama simgesi gradyanlı.** Açılışta bu yüz görünüyor ve animasyonun
  son 350 ms'sinde uygulamanın içindeki düz tona çözülüyor.
- **Durum çubuğunun altında ince cam.** İçerik kenardan kenara çizildiği
  için saat ve pil simgesi o an oradan geçen şeyin rengine kalıyordu.
- **Alt sayfalar artık telefonun en üst kenarına dayanmıyor.**

### Deneysel efektler (ayarlardan kapatılabilir)

- **Eğim parlaması**: kart yüzünde ışık cihazın eğimiyle geziniyor.
- **Basınç çiçeklenmesi**: dokunulan noktadan açılan, kenarda kesilmeyen ışık.
- **Parıltı şeridi**: cilalı yüzeylerin üzerinden arada bir geçen yansıma.
- **Kenar derinliği**: ekranın uçlarına yaklaşan liste satırları geriye
  çekiliyor.

Anahtar kapalıyken kod yolları **hiç çalışmıyor**: ivmeölçer dinleyicisi
kaydedilmiyor, sonsuz animasyon başlamıyor, fazladan katman kurulmuyor.

### Üreteç: beş yeni özellik

PIN, kullanıcı adı, onaltılık anahtar, toplu üretim ve entropi hedefi.
Sonuncusu şunu düzeltiyor: "20 karakter" bir güç ölçüsü değil — sembol
kapatıldığında aynı uzunluk belirgin şekilde zayıflıyor ve kullanıcı bunu
görmüyordu. Hedef entropiyle uzunluk artık seçilen kümelerin sonucu.

### Düzeltmeler

- Silme penceresinde **"Vazgeç" düğmesi "Vaz" olarak** çiziliyordu.
  Ağırlıksız bir satırda ilk düğme istediği genişliği alıyor, ikinciye kalanı
  kalıyordu; hata her zaman ikinci düğmede çıkıyordu. Düğmeler artık eşit
  ağırlıkta ve etiket taşarsa kelimenin ortasından değil üç noktayla kesiliyor.
- Yatay mod kaldırıldı: içerik alanı yarıya iniyordu ve bir parola
  yöneticisinde okunacak şey zaten uzun bir liste.
- Kilit ekranı her zaman ortalı; ayarlar geç geldiğinde ya da klavye
  açıldığında yerinden oynamıyor.

---

## Kasa nedir

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
  tamamlanıyor — dosyaya bakarak kurulu olup olmadığı anlaşılmıyor
- Bağlama duyarlı kilit süresi: güvenilen Wi-Fi ağında daha uzun. Ağ adı ham
  hâlde saklanmıyor, SHA-256 özetinin ilk 16 baytı tutuluyor

### Ağa çıkan tek şey

Parola sızıntısı denetimi (Have I Been Pwned), k-anonimlik ile: parolanın SHA-1
özetinin yalnızca ilk beş hanesi gönderiliyor. Kapatılabilir.

### Bilinen sınırlar

Sınırların tamamı [README](https://github.com/Erenkng/G-venli-ifre-y-neticisi#açıkça-söylenen-sınırlar)
içinde yazılı. Kısaca: deneme sayacı dosyasını silen bir saldırgan sayacı
sıfırlar (asıl koruma ana parolanın entropisi ve ölçülmüş Argon2id maliyeti);
cihaz kök erişimine açıksa kasa açıkken bellekten okunabilir; ekran görüntüsü
engeli kök erişimini durdurmaz.

**Bu sürümdeki arayüz hiçbir cihazda çalıştırılarak görülmedi.** Depo yazılırken
emülatör erişimi yoktu; doğrulama derlemeyle sınırlı (debug, R8 sürüm derlemesi
ve lint).
