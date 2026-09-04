**Kasa**, cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok,
eşitleme yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç kayıt
olduğu ve hangi kategorilerin kullanıldığı dâhil her şey o dosyanın içinde.

Android 16 (API 36) ve üstü, 64 bit cihaz.

> **Hangi dosyayı indirmeli:** `app-release.apk`. Bu sürümden itibaren APK
> imzalı, yani doğrudan kurulabiliyor ve bundan sonraki sürümler kaldırmadan
> üstüne güncelleniyor.
>
> İmza depoda açıkça duran bir anahtarla atılıyor. Bunun anlamı: APK kuruluyor
> ve güncelleniyor, ama imza **"bunu kim derledi" sorusunun cevabı değil** —
> anahtar herkese açık olduğu için aynı imzayı başkası da atabilir. Güvence,
> APK'yı bu sayfadan indirmiş olmandan geliyor. Anahtarı kendine ait bir
> gizli anahtarla değiştirmek istersen yol README'de yazılı; ama önce kasanı
> dışa aktar, çünkü imza değişince Android kurulu sürümün üstüne yazmayı
> reddediyor ve uygulamayı kaldırmak kasayı da siliyor.

---

## 1.9'da yenilikler

Bu sürümün ekseni gezinme: geri gitmek artık bir tuşa basmak değil, parmakla
yürütülen bir hareket. Yanında bir tur kusur avı var ve çıkanların çoğu
görünmeyen türden — kare bütçesi yiyen, sessizce veri kaybettiren şeyler.

### APK artık kurulabiliyor

Bugüne kadar `app-release-unsigned.apk` imzasızdı ve Android imzasız bir APK'yı
kabul etmiyor; kurulabilen tek dosya hata ayıklama derlemesiydi ve o da her CI
çalışmasında farklı bir anahtarla imzalandığı için kurulu sürümün üstüne
güncellenemiyordu — her sürümde uygulamayı kaldırmak, yani kasayı silmek
gerekiyordu.

Artık depoda sabit bir imzalama anahtarı var. APK kuruluyor ve bundan sonraki
sürümler kaldırmadan üstüne biniyor. Anahtar açıkta olduğu için imza kimlik
taşımıyor; ayrıntısı yukarıdaki kutuda.

### Geri hareketi parmakla birlikte yürüyor

Tam ekran katmanlar — düzenleyici, çöp kutusu, ayarlardaki kategoriler — geri
tuşuyla kapanıyordu ama kapanma **basıldıktan sonra** başlıyordu. Kullanıcı
kenardan içeri çekerken ekranda hiçbir şey olmuyor, sonra bir anda katman
gidiyordu. O aralıkta iki şey bilinmiyor: hareketin tanınıp tanınmadığı ve
bırakılırsa ne olacağı.

Sistem bunu kendi pencereleri arasında zaten yapıyor. Uygulamanın kendi
katmanları aynı hareketi taklit etmediğinde, aynı parmak hareketi ekranın
neresinde yapıldığına göre iki farklı şey hissettiriyor.

İki ayrı işleme var, çünkü iki ayrı şey oluyorlar:

- **Pencere kapanışı** (düzenleyici, çöp kutusu): yüzey küçülüyor, çekilen
  kenarın tersine kayıyor ve köşeleri yuvarlanıyor. Ekranı kaplayan bir
  yüzeyin köşesi zaten ekranın köşesi; yuvarlanmaya başladığı an artık ekranı
  kaplamadığını söylüyor. Ölçek merkezi çekilen kenarın karşısında, yani
  altındaki şey parmağın geldiği taraftan görünmeye başlıyor — geri gidilen
  yer orası.
- **İtmeli gezinme** (ayarlarda bir kategori): küçülme ve köşe yok, yalnızca
  gidilen yolun tersine kayma ve hafif solma. Kategori ekranı ayrı bir pencere
  değil, aynı listenin bir sonraki durağı. Yön parmağın hangi kenardan
  geldiğine de bakmıyor: itmeli gezinmede geri, ileri gidilen yolun tersi
  demek.

Bırakınca çıkış animasyonu ilerlemenin bırakıldığı yerden devralıyor; başa
sarılsaydı yüzey önce tam boyuna sıçrar, sonra giderdi. Vazgeçilirse yerine
yaylanarak dönüyor. Hareket kapalıyken geri tuşu yine çalışıyor, yalnızca
yüzey kıpırdamıyor.

