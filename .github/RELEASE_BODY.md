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

## 2.5'te yenilikler

Bu sürüm baştan sona **Güvenlik** ekranı.

- **Sızıntı taraması artık dakikalar değil saniyeler sürüyor.** Her parola için
  ayrı ve ardışık bir istek atılıyordu — dört yüz kayıtlı bir kasada dört yüz
  gidiş-dönüş. Artık ön eke göre gruplanıyor: her ayrı ön ek bir kez
  indiriliyor, kalanı cihazda çözülüyor.
- **Ve daha az bilgi sızdırıyor.** Aynı ön eki tekrar tekrar sormak, sunucuya
  "bu kullanıcının bu ön ekte on parolası var" demekti; k-anonimliğin sakladığı
  şeyi trafik deseni sızdırıyordu.
- **Ekran denetimin kapalı olduğunu söylemiyordu.** Kart k-anonimliğin nasıl
  çalıştığını anlatıyor ama açık mı kapalı mı olduğunu söylemiyordu. Artık
  durumu gösteriyor, anahtarı taşıyor ve son tarama ağa çıkamadıysa bunu yazıyor.
- **Puanın dökümü ekranda.** Taban ortalama güç, üstüne her bulgunun oranına
  göre inen ceza. Puanın kaç kaydın ölçülebilir sırrından hesaplandığı da
  yazılı — "kasanın puanı" ile "kasanın bir kısmının puanı" aynı şey değil.
- **Seyir çizgisi.** Son yirmi dört tarama puanı kasada tutuluyor; on parolasını
  düzelten kullanıcı artık ne kadar yol aldığını da görüyor.
- **"Yenileme zamanı geldi" doğru listeye götürüyor.** Kendi klasörü yoktu ve
  "bir yıldan eski" listesine bağlıydı: kullanıcı doksan günlük kuralını arıyor,
  bir yıldan eskileri buluyordu. Bulgu artık puana da giriyor.
- **SSH anahtarı, lisans ve banka kayıtları da taranıyor.** Sırlarını ayrı bir
  alanda tuttukları için taramanın tamamen dışında kalıyorlardı; iki anahtara
  aynı parolayı vermek artık görünüyor.

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
