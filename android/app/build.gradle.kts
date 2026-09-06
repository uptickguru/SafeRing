plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "online.db1k.safering.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.db1k.safering.android"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 26
        versionName = "1.0.26"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // env → keystore.properties → CI defaults
            val propFile = rootProject.file("keystore.properties")
            val props = mutableMapOf<String, String>()
            if (propFile.exists()) {
                propFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                    val idx = trimmed.indexOf('=')
                    if (idx > 0) {
                        props[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
                    }
                }
            }
            fun fromEnv(name: String): String? = System.getenv(name)?.takeIf { v -> v.isNotBlank() }
            val ksPath = fromEnv("ANDROID_KEYSTORE_PATH")
                ?: props["storeFile"]
                ?: "safering-keystore.jks"
            val candidate = rootProject.file(ksPath)
            val sibling = rootProject.file("../${ksPath}")
            storeFile = if (candidate.exists()) candidate else if (sibling.exists()) sibling else candidate
            storePassword = fromEnv("ANDROID_KEYSTORE_PASSWORD")
                ?: fromEnv("KEYSTORE_PASSWORD")
                ?: props["storePassword"]
                ?: "kailey99"
            keyAlias = fromEnv("ANDROID_KEY_ALIAS")
                ?: props["keyAlias"]
                ?: "safering"
            keyPassword = fromEnv("ANDROID_KEY_PASSWORD")
                ?: fromEnv("KEYSTORE_PASSWORD")
                ?: props["keyPassword"]
                ?: "kailey99"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            firebaseAppDistribution {
                appId = "1:424555525887:android:8e00f3bd59649192267eca"
                artifactType = "APK"
                serviceCredentialsFile = System.getenv("FIREBASE_SERVICE_ACCOUNT") ?: ""
                groups = "beta-testers"
                releaseNotes = System.getenv("FIREBASE_RELEASE_NOTES")
                    ?: "Free-tier tripwire: trusted contact, Help SMS, family password, on-device check."
            }
        }
        debug {
            isMinifyEnabled = false
            // applicationIdSuffix removed so debug matches google-services.json package
            firebaseAppDistribution {
                appId = "1:424555525887:android:8e00f3bd59649192267eca"
                artifactType = "APK"
                serviceCredentialsFile = System.getenv("FIREBASE_SERVICE_ACCOUNT") ?: ""
                groups = "beta-testers"
                releaseNotes = System.getenv("FIREBASE_RELEASE_NOTES")
                    ?: "Free-tier tripwire: trusted contact, Help SMS, family password, on-device check."
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets.getByName("main").java.exclude(
        "**/EmailCheckScreen.kt",
        "**/AttachmentScanScreen.kt",
        "**/TranscriptCheckScreen.kt",
        "**/SubmitToCheckViewModel.kt",
        "**/CircleManager.kt"
    )
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Room (local database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Retrofit + OkHttp (API client)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // WorkManager (background sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Security (SHA-256 hashing)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


// After SafeRing FAD, also distribute Dreamer (same Firebase project / beta-testers).
// Triggered only when FIREBASE_SERVICE_ACCOUNT is set (CI).
tasks.configureEach {
    if (name == "appDistributionUploadRelease") {
        doLast {
            val sa = System.getenv("FIREBASE_SERVICE_ACCOUNT") ?: ""
            if (sa.isBlank()) {
                logger.lifecycle("Skip Dreamer FAD (no FIREBASE_SERVICE_ACCOUNT)")
                return@doLast
            }
            val script = rootProject.file("scripts/fad_dreamer_dedicated.py")
            if (!script.exists()) {
                logger.warn("Dreamer FAD script missing: ${script}")
                return@doLast
            }
            exec {
                environment("FIREBASE_SERVICE_ACCOUNT", sa)
                environment(
                    "DREAMER_APK_URL",
                    System.getenv("DREAMER_APK_URL")
                        ?: "https://safering.gulfmeridiangroup.com/downloads/Dreamer-1.0.3-4-release.apk"
                )
                environment("DREAMER_FIREBASE_PROJECT_ID", "gmg-dreamer-android")
                environment(
                    "DREAMER_FIREBASE_RELEASE_NOTES",
                    System.getenv("DREAMER_FIREBASE_RELEASE_NOTES")
                        ?: (System.getenv("FIREBASE_RELEASE_NOTES") ?: "Dreamer Android beta")
                )
                commandLine("python3", script.absolutePath)
            }
        }
    }
}

