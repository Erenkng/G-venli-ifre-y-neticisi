**Kasa**, cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok,
eşitleme yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç kayıt
olduğu ve hangi kategorilerin kullanıldığı dâhil her şey o dosyanın içinde.

Android 16 (API 36) ve üstü, 64 bit cihaz.

---

## 1.6'da yenilikler

### Her titreşim, gerçekte olan olayın titreşimi

1.5 motoru yazdı ama uygulama onu kullanmıyordu: kırk küsur çağrı yerinin on
sekizi `SUCCESS`, on beşi `WARNING` çalıyordu. Kaydetmek ile kilidi açmak,
çöpe atmak ile kasayı sıfırlamak parmakta aynı hissediyordu — motorun ayırt
edebildiği şeyleri uygulama söylemiyordu.

Duygu sözlüğüne dokuz giriş eklendi ve her çağrı yeri gerçekte olan olaya
bağlandı:

- **Kilit açıldı** — yükselen üç darbe, sonuncusu en yumuşak. Günün en
  beklenen anı.
- **Kasa kendini sildi** — alarm. Yanlış paroladan on kez sonra gelen bu an,
  yanlış parolayla aynı hissedemez.
- **Bekleme süresi** — nabız gibi tekrarlı. "Yanlış" değil "henüz değil"
  diyor; tek sert bir darbe olsaydı kullanıcı parolasını yeniden yazmayı
  denerdi.
- **Kalıcı silme, çöp boşaltma, kasa sıfırlama** — geri alınamaz olanın
  ağırlığı.
- **Çöpe atma** — olumsuz ama daha hafif: geri alınabilir. Aradaki fark
  parmakta, ekrana bakmadan.
- **Geri yükleme** — bir şeyin geri dönmesi.
- **Ana parola, PIN, biyometri, anahtar döndürme** — mühür. Kasanın kilidi
  açılmıyor, kilidin **kendisi** değişiyor.
- **Yeni kayıt** ile **güncelleme** ayrı; **gizli değer kopyalama** ile düz
  kopyalama ayrı.
- **Güvenlik taraması** biterken sonucun kendisini çalıyor: temiz bir kasa
  rahatlama, dolu bir bulgu listesi uyarı. Karışım oranı bulgu sayısıyla
  artıyor.

Yeni titreşim noktaları: göz tuşu (gizli bir alanı açan her yol aynı
bileşenden geçiyor) ve kaydırıcının **her adımı** — rayın üzerindeki noktalar
parmağın altında kalıyor, adım hissini artık tıkırtı taşıyor.

### Cam yüzey katmanı

Uygulamanın zemini üç radyal duraklı, günün saatine göre kayan bir gradyan —
ve üzerindeki her satır, her kart onu **tamamen** kapatıyordu.

Artık kapatmıyor. Liste satırları, kartlar, son kullanılanlar, arama pili,
güvenlik bulguları ve ayar kategorileri zemini geçiriyor; üst kenarları ışık
alıyor, altları sönük kalıyor. Fark tek tek bakınca görünmüyor ama liste
kayarken yüzeyler zeminin üstünde **geziniyor**.

Örtücülük bilerek yüksek: geçirgenlik derinlik ekliyor, kontrast taşımıyor.
Tersi kurulsaydı sabah açık, gece koyu bir zeminde aynı yazı iki farklı
okunabilirlikte olurdu.

### Gerçek bulanıklık, uygulamanın içinde

- **Cam başlık çubuğu.** Her ekranın başlığı listenin ilk öğesiydi ve
  kaydırınca gidiyordu; uzun bir listenin ortasında hangi ekranda olunduğunu
  söyleyen tek şey ekranın en altındaki gezinme simgesi kalıyordu. Artık
  büyük başlık yukarı çıkarken yerini üstte duran cam bir çubuk alıyor.
  Altındaki içerik bulanıklaşıyor ve alt kenar sert bir çizgiyle değil
  sönerek bitiyor — içeriğin çubuğun altında devam ettiğini söyleyen şey bu.
