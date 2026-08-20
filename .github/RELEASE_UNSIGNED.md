
---

### ⚠️ Bu sürümdeki APK imzasız

Depoda imzalama anahtarı tanımlı değil, bu yüzden `app-release-unsigned.apk`
**kurulamaz**. Yanına konan `app-debug.apk` kurulabilir ama uygulama kimliği
`app.kasa.debug`; gerçek sürümün yerine geçmez ve küçültme (R8) uygulanmamıştır.

Kurulabilir bir sürüm üretmek için bir kez anahtar oluşturup depo gizli
anahtarlarına eklemek gerekiyor:

```sh
keytool -genkeypair -v -keystore kasa.jks -alias kasa \
  -keyalg RSA -keysize 4096 -validity 10000

base64 -w0 kasa.jks          # çıktıyı KASA_KEYSTORE_BASE64 olarak ekle
```

Depo → Settings → Secrets and variables → Actions:

| Gizli anahtar | Değer |
|---|---|
| `KASA_KEYSTORE_BASE64` | yukarıdaki base64 çıktısı |
| `KASA_KEYSTORE_PASSWORD` | anahtar deposu parolası |
| `KASA_KEY_ALIAS` | `kasa` |
| `KASA_KEY_PASSWORD` | anahtar parolası |

`kasa.jks` dosyasını kaybetme: aynı anahtarla imzalanmayan bir APK, kurulu
uygulamanın üzerine güncelleme olarak yüklenemez.
