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

## 2.9'da yenilikler

Bu sürümde iki düzeltme ve ana ekranın yeni görünümü var.

- **Parmak izi iki kez soruluyordu.** Başka bir uygulamada parola
  doldururken Kasa parmak izini iki kez istiyordu. "Açılışta bir kez sor"
  kuralı, ekranın yerinde kalmasına bağlı bir yerde tutuluyordu; ekran
  yeniden kurulduğunda kural sıfırlanıyor ve istem ikinci kez açılıyordu.
  Aynı hata üç yerde vardı (doldurma penceresi, kilit ekranı, passkey
  penceresi), üçü de düzeltildi.
- **Ortalanmış şekiller soldan çıkıp ortaya sıçrıyordu.** Kurulumda parola
  yazarken güç göstergesinin yaptığı şey buydu. Beliriş efektinin içindeki
  toz katmanı, sarmalayan kutuyu tam genişliğe açıyor; içerik sola düşüyor
  ve efekt bitince ortaya sıçrıyordu. Bu beliriş efektiyle gelen her
  ortalanmış öğeyi etkiliyordu, hepsi düzeldi.
- **Kurtarma anahtarının gruplarını geri yazma adımı kaldırıldı.** Amacı
  anahtarı gerçekten kaydettiğini kanıtlatmaktı ama bedeli yanlış yere
  düşüyordu: kodu kâğıda yeni yazmış birinden parçalarını elle girmesini
  istemek. Anahtarı dosyaya kaydetme ve karekod seçenekleri aynı ekranda
  duruyor; kaydetmeden geçen biri de kilitlenmiyor, ayarlardan yeni bir
  kurtarma anahtarı üretilebiliyor.
- **Kasa listesi artık bir dizin.** Kayıtlar kart içinde değil, doğrudan
  zemin üzerinde. Ad belirgin biçimde iri; ikincil satır daha sakin. Parola
  gücü satırın sonundaki noktadan **sol kenardaki kısa çizgiye** taşındı —
  listeye dikey bakarken o çizgiler tek bir sütun oluşturuyor, yani hangi
  kayıtların zayıf olduğu aramadan görünüyor. Kart kalkınca arkadaki
  gradyan da ilk kez gerçekten görünüyor. Arama sonuçları da aynı görünümü
  kullanıyor.

Gezinme çubuğu bu sürümde de aynı — sıradaki sürümde değişiyor.

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
