# Add project specific ProGuard rules here.

# Keep TensorFlow Lite runtime classes (Dot_Detector).
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep native (JNI) method signatures for OpenCV and liblouis bridges.
-keepclasseswithmembernames class * {
    native <methods>;
}
