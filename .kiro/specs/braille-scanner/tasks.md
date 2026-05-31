# Implementation Plan: Braille Scanner

## Overview

This plan converts the Braille Scanner design into incremental, test-backed coding tasks for a 100% on-device native Android app (Kotlin + Jetpack Compose, CameraX, OpenCV, TFLite, liblouis, Android TextToSpeech). Work starts with the project skeleton, shared data models, and the centralized confidence/threshold configuration, then builds each deterministic domain component (alignment, segmentation, recognition, translation, grade, confidence/error policy) with its property-based tests close to the implementation. The hardware-facing stages (camera, TTS), privacy/offline boundary, and UI are added next, and everything is finally wired together through the `ScanCoordinator`, followed by accessibility, the accuracy harness, and packaging.

Property-based tests (marked optional with `*`) implement the 35 Correctness Properties from the design. Each is annotated with its property number and the requirements clause it validates. Unit/example, fixture, integration, and packaging tests cover the criteria classified as examples, timing, or packaging facts in the design's Testing Strategy.

## Tasks

- [ ] 1. Project setup, core data models, and shared configuration
  - [ ] 1.1 Set up the Android project structure and layering
    - Create the Kotlin + Jetpack Compose project with the four-layer structure (UI, Domain, Pipeline, Native/runtime) and module boundaries that point downward only
    - Add Gradle dependencies and `jniLibs`/assets placeholders for CameraX, OpenCV, TFLite runtime, and liblouis
    - Configure the JVM property-testing framework (kotest-property or jqwik) and the instrumented/unit test source sets
    - _Requirements: 15.1, 19.2_

  - [ ] 1.2 Implement core data models and enums
    - Implement `Confidence` (clamped [0,1]), `BrailleDots` (subset of {1..6}), `DetectedDot`, `BrailleCell`, `TextLine`, `SegmentedDocument`, `RecognizedCell`, `CharSpan`, `ScanResult`, and the `ScanStatus` sealed interface
    - Implement the `ScanningMode`, `CaptureMode`, `Grade`, and `GradeMode` enums
    - _Requirements: 4.3, 4.7, 5.1, 6.2, 6.3, 10.2_

  - [ ] 1.3 Implement the centralized threshold and constants configuration
    - Implement the `ConfidenceThresholds` object (min dot-detection, cell-confidence, display-confidence, rescan-recommendation) and the spacing/timing constants (half median cell height, 1.5× median spacing, 5–25 cm, 25%/90% fill, 2% movement, 20% luminance, 15° tilt, 750 ms debounce, 500 ms reaction) as a single source of truth
    - _Requirements: 2.10, 4.3, 5.5, 6.3, 10.3, 14.2_

  - [ ]* 1.4 Write unit tests for data model validation
    - Test `Confidence` range enforcement and `BrailleDots` position validation, including invalid inputs
    - _Requirements: 4.3, 6.2_

- [ ] 2. Theming Layer (Design Tokens)
  - [ ] 2.1 Implement the Theming_Layer with semantic Design_Tokens
    - Implement `ColorTokens`, `TypographyTokens`, `SpacingTokens` (including `touchTargetMin = 48.dp`) and expose them via Compose `CompositionLocal`s; provide default and high-contrast token sets
    - Keep component structure/contracts separate from token values so a value swap requires no structural change
    - _Requirements: 18.1, 18.2, 18.5, 17.7_

  - [ ]* 2.2 Write property test for design-token propagation
    - **Property 35: Design-token changes propagate to all consumers without structural change**
    - **Validates: Requirements 18.4**

