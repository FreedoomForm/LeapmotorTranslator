plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.leapmotor.translator"
    compileSdk = 31

    defaultConfig {
        applicationId = "com.leapmotor.translator"
        minSdk = 28
        targetSdk = 31
        versionCode = 3
        versionName = "3.0.0"

        // Leapmotor C11 central screen resolution
        resConfigs("ru", "zh")
        
        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        
        // Build config fields
        buildConfigField("boolean", "ENABLE_KALMAN_FILTER", "true")
        buildConfigField("int", "MAX_CACHE_SIZE", "5000")
        buildConfigField("int", "MAX_NODES_PER_FRAME", "128")
        buildConfigField("long", "UPDATE_DEBOUNCE_MS", "50L")
        
        // Instrumented tests
        testInstrumentationRunner = "com.leapmotor.translator.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ENABLE_STRICT_MODE", "false")
            buildConfigField("boolean", "VERBOSE_LOGGING", "false")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            buildConfigField("boolean", "ENABLE_STRICT_MODE", "true")
            buildConfigField("boolean", "VERBOSE_LOGGING", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.ExperimentalStdlibApi"
        )
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

// Hilt configuration
kapt {
    correctErrorTypes = true
}

dependencies {
    // ============================================================================
    // KOTLIN
    // ============================================================================
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ============================================================================
    // ANDROIDX CORE
    // ============================================================================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ============================================================================
    // HILT - DEPENDENCY INJECTION
    // ============================================================================
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")
    
    // Hilt ViewModel integration
    implementation("androidx.hilt:hilt-navigation-fragment:1.1.0")
    
    // ============================================================================
    // ROOM - DATABASE
    // ============================================================================
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ============================================================================
    // LIFECYCLE & VIEWMODEL
    // ============================================================================
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.7.0")

    // ============================================================================
    // GOOGLE ML KIT - TRANSLATION
    // ============================================================================
    implementation("com.google.mlkit:translate:17.0.2")

    // ============================================================================
    // UNIT TESTING
    // ============================================================================
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // Hilt testing
    testImplementation("com.google.dagger:hilt-android-testing:2.48.1")
    kaptTest("com.google.dagger:hilt-android-compiler:2.48.1")

    // ============================================================================
    // INSTRUMENTED TESTING (ESPRESSO)
    // ============================================================================
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    
    // Hilt instrumented testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48.1")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48.1")
    
    // Room testing
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}

// Print build info
tasks.register("printBuildInfo") {
    doLast {
        println("╔════════════════════════════════════════════╗")
        println("║ LeapmotorTranslator v3.0.0                 ║")
        println("║ Hilt DI + Room + ViewModel + Modular       ║")
        println("╚════════════════════════════════════════════╝")
    }
}