Arama ile kamera dışarıda: aramanın kapanışı zaten çubuğa geri toplanan bir
daire ve iki mekân eğretilemesi birbiriyle yarışırdı; kamerada ise önizleme
yüzeyi ölçeklenip kırpılamıyor.

### Düzenleyici artık sormadan atmıyor

Geri tuşu ve çarpı düğmesi formu doğrudan kapatıyordu. Otuz karakterlik bir
parola üretip yazdıktan sonra yanlışlıkla geri gitmek, yazılan her şeyi geri
dönüşsüz siliyordu — ne uyarı vardı ne geri alma. Parmağa bağlı geri hareketi
bu yolu bir kaza olarak daha da kolaylaştırıyordu.

Metin duruma göre değişiyor: yeni bir kayıtta yazılanlar hiçbir yere
yazılmadı, var olan bir kayıtta eski hâli yerinde duruyor. İkisi aynı şey
değil ve aynı cümleyle anlatılamaz.

### Kasaya yazamama artık sessiz değil

Depo yazımı başarısız olduğunda (disk dolu, anahtar deposu erişilemez, dosya
kilitli) uygulama yalnızca başarı dalını çalıştırıyordu: titreşim yok,
bildirim yok, ekranda hiçbir değişiklik yok. Kullanıcının gördüğü şey "hiçbir
şey olmadı" — ama bir parola yöneticisinde bunun "kaydedildi" ile
karıştırılması, parolanın hiçbir yerde durmaması demek.

On yedi yazım noktası tek bir yerden geçiyor. İki yerde davranış da düzeldi:
**sık kullanılan** anahtarı titreşimi koşulsuz çalıyordu — yazım başarısızken
parmağa "oldu" diyor, ekran ise değişmiyordu; **biyometrik açma**
kurulamadığında hiçbir şey söylenmiyordu ve kullanıcı parmağını okutup
anahtarın kapalı kaldığını çoğu zaman fark etmiyordu.

### Sonsuz animasyonlar kare bütçesini yemiyor

Beş bileşen sonsuz bir animasyonun değerini beste aşamasında okuyordu: iskelet
parıltısı, dalgalı gösterge, morph kadranı, tarama şekli ve tanıtım ekranının
çizimi. Değer her karede değiştiği için bu bileşenler ekranda durduğu
**sürece** saniyede 120 kez yeniden besteleniyordu — ve sonsuz bir animasyonda
bunun bir sonu yok.

En pahalısı tanıtım ekranıydı: kullanıcının okuduğu bütün süre boyunca sürüyor
ve kaydırma tam o sırada oluyor.

### Liste iskeletten sırayla doluyor

İskelet listenin biçimini gösteriyor, sonra içerik geliyordu — ama içerik tek
karede belirince ikisi arasında hiçbir bağ kalmıyordu. Sırayla gelince olan
şey tek bir olay: biçim doluyor. Yalnızca ilk doluşta; kaydırırken görüş
alanına giren her satır belirseydi liste sürekli kıpırdayan bir şey olurdu.

### Çöp kutusunda toplu işlem

Kasa listesindeki seçim kipi çöp kutusuna da geldi: toplu geri alma ve toplu
kalıcı silme. Kalıcı silmede geri alma **yok** — silinen şey artık hiçbir
yerde durmuyor ve bir "geri al" şeridi kullanıcıya olmayan bir güvence
verirdi; onun yerine onay penceresi çıkıyor.

Seçim kümesi kasayla ortak olduğu için geçişlerde temizleniyor. Temizlenmezse
çöp kutusunda "3 seçildi" yazarken silinecek olanlar kasadaki kayıtlar
oluyordu; kalıcı silmede bunun geri dönüşü yok.

### Aynı şeyin iki kopyası kalmadı

- **Alt sayfaların cam yüzeyi** altı dosyada altı kez tanımlıydı ve çoktan
  ayrışmıştı: dördü bir belirteci, ikisi başkasını kullanıyordu, yani sayfalar
  hangi dosyada yazıldıklarına göre iki ayrı tonda duruyordu.
- **Çöp kutusunun seçim çubuğu** kasa listesindekinin kopyasıydı ve daha ilk
  sürümünde ayrışmıştı: kopyada düğmeler arasındaki boşluk yoktu. Çubuk artık
  tek, eylemleri çağıran taraf veriyor.
