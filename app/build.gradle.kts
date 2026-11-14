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
  versionCode = 9
  versionName = "0.6.0"
 }
 lint {
  disable += "ExpiredTargetSdkVersion"
 }
 signingConfigs {
  create("releaseKey") {
   val path = System.getenv("ROGBID_KEYSTORE_PATH")
   if (!path.isNullOrBlank()) {
    storeFile = file(path)
    storePassword = System.getenv("ROGBID_KEYSTORE_PASSWORD")
    keyAlias = System.getenv("ROGBID_KEY_ALIAS")
    keyPassword = System.getenv("ROGBID_KEY_PASSWORD")
   }
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = false
   signingConfig = signingConfigs.getByName("releaseKey")
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
