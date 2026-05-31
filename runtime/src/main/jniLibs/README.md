# Native libraries (`jniLibs`)

This directory holds the bundled native `.so` libraries packaged into the
Application_Package. Nothing is downloaded post-install (offline-forever /
no-backend requirement: Req 15.4, 19.2).

Gradle packages every `<abi>/*.so` file found here automatically; the ABIs
built are filtered by `defaultConfig.ndk.abiFilters` in
`runtime/build.gradle.kts`.

## Expected layout

```
jniLibs/
  arm64-v8a/
    libopencv_java4.so      # OpenCV for Android (Image_Preprocessor, Req 3)   — task 5
    liblouis.so             # liblouis Braille translator (Req 7)              — task 10
    liblouis_jni.so         # thin JNI bridge to liblouis                      — task 10
  armeabi-v7a/
    (same set)
  x86_64/
    (same set, for emulator)
```

TensorFlow Lite native code is supplied transitively by the
`org.tensorflow:tensorflow-lite*` AAR dependencies (see `runtime/build.gradle.kts`),
so no TFLite `.so` is hand-placed here.

## Sourcing the binaries

- **OpenCV:** use the official OpenCV Android SDK `libopencv_java4.so` for each
  ABI, or substitute a Maven coordinate in `gradle/libs.versions.toml` once the
  distribution is pinned.
- **liblouis:** cross-compiled with the Android NDK from the pinned liblouis
  release, together with a small JNI bridge.

These binaries are intentionally **not** committed in this skeleton task; the
directory structure and bundling configuration are established here so later
tasks (5, 10) can drop the artifacts in without further Gradle changes.
