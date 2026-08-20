plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kasa"

    // Kasa yalnızca Android 16 ve üstünde çalışır: minSdk = targetSdk = 36.
    //
    // Android 17'de çalışmak için burayı yükseltmek GEREKMEZ; Android sürümleri
    // geriye dönük uyumludur ve targetSdk yalnızca hangi davranış değişikliklerine
    // katıldığını belirler. API 37 SDK'sı kurulduğunda yapılacak tek şey bu
    // sayıları 37'ye çekmek — uygulama kodunda başka hiçbir yerde sürüm dalı yok,
    // çünkü minSdk 36 ile tüm eski sürüm kontrolleri kaldırıldı.
    compileSdk = 36

    defaultConfig {
        applicationId = "app.kasa"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Android 16 yalnızca 64 bit cihazlarda çalışıyor; 32 bit ABI'leri
        // paketlemek APK'yı büyütmekten başka bir işe yaramaz.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Yalnızca gerçekten çevrilmiş diller paketlensin; kitaplıkların getirdiği
    // yüzlerce dil dizini APK'yı büyütmekten başka işe yaramıyor.
    androidResources {
        localeFilters += listOf("tr", "en")
    }

    // Sürüm imzalama yapılandırması ortam değişkenlerinden geliyor.
    //
    // Anahtar deposu depoya konmuyor: imzalama anahtarı uygulamanın kimliği
    // demek ve onu ele geçiren biri, kullanıcıların güncelleme sanıp kuracağı
    // sahte bir sürüm yayımlayabilir. CI bu değerleri gizli anahtarlardan
    // (secrets) alıyor; tanımlı değilse imzasız derleniyor ve derleme yine de
    // başarılı oluyor — böylece anahtarı olmayan biri de projeyi derleyebilir.
    val keystorePath: String? = System.getenv("KASA_KEYSTORE_PATH")

    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KASA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KASA_KEY_ALIAS")
                keyPassword = System.getenv("KASA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sürüm derlemesinde hata ayıklama tamamen kapalı.
            isDebuggable = false
            // Anahtar yoksa null kalıyor; APK imzasız çıkıyor ve kurulamıyor
            // ama derleme kırılmıyor.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Yerel kitaplıklar APK içinde sıkıştırılmadan durur ve doğrudan
            // eşlenir. Android 15+ cihazların bir kısmı 16 KB bellek sayfası
            // kullanıyor; sıkıştırılmış kitaplık orada eşlenemez.
            // Argon2Kt 1.6.0'ın dört ABI'si de 16 KB hizalı (doğrulandı).
            useLegacyPackaging = false
        }
    }
    dependenciesInfo {
        // APK'ya imzalı bağımlılık meta verisi gömülmez (gizlilik).
        includeInApk = false
        includeInBundle = false
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.argon2kt)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
