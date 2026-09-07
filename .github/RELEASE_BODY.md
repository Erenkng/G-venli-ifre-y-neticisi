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

## 2.4'te yenilikler

- **Kurtarma anahtarı ekranı bugüne kadar hiç açılmıyormuş.** Ekran seçimi
  yalnızca kilit durumuna bakıyordu ve kasa yaratıldığı anda kurulum yerini
  ana listeye bırakıyordu: anahtar üretiliyor, diske yazılıyor ve kullanıcıya
  gösterilmeden atlanıyordu. Ana parolasını unutanın tek çıkış yolu,
  varlığından haberi olmadığı bir koddu.
- **Kurulum baştan yazıldı.** "Yeni kasa / yedeğim var" çatalı · anahtarın üç
  grubunu geri yazdırma (onay kutusu kaldırıldı) · anahtarı dosyaya kaydetme
  ve karekod · ana parolayı kurtarma adımından **sonra** yineleme · sözcük
  dizisi önerisi · Dengeli/Sıkı sıkılık seçimi · son adımda otomatik
  doldurma, geçiş anahtarı ve içe aktarma.
- **Sızıntı bildirimleri artık gerçekten geliyor.** İzin manifestte tanımlıydı
  ve tarama onu kontrol ediyordu, ama hiçbir yerde istenmiyordu.
- **Ölçülemeyen kayıt yeşil nokta almıyor.** Notun parolası yok, kartın
  numarası da seçilmiş bir sır değil; ikisi de bir yargı bildiriyordu. Artık
  içi boş halka.
- **İki adımlı kodlar listede**, geri sayımıyla, dokununca kopyalanıyor. Üst
  sıra da sık kullanılanları gösteriyor — "son kullanılan" altındaki listeyi
  tekrarlıyordu.
- **Üreteç:** kadranın üstündeki yazı okunuyor artık (beyaz sabitti, kontrast
  1,5'e kadar düşüyordu) · kırılma süresi "78 bit"in yanında · "kullan"
  düğmesi kipe göre doğru alana yazıyor · geçmiş satırları kopyalanabiliyor ·
  sitenin kabul etmediği simgeler çıkarılabiliyor.
- **Çöp kutusunda sola kaydırmak** artık kalıcı silme onayı açıyor; önceden
  kaydı yeniden çöpe atıp otuz günlük sayacı sıfırlıyordu.
- Artı düğmesinin örtüsündeki çift bulanıklık · üretici kadranının 16
  saniyede bir sıçraması ve sallanması · tanıtım sayfalarına üç derinlik
  düzlemi.

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
