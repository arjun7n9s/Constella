package com.constella.braille.domain.config

/**
 * Centralized confidence thresholds for the recognition pipeline — the single
 * source of truth for every "defined ... confidence threshold" referenced by the
 * requirements. Tuning recognition behaviour means editing the values here and
 * nowhere else.
 *
 * All values are normalized confidence scores in the closed interval `[0, 1]`,
 * expressed as [Float] to line up with the project's `Confidence` value class
 * (which wraps a `Float`) and the `Float`-typed detector/segmenter outputs.
 * This object deliberately holds only raw numeric constants and does **not**
 * reference the `Confidence` type: callers wrap a threshold with
 * `Confidence(...)` (or compare against `someConfidence.value`) at the use site.
 *
 * The numeric defaults below are the initial calibration targets. The
 * requirements specify that these thresholds exist and how they are used, but
 * not their exact values, so they are tunable here as recognition accuracy is
 * measured against the evaluation sets (Req 13).
 */
object ConfidenceThresholds {

    /**
     * Minimum dot-detection confidence. A detected dot whose [confidence] is
     * below this value is discarded before segmentation.
     *
     * _Requirements: 4.3_
     */
    const val MIN_DOT_DETECTION: Float = 0.50f

    /**
     * Cell-confidence threshold. A recognized cell whose confidence is below
     * this value is flagged `uncertain`, and a region that cannot form a valid
     * 2x3 grid is assigned a confidence below this value.
     *
     * _Requirements: 5.5, 6.3_
     */
    const val CELL_CONFIDENCE: Float = 0.60f

    /**
     * Display-confidence threshold. A recognized character whose confidence is
     * below this value is visually marked as uncertain in the displayed text.
     *
     * _Requirements: 10.3_
     */
    const val DISPLAY_CONFIDENCE: Float = 0.60f

    /**
     * Rescan-recommendation threshold. When the overall per-scan confidence is
     * below this value the System recommends a rescan and states the likely
     * cause from the failed alignment condition.
     *
     * _Requirements: 14.2_
     */
    const val RESCAN_RECOMMENDATION: Float = 0.50f

    init {
        // Guard the single-source-of-truth invariant: every threshold must be a
        // valid normalized confidence in [0, 1]. Runs once when the object loads.
        require(MIN_DOT_DETECTION in 0f..1f) { "MIN_DOT_DETECTION must be in [0,1]" }
        require(CELL_CONFIDENCE in 0f..1f) { "CELL_CONFIDENCE must be in [0,1]" }
        require(DISPLAY_CONFIDENCE in 0f..1f) { "DISPLAY_CONFIDENCE must be in [0,1]" }
        require(RESCAN_RECOMMENDATION in 0f..1f) { "RESCAN_RECOMMENDATION must be in [0,1]" }
    }
}
