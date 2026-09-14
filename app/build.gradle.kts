plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yourteam.sahara"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yourteam.sahara"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Local development only, debug builds only. Point this at the FastAPI instance
            // your emulator/device can actually reach:
            //  - "http://127.0.0.1:8000/" with `adb reverse tcp:8000 tcp:8000` running (works for
            //    both emulator and a USB-connected real device; the emulator's app-process socket
            //    connecting straight to 10.0.2.2 was unreliable in Stage 3A troubleshooting, while
            //    a plain adb port-forward consistently was not).
            //  - "http://10.0.2.2:8000/" as the emulator's classic host-loopback alias, if you're
            //    not using adb reverse.
            // Never a real/production backend URL. See backend/README.md and
            // docs/ONLINE_BACKEND_ARCHITECTURE.md.
            buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8000/\"")
        }
        release {
            optimization {
                enable = false
            }
            // No default: a real deployment must set this explicitly (Stage 10), never inherit a
            // local-development value.
            buildConfigField("String", "API_BASE_URL", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
    testImplementation(libs.retrofit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