- [ ] 3. Alignment guidance domain logic
  - [ ] 3.1 Implement the AlignmentEvaluator threshold and prioritization logic
    - Map `AlignmentMetrics` to a single `AlignmentGuidance`, selecting the condition furthest past its threshold; return `PointAtDocument` when no document is present and `ReadyToScan` when all conditions pass
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.9, 2.14_

  - [ ]* 3.2 Write property test for single furthest-from-threshold guidance
    - **Property 1: Alignment guidance selects a single furthest-from-threshold instruction**
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6, 2.9, 2.14**

  - [ ]* 3.3 Write property test for ready-to-scan when all thresholds pass
    - **Property 2: Ready-to-scan is announced only when all thresholds pass**
    - **Validates: Requirements 2.7**

  - [ ] 3.4 Implement the debounced ready-to-scan state machine
    - Implement a virtual-clock-driven state machine that holds ready-to-scan through sub-750 ms bursts, exits when a condition stays out of threshold beyond 750 ms, leaves ready within 500 ms when the document exits, and always passes through a non-ready state before re-announcing readiness
    - _Requirements: 2.10, 2.11, 2.12, 2.13_

  - [ ]* 3.5 Write property test for ready-state debounce and re-announcement
    - **Property 3: Ready-state debounce and re-announcement**
    - **Validates: Requirements 2.10, 2.11, 2.13**

- [ ] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Image preprocessing (OpenCV)
  - [ ] 5.1 Implement document boundary detection and perspective correction
    - Implement `ImagePreprocessor.process` boundary detection (minimum frame-area fraction) and perspective warp to an axis-aligned rectified image, recording the detected `documentQuadInPixels`
    - _Requirements: 3.1, 3.2_

  - [ ]* 5.2 Write property test for perspective correction edge alignment
    - **Property 4: Perspective correction aligns document edges within tolerance**
    - **Validates: Requirements 3.2**

  - [ ] 5.3 Implement lighting normalization and graceful no-boundary fallback
    - Apply background-illumination estimation / CLAHE-style local contrast normalization on the rectified image; when no boundary is found, normalize the unrectified frame, set `rectified = false`, and record skipped perspective correction; hand the result to the Dot_Detector
    - _Requirements: 3.3, 3.4, 3.5_

  - [ ]* 5.4 Write property test for lighting normalization
    - **Property 5: Lighting normalization reduces illumination variation below threshold**
    - **Validates: Requirements 3.3**

  - [ ]* 5.5 Write property test for missing-boundary graceful degradation
    - **Property 6: Missing boundary degrades gracefully with recorded provenance**
    - **Validates: Requirements 3.5**

- [ ] 6. Machine-learning dot detection (TFLite)
  - [ ] 6.1 Implement the DotDetector TFLite wrapper with policy filtering
    - Run the bundled object-detection model on-device via the TFLite runtime; output dots in preprocessed-image pixel coordinates with `Confidence`, filtered to the minimum dot-detection threshold; set `structureInferable`
    - _Requirements: 4.1, 4.2, 4.3, 4.7_

  - [ ]* 6.2 Write property test for detector output well-formedness
    - **Property 7: Detector output is well-formed**
    - **Validates: Requirements 4.3, 4.7**

  - [ ] 6.3 Implement scanning-mode DetectorParams selection
    - Select the `DetectorParams` (and preprocessing parameters) per `ScanningMode`, applying the Handwritten_Mode irregular-spacing/variable-depth parameter set
    - _Requirements: 4.4, 9.3_

  - [ ]* 6.4 Write property test for scanning-mode parameter selection
    - **Property 8: Scanning-mode parameter selection**
    - **Validates: Requirements 4.4, 9.3**

