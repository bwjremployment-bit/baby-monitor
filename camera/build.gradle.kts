plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.family.jiandie.camera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.family.jiandie.camera"
        minSdk = 29          // Realme 6 (Android 10). See TECHNICAL_DECISIONS TD-009.
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.shared)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.webrtc)
    implementation(libs.zxing.core)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
