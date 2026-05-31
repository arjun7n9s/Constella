// :runtime — Native / bundled-runtime layer (Android library).
//
// Owns the native dependencies and bundled assets:
//   * OpenCV (.so in src/main/jniLibs) for Image_Preprocessor
//   * TensorFlow Lite runtime + the bundled .tflite model (assets) for Dot_Detector
//   * liblouis (.so + JNI bridge) and its Grade 1/Grade 2 tables (assets)
//
// Depends only on :domain (for shared data models/types). It exposes narrow
// runtime wrappers consumed by :pipeline. It must never reference :app or
// :pipeline (downward-only boundary).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.constella.braille.runtime"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ABIs for which native libraries (OpenCV, TFLite, liblouis) are bundled.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // The bundled .tflite model and liblouis tables live in src/main/assets.
    // The native .so files live in src/main/jniLibs (default source set location).
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets")
        }
    }

    // Do not compress the model/table assets so they can be memory-mapped at runtime.
    androidResources {
        noCompress += listOf("tflite", "lite")
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
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain"))

    implementation(libs.kotlinx.coroutines.android)

    // TensorFlow Lite runtime + delegates (Dot_Detector).
    implementation(libs.bundles.tensorflow.lite)

    // OpenCV is integrated via bundled .so + Java bindings under src/main/jniLibs
    // and a thin Kotlin wrapper. A Maven coordinate may be substituted once the
    // OpenCV distribution is pinned (see runtime/src/main/jniLibs/README.md).

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