- **Ana eylem menüsü** açıldığında ekran gerçekten buzlanıyor. Eski düz
  karartmanın altında liste okunmaya devam ediyordu ve menüyle dikkat için
  yarışıyordu.
- **Bildirim çubuğunun** altı bulanıklaşıyor: altındaki satırlar çubuğun
  kenarına kadar okunabiliyordu.

Üçü de gezinme çubuğunun kullandığı kaydedilmiş ekran kopyasını paylaşıyor;
ikinci bir ekran kaydı alınmıyor, yani kare bütçesine ek yük yok. Kaydırma
oranı beste sırasında değil çizim sırasında okunuyor: doğrudan okunsaydı
kullanıcı listeyi kaydırdığı sürece bütün ekran iskeleti her karede yeniden
kurulurdu.

### Kurulum ekranı

- Adımlar arası geçiş animasyonlu değildi, düz bir dallanmaydı: kasanın
  yaratıldığı an — kurulumun tek geri alınamaz adımı — hiçbir şey olmamış gibi
  geçiyordu. Artık yeni adım alttan yükseliyor, eski yukarı çekiliyor.
  Yön tek: kurulum tek yönlü bir yol ve hareketin yönü bunu söylüyor.
- Üstte üç adımlı bir ray. Geçmiş adım dolu, bulunulan yarı dolu, gelecek boş
  — kaç adım olduğu, kaçıncısında olunduğu ve ne kadar ilerlendiği tek bir
  biçimde.
- Her adımın bölümleri sırayla ve bulanıklıktan çözülerek beliriyor. Aynı anda
  gelen bir ekran tek bir blok olarak okunuyor; sırayla gelince okuma yönü
  hareketin kendisinden çıkıyor.
- Kurtarma anahtarı en son ve en güçlü bulanıklıkla geliyor. O ekranın tek
  gerçek işi o koda bakılmasını sağlamak; ötekilerle birlikte belirseydi
  sayfanın bir parçası olurdu.

Gezinme çubuğuna yine dokunulmadı.

---

## 1.5'te gelenler

### Akıllı titreşim motoru

Uygulamanın hiçbir yerinde artık titreşim deseni yazılı değil. Çağıran taraf
ne **hissettirmek** istediğini söylüyor, motor onu o cihazda çalınabilecek en
iyi şeye çeviriyor.

Önceki katman yedi sabit desendi ("dokunuş: 8 ms, 90 genlik") ve iki yerden
kırılıyordu: tablo büyüdükçe yeni olaylar ya var olan bir deseni yeniden
kullanmak — iki farklı şeyi aynı hissettirmek — ya da uydurulmuş yeni bir
satır demekti; ve sabit süre/genlik, onu yazanın telefonunda doğru
hissediyordu, başka bir aktüatörde bambaşka bir şey üretiyordu.

Artık dört eksen var — hoşluk, uyarılma, kesinlik, ağırlık — ve titreşim
bunlardan **üretiliyor**: uyarılma şiddete, hoşluk keskinliğe, kesinlik
ritme, ağırlık süreye. Aynı kurallar on dört duyguyu da, ikisinin karışımını
da üretiyor.

Donanım tarafında dört basamaklı bir merdiven var: zarf (Android 16+, şiddet
ve keskinliği doğrudan alıyor) → ilkeller → dalga biçimi → tek atış. Her
basamak bir öncekinin gerçek yedeği.

Motorun kendi aklı: aynı olay arka arkaya geldiğinde üstel olarak sessizleşiyor
(alarmlar hariç — ikincisi de birincisi kadar acil), kayan pencerede toplam
titreşim süresi tavanlı, sessiz kip ve pil tasarrufu ölçeği düşürüyor, bir
donanım yolu fırlatırsa bir daha denenmiyor.

