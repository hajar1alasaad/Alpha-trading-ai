plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val phantomAppUrl = project.findProperty("PHANTOM_APP_URL") as String? ?: ""

android {
    namespace = "com.alphatrade.ai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.alphatrade.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        // Configure this to a public HTTPS page describing ALPHA TRADE AI before using Phantom.
        buildConfigField("String", "PHANTOM_APP_URL", "\"$phantomAppUrl\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // MIT-licensed TweetNaCl port: Phantom uses NaCl box (X25519 + XSalsa20-Poly1305).
    implementation("org.purejava:tweetnacl-java:1.1.3")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
