plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pipboywatch.notes"
    compileSdk = 36

    defaultConfig {
        // Must match the wear module's applicationId exactly — the Wear
        // Data Layer routes messages by AppKey (package+signature), so a
        // phone app and its watch companion have to share applicationId
        // to be recognized as a pair. Confirmed via a real on-device
        // "Failed to deliver message to AppKey" failure during Phase 7
        // testing before this fix. Kotlin package name (com.pipboywatch.
        // notes, via `namespace` above) is unaffected — only this ID
        // needs to match.
        applicationId = "com.pipboywatch.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
