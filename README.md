<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/✦_CONSTELLA-Braille_to_Speech-00C896?style=for-the-badge&labelColor=0D1117&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiMwMEM4OTYiIHN0cm9rZS13aWR0aD0iMiI+PGNpcmNsZSBjeD0iOCIgY3k9IjQiIHI9IjIiLz48Y2lyY2xlIGN4PSIxNiIgY3k9IjQiIHI9IjIiLz48Y2lyY2xlIGN4PSI4IiBjeT0iMTIiIHI9IjIiLz48Y2lyY2xlIGN4PSIxNiIgY3k9IjEyIiByPSIyIi8+PGNpcmNsZSBjeD0iOCIgY3k9IjIwIiByPSIyIi8+PGNpcmNsZSBjeD0iMTYiIGN5PSIyMCIgcj0iMiIvPjwvc3ZnPg==">
    <img alt="Constella" src="https://img.shields.io/badge/✦_CONSTELLA-Braille_to_Speech-00504D?style=for-the-badge&labelColor=F0F0F0">
  </picture>
</p>

<p align="center">
  <strong>An offline-first Android app that sees Braille through a phone camera<br>and speaks it aloud — no internet, no server, no compromise on privacy.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white&style=flat-square" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white&style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white&style=flat-square" alt="Compose">
  <img src="https://img.shields.io/badge/TFLite-On_Device-FF6F00?logo=tensorflow&logoColor=white&style=flat-square" alt="TFLite">
  <img src="https://img.shields.io/badge/Privacy-100%25_Offline-1B5E20?style=flat-square" alt="Offline">
  <img src="https://img.shields.io/badge/License-Private-555?style=flat-square" alt="License">
</p>

---

## The Problem

Over **85%** of Braille-eligible individuals worldwide cannot read Braille. But Braille doesn't just exist for Braille readers — it appears on worksheets, labels, signs, handwritten notes, and exam papers, surrounded by people who *want* to understand it but never learned to read by touch.

**Constella** turns an ordinary Android phone into a private, offline bridge from raised dots to spoken words.

> *Braille is made of points that become meaningful when their pattern is understood.*
> *Constella is named after constellations: scattered points connected into something readable.*

---

## How It Works

```
 ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
 │   📷 Camera   │────▶│  🔬 Detect   │────▶│  🧩 Segment  │────▶│  🔤 Recognize│
 │  Raking Light │     │  Dot Finder  │     │  Cell Grid   │     │  6-dot Map   │
 └──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                                                       │
 ┌──────────────┐     ┌──────────────┐     ┌──────────────┐           │
 │   🔊 Speak   │◀────│  📊 Display  │◀────│  🌐 Translate│◀──────────┘
 │  TTS Engine  │     │  Confidence  │     │  Grade 1 / 2 │
 └──────────────┘     └──────────────┘     └──────────────┘
```

The computer vision challenge is the heart of this project. A clean digital Braille character is already structured. A real page is not. Constella reasons through lighting, paper angle, blur, motion, missing dots, false detections, and irregular handwritten spacing — all on-device, in real time.

---

## ✦ Features

### 📷 Intelligent Camera Pipeline
- **Raking-light torch** biases lighting to cast shadows across embossed dots, dramatically improving detection contrast
- **Close-range macro focus** locks to a 5–25 cm working window with diopter-calibrated manual focus
- **Adaptive resolution** — high-res stills for capture mode, throttled low-res streams for live analysis
- **Typed error recovery** — every camera failure (permission denied, no torch, no macro, capture failed) maps to a deterministic user-facing message with a specific recovery action

### 🧠 Multi-Stage Recognition
- **Illumination normalization** with morphological top-hat filtering to flatten uneven lighting
- **Perspective correction** using document-boundary detection and four-point homography
- **TFLite dot detection** running entirely on-device with GPU delegate support
- **Grid-based cell segmentation** clustering dots into standard 2×3 Braille cells
- **Reading-order line grouping** with median-height tolerance and word-boundary detection at 1.5× median spacing
- **Degraded-region handling** — dots that don't form valid grids are flagged as uncertain rather than silently dropped

