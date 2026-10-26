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

## 3.2'de yenilikler

**Kurulumda kasanın kilitlenmesi düzeldi.** Bu sürümün asıl sebebi bu.
Kurulumda kurtarma anahtarını kaydetmek, CSV seçmek ya da bildirim izni
vermek için sistemin ekranına gidildiğinde uygulama arka plana düşüyor ve
otomatik kilit devreye giriyordu — sıkı duruşta 15, dengelide 60 saniye.
Kurulum ekranı bunu görmüyordu ve altındaki her adım sessizce bozuluyordu:

- **Ana parola onayı** kilitli kasada her zaman "yanlış" diyordu. Yani
  doğru parolanızı yazıyor, yanlış cevabını alıyor ve üç denemeden sonra
  kasayı silip baştan başlama önerisiyle karşılaşıyordunuz.
- **İçe aktarma** hiçbir şey yazamıyor, "kayıt bulunamadı" diyordu.
- **Biyometri** kurulamıyordu.

Artık kurulumun seçicileri kilidi erteliyor; yine de kilitlenirse ekran
bunu saklamıyor, kilit ekranı çıkıyor ve açtığınızda kurulum kaldığı
adımdan devam ediyor.

**Duvar kâğıdı renkleri artık temanın tamamına iniyor** ve varsayılan
olarak açık geliyor. Eskiden yalnızca düğmeler ve rozetler duvar kâğıdına
uyuyordu; zemin, kartlar ve yazı rengi sabit kalıyordu. Şimdi hepsi
uyuyor. *Güç ve sızıntı renkleri bilerek dışarıda: zayıf kırmızı, orta
kehribar, güçlü yeşil kalıyor — bunlar süs değil uyarı.* Ayarlar →
Görünüm'den kapatılabilir.

**Tanıtımın açılışı üç kademede.** Çizim yerine oturuyor, 90 ms sonra yazı,
190 ms sonra düğmeler alttan yükseliyor. Çizim dışarıdan içeri geliyor,
yani açılış ekranındaki kadranın dışa doğru büyüyen hareketi kesilmeden
devam ediyor. Hareket kapalıysa üçü de anında.

Ayrıca: **içe aktarma artık yalan söylemiyor.** Yazma gerçekten olmadıysa
"aktarıldı" demiyor; eskiden diske hiçbir şey yazılmadığı hâlde "N kayıt
aktarıldı" diyebiliyordu.

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
