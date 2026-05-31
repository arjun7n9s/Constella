/**
 * Pipeline layer for the Braille Scanner.
 *
 * Hosts the staged recognition pipeline and the narrow Kotlin interfaces for
 * each stage so stages can be tested in isolation with fixture images:
 *
 *   Camera_Module (CameraX) -> Image_Preprocessor (OpenCV) -> Dot_Detector
 *   (TFLite) -> Cell_Segmenter -> Pattern_Recognizer -> Translation_Engine
 *   (liblouis) -> TTS_Engine (Android TextToSpeech)
 *
 * Planned sub-packages (populated by later tasks):
 *  - `camera`     CameraModule (CameraX lifecycle, torch, focus, capture) — task 18
 *  - `preprocess` ImagePreprocessor wrapper over the OpenCV runtime — task 5
 *  - `detect`     DotDetector wrapper over the TFLite runtime — task 6
 *  - `segment`    CellSegmenter adapter to domain segmentation — task 7
 *  - `recognize`  PatternRecognizer adapter — task 9
 *  - `translate`  TranslationEngine wrapper over the liblouis runtime — task 10
 *  - `tts`        TtsEngine over Android TextToSpeech — task 19
 *
 * Dependency rule: depends on `domain` (data models / pure logic) and
 * `runtime` (native wrappers). Must never reference the `app` (UI) layer.
 */
package com.constella.braille.pipeline