### 🔤 Translation & Grade Detection
- **Automatic Grade detection** — heuristic analysis of recognized patterns determines Grade 1 vs Grade 2
- **Manual override** — operators can lock to a specific grade when they know the source material
- **liblouis-powered translation** via JNI for standards-compliant Braille-to-English conversion
- **Untranslatable cell reporting** — cells that don't map to known characters are surfaced, not hidden

### 🔊 Confidence-First Output
Constella treats recognition confidence as a **first-class signal**, not an afterthought:

$$
c \in [0,\, 1] \quad \text{propagated through every pipeline stage}
$$

- Per-character **uncertainty marking** highlights characters below the display threshold
- **Low-confidence scans** trigger a rescan recommendation with the likely cause (lighting, distance, motion)
- **Handwritten mode** labels all results with a lower-confidence tier indicator
- **First-scan reliability notice** spoken aloud when entering handwritten mode for the first time

### ♿ Accessibility by Design
- Every interactive control has a descriptive **content description** for screen readers
- All primary touch targets enforce a **≥ 48 dp** minimum (configurable via design tokens)
- **High-contrast theme** with pure black backgrounds and maximum-contrast foregrounds
- **Dual-channel notifications** — every guidance message is delivered both visually and via speech
- Spoken **alignment guidance** walks the operator through distance, angle, lighting, and steadiness

### 🔒 Privacy Architecture
- **100% offline by default** — camera frames, recognition, translation, and speech never leave the device
- **Default-deny `CloudTransmissionGate`** — cloud features require explicit opt-in *and* a provider allowlist check
- No analytics, no telemetry, no tracking in the default configuration

---

## Architecture

Four Gradle modules with a strict **downward-only dependency rule**:

```
 ┌─────────────────────────────────────────────────────┐
 │  :app                                               │
 │  Jetpack Compose • Screens • Design Tokens • A11y   │
 ├──────────┬──────────────────────────────────────────┤
 │          │                                          │
 │          ▼                                          │
 │  :pipeline                                          │
 │  CameraX • TTS Engine • Privacy Gates               │
 │  ┌───────┴──────────────────────┐                   │
 │  │                              │                   │
 │  ▼                              ▼                   │
 │  :runtime                   :domain                 │
 │  TFLite • OpenCV            Pure Kotlin/JVM         │
 │  liblouis JNI               Models • Alignment      │
 │  Native .so libs            Segmentation • Grade    │
 │                             Recognition • Policies  │
 └─────────────────────────────────────────────────────┘
```

| Layer | Module | Runs on | Key Tech |
|-------|--------|---------|----------|
| **UI** | `:app` | Android | Jetpack Compose, Material 3, Design Tokens |
| **Pipeline** | `:pipeline` | Android | CameraX, Camera2 interop, TTS, Privacy Gates |
| **Runtime** | `:runtime` | Android | TFLite (+ GPU), OpenCV `.so`, liblouis JNI |
| **Domain** | `:domain` | **Pure JVM** | Kotlin stdlib, coroutines-core |

> **Why?** The `:domain` module has zero Android dependencies. Every model, threshold, policy, and algorithm is testable on the JVM with sub-second feedback — no emulator, no device, no camera required.

### Design Token System

The UI is built on a **semantic token architecture** (`ColorTokens`, `TypographyTokens`, `SpacingTokens`) consumed through Compose `CompositionLocal`s. Swapping from the default palette to the high-contrast accessibility theme is a pure value swap — zero structural changes propagate through every component automatically.

---

## The Math

A standard Braille cell is a $2 \times 3$ grid of dot positions:

$$
\begin{bmatrix} 1 & 4 \\ 2 & 5 \\ 3 & 6 \end{bmatrix}
\quad \Rightarrow \quad 2^6 = 64 \text{ possible patterns}
$$

**Line grouping** uses median cell height:

$$
\text{same line} \iff |y_{\text{center}} - y_{\text{line}}| \leq 0.5 \cdot \tilde{h}
$$

**Word boundaries** are detected at spacing gaps:

$$
\text{word break} \iff \text{gap}_{i,\,i+1} > 1.5 \cdot \tilde{s}
$$

where $\tilde{h}$ is the median cell height and $\tilde{s}$ is the median intra-line cell spacing.

**Focus distance** is converted from centimeters to diopters for Camera2 manual focus:

