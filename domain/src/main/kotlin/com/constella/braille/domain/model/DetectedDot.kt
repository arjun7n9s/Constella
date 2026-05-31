package com.constella.braille.domain.model

/**
 * A single Braille dot detected by the Dot_Detector.
 *
 * Coordinates are pixel centers referenced to the *preprocessed* image so the
 * Cell_Segmenter can group dots spatially. [radius] approximates the detected
 * dot size in the same pixel units; it must be non-negative. [confidence] is
 * the detector's reliability estimate for this detection.
 *
 * _Requirements: 4.3, 4.7_
 */
data class DetectedDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Confidence,
) {
    init {
        require(radius >= 0f) { "DetectedDot radius must be >= 0 but was $radius" }
    }
}
