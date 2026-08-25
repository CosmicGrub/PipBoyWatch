plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pipboywatch.app"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.pipboywatch.app"
        // Intentionally not centralized in the version catalog — this
        // genuinely differs from phone/shared's 26, unlike compileSdk/
        // targetSdk below which are identical across all three modules.
        minSdk = 30
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
        compose = true
    }

    kotlinOptions {
        freeCompilerArgs += "-opt-in=androidx.wear.compose.foundation.ExperimentalWearFoundationApi"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear)

    implementation(libs.health.connect.client)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Room's DB holds GPS routes, heart rate, free-text notes, and mirrored
    // notification content — SQLCipher encrypts the on-disk file; the
    // passphrase itself is generated and Keystore-wrapped via security-crypto
    // (see DatabasePassphrase.kt), never hardcoded.
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)
    implementation(libs.security.crypto)

    implementation(libs.datastore.preferences)

    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)

    implementation(libs.wear.input)
    implementation(libs.core.ktx)

    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
