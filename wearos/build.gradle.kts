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
  versionCode = 1
  versionName = "0.1.0"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
 implementation("androidx.health:health-services-client:1.0.0-rc02")
}
