import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: read from keystore.properties in the project root (never committed).
// Create it with create-upload-key.ps1. Without it, release builds fall back to the debug key
// so they can be installed for testing - Google Play rejects those, so always create the key first.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// A Play upload must never be debug-signed: refuse to build the bundle without the upload key.
if (!keystorePropsFile.exists() && gradle.startParameter.taskNames.any { it.contains("bundleRelease", ignoreCase = true) }) {
    throw GradleException(
        "No upload key: keystore.properties is missing.\n" +
            "Run: powershell -ExecutionPolicy Bypass -File .\\create-upload-key.ps1   then build again."
    )
}

android {
    namespace = "com.troks.tomatorouters"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Google Play package name - permanent once the app is created in Play Console
        applicationId = "com.troks.tomatorouter.remote"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.1.7"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (keystorePropsFile.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.okhttp)
    implementation(libs.androidx.biometric)
    implementation(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    // newer than the one biometric brings: older FragmentActivity breaks permission requests from Compose
    implementation(libs.androidx.fragment)
    debugImplementation(libs.compose.ui.tooling)
}