- [ ] 7. Cell segmentation
  - [ ] 7.1 Implement cell grid clustering and noise exclusion
    - Estimate dot pitch, fit 2-column × 3-row grids, assign each accepted dot to at most one cell's six candidate positions, and exclude dots that fit no cell as noise
    - _Requirements: 5.1_

  - [ ]* 7.2 Write property test for at-most-one-cell assignment
    - **Property 10: Segmentation assigns each accepted dot to at most one cell**
    - **Validates: Requirements 5.1**

  - [ ] 7.3 Implement line grouping, reading order, and word boundaries
    - Group cells within half the median cell height into lines, order cells left→right within lines and lines top→bottom, and insert word boundaries where the gap exceeds 1.5× the median intra-line spacing
    - _Requirements: 5.2, 5.3, 5.4_

  - [ ]* 7.4 Write property test for reading-order and line-grouping monotonicity
    - **Property 11: Reading-order and line-grouping monotonicity**
    - **Validates: Requirements 5.2, 5.3**

  - [ ]* 7.5 Write property test for word-boundary insertion
    - **Property 12: Word boundaries at gaps beyond 1.5× median spacing**
    - **Validates: Requirements 5.4**

  - [ ] 7.6 Implement degraded-region handling and empty input
    - Assign sub-threshold confidence to regions that cannot form a valid 2×3 grid while continuing to segment remaining regions; return an empty ordered set for empty input
    - _Requirements: 5.5, 5.6_

  - [ ]* 7.7 Write property test for invalid grid regions
    - **Property 13: Invalid grid regions get sub-threshold confidence without halting segmentation**
    - **Validates: Requirements 5.5**

  - [ ]* 7.8 Write unit test for empty-input segmentation
    - Test that no detected dots yields an empty ordered set of cells
    - _Requirements: 5.6_

- [ ] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 9. Pattern recognition
  - [ ] 9.1 Implement the Pattern_Recognizer
    - Map each segmented cell to a six-dot `BrailleDots` pattern, aggregate constituent dot confidences and grid-fit quality into a per-cell `Confidence`, and set the `uncertain` flag when below the cell-confidence threshold
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ]* 9.2 Write property test for valid six-dot pattern mapping
    - **Property 14: Pattern recognition maps cells to valid six-dot patterns**
    - **Validates: Requirements 6.1**

  - [ ]* 9.3 Write property test for cell confidence and uncertain flag
    - **Property 15: Cell confidence is total and the uncertain flag is consistent**
    - **Validates: Requirements 6.2, 6.3**

- [ ] 10. Translation engine (liblouis)
  - [ ] 10.1 Integrate liblouis via JNI and implement translation with char-span mapping
    - Compile/bundle liblouis and its Grade 1 and Grade 2 English tables; implement `TranslationEngine.translate` using only bundled tables, producing `text` and the `charSpans` mapping back to source cells; display Recognized_Text
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 10.2 Write property test for translation round-trip
    - **Property 16: Translation round-trip preserves text**
    - **Validates: Requirements 7.2**

  - [ ] 10.3 Implement untranslatable-cell reporting
    - Populate `untranslatableCells` with cells liblouis cannot translate and surface the raw patterns with an explanatory message
    - _Requirements: 7.5_

  - [ ]* 10.4 Write property test for untranslatable-cell reporting
    - **Property 17: Untranslatable cells are reported exactly**
    - **Validates: Requirements 7.5**

  - [ ]* 10.5 Write unit test for untranslatable-pattern display
    - Test that untranslatable cell patterns are displayed with the "could not translate" message
    - _Requirements: 7.5_

- [ ] 11. Grade detection and override
  - [ ] 11.1 Implement the GradeDetector heuristic and re-translate-on-override
    - When Grade_Mode is Auto, estimate Grade 1 vs Grade 2 from recognized patterns and select it; implement the one-tap override that re-runs only translation on the already-recognized patterns (no rescan) and updates the displayed text
    - _Requirements: 8.1, 8.4_

  - [ ]* 11.2 Write property test for grade override re-translation
    - **Property 18: Grade override always re-translates current patterns without rescanning**
    - **Validates: Requirements 8.1, 8.4**

  - [ ]* 11.3 Write unit tests for grade defaults, display, and disclaimer
    - Test selected-grade display, the one-tap override control, the estimate disclaimer, and the Auto default on screen open
    - _Requirements: 8.2, 8.3, 8.5, 8.6_

