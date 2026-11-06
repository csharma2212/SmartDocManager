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
    defaultConfig { applicationId = "com.smartdoc.poc"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    buildFeatures {
        compose = true
    }
    signingConfigs {
        val path = System.getenv("SMARTDOC_KEYSTORE")
        if (!path.isNullOrBlank()) {
            create("personalRelease") {
                storeFile = file(path)
                storePassword = System.getenv("SMARTDOC_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("SMARTDOC_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("SMARTDOC_KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("personalRelease")?.let { signingConfig = it }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
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
