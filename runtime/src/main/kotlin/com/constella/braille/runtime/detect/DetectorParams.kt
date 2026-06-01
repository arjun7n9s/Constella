package com.constella.braille.runtime.detect

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.ScanningMode

/**
 * Scanning-mode-specific detection (and preprocessing) parameter set for the
 * Dot_Detector (Req 4.4, 9.3).
 *
 * The design keeps the *what* axis — [ScanningMode] — separate from the *how*
 * axis (Live vs Capture). This type is the concrete realization of the "what":
 * one immutable bundle of detector knobs per [ScanningMode], selected by the
 * pure [paramsFor] function and handed to subsequent scans.
 *
 * Two parameter sets exist:
 *  - **Embossed** ([forEmbossed]) — the high-accuracy tier. Machine-embossed
 *    dots sit on a regular pitch and cast consistent raking-light shadows, so
 *    this set assumes *regular spacing* and *uniform depth*: a tighter spacing
 *    tolerance, a tighter depth tolerance, and the higher (baseline) minimum
 *    dot-detection confidence.
 *  - **Handwritten** ([forHandwritten]) — the explicitly lower-confidence
 *    second tier. Slate-and-stylus dots are punched by hand, so their spacing
 *    is *irregular* and their depth is *variable*. This set widens the spacing
 *    and depth tolerances, raises the merge distance, sets the
 *    [toleratesIrregularSpacing] / [toleratesVariableDepth] flags, and lowers
 *    the minimum dot-detection confidence so fainter, shallower hand-punched
 *    dots are still accepted (Req 4.4).
 *
 * The type depends only on `:domain` ([ScanningMode], [ConfidenceThresholds])
 * and the JDK, so both the type and [paramsFor] are pure and JVM-testable
 * without a real model or any Android dependency.
 *
 * The exact numeric tolerances below are initial calibration defaults: the
 * requirements pin that a *distinct* parameter set exists per mode and that the
 * Handwritten set accommodates irregular spacing and variable depth (Req 4.4,
 * 9.3), not the precise values, so these are tunable against the evaluation
 * sets (Req 13).
 *
 * _Requirements: 4.4, 9.3_
 */