- [ ] 12. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Scanning modes and confidence tiering
  - [ ] 13.1 Implement scanning-mode selection and handwritten lower-confidence labeling
    - Implement Embossed/Handwritten selection, apply mode parameters to subsequent scans, display the active mode, and attach the lower-confidence second-tier label to every Handwritten_Mode result rendering
    - _Requirements: 9.1, 9.3, 9.4, 9.5_

  - [ ]* 13.2 Write property test for handwritten lower-confidence labeling
    - **Property 19: Handwritten results are always labeled lower-confidence**
    - **Validates: Requirements 9.5**

  - [ ]* 13.3 Write unit tests for mode defaults, controls, and spoken notice
    - Test Embossed default, mode control, active-mode display, the first-handwritten-scan spoken reliability notice, and the per-session disable control
    - _Requirements: 9.1, 9.2, 9.4, 9.6, 9.7_

- [ ] 14. Accuracy metric
  - [ ] 14.1 Implement the Character_Accuracy and Cell_Accuracy metric functions
    - Implement Character_Accuracy as 1 − character error rate and Cell_Accuracy against ground truth, handling empty-string and identical-string cases
    - _Requirements: 13.7_

  - [ ]* 14.2 Write property test for accuracy metric correctness
    - **Property 23: Accuracy metric correctness**
    - **Validates: Requirements 13.7**

- [ ] 15. Error handling and dual-channel Notifier
  - [ ] 15.1 Implement the centralized Notifier
    - Implement dual-channel (speech + on-screen) delivery for guidance, failure, and low-confidence messages, degrading to whichever channel is available
    - _Requirements: 2.8, 14.5, 14.6_

  - [ ]* 15.2 Write property test for dual-channel delivery
    - **Property 26: Messages are delivered on both channels, degrading to whichever is available**
    - **Validates: Requirements 2.8, 14.5, 14.6**

  - [ ] 15.3 Implement ScanStatus message generation and low-confidence policy
    - Generate a non-empty message for every non-success `ScanStatus`; for overall confidence below the rescan-recommendation threshold produce `LowConfidence` with a likely cause from the failed alignment condition and a rescan recommendation
    - _Requirements: 14.1, 14.2, 14.4_

  - [ ]* 15.4 Write property test for low-confidence rescan recommendation
    - **Property 24: Low-confidence scans recommend rescan with a likely cause**
    - **Validates: Requirements 14.2**

  - [ ]* 15.5 Write property test for failure-message generation
    - **Property 25: Every failure or low-confidence condition produces a message**
    - **Validates: Requirements 14.4**

- [ ] 16. Results presentation
  - [ ] 16.1 Implement the Results composable
    - Render Recognized_Text using only Design_Tokens, mark each character below the display-confidence threshold as uncertain via `charSpans`, display the per-scan Confidence_Score, and provide a copy-to-clipboard control
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 13.9_

  - [ ]* 16.2 Write property test for per-scan confidence exposure
    - **Property 20: Every completed scan exposes a per-scan confidence score**
    - **Validates: Requirements 10.2, 13.9**

  - [ ]* 16.3 Write property test for per-character uncertainty marking
    - **Property 21: Per-character uncertainty marking matches the threshold**
    - **Validates: Requirements 10.3**

  - [ ] 16.4 Implement display-unavailable retention
    - Complete recognition when the display is unavailable and retain the `ScanResult` for presentation when the display becomes available
    - _Requirements: 10.6_

  - [ ]* 16.5 Write property test for retention when display is unavailable
    - **Property 22: Recognition completes and is retained when the display is unavailable**
    - **Validates: Requirements 10.6**

  - [ ]* 16.6 Write unit tests for results rendering and copy control
    - Test text rendering and copy-to-clipboard behavior
    - _Requirements: 10.1, 10.4_

