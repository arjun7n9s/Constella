/**
 * Native / bundled-runtime layer for the Braille Scanner.
 *
 * Owns the native libraries and bundled assets and exposes thin Kotlin
 * wrappers consumed by the `pipeline` layer:
 *  - OpenCV (.so in src/main/jniLibs) for Image_Preprocessor — task 5
 *  - TensorFlow Lite runtime + bundled .tflite model (assets) for Dot_Detector — task 6
 *  - liblouis (.so + JNI bridge) and Grade 1/Grade 2 tables (assets) — task 10
 *
 * Everything here is bundled inside the Application_Package (no post-install
 * downloads) to satisfy the offline-forever / no-backend requirement.
 *
 * Dependency rule: depends only on `domain`. Must never reference `pipeline`
 * or `app`.
 */
package com.constella.braille.runtime
