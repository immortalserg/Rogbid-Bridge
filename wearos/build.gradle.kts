plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 namespace = "org.watchbridge.wearos"
 compileSdk = 35
 defaultConfig {
  applicationId = "org.watchbridge.wearos"
  minSdk = 30
  targetSdk = 35
  versionCode = (System.getenv("WATCH_BRIDGE_VERSION_CODE")?.toIntOrNull() ?: 2)
  versionName = "0.2.0"
 }
 val keyPath = System.getenv("WATCH_BRIDGE_KEYSTORE_PATH")
 val keyPassword = System.getenv("WATCH_BRIDGE_KEYSTORE_PASSWORD")
 val keyAlias = System.getenv("WATCH_BRIDGE_KEY_ALIAS")
 val aliasPassword = System.getenv("WATCH_BRIDGE_KEY_PASSWORD")
 val releaseSigningReady = listOf(keyPath, keyPassword, keyAlias, aliasPassword).all { !it.isNullOrBlank() }
 signingConfigs {
  if (releaseSigningReady) {
   create("watchBridgeRelease") {
    storeFile = file(keyPath!!)
    storePassword = keyPassword
    this.keyAlias = keyAlias
    keyPassword = aliasPassword
   }
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   if (releaseSigningReady) signingConfig = signingConfigs.getByName("watchBridgeRelease")
  }
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
