plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

val localBuildDir = System.getenv("LOCALAPPDATA")?.let { "$it/AndroidDev/SmartDocManager-build/root" }
if (localBuildDir != null) {
    layout.buildDirectory.set(file(localBuildDir))
}

