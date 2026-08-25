import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// See RELEASE.md and keystore.properties.example. Absent on a fresh clone
// and in CI (neither commits a real keystore) — without it the release
// build type produces an unsigned APK, exactly AGP's own default when no
// signingConfig is assigned (there is no implicit debug-signing fallback
// for the release build type the way there is for debug). This file
// never changes that baseline behavior; it only ever adds real signing
// once a developer actually sets up keystore.properties.
val keystorePropertiesFile = file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) load(keystorePropertiesFile.inputStream())
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

        // First androidTest in this project needs a real instrumentation
        // runner (Room's MigrationTestHelper runs on-device, not on the JVM).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        compose = true
    }

    kotlinOptions {
        freeCompilerArgs += "-opt-in=androidx.wear.compose.foundation.ExperimentalWearFoundationApi"
    }

    sourceSets {
        // Room's MigrationTestHelper reads the exported schema JSONs as
        // test assets at runtime to build historical databases — without
        // this, PipBoyDatabaseMigrationTest compiles fine but fails at
        // run time unable to find them.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
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

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}

ksp {
    // Room writes one JSON per version under wear/schemas/ — the ground
    // truth PipBoyDatabaseMigrationTest's MigrationTestHelper builds
    // historical databases from. Versions 1 and 2 predate this flag
    // (exportSchema was false back then); those two were generated for
    // real from their actual historical commits via scratch git
    // worktrees, not hand-authored, and checked in alongside this one.
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    resolutionStrategy {
        // room-compiler's schema-export path (only ever exercised once
        // exportSchema=true, i.e. only just now) deserializes its own
        // JSON format via kotlinx.serialization. Without forcing a single
        // consistent version here, a stale/older kotlinx-serialization-core
        // can win classloading in the KSP worker and throw
        // AbstractMethodError on GeneratedSerializer.typeParametersSerializers()
        // — a real, documented Room/KSP interaction, not a mistake in this
        // build's own declared dependencies (which already resolve 1.8.1
        // correctly on their own; this just makes that non-negotiable).
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
    }
}
