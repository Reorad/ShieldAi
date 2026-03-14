// Apply the model download script
apply(from = "download_models.gradle")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.floatingdot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.floatingdot"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Ensure native libraries for emulator (x86_64) and phones (arm64-v8a) are included
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
    // MediaPipe requires models not to be compressed in the APK
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // MediaPipe Tasks Text dependency (Updated version)
    implementation("com.google.mediapipe:tasks-text:0.20230731")

    // ML Kit Text Recognition for OCR (Updated version)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // HTTPS Depedency for Safe Browsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
