<!--
  Bu dosya her sürümün notlarının **tamamı** olarak yayımlanıyor.

  Kısa tutulmalı: 967 satıra çıktığı bir dönem oldu ve APK bağlantısına
  ulaşmak sayfayı baştan sona kaydırmayı gerektiriyordu — yani notlar,
  varlık sebebi olan indirmenin önüne geçmişti.

  Kural: yalnızca **bu sürümün** yenilikleri, en fazla bir düzine satır.
  Önceki sürümlerin notları kendi sürüm sayfalarında duruyor ve oradan
  ulaşılabiliyor; buraya kopyalanmalarına gerek yok.
-->

## ⬇️ İndir: `app-release.apk`

Aşağıdaki **Assets** bölümünden. İmzalı, doğrudan kurulur ve kurulu sürümün
üstüne güncellenir.

Android 16 (API 36) ve üstü, 64 bit.

---

## 2.7'de yenilikler

Sızma denemesinin ikinci turu. Yine yeni özellik yok; çıkan altı açık
kapatıldı.

- **Panodaki parola aslında hiç silinmiyordu.** Uygulama panoyu silmeden
  önce "bu benim koyduğum şey mi" diye kontrol ediyordu, ama Android 10'dan
  beri arka plandaki bir uygulama panoyu okuyamıyor — kontrol her seferinde
  başarısız oluyor ve silme hiç yapılmıyordu. Silmeyi isteyen iki yol da
  (zamanlayıcı ve kasanın kilitlenmesi) arka planda çalıştığı için,
  kopyalanan parola panoda süresiz kalıyordu; ekranda "30 saniye sonra
  silinecek" yazarken. Artık siliniyor. Zamanlayıcı da telefon uyurken
  beklemiyor.
- **Otomatik kilit süresi telefon uykudayken ilerlemiyordu.** "Beş dakika
  sonra kilitle" gerçekte "beş dakika **açık kaldıktan** sonra kilitle"
  demekti: cebe giren telefonda sayaç neredeyse duruyordu. Ekran kapanınca
  kilitleme bunu gizliyordu, ama o ayarı kapatan kullanıcı tam da süreye
  güvenen kullanıcı. Süre artık uykuyu da sayan bir saate bakıyor.
- **Dosya seçtikten sonra dönülmezse kasa açık kalıyordu.** Dışa aktarma
  ya da içe aktarma için dosya seçicisi açıldığında kilit bilerek
  erteleniyor; ama kullanıcı seçiciden geri dönmezse kasayı kilitleyecek
  hiçbir şey kalmıyordu. Artık kalıyor.
- **Sahte bir tarayıcı, kasadan gerçek kaydı isteyebiliyordu.** Otomatik
  doldurmada tarayıcı tanıma yalnızca uygulamanın adına bakıyordu ve o adı,
  gerçek tarayıcı kurulu değilse başka bir uygulama alabilir. Böyle bir
  uygulama "şu an bankanın sayfasındayım" deyip gerçek banka kaydının
  önerilmesini sağlayabiliyordu; kullanıcı listede doğru kaydın adını
  gördüğü için dokunuyordu. Artık tarayıcının ya telefonla gelmiş olması ya
  da kullanıcının varsayılan tarayıcısı olması gerekiyor.
  *Bunun bir bedeli var: varsayılan olmayan bir tarayıcıda kaydı ilk kez
  elle seçmen gerekebilir. O seçim kalıcı — ikinci seferden itibaren yine
  kendiliğinden geliyor.*
- **Kayıt bazlı ek kilit, doğrulama bozulduğunda açılıyordu.** Parmak izi
  okuyucusu meşgulse ya da çok denemeden sonra kilitlendiyse kapı
  kendiliğinden açılıyordu — yani ek kilidi aşmanın yolu doğrulamayı geçmek
  değil bozmaktı. Artık yalnızca cihazda hiçbir doğrulama yolu yoksa
  açılıyor.
- **Bozuk bir kayıt kasayı çökertebiliyordu.** İçe aktarılan bir dosyadaki
  geçersiz bir 2FA ayarı, listeyi her açılışta çökerten kalıcı bir hataya
  dönüşüyordu.

Gezinme çubuğuna yine dokunulmadı.

---

<details>
<summary>İmza hakkında</summary>

APK, depoda açıkça duran bir anahtarla imzalanıyor. Anlamı: kurulur ve
güncellenir, ama imza **"bunu kim derledi" sorusunun cevabı değil** —
anahtar herkese açık olduğu için aynı imzayı başkası da atabilir. Güvence,
APK'yı bu sayfadan indirmiş olmandan geliyor.

Anahtarı kendi gizli anahtarınla değiştirmek istersen yol README'de; ama
önce kasanı dışa aktar, çünkü imza değişince Android kurulu sürümün üstüne
yazmayı reddediyor ve uygulamayı kaldırmak kasayı da siliyor.

</details>

<details>
<summary>Kasa nedir</summary>

Cihazdan çıkmayan bir parola yöneticisi. Sunucu yok, hesap yok, eşitleme
yok. Kasanın tamamı tek bir şifreli dosya; kayıt adları, kaç kayıt olduğu ve
hangi kategorilerin kullanıldığı dâhil her şey o dosyanın içinde.

Argon2id ile cihaza göre ölçülmüş anahtar türetme, AES-256-GCM ya da
XChaCha20-Poly1305, Android Keystore sarmalayıcıları, biyometri, hızlı PIN,
kayıt bazlı ek kilit ve zorlama parolası. Ağa çıkan tek şey sızıntı
denetimi ve o da k-anonimlik ile: parolanın SHA-1 özetinin yalnızca ilk beş
hanesi gidiyor. Kapatılabilir.

Sınırların tamamı [README](https://github.com/Erenkng/G-venli-ifre-y-neticisi#açıkça-söylenen-sınırlar)
içinde yazılı.

</details>

Önceki sürümlerin notları kendi sayfalarında:
[**Sürümler**](https://github.com/Erenkng/G-venli-ifre-y-neticisi/releases)

> Bu sürümdeki arayüz hiçbir cihazda çalıştırılarak görülmedi; doğrulama
> derlemeyle sınırlı (debug, R8 sürüm derlemesi ve lint).
