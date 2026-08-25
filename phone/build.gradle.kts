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

    buildFeatures {
        // Opt-in as of AGP 8+ — needed for BuildConfig.DEBUG (used to guard
        // a log line that would otherwise print shared note text in
        // release logcat, see SendNoteActivity).
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Phone-side Health Connect relay: the watch's own Health Connect access
    // is broken on this hardware (see :shared's HealthConnectManager) — the
    // phone is the reliable source, so it reads here and pushes a snapshot
    // to the watch over the Wear Data Layer.
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
