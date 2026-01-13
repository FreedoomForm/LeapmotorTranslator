pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LeapmotorTranslator"

// ============================================================================
// MODULAR ARCHITECTURE
// ============================================================================
// Core modules (shared across features)
include(":core:common")
include(":core:data")
include(":core:domain")
include(":core:ui")

// Feature modules
include(":feature:translator")
include(":feature:dictionary")

// Main app module (composes all features)
include(":app")
