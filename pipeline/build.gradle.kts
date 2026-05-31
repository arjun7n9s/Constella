// :pipeline — Pipeline layer (Android library).
//
// The staged recognition pipeline and its narrow Kotlin interfaces:
//   Camera_Module (CameraX) -> Image_Preprocessor -> Dot_Detector ->
//   Cell_Segmenter -> Pattern_Recognizer -> Translation_Engine -> TTS_Engine
//
// Depends on :domain (data models / pure logic) and :runtime (native wrappers).
// It must never reference :app (downward-only boundary), so the UI can depend
// on the pipeline but not vice versa.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.constella.braille.pipeline"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // kotest runs on the JUnit Platform.
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain"))
    implementation(project(":runtime"))

    implementation(libs.kotlinx.coroutines.android)

    // CameraX (Camera_Module).
    implementation(libs.bundles.camerax)

    // JVM property-based + unit tests for the deterministic parts of the pipeline.
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
