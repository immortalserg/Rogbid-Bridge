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
  versionCode = 2
  versionName = "0.2.0-dev"
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
