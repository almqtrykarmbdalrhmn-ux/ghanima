plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
fun prop(k: String) = (findProperty(k) as String?) ?: error("Missing gradle property $k")
android {
    namespace = "com.honestreward.app"; compileSdk = 34
    defaultConfig {
        applicationId = "com.honestreward.app"; minSdk = 24; targetSdk = 34; versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1); versionName = "0.1"
        buildConfigField("String", "API_BASE_URL", "\"${prop("API_BASE_URL")}\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT", "\"${if (prop("ADMOB_TEST_MODE") == "true") "ca-app-pub-3940256099942544/5224354917" else prop("ADMOB_REWARDED_UNIT")}\"")
        manifestPlaceholders["ADMOB_APP_ID"] = prop("ADMOB_APP_ID")
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-ads:23.3.0")
}