- **Düzenleyici** düz bir renkle boyanıyor ve uygulamanın tek gradyansız
  ekranı oluyordu. Dahası bu bir kusuru örtüyordu: 1.8'de metin girişleri cam
  yüzeye geçmişti ama düzenleyicide camın arkasında gösterecek bir şey yoktu.

### Ufak olanlar

- Kasanın seçim çubuğu çöp kutusu açıkken gizleniyor; tam ekran açılış
  animasyonu sırasında iki çubuk üst üste biniyordu.
- Çöp kutusundaki toplu seçimin görünür bir girişi var. Uzun basmak zaten
  açıyordu ama kasa listesinde o yolu satır menüsündeki "Seç" gösteriyor,
  çöp kutusunda ise menü yok.
- Çöp kutusunun geri oku sağdan sola yazılan dillerde ters yöne bakıyordu.

### Tasarım dili becerisi

Depodaki `android-expressive-ui` becerisine parmağa bağlı gezinme bölümü
eklendi: manifest bayrağının neden yetmediği, iki işlemenin farkı ve hangi
durumlarda hiç uygulanmaması gerektiği. Beceride zaten yazılı olan "animasyon
değerini beste aşamasında okuma" kuralının bu depoda beş yerde çiğnendiği de
bu sürümde bulundu — beceri haklıydı, kod ondan sapmıştı.

Gezinme çubuğuna yine dokunulmadı.

---

## 1.8'de gelenler

1.7'nin üstüne bir tur cila ve o turda bulunan kusurların düzeltmeleri.

### Cam artık alanların da dili

Uygulamanın geri kalanı zeminini geçiriyordu; alan blokları ve metin girişleri
opak kaldığı sürece kayıt ayrıntısı ile düzenleyici ötekilerden farklı bir dile
konuşuyordu.

Örtücülük liste satırlarınınkinden yüksek: satır bir adı taşıyor, alan bloğu
okunacak bir değeri — çoğu zaman tek tek harfleri sayılan bir parolayı. Orada
zeminin geçmesine bırakılan her yüzde okunabilirlikten düşüyor. Hata çerçevesi
camın kenar ışığının üstüne biniyor; ikisi aynı kenarı paylaşıyor ve hata
durumunda okunması gereken şey ışık değil uyarı.

### Büyük başlık cam çubuğa devrediyor

Başlık listenin ilk öğesi olduğu için kaydırınca yukarı kayıp kayboluyordu,
üstteki cam çubuk ise ondan bağımsız beliriyordu. İki hareket aynı anda
oluyordu ama birbirini görmüyordu; göz iki ayrı şey olduğunu okuyordu, oysa
olan tek bir şey — başlık yer değiştiriyor.

Artık büyük başlık sönerken hafifçe küçülüp yukarı çekiliyor, yani çubuğa
**gidiyor**. Sönme mesafesi çubuğun geliş mesafesiyle aynı; hiçbir anda ikisi
birden tam görünür olmuyor. Ölçek merkezi üstte: başlık kendi tabanına değil
çubuğun olduğu yöne toplanıyor.

### Sızmış parola uyarısı kasa ekranında

Sızıntılar zaten tespit ediliyordu ama kullanıcı güvenlik sekmesine gitmedikçe
görmüyordu — ve oraya gitmek için bir sebebi olmuyordu, çünkü bir şey olduğunu
bilmiyordu. Uyarı artık kullanıcının zaten olduğu yerde, listenin başında.

Yalnızca sızıntı için. Zayıf ve tekrar eden parolalar da bulgu ama onlar "bir
gün düzelt" işi; sızıntı, parolanın **şu anda** başkasının elinde olduğu
anlamına geliyor. Her bulguyu listenin başına koymak, hiçbirinin okunmamasıyla
sonuçlanırdı.

Zemin sakin, yalnızca işaret ve sayı güç renginde: tam kırmızı bir kart her
açılışta duran bir alarm oluyor ve üçüncü açılışta artık okunmuyor. Kapatma
düğmesi yok — kapatılabilir bir uyarı, kapatıldığı anda sorunu da gizliyor;
kart bulgu çözüldüğünde kendiliğinden gidiyor.

### 1.7'de bulunan kusurlar

- **Seçim çubuğu sistem çubuğu boşluğunu iki kez sayıyordu** ve bir sistem
  çubuğu yüksekliği kadar fazla kalkmış duruyordu.
