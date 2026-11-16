plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localBuildDir = System.getenv("LOCALAPPDATA")?.let { "$it/AndroidDev/SmartDocManager-build/app" }
if (localBuildDir != null) {
    layout.buildDirectory.set(file(localBuildDir))
}

android {
    namespace = "com.smartdoc.poc"
    compileSdk = 35
    defaultConfig { applicationId = "com.smartdoc.poc"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "0.9" }
    buildFeatures {
        compose = true
    }
    signingConfigs {
        val envPath = System.getenv("SMARTDOC_KEYSTORE")
        val defaultReleaseKeystore = rootProject.file("../keystores/smartdocmanager-release.jks")
        val keystoreFile = if (!envPath.isNullOrBlank() && file(envPath).exists()) {
            file(envPath)
        } else if (defaultReleaseKeystore.exists()) {
            defaultReleaseKeystore
        } else null

        if (keystoreFile != null) {
            create("personalRelease") {
                storeFile = keystoreFile
                storePassword = System.getenv("SMARTDOC_STORE_PASSWORD") ?: "SmartDoc@Release2026"
                keyAlias = System.getenv("SMARTDOC_KEY_ALIAS") ?: "smartdocmanager"
                keyPassword = System.getenv("SMARTDOC_KEY_PASSWORD") ?: "SmartDoc@Release2026"
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("personalRelease")?.let { signingConfig = it }
        }
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs(rootProject.file("../shared-spec"), "src/main/assets")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
