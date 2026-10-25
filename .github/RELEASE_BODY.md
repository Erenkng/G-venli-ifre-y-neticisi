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

## 2.8'de yenilikler

Sızma denemesinin üçüncü turu. Yine yeni özellik yok; dört açık kapatıldı.

- **Passkey imzası, anahtarın hangi siteye ait olduğunu denetlemiyordu.**
  İmza, isteği yapanın bildirdiği site adına göre atılıyor; anahtar ise
  yalnızca kimlik numarasına göre bulunuyordu ve bu numaralar gizli değil.
  İkisinin birbirine uyduğunu kontrol eden hiçbir şey yoktu — kuralın
  tutmasını sağlayan tek şey, listeyi siteye göre süzen arayüzdü. Ama o bir
  görüntü süzgeci; imza atan kod onun doğru çalıştığına güvenmemeli. Artık
  imza aşaması kendi kontrolünü yapıyor.
- **Bir önbellek dosyası, hesabın olduğu siteleri açık yazıyordu.**
  Otomatik doldurmanın site doğrulama önbelleği şifresiz duruyordu ve
  içindeki site adları rastgele değil: sorgu yalnızca kasadaki kayıtların
  siteleri için atılıyor. Yani şifreli kasanın yanında, hesabın olduğu iki
  yüze kadar sitenin açık listesi vardı. Uygulamanın sözü "kaç kayıt olduğu
  dâhil her şey o şifreli dosyanın içinde" idi; bu dosya o sözün dışındaydı.
  Artık cihaza bağlı bir anahtarla mühürlü ve eski açık dosya siliniyor.
- **Önceki sürümdeki tarayıcı düzeltmesi aynı açığı taşıyordu.** 2.7'de
  sahte tarayıcıları engellemek için "kullanıcının varsayılan tarayıcısı mı"
  kontrolü eklenmişti. Ama Android, aday **tek** ise kullanıcı hiçbir şey
  seçmemiş olsa da o adayı "varsayılan" olarak bildiriyor — yani hiç
  tarayıcısı olmayan bir telefonda kötü niyetli bir uygulama yine geçebilirdi.
  Artık gerçek bir seçim aranıyor.
- **Dosya yazımı başarısız olursa dosya yok olabiliyordu.** Yazma, dosyayı
  silip yeniden adlandırma denemesine düşüyordu; ikinci deneme de tutmazsa
  dosya kayboluyor. En kötü hâli yanlış deneme sayacı: dosyası kaybolunca
  sayaç sıfırlanıyor, yani ana parolayı denemeye karşı bekleme süresi
  tamamen temizleniyor. Silme adımı kaldırıldı; artık başarısızlıkta eski
  dosya yerinde kalıyor.

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
