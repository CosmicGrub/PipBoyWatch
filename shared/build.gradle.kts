plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pipboywatch.shared"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        // Matches phone's floor (the lower of the two consumers) so this
        // module never forces phone's minSdk up just by being a dependency.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // PendingRequestTracker logs a couple of diagnostic lines via
            // android.util.Log on its stale-reply/expiry paths. Without
            // this, Android's unmocked Log throws "Method d ... not
            // mocked" in a plain JVM unit test — this is the standard,
            // dependency-free fix (vs. pulling in Robolectric just to
            // stub two log lines).
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.health.connect.client)
    implementation(libs.play.services.wearable)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit)
}
