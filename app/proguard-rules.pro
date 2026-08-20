# --- Kasa · sürüm derlemesi kuralları -------------------------------------

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.kasa.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class app.kasa.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.kasa.data.model.**$$serializer { *; }

# Otomatik doldurmanın alan adı doğrulama önbelleği de serileştiriliyor;
# sınıf data/model altında olmadığı için yukarıdaki kural kapsamıyor.
-keepclassmembers class app.kasa.autofill.** {
    *** Companion;
}
-keepclasseswithmembers class app.kasa.autofill.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.kasa.autofill.**$$serializer { *; }

# Satır içi öneri arayüzü Slice üzerinden yansımayla kuruluyor
-keep class androidx.autofill.inline.** { *; }
-dontwarn androidx.autofill.**

# Argon2Kt yerel köprüsü
-keep class com.lambdapioneer.argon2kt.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing
-dontwarn com.google.zxing.**

# Otomatik doldurma servisi ve sistem giriş noktaları sistem tarafından adla çözülür
-keep class app.kasa.autofill.KasaAutofillService { *; }
-keep class app.kasa.passkey.KasaCredentialProviderService { *; }
-keep class app.kasa.passkey.PasskeyActivity { *; }
-keep class app.kasa.tile.VaultTileService { *; }
-keep class app.kasa.widget.** { *; }

# Credential Manager sağlayıcı API'si yansımayla sınıf çözüyor
-keep class androidx.credentials.provider.** { *; }
-dontwarn androidx.credentials.**

# Gizli veri kapları: toString() gövdeleri bilerek içerik göstermiyor,
# iyileştirici bunları satır içine alıp çıkarmasın.
-keep class app.kasa.core.crypto.SecretBytes { *; }
-keep class app.kasa.core.crypto.SecretText { *; }
-keep class app.kasa.core.crypto.SecretTextSerializer { *; }

# Kayıt (log) çağrılarını sürüm derlemesinden tamamen kaldır
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