data class DetectorParams(
    /**
     * Minimum dot-detection confidence: detections scoring below this are
     * discarded before segmentation (Req 4.3). Higher = stricter. Normalized
     * confidence in `[0, 1]`.
     */
    val minDotConfidence: Float,

    /**
     * Expected dot-spacing tolerance, as a fraction of the estimated dot pitch,
     * within which a detected dot is still considered "on grid". Larger values
     * accommodate the irregular spacing of hand-punched dots. Must be `>= 0`.
     */
    val spacingTolerance: Float,

    /**
     * Variable-depth tolerance, as a fraction of the expected dot
     * shadow/intensity signal, within which a fainter or shallower dot is still
     * accepted. Larger values accommodate the variable depth of slate-and-stylus
     * dots. Must be `>= 0`.
     */
    val depthTolerance: Float,

    /**
     * Merge/non-maximum-suppression distance, as a fraction of the estimated dot
     * pitch: two candidate detections closer than this are merged into one dot.
     * Must be `>= 0`.
     */
    val mergeDistanceFactor: Float,

    /**
     * Whether this set is tuned for irregular (hand-punched) dot spacing. `true`
     * for Handwritten_Mode, `false` for the regular-pitch Embossed_Mode.
     */
    val toleratesIrregularSpacing: Boolean,

    /**
     * Whether this set is tuned for variable dot depth. `true` for
     * Handwritten_Mode, `false` for the uniform-depth Embossed_Mode.
     */
    val toleratesVariableDepth: Boolean,
) {
    init {
        require(minDotConfidence in 0f..1f) {
            "minDotConfidence must be in [0,1] but was $minDotConfidence"
        }
        require(spacingTolerance >= 0f) {
            "spacingTolerance must be >= 0 but was $spacingTolerance"
        }
        require(depthTolerance >= 0f) {
            "depthTolerance must be >= 0 but was $depthTolerance"
        }
        require(mergeDistanceFactor >= 0f) {
            "mergeDistanceFactor must be >= 0 but was $mergeDistanceFactor"
        }
    }

    companion object {

        // --- Embossed_Mode (regular spacing, uniform depth) ----------------

        /**
         * Spacing tolerance for machine-embossed Braille. Tight, because the
         * emboss pitch is regular. Fraction of the estimated dot pitch.
         */
        const val EMBOSSED_SPACING_TOLERANCE: Float = 0.15f

        /** Depth tolerance for machine-embossed Braille. Tight (uniform depth). */
        const val EMBOSSED_DEPTH_TOLERANCE: Float = 0.10f

        /** Merge distance for machine-embossed Braille, as a fraction of pitch. */
        const val EMBOSSED_MERGE_DISTANCE_FACTOR: Float = 0.40f

        // --- Handwritten_Mode (irregular spacing, variable depth) ----------

        /**
         * Minimum dot-detection confidence for handwritten Braille. Lower than
         * the embossed baseline so fainter, shallower hand-punched dots are
         * still accepted; the lower bar is part of why Handwritten_Mode is the
         * explicitly lower-confidence tier (Req 4.4, 9.5).
         */
        const val HANDWRITTEN_MIN_DOT_CONFIDENCE: Float = 0.35f

        /**
         * Spacing tolerance for handwritten Braille. Wide, to accommodate the
         * irregular spacing of slate-and-stylus dots (Req 4.4). Fraction of the
         * estimated dot pitch.
         */
        const val HANDWRITTEN_SPACING_TOLERANCE: Float = 0.45f

        /**
         * Depth tolerance for handwritten Braille. Wide, to accommodate variable
         * dot depth (Req 4.4).
         */
        const val HANDWRITTEN_DEPTH_TOLERANCE: Float = 0.40f

        /** Merge distance for handwritten Braille, as a fraction of pitch. */
        const val HANDWRITTEN_MERGE_DISTANCE_FACTOR: Float = 0.70f

        /**
         * The Embossed_Mode parameter set: the high-accuracy tier assuming a
         * regular dot pitch and uniform depth. Uses the centralized baseline
         * minimum dot-detection confidence ([ConfidenceThresholds.MIN_DOT_DETECTION]).
         */
        val forEmbossed: DetectorParams = DetectorParams(
            minDotConfidence = ConfidenceThresholds.MIN_DOT_DETECTION,
            spacingTolerance = EMBOSSED_SPACING_TOLERANCE,
            depthTolerance = EMBOSSED_DEPTH_TOLERANCE,
            mergeDistanceFactor = EMBOSSED_MERGE_DISTANCE_FACTOR,
            toleratesIrregularSpacing = false,
            toleratesVariableDepth = false,
        )

        /**
         * The Handwritten_Mode parameter set: the lower-confidence second tier
         * that accommodates irregular spacing and variable dot depth (Req 4.4).
         */
        val forHandwritten: DetectorParams = DetectorParams(
            minDotConfidence = HANDWRITTEN_MIN_DOT_CONFIDENCE,
            spacingTolerance = HANDWRITTEN_SPACING_TOLERANCE,
            depthTolerance = HANDWRITTEN_DEPTH_TOLERANCE,
            mergeDistanceFactor = HANDWRITTEN_MERGE_DISTANCE_FACTOR,
            toleratesIrregularSpacing = true,
            toleratesVariableDepth = true,
        )

        /**
         * Pure selection function (Req 4.4, 9.3): return the [DetectorParams]
         * associated with [mode]. This is total over [ScanningMode] and has no
         * side effects, so the same mode always yields the same parameter set —
         * exactly the property the System relies on when it "applies the
         * detection and preprocessing parameters associated with that mode to
         * subsequent scans" (Req 9.3).
         *
         * @return [forEmbossed] for [ScanningMode.EMBOSSED]; [forHandwritten]
         *   (the irregular-spacing / variable-depth set) for
         *   [ScanningMode.HANDWRITTEN].
         */
        fun paramsFor(mode: ScanningMode): DetectorParams = when (mode) {
            ScanningMode.EMBOSSED -> forEmbossed
            ScanningMode.HANDWRITTEN -> forHandwritten
        }
    }
}

/**
 * Clean, additive bridge from a selected [DetectorParams] into the existing
 * pure [DotDetectionPostProcessor] pipeline (owned by the Dot_Detector wrapper
 * task). It simply forwards [DetectorParams.minDotConfidence] as the
 * post-processor's confidence threshold, so a mode's selected parameters drive
 * the confidence filtering of subsequent scans (Req 9.3) without changing any
 * existing signature.
 *
 * Defined as an extension here (rather than editing the post-processor) so the
 * wiring stays additive and non-breaking; callers that already pass a raw
 * `minConfidence: Float` are unaffected.
 */
fun DotDetectionPostProcessor.process(
    raw: RawDetections,
    imageWidthPx: Int,
    imageHeightPx: Int,
    params: DetectorParams,
): DetectorOutput = process(
    raw = raw,
    imageWidthPx = imageWidthPx,
    imageHeightPx = imageHeightPx,
    minConfidence = params.minDotConfidence,
)
