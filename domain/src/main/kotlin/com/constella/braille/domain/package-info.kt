/**
 * Domain layer (pure Kotlin/JVM) for the Braille Scanner.
 *
 * This is the deterministic core of the application and the primary target of
 * the JVM property-based tests. It has no Android or native dependencies.
 *
 * Planned sub-packages (populated by later tasks):
 *  - `model`     core data models and enums (Confidence, BrailleDots, DetectedDot,
 *                BrailleCell, TextLine, SegmentedDocument, RecognizedCell, CharSpan,
 *                ScanResult, ScanStatus; ScanningMode, CaptureMode, Grade, GradeMode) — task 1.2
 *  - `config`    centralized ConfidenceThresholds + spacing/timing constants — task 1.3
 *  - `alignment` AlignmentEvaluator + debounced ready-to-scan state machine — task 3
 *  - `segment`   Cell_Segmenter reading-order / word-boundary logic — task 7
 *  - `recognize` Pattern_Recognizer + confidence policy — task 9
 *  - `grade`     GradeDetector heuristic + override re-translation — task 11
 *  - `accuracy`  Character_Accuracy / Cell_Accuracy metrics — task 14
 *  - `notify`    dual-channel Notifier message generation — task 15
 *  - `coordinator` ScanCoordinator / ScanSession orchestration contracts — task 21
 *
 * Dependency rule: this layer depends on nothing else in the project. All
 * other layers (`pipeline`, `runtime`, `app`) may depend on it, never the
 * reverse — dependencies point downward only.
 */
package com.constella.braille.domain
