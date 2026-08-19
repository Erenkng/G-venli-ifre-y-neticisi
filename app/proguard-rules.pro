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
-keep class app.kasa.tile.VaultTileService { *; }
-keep class app.kasa.widget.** { *; }

# Kayıt (log) çağrılarını sürüm derlemesinden tamamen kaldır
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
