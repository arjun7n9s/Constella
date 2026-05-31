// :app — UI layer (Android application, Jetpack Compose).
//
// Screens, composables, navigation, the Theming_Layer (Design_Tokens), and
// accessibility semantics. Holds no business logic; renders ScanResult state
// and dispatches Operator intents.
//
// Depends downward on :pipeline and :domain only. This is the top of the
// dependency arrows; nothing depends on :app.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.constella.braille"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.constella.braille"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
    // Downward-only: UI depends on pipeline and domain.
    implementation(project(":pipeline"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM aligns all Compose artifact versions.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit + property tests for any pure UI-layer logic (e.g. token/contrast checks).
    testImplementation(libs.bundles.kotest)

    // Instrumented + Compose UI tests (accessibility, semantics).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.accessibility.test.framework)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
