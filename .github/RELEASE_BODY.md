<!--
  Bu dosya her sürümün notlarının **tamamı** olarak yayımlanıyor.

  Kısa tutulmalı: 967 satıra çıktığı bir dönem oldu ve APK bağlantısına
  ulaşmak sayfayı baştan sona kaydırmayı gerektiriyordu — yani notlar,
  varlık sebebi olan indirmenin önüne geçmişti.

  Kural: yalnızca bu sürümün yenilikleri, birer satır. Gerekçe, ayrıntı ve
  "neden böyle yapıldı" buraya değil commit'lere yazılıyor.
-->

## ⬇️ İndir: `app-release.apk`

Aşağıdaki **Assets** bölümünden. İmzalı, doğrudan kurulur ve kurulu sürümün
üstüne güncellenir. Android 16 (API 36) ve üstü, 64 bit.

---

## 3.2

Kurulum baştan sona elden geçti.

- Kurulum sırasında kasanın kilitlenmesi düzeldi. Doğru ana parolanın
  "yanlış" görünmesine ve içe aktarmanın boş dönmesine bu yol açıyordu.
- Ana parola kuralı artık yazarken uygulanıyor: güç göstergesinde kabul
  çizgisi işaretli, düğme eşik geçilene kadar kapalı.
- Kurulumda ilerleme ipliği: sekiz ekran tek bir yol gibi okunuyor.
- Tanıtımın açılışı üç kademede geliyor.
- Duvar kâğıdı renkleri artık temanın tamamına iniyor ve varsayılan açık.
  Ayarlar → Görünüm'den kapatılabilir.

---

<details>
<summary>İmza hakkında</summary>

APK, depoda açıkça duran bir anahtarla imzalanıyor. Kurulur ve güncellenir,
ama imza "bunu kim derledi" sorusunun cevabı değil — anahtar herkese açık.
Güvence, APK'yı bu sayfadan indirmiş olmandan geliyor.

</details>

Kasa, cihazdan çıkmayan bir parola yöneticisi: sunucu yok, hesap yok,
eşitleme yok. Ayrıntılar ve açıkça söylenen sınırlar
[README](https://github.com/Erenkng/G-venli-ifre-y-neticisi#açıkça-söylenen-sınırlar)
içinde.

Önceki sürümler: [**Sürümler**](https://github.com/Erenkng/G-venli-ifre-y-neticisi/releases)

> Bu sürümdeki arayüz hiçbir cihazda çalıştırılarak görülmedi; doğrulama
> derlemeyle sınırlı (debug, R8 sürüm derlemesi ve lint).
