# Bundled assets

Read-only assets packaged into the Application_Package. Everything the System
needs to run a full scan-to-speech cycle ships here or in `jniLibs/` — there is
no post-install download (Req 15.4, 19.2).

`.tflite` / `.lite` files are kept uncompressed (`androidResources.noCompress`
in `runtime/build.gradle.kts`) so the TFLite interpreter can memory-map them.

## Expected layout

```
assets/
  models/
    braille_dot_detector.tflite   # bundled object-detection model (Req 4.1) — task 6
  liblouis/
    tables/
      en-us-g1.ctb                # Grade 1 English table (Req 7.2, 7.4)      — task 10
      en-us-g2.ctb                # Grade 2 English table (Req 7.2, 7.4)      — task 10
      (+ any tables these include)
```

The model and tables are intentionally **not** committed in this skeleton
task; the directories and packaging configuration are established here so
tasks 6 and 10 can drop the artifacts in without further Gradle changes.
