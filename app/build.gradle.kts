plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.parakeet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.parakeet"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // A 0.6B model is only practical on 64-bit phones; also keeps the APK small.
        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("k2-fsa:sherpa-onnx:1.13.8@aar")
}