$$
D = \frac{100}{d_{\text{cm}}} \quad \text{diopters}
$$

clamped to the device's supported `LENS_INFO_MINIMUM_FOCUS_DISTANCE` range.

---

## Project Structure

```
app/
├── ui/
│   ├── scanning/        # Camera preview + alignment guidance + capture
│   ├── results/         # Recognized text + confidence + uncertainty marks
│   ├── settings/        # Mode selection, grade override, cloud opt-in
│   └── theme/           # Design tokens (Color, Typography, Spacing)
│
domain/
├── alignment/           # AlignmentEvaluator, ReadyToScanStateMachine
├── grade/               # GradeDetector, GradeController, GradeOverride
├── mode/                # ScanningModeController (Embossed / Handwritten)
├── model/               # ScanResult, Confidence, BrailleCell, DetectedDot
├── notify/              # Notifier, LowConfidencePolicy, ScanStatusMessages
├── orchestration/       # ScanCoordinator, ScanSession state machine
├── recognize/           # PatternRecognizer, DefaultPatternRecognizer
├── results/             # ScanResultRetainer (display-unavailable retention)
├── segmentation/        # GridClusterer, LineGrouper, DegradedRegionHandler
└── translate/           # TranslationEngine, UntranslatableReport

pipeline/
├── camera/              # CameraXCameraModule, CameraPolicy, CameraErrorPolicy
├── privacy/             # CloudTransmissionGate, ProviderAllowlist, CloudOptIn
└── tts/                 # AndroidTtsEngine, TTS state management

runtime/
├── detect/              # TFLite detector wrapper, DetectorParams
├── preprocess/          # OpenCvImagePreprocessor, LightingNormalization
└── translate/           # LiblouisTranslationEngine, LiblouisBridge (JNI)
```

---

## Getting Started

### Prerequisites

- **JDK 17**
- **Android SDK** with platform 34 (`ANDROID_HOME` or `local.properties`)
- **Android device** running 8.0+ (API 26) with USB debugging enabled

### Build

```bash
# Clone
git clone https://github.com/arjun7n9s/Constella.git
cd Constella

# Build debug APK
./gradlew :app:assembleDebug

# Install to connected device
./gradlew installDebug
```

### Test

```bash
# Domain logic (JVM — fast, no device needed)
./gradlew :domain:test

# All modules
./gradlew test

# Full build + lint + tests
./gradlew build

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

---

## Current Status

### ✅ Implemented
- Four-module Android build with downward-only dependencies
- Compose UI: Scanning, Settings, and Results screens
- Full domain pipeline: Alignment → Detection → Segmentation → Recognition → Translation
- `ScanCoordinator` orchestrating the entire pipeline with crash isolation
- Confidence-aware result rendering with per-character uncertainty marking
- Embossed / Handwritten mode selection with tier labeling
- Auto / Manual grade detection and override
- CameraX integration with torch, macro focus, and typed error recovery
- Offline TTS with engine selection and playback state management
- Default-deny cloud privacy gates with provider allowlists
- Design token system with default and high-contrast themes
- Accessibility: content descriptions, 48dp targets, dual-channel notifications
- 194 passing JVM unit tests across all modules

### 🔧 Needs Production Assets
- Trained `.tflite` Braille dot detector model
- OpenCV and liblouis native `.so` libraries for target ABIs
- liblouis Grade 1 / Grade 2 English translation tables
- Real camera-to-pipeline wiring on device
- End-to-end validation with physical Braille samples

---

## Built With

| Technology | Purpose |
|---|---|
| **Kotlin 2.0** | Language — pure JVM in domain, Android elsewhere |
| **Jetpack Compose** | Declarative UI with Material 3 |
| **CameraX + Camera2** | Camera lifecycle, torch, manual focus |
| **TensorFlow Lite** | On-device dot detection inference |
| **OpenCV** | Image preprocessing (morphological ops, homography) |
| **liblouis** | Standards-compliant Braille translation (JNI) |
| **Android TTS** | Offline speech synthesis |
| **Kotest** | Property-based and unit testing |
| **Gradle Version Catalog** | Centralized dependency management |

---

<p align="center">
  <em>Raised dots become English text. English text becomes speech.<br>The page becomes accessible to one more person.</em>
</p>