- [ ] 17. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 18. Camera module (CameraX)
  - [ ] 18.1 Implement the Camera_Module core
    - Own the CameraX lifecycle; start preview; enable Torch by default and keep it on in Embossed_Mode unless toggled; provide the toggle control; constrain focus to 5–25 cm; provide the low-res analysis stream and highest-resolution still capture; apply scanning-mode torch/focus policy
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.8_

  - [ ] 18.2 Implement typed camera and permission error states with recovery
    - Map `NoTorch`, `PermissionDenied`, `NoMacroFocus`, `Unavailable`, and `CaptureFailed` to typed states, each delivered as text + speech with the appropriate recovery control (open settings / retry) and preview preservation where required
    - _Requirements: 1.6, 1.7, 1.9, 1.10, 1.11_

  - [ ]* 18.3 Write unit tests for torch, capture, and error branches
    - Test torch default/toggle and capture-resolution selection against a fake CameraX controller and each error branch's message + recovery affordance
    - _Requirements: 1.2, 1.3, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11_

- [ ] 19. Text-to-speech engine
  - [ ] 19.1 Implement the offline TTS_Engine
    - Use Android `TextToSpeech` offline by default with replay/pause/stop; fall back to another installed offline engine; when no voice data exists show a message, announce it if an audio path exists, offer a control to open TTS settings, and let already-playing audio continue
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

  - [ ]* 19.2 Write unit tests for TTS fallback and no-voice-data branches
    - Test default-engine fallback and the missing-voice-data message/announcement/settings-control flow
    - _Requirements: 11.4, 11.5, 11.6_

- [ ] 20. Privacy, offline, and cloud boundary
  - [ ] 20.1 Implement in-memory frame handling and explicit-save persistence
    - Keep camera frames in memory only and persist a captured frame to storage solely when the Operator explicitly requests to save the scan
    - _Requirements: 16.1, 16.2_

  - [ ]* 20.2 Write property test for save-gated persistence
    - **Property 29: Camera frames are persisted only on explicit save**
    - **Validates: Requirements 16.1, 16.2**

  - [ ] 20.3 Implement the default-off cloud opt-in gate, cleanup, and provider allowlist
    - Compile cloud features behind a default-off opt-in; block any image transmission unless opt-in is set; on transmission, delete local temporary copies afterward and restrict destinations to the approved-provider allowlist; ensure no developer-controlled backend is ever contacted
    - _Requirements: 11.8, 15.3, 15.5, 15.6, 16.3, 16.4_

  - [ ]* 20.4 Write property test for the offline scan-to-speech cycle
    - **Property 27: Full scan-to-speech cycle works offline with cloud disabled**
    - **Validates: Requirements 11.8, 15.2, 15.5**

  - [ ]* 20.5 Write property test for no developer-backend contact
    - **Property 28: The System never contacts a developer-controlled backend**
    - **Validates: Requirements 15.3, 15.6**

  - [ ]* 20.6 Write property test for cloud transmission gating and cleanup
    - **Property 30: Cloud transmission is gated by opt-in and cleaned up afterward**
    - **Validates: Requirements 16.3, 16.4**

- [ ] 21. Scan orchestration and UI wiring
  - [ ] 21.1 Implement the ScanCoordinator and ScanSession state machine
    - Wire Camera_Module → Image_Preprocessor → Dot_Detector → Cell_Segmenter → Pattern_Recognizer → Translation_Engine → TTS_Engine on coroutine dispatchers; reduce pipeline output to `ScanStatus` (NoBrailleRecognized / StructureButNoCell / LowConfidence / ProcessingError) and wrap the pipeline so no uncaught exception crashes the session
    - _Requirements: 4.5, 4.6, 14.1, 14.3, 15.1_

  - [ ]* 21.2 Write property test for recognition outcome classification
    - **Property 9: Recognition outcome classification**
    - **Validates: Requirements 4.5, 4.6, 14.1**

  - [ ] 21.3 Wire UI screens and Live/Capture modes into the coordinator
    - Connect the scanning, results, and settings screens; implement Live_Mode (throttled low-res analysis) and Capture_Mode (high-res still) switching; show and announce the processing indicator; surface alignment guidance and errors through the Notifier
    - _Requirements: 12.1, 12.5, 14.3_

