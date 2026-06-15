plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "online.db1k.safering.android.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.db1k.safering.android.wear"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha07")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("androidx.wear:wear-phone-interactions:1.0.1")
    implementation("com.google.android.horologist:horologist-composables:0.5.9")
}
