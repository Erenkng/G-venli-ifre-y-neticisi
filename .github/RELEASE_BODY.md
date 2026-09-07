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

## 2.6'da yenilikler

Bu sürümde yeni özellik yok. Uygulamaya saldıran gözle bakıldı ve çıkan
açıklar kapatıldı.

- **Yanlış deneme engeli telefonun saatiyle atlatılabiliyordu.** Bekleme
  süresi bir tarih damgası olarak yazılıyordu ve o tarihi kullanıcı
  değiştirebiliyor: üç yanlış dene, Ayarlar'dan saati ileri al, üç yanlış
  daha. Çalınmış bir telefonda ana parolayı sınırsız denemek demekti.
  Engel artık değiştirilemeyen bir saate bakıyor; yeniden başlatmak da
  süreyi silmiyor, baştan başlatıyor.
- **Bozuk bir yedek dosyası uygulamayı düşürebiliyordu.** Dosya, kendisini
  açmanın ne kadar bellek harcayacağını kendisi söylüyordu ve bu değer hiç
  denetlenmiyordu; üstelik parola sorulmadan önce kullanılıyordu. Yani
  "şunu bir açar mısın" diyen birinin parolayı bilmesine gerek yoktu.
  Dosyadan okunan bütün boyutlar artık sınırlı.
- **Güvenilen ağ ayarı ev adresini ele verebiliyordu.** Ağ adının özeti
  şifresiz ayar dosyasında duruyordu. Özet geri çevrilemez ama Wi-Fi ağları
  herkese açık veri tabanlarında konumlarıyla listeli — yani deneyerek
  bulunabilirdi. Özet artık telefondan çıkamayan bir anahtarla alınıyor.
  *Bu özelliği kullanıyorsan güvenilen ağını bir kez yeniden seçmen
  gerekiyor; eski kayıt siliniyor.*
- **Ana ekran aracı kaç kaydın olduğunu yazıyordu.** Kasanın sakladığını
  söylediği bilgilerden biri de tam olarak buydu ve araç, ekran koruması
  kapsamının dışında duruyor. Artık yalnızca kilit durumunu gösteriyor.
- **Zorlama parolasıyla açılan kasada gerçek parola sınanabiliyordu.**
  Artık sınanamıyor.

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