- [ ] 22. Accessibility
  - [ ] 22.1 Implement accessibility semantics and announcements
    - Add descriptive semantics labels to every interactive control, enforce ≥48dp primary targets via `SpacingTokens.touchTargetMin`, wire the high-contrast theme, provide spoken workflow guidance, and emit a single Screen_Reader announcement on each processing-state change
    - _Requirements: 17.1, 17.2, 17.3, 17.5, 17.6, 12.5_

  - [ ]* 22.2 Write property test for interactive-control labels
    - **Property 31: Every interactive control has a descriptive accessibility label**
    - **Validates: Requirements 17.2**

  - [ ]* 22.3 Write property test for minimum touch-target size
    - **Property 32: Primary action controls meet the minimum touch-target size**
    - **Validates: Requirements 17.3**

  - [ ]* 22.4 Write property test for contrast ratio
    - **Property 33: Text and essential elements meet the contrast ratio**
    - **Validates: Requirements 17.4, 17.7**

  - [ ]* 22.5 Write property test for processing-state announcements
    - **Property 34: Every processing-state change is announced**
    - **Validates: Requirements 17.6, 12.5**

- [ ] 23. Accuracy harness and packaging
  - [ ] 23.1 Implement the accuracy harness over the fixed evaluation sets
    - Run the full pipeline over the checksum-pinned Embossed_Evaluation_Set and Handwritten_Evaluation_Set, compute per-item and aggregate Character_Accuracy and Cell_Accuracy, and assert the MVP/Production gates
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.8_

  - [ ] 23.2 Configure offline bundling and first-use permission rationale
    - Package the `.tflite` model, liblouis tables, and OpenCV/TFLite/liblouis runtimes as assets/jniLibs with no post-install downloads; request camera and other runtime permissions at first use with plain-language rationale
    - _Requirements: 15.4, 19.1, 19.2, 19.4_

  - [ ]* 23.3 Write smoke/packaging tests
    - Verify bundled model/tables/runtimes with no download path, offline launch to the scanning screen, and the design-token lint rule banning hard-coded visuals
    - _Requirements: 4.1, 4.2, 7.1, 7.4, 15.1, 15.4, 18.1, 18.2, 18.3, 19.1, 19.2, 19.3_

- [ ] 24. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Each property test references a specific Correctness Property from the design and the requirements clause it validates; property tests run a minimum of 100 generated cases and are tagged `Feature: braille-scanner, Property {number}: {property_text}`.
- Timing/latency (e.g., Req 1.1, 2.1, 2.15, 3.6, 12.2–12.4), hardware behavior, and accuracy gates are validated by integration and harness tests rather than property tests, per the design's Testing Strategy.
- Checkpoints ensure incremental validation; resolve failures before continuing.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["1.4", "2.1", "3.1", "5.1", "6.1", "7.1", "9.1", "10.1", "14.1", "15.1", "18.1", "19.1", "20.1"] },
    { "id": 3, "tasks": ["2.2", "3.2", "3.3", "3.4", "5.2", "5.3", "6.2", "6.3", "7.2", "7.3", "9.2", "9.3", "10.2", "10.3", "11.1", "13.1", "14.2", "15.2", "15.3", "16.1", "18.2", "19.2", "20.2", "20.3"] },
    { "id": 4, "tasks": ["3.5", "5.4", "5.5", "6.4", "7.4", "7.5", "7.6", "10.4", "10.5", "11.2", "11.3", "13.2", "13.3", "15.4", "15.5", "16.2", "16.3", "16.4", "16.6", "18.3", "20.6"] },
    { "id": 5, "tasks": ["7.7", "7.8", "16.5", "21.1"] },
    { "id": 6, "tasks": ["20.4", "20.5", "21.2", "21.3"] },
    { "id": 7, "tasks": ["22.1", "23.1", "23.2"] },
    { "id": 8, "tasks": ["22.2", "22.3", "22.4", "22.5", "23.3"] }
  ]
}
```
