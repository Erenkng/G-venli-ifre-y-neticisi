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

## 3.0'da yenilikler

Arayüz baştan düşünüldü. Yeni sürüm numarası bu yüzden: uygulama aynı işi
yapıyor ama artık başka görünüyor.

**Tez: liste bir kart yığını değil, bir dizin.** Bir parola yöneticisinin
ana ekranı özünde taradığın adların listesi. Basılı dizinler girdiyi kutuya
koymaz — işi tipografi ve ritim yapar. Kartlar hem arkadaki gradyanı
örtüyordu hem de boşluğun yapabileceği bir ayırmayı yüzey harcayarak
yapıyordu.

- **Gezinme çubuğu artık kelimelerle geziniyor.** Simgeler ve seçilinin
  arkasındaki hap gitti. Hedefler bir satırda duran kelimeler; seçili olan
  tam ağırlık ve genişlikte, ötekiler ince ve kısık. Seçim bir şeyin kayması
  değil, **kelimenin kendisinin öne çıkması** — biri ağırlaşırken komşuları
  geri çekiliyor. Altındaki ince ray kelimeden kelimeye yaylanarak kayıyor.
  *Simge yerine kelime bir tercih: simge daha hızlı tanınıyor, kelime daha
  açık söylüyor. Çubuk da kısaldı, listeye yer kaldı.*
- **Kartlar her ekrandan kalktı.** Ayarlar, üretici ve güvenlik listeleri de
  kasa listesiyle aynı dili konuşuyor: satırlar doğrudan zemin üzerinde,
  tek yüzey parmak basılıyken görünen ton.
- **Bölüm başlıkları sol kenara, marja çekildi.**
- **Cam, ait olduğu yerde kaldı:** üst/alt çubuklar ve açılan sayfalar.
  Gradyan aileleri, günün saatine göre değişmesi ve bütün yay hareketleri
  de yerinde.

Ve arayüze bakarken çıkan birkaç gerçek hata:

- **İngilizce kullananlar bozuk büyük harf görüyordu.** Başlıklar sabit
  Türkçe kurallarıyla büyütülüyordu, yani "Security" → "SECURİTY",
  "Settings" → "SETTİNGS". Alan etiketlerinde ve klasör alanında da aynı
  hata vardı. Dördü de düzeldi.
- **Parola geçmişindeki tarih hep Türkçe ay adı gösteriyordu** — İngilizce
  kullanan biri "25 Ekim 2026" okuyordu. Artık uygulamanın diline uyuyor.

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