Rastgelelik **yok**: aynı duygu her zaman aynı hissediyor. Çeşitlilik dokunsal
geri bildirimin tek işini — olayla eşleşmeyi — bozardı.

### Android 17 cam yüzeyleri

Sistem arayüzü derinliği gölgeyle değil bulanıklıkla anlatmaya geçti. Gölge
"bu yükseltilmiş" diyor; bulanıklık "arkasında bir şey var ve hâlâ orada"
diyor.

Pencereler ve alt sayfalar artık arkalarını sistemin kendi mekanizmasıyla
bulanıklaştırıyor — güç menüsünü ve ses panelini bulanıklaştıran şeyin
aynısı — ve yarı saydam bir levhanın üstünde duruyor.

Bulanıklık tek başına okunabilirliği taşımıyor: sistem onu pil tasarrufunda ve
düşük güçlü cihazlarda kapatıyor, kapalıyken levhanın kendi rengi zaten
yeterli kontrastı veriyor.

Gezinme çubuğuna dokunulmadı.

---

## 1.4'te gelenler

### Chrome'dan gelen parolalar kayboluyordu — düzeltildi

İçe aktarmadan sonra ad, adres ve e-posta duruyor ama parola boş kalıyordu.

Sebebi sahiplikti. Kayıtlar depoya verildikten hemen sonra, çağıran kendi
kopyasını temizlemek için gizli metni siliyordu — ve sildiği şey kasanın
içindeki tamponun ta kendisiydi, çünkü ikisi aynı diziyi paylaşıyordu. Ad ve
adres birer `String` olduğu için sağ kalıyor, yalnızca parola boşalıyordu:
içe aktarma başarılı görünüp parolasız kayıtlar bırakıyordu.

Depo artık sakladığı gizli veriyi kopyalıyor. Aynı dosyadaki ikinci kusur da
düzeltildi: bayt sırası işareti (BOM) yalnızca metnin başında duruyor ama iki
uçta birden aranıyordu, bu yüzden ilk sütunun adı hiç tanınmıyordu.

**Chrome'dan aktardığı kayıtlarda parolası boş görünenler varsa, o kayıtları
silip dosyayı yeniden aktarmak gerekiyor** — eski içe aktarmada parola diske
hiç yazılmadı.

### Üreteç

- Kadran koyu kalıyordu: kap renkleri kullanılıyordu ve onlar **üzerine yazı
  gelsin diye** seçilmiş tonlar. Renk artık güç tonlarından geliyor (iki
  temada da canlı) ve kadran kendi içinde üç duraklı bir degrade kuruyor.
- Üretilen değer kadranın içinden çıktı. Uzun bir sonuç dönen biçime sığmıyor
  ve kırpılıyordu; kopyalama ile yeniden üretme de ekranın başka yerlerindeydi.
  Değer şimdi kendi kartında, iki eylemiyle.
- İki yeni tür: **UUID** ve **yedek giriş kodları**.

### Efektler artık çerçevede

Yüzeyin tamamına yayılan ışık, üzerindeki metnin kontrastını düşürüyordu.
Üçü de yalnızca kenar bandına çiziliyor — gerçek nesnelerde de ilk parlayan
yer kenar.

### Arama açılışındaki takılma

Animasyon değeri **beste aşamasında** okunuyordu: her karede değiştiği için
ana iskele saniyede 120 kez yeniden besteleniyor, altındaki bütün ekran onunla
geçiyordu. Değer artık yalnızca çizim aşamasında okunuyor.

### Kategoriye özel açılış

Kart kendi yüzüyle açılıyordu ama kalan yedi tür aynı görünüyordu. Giriş
sitenin işaretiyle, kimlik belge yerleşimiyle, şema tabanlı türler kendi rengi
ve simgesiyle açılıyor.

---

## 1.3'te gelenler

Bu sürüm 1.2 ve 1.3'te yapılanları da içeriyor; 1.2 ayrı yayımlanmadı.

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
