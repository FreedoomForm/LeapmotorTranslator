plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.leapmotor.translator"
    compileSdk = 28  // Android 9 Pie

    defaultConfig {
        applicationId = "com.leapmotor.translator"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"

        // Leapmotor C11 central screen resolution
        resConfigs("ru", "zh")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // OpenGL ES 3.0 requirement for Adreno 640
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Google ML Kit - On-device Translation
    implementation("com.google.mlkit:translate:17.0.2")

    // Lifecycle for service
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
}
