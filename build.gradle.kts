// Top-level build file for Leapmotor Translator
// Modular architecture with Hilt DI, Room, and ViewModels

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.21" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.15" apply false
}

// Shared versions for all modules
extra.apply {
    set("kotlinVersion", "1.9.21")
    set("hiltVersion", "2.48.1")
    set("roomVersion", "2.6.1")
    set("lifecycleVersion", "2.7.0")
    set("coroutinesVersion", "1.7.3")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
