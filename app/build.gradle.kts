plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "org.rogbid.bridge"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.rogbid.bridge"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-dev"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
