plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pipboywatch.notes"
    compileSdk = libs.versions.compile.sdk.get().toInt()

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
        // Intentionally not centralized — genuinely differs from wear's 30.
        minSdk = 26
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = libs.versions.pipboy.version.code.get().toInt()
        versionName = libs.versions.pipboy.version.name.get()
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

    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)

    // Phone-side Health Connect relay: the watch's own Health Connect access
    // is broken on this hardware (see :shared's HealthConnectManager) — the
    // phone is the reliable source, so it reads here and pushes a snapshot
    // to the watch over the Wear Data Layer.
    implementation(libs.health.connect.client)
    implementation(libs.activity.ktx)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
}
