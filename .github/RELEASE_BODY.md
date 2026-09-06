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

## 2.3'te yenilikler

- **Basış animasyonu takılmıyor.** İki sebep vardı: titreşim arayüz iş
  parçacığında çalınıyordu (binder çağrısı, tam animasyonun ilk karesinde) ve
  basış ölçeği beste aşamasında okunuyordu — parmak düğmede durduğu sürece
  düğmenin bütün iskeleti her karede yeniden kuruluyordu.
- **Titreşimin kendi ayarları.** Güç kademesi (dokununca örneği hemen çalıyor)
  ve ayrı bir dokunuş tıkırtısı anahtarı.
- **Yüzey efektleri kendi sayfasında**, sekizi tek tek kapatılabiliyor. Üçü
  yeni: basılı tutma dolumu, cam altı derinlik, odak ışığı. Zemin manzarası da
  eklendi.
- **Artı düğmesinin bulanıklığı** aşağıdan yukarı açılıyor ve iki ayrı
  yarıçapla çiziliyor — tek bir bulanıklığı soldurmak hayalet çift görüntü
  üretiyordu.
- **Tanecikli zemin** artık gradyanı griye yıkamıyor (Overlay karışımı) ve dört
  kademesi var.
- Aramada geçmişi temizleme · uzun basış menüsü sırayla açılıyor · ayarların
  kategori başlıkları hizalandı · kayıt satırındaki kopyalama düğmesi kaldırıldı.

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
