import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// See RELEASE.md and keystore.properties.example — and its own comment on
// why keyAlias here MUST match wear's exactly. Absent on a fresh clone and
// in CI; without it the release build type produces an unsigned APK,
// exactly AGP's own default when no signingConfig is assigned.
val keystorePropertiesFile = file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) load(keystorePropertiesFile.inputStream())
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

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // Forces the transitive fragment dependency (was resolving to 1.1.0)
    // up to the 1.3.0+ floor MainActivity's registerForActivityResult
    // requires — see the version catalog entry for why this was latent
    // until :phone:assembleRelease actually ran for the first time.
    implementation(libs.fragment.ktx)
}
