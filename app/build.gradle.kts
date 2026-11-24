plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.barccelo.stickit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.barccelo.stickit"
        minSdk = 29
        targetSdk = 35
        // The app is built for Galaxy S25 Ultra (64-bit ARM). This avoids
        // bundling x86 and 32-bit WebP native libraries in the APK.
        ndk { abiFilters += "arm64-v8a" }
        versionCode = 13
        versionName = "0.5.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    val keystore = System.getenv("STICKIT_KEYSTORE_PATH")
    if (!keystore.isNullOrBlank()) {
        signingConfigs {
            create("stickitRelease") {
                storeFile = file(keystore)
                storePassword = System.getenv("STICKIT_STORE_PASSWORD")
                keyAlias = System.getenv("STICKIT_KEY_ALIAS")
                keyPassword = System.getenv("STICKIT_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!keystore.isNullOrBlank()) signingConfig = signingConfigs.getByName("stickitRelease")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(project(":gif-core"))
    implementation("com.github.bumptech.glide:gifdecoder:4.16.0")
    implementation("com.aureusapps.android:webp-android:1.1.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
