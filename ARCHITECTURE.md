# Architecture — Braille Scanner (Constella)

Native Android app (Kotlin + Jetpack Compose) that scans physical Braille and
reads it aloud, 100% on-device and offline. This document describes the project
skeleton created in task 1.1: the four-layer module structure, the
downward-only dependency rule, and where the bundled native runtimes/assets go.

## Four-layer module structure

The build is split into four Gradle modules, one per architectural layer. The
design's rule is that **dependencies point downward only** — the pipeline and
native runtimes never reference the UI.

```
:app        UI layer (Android application, Jetpack Compose)
  │           Screens, navigation, Theming_Layer (Design_Tokens), a11y semantics.
  ├──────────────► :pipeline
  └──────────────► :domain

:pipeline   Pipeline layer (Android library)
  │           Camera_Module → Image_Preprocessor → Dot_Detector → Cell_Segmenter
  │           → Pattern_Recognizer → Translation_Engine → TTS_Engine (interfaces).
  ├──────────────► :runtime
  └──────────────► :domain

:runtime    Native / bundled-runtime layer (Android library)
  │           OpenCV (.so), TFLite runtime + .tflite model, liblouis (.so + JNI)
  │           + Grade 1/Grade 2 tables. All bundled in the APK.
  └──────────────► :domain

:domain     Domain layer (pure Kotlin/JVM)
              Data models, ConfidenceThresholds, AlignmentEvaluator, segmentation,
              GradeDetector, accuracy metrics, Notifier, ScanCoordinator contracts.
              Depends on NOTHING else in the project. Primary PBT target.
```

### Why these boundaries

- `:domain` is a **pure Kotlin/JVM** module (not Android). Keeping it free of
  Android/native dependencies makes the deterministic core fast to test with
  JVM property-based tests (kotest-property) and impossible to accidentally
  couple to the UI or hardware.
- `:runtime` isolates everything native and bundled (OpenCV, TFLite, liblouis)
  so the offline-forever / no-backend guarantee is enforced in one place.
- `:pipeline` depends on `:runtime` and `:domain` but **not** `:app`, so the
  recognition pipeline can be exercised with fixtures independently of the UI.
- `:app` sits on top and only renders state + dispatches intents.

The downward-only rule is enforced structurally by the `dependencies {}` blocks
in each module's `build.gradle.kts` (no module declares an upward dependency)
and by `settings.gradle.kts`.

## Technology mapping

| Layer | Module | Key dependencies |
|---|---|---|
| UI | `:app` | Jetpack Compose, Activity Compose, Lifecycle |
| Pipeline | `:pipeline` | CameraX (core/camera2/lifecycle/view), coroutines |
| Native/runtime | `:runtime` | TensorFlow Lite (+gpu/+support); OpenCV & liblouis via `jniLibs` |
| Domain | `:domain` | Kotlin stdlib, coroutines-core |

All versions are pinned centrally in `gradle/libs.versions.toml` (Gradle
version catalog).

## Bundled native libraries and assets

Packaged into the Application_Package with **no post-install download**
(Req 15.4, 19.2):

- `runtime/src/main/jniLibs/<abi>/` — `libopencv_java4.so`, `liblouis.so`,
  `liblouis_jni.so` (see `runtime/src/main/jniLibs/README.md`).
- `runtime/src/main/assets/models/braille_dot_detector.tflite` — the bundled
  detector model (kept uncompressed for memory-mapping).
- `runtime/src/main/assets/liblouis/tables/` — Grade 1/Grade 2 English tables.

The actual binaries/models/tables are added by later tasks (5, 6, 10); task 1.1
establishes the directory structure and packaging configuration only.

## Testing setup

- **JVM property-based testing:** kotest-property (`io.kotest:kotest-property`)
  plus `kotest-runner-junit5` and `kotest-assertions-core`, wired in `:domain`,
  `:pipeline`, and `:app`. The 35 design Correctness Properties are implemented
  here in later tasks.
- **Unit tests:** module `src/test/` source sets (JUnit Platform).
- **Instrumented tests:** module `src/androidTest/` source sets using
  `androidx.test.runner.AndroidJUnitRunner`, with Espresso, Compose UI test, and
  the Accessibility Test Framework available to `:app`.

## Building

This repo uses the Gradle wrapper. Requirements:

- JDK 17.
- Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`).
- The Gradle wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) must be present.
  It is a binary and is generated once with `gradle wrapper --gradle-version 8.9`
  (or copied from any Android Studio project) — see
  `gradle/wrapper/README.md`.

Common commands once the SDK + wrapper jar are present:

```
./gradlew :domain:test          # JVM unit + property tests for the domain core
./gradlew assembleDebug         # build the debug APK
./gradlew test                  # all JVM unit/property tests
./gradlew connectedAndroidTest  # instrumented tests (device/emulator)
```
