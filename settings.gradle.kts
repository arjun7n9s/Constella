// Root Gradle settings for the Braille Scanner (Constella) project.
//
// Declares the four-layer module structure. Dependencies point downward only:
//   :app (UI)  ->  :pipeline  ->  :runtime
//   :app (UI)  ->  :domain
//   :pipeline  ->  :domain
//   :runtime   ->  :domain
// The :domain module depends on nothing else in the project (pure Kotlin).

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "BrailleScanner"

// Four-layer modules.
include(":app")        // UI layer (Jetpack Compose) — depends on :domain and :pipeline
include(":pipeline")   // Pipeline layer (CameraX, staged recognition) — depends on :domain and :runtime
include(":runtime")    // Native/runtime layer (OpenCV, TFLite, liblouis JNI) — depends on :domain
include(":domain")     // Domain layer (pure Kotlin) — depends on nothing else in the project