- **Çubuğun düğmeleri 42dp'ydi**; Android'in en küçük dokunma hedefi 48dp. Beş
  düğme yan yana ve yanlış olana basmanın sonucu "kayıt silindi" olabiliyor.
- **Çubuk her sekmede görünüyordu.** Ayarlar ekranının üstünde duran bir "12
  seçildi" çubuğu, orada işe yaramayan bir eylem takımı demek. Seçimin kendisi
  duruyor — kullanıcı klasörlerine bakıp dönebilsin diye — ama çubuk yalnızca
  kasa sekmesinde.
- **Çöp kutusunda seçim kipi kapatıldı.** Çubuğun eylemleri silinmiş kayıtlar
  için ya anlamsız ya da etkisizdi.
- **Sızıntı uyarısı kendi kabını kuruyordu** ve basınca hiçbir tepki
  vermiyordu; artık liste satırının kabını kullanıyor.
- **Liste iskeleti pratikte hiç görünmüyordu:** bayrak deponun ilk yayınını
  bekliyordu ama depo elindeki değeri anında veriyor. Beklenen şey deponun
  kendisi değil, süzmenin ilk sonucu.

### Kurulum sorununun cevabı indirmeden önce

Sürüm notlarının başına hangi dosyanın kurulabildiği yazıldı ve README'ye
imzalama anahtarının nasıl üretilip tanımlanacağı eklendi. Uyarı en alttaydı,
yani ancak indirip denedikten sonra okunuyordu.

---

## 1.7'de gelenler

### Parlayan kenarlar gerçekten parlıyor

Kenar efektleri tek genişlikte bir çizgiyle çiziliyordu ve bu, bir kenarı
**parlayan** değil **çizilmiş** gösteriyor. Parlayan bir şey kendi sınırının
dışına ışık taşırıyor; göz "bu parlıyor" kararını çizginin kendisinden değil
o dağılımdan veriyor.

Aynı yol artık üç kez çiziliyor: en dıştaki geniş ve sönük, en içteki dar ve
tam parlak. Üst üste binen katmanlar merkeze doğru hızla artan bir yoğunluk
veriyor — bir bulanıklık katmanı kurmadan hâlenin yaptığı iş bu. Genişlik
katsayıları doğrusal değil, çünkü hâle yakında hızlı uzakta yavaş sönüyor.

Basış hâlesi parmakla birlikte büyüyor. Parıltı şeridinin uçlarında hâle
sönüyor, yoksa şerit belirip kaybolmak yerine yanıp sönüyordu. Güç puanı
halkası, TOTP sayacı ve yükleme göstergesi de aynı hâleyi kullanıyor.

### Bir şeyin sürdüğünü söyleyen gösterge

Uygulamadaki tek gösterge **belirli** bir ilerleme istiyordu: dolacak bir yer,
dolduran bir sayı. Ama işlerin çoğu öyle değil — bir kasanın açılması, bir
dışa aktarmanın yazılması. Oralarda çubuk ya sıfırda duruyordu (donmuş
görünüyor) ya da uydurulmuş bir sayıyla doluyordu.

Yeni gösterge nefes alan bir yay. Sabit uzunlukta dönen bir yayın tekrarını
göz hemen yakalıyor ve hareket "dönüyor" değil "bekliyor" diyor; uzunluk
dönüşten farklı hızda değişince desen çok daha geç tekrarlıyor. Ucundaki ışık
yönü söylüyor.

- **Kilit ekranı.** Anahtar türetme uygulamanın en uzun beklemesi (~800 ms) ve
  buradaki tek işaret düğmenin yazısıydı — ekran donmuş görünüyordu.
- **Ayarlar.** Anahtar döndürme bütün kasayı yeniden şifreliyor, ana parola
  değişimi anahtarı yeniden türetiyor, içe aktarma her kaydı ayrı şifreliyor.
  Üçünün de ekranda hiçbir karşılığı yoktu ve aynı düğmeye ikinci kez
  basılabiliyordu. Cam örtü dokunuşları yutuyor ve ne beklendiğini yazıyor.
- **Kasa listesi.** İlk süzme hesabı bitene kadar boş liste duruyordu, yani
  "yükleniyor" ile "hiç kayıt yok" aynı görünüyordu. İkincisi bir parola
  yöneticisinde ürkütücü bir cümle. İskelet gelecek olanın biçimini gösteriyor.

### Cam başlık çubuğu

Her ekranın başlığı listenin ilk öğesiydi ve kaydırınca gidiyordu; uzun bir
listenin ortasında hangi ekranda olunduğunu söyleyen tek şey ekranın en
altındaki gezinme simgesi kalıyordu — gözün bulunduğu yerin tam tersinde.

Büyük başlık yukarı çıkarken yerini üstte duran cam bir çubuk alıyor.
Altındaki içerik bulanıklaşıyor, alt kenar sert bir çizgiyle değil sönerek
bitiyor. Ayarlarda açık olan kategorinin adını gösteriyor ve bir kategorinin
ya da süzülmüş bir görünümün içindeyken geri oku taşıyor: o çıkış listenin
başındaki çubuktaydı ve o da kaydırınca gidiyordu.

### Kurulum ekranı

- Adımlar arası geçiş animasyonlu değildi: kasanın yaratıldığı an — kurulumun
  tek geri alınamaz adımı — hiçbir şey olmamış gibi geçiyordu. Artık yeni adım
  alttan yükseliyor, eski yukarı çekiliyor. Yön tek, çünkü kurulum tek yönlü
  bir yol.
- Üstte üç adımlı bir ray: geçmiş adım dolu, bulunulan yarı dolu.
- Her adımın bölümleri sırayla ve bulanıklıktan çözülerek beliriyor.
- Kurtarma anahtarı en son ve en güçlü bulanıklıkla geliyor. O ekranın tek
  gerçek işi o koda bakılmasını sağlamak.

### Parola geçmişi artık açılabiliyor

Kasa parola değişikliklerinde eskisini zaten saklıyordu ama arayüzde yalnızca
**sayısı** görünüyordu: "3 eski parola". Saklamanın tek sebebi geri
dönebilmek olduğuna göre, ulaşılamayan bir geçmiş hiçbir işe yaramıyordu.

Asıl senaryo şu: bir sitede parolanı değiştiriyorsun, kasaya yenisini
yazıyorsun, sonra sitenin değişikliği kaydetmediği ortaya çıkıyor. O anda
doğru parola yalnızca burada duruyor.

Her satır kapalı başlıyor ve ayrı açılıyor — hepsi birden açık dursaydı omuz
üstünden tek bakış bütün geçmişi verirdi. Geri yükleme geçmişi silmiyor: o an
kayıtlı olan parola geçmişin başına gidiyor, yani yanlış satırı seçen
kullanıcı tek dokunuşla geri dönebiliyor.

### Çoklu seçim ve toplu işlem

Otuz kaydı bir klasöre taşımanın tek yolu, otuz kez kaydı açıp klasörünü
değiştirmekti. Kasa büyüdükçe bu iş yapılmaz hâle geliyor ve kullanıcı düzeni
bırakıyor — yani klasörler var ama kimse kullanmıyor.

Seçim kipi basılı tutma sayfasındaki **Seç** ile başlıyor. Kipteyken dokunuş
kaydı açmıyor, seçiyor; satırın rozeti seçim işaretiyle yer değiştiriyor.
Toplu olarak çöpe atma, sık kullanılana ekleme ve klasöre taşıma var.

Depoda toplu işlem tek yazım: eskiden otuz kayıt otuz kez kasanın tamamının
şifrelenip diske yazılması demekti — hem yavaş hem de her biri ayrı bir
kesinti noktası. Şimdi ya hepsi ya hiçbiri.

Eylem çubuğu gezinme çubuğunun **üstüne biniyor**, yerine geçmiyor: kullanıcı
seçim yaparken sekme değiştirmek isteyebiliyor.

### Boş durumlar

İki satır gri yazıydı ve kullanıcıya bir şeyin **eksik** olduğunu
söylüyordu; işaret o boşluğun beklenen bir durum olduğunu söylüyor. Süzgeç
yüzünden boşalan listede artık çıkış yolu da var.

### Depo görselleri

Ekran çizimlerinde uydurulmuş kayıt adları ve sahte parolalar vardı; bunlar
gerçek bir ekran görüntüsü gibi okunup yanlış bir izlenim bırakıyordu — hem
"uygulama böyle görünüyor" hem de "kasada bu kayıtlar var" anlamında. Görseller
yazısız taslağa çevrildi: yalnızca yerleşim, ölçü ve renk ailesi.

Gezinme çubuğuna yine dokunulmadı.

---

## 1.6'da gelenler

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
