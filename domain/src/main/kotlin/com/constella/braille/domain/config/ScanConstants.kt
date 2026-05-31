package com.constella.braille.domain.config

/**
 * Centralized spacing, timing, distance, and alignment constants for the scan
 * pipeline — the single source of truth for every numeric threshold that is not
 * a confidence score. Confidence thresholds live in their own companion object,
 * [ConfidenceThresholds]; everything else (geometry, framing, timing, focus
 * distance) lives here. Tuning any of these behaviours means editing the value
 * here and nowhere else.
 *
 * Unit conventions (encoded in the names and KDoc of every constant):
 * - Fractions of an area / width / normalized scale are [Float] in the closed
 *   interval `[0, 1]` and named `*_FRACTION`.
 * - Multiplicative tolerance factors are unitless [Float] named `*_FACTOR`.
 * - Angles are [Float] degrees named `*_DEGREES`.
 * - Durations are whole-millisecond [Int] named `*_MS`.
 * - Working distances are whole-centimeter [Int] named `*_CM`.
 * - Rates are whole [Int] named `*_HZ`.
 *
 * Most values here are fixed by the requirements (e.g. the 25% / 90% framing
 * bounds, the 750 ms debounce). Where the requirements only state that a
 * threshold *exists* without pinning its value (e.g. the minimum document
 * frame-area fraction in Req 3.1), the constant below is an initial calibration
 * default that is tunable as accuracy is measured against the evaluation sets
 * (Req 13); such cases are called out individually.
 */
object ScanConstants {

    /**
     * Live-camera alignment thresholds (Req 2). Each value is the boundary of a
     * defined alignment condition; the `AlignmentEvaluator` compares an
     * `AlignmentMetrics` reading against these and surfaces the single condition
     * furthest past its threshold (Req 2.9).
     */
    object Alignment {

        /**
         * Minimum fraction of the frame area the document must occupy. Below
         * this the Operator is told to move closer.
         *
         * Fraction in `[0, 1]` (25% of frame area).
         *
         * _Requirements: 2.2_
         */
        const val MIN_FILL_FRACTION: Float = 0.25f

        /**
         * Maximum fraction of the frame area the document may occupy. Above this
         * the Operator is told to move farther away.
         *
         * Fraction in `[0, 1]` (90% of frame area).
         *
         * _Requirements: 2.3_
         */
        const val MAX_FILL_FRACTION: Float = 0.90f

        /**
         * Maximum apparent movement per evaluation cycle before the Operator is
         * told to hold steady, expressed as a fraction of the frame width.
         *
         * Fraction in `[0, 1]` (2% of frame width per cycle).
         *
         * _Requirements: 2.4_
         */
        const val MAX_MOVEMENT_FRACTION_PER_CYCLE: Float = 0.02f

        /**
         * Minimum average frame luminance, on a normalized 0-to-1 scale, before
         * the Operator is told to turn on the Torch or add light.
         *
         * Fraction in `[0, 1]` (20% on the normalized 0-100% luminance scale).
         *
         * _Requirements: 2.5_
         */
        const val MIN_LUMINANCE_FRACTION: Float = 0.20f

        /**
         * Maximum deviation of the document plane from parallel-to-the-lens
         * before the Operator is told to flatten the document.
         *
         * Degrees.
         *
         * _Requirements: 2.6_
         */
        const val MAX_PLANE_TILT_DEGREES: Float = 15f

        /**
         * Minimum rate at which the live feed is evaluated for distance,
         * framing, steadiness, lighting, and flatness ("at least twice per
         * second").
         *
         * Whole hertz.
         *
         * _Requirements: 2.1_
         */
        const val MIN_EVALUATION_HZ: Int = 2
    }

    /**
     * Ready-to-scan and guidance timing (Req 2.10–2.12, 2.15). These drive the
     * debounced ready-to-scan state machine on a virtual clock.
     */
    object Timing {

        /**
         * Debounce period for the ready-to-scan state. Threshold fluctuations
         * shorter than this hold the ready-to-scan state (Req 2.10); a condition
         * that stays out of threshold longer than this leaves ready-to-scan and
         * resumes active guidance (Req 2.11).
         *
         * Milliseconds.
         *
         * _Requirements: 2.10, 2.11_
         */
        const val READY_DEBOUNCE_MS: Int = 750

        /**
         * Maximum time to leave the ready-to-scan state and resume active
         * guidance after the document leaves the frame.
         *
         * Milliseconds.
         *
         * _Requirements: 2.12_
         */
        const val DOCUMENT_EXIT_REACTION_MS: Int = 500

        /**
         * Maximum time to deliver the corresponding guidance change after an
         * alignment condition crosses a defined threshold.
         *
         * Milliseconds.
         *
         * _Requirements: 2.15_
         */
        const val GUIDANCE_CHANGE_DEADLINE_MS: Int = 500
    }

    /**
     * Cell-segmentation geometry tolerances (Req 5). These are multiplicative
     * factors applied to per-document medians (cell height, intra-line spacing)
     * rather than absolute pixel values, so segmentation scales with the
     * document's apparent size in the frame.
     */
    object Segmentation {

        /**
         * Line-grouping vertical tolerance. Cells whose vertical centers lie
         * within this factor times the median cell height of each other are
         * grouped into a common line (half the median cell height).
         *
         * Unitless factor applied to the median cell height.
         *
         * _Requirements: 5.2_
         */
        const val LINE_GROUPING_CELL_HEIGHT_FACTOR: Float = 0.5f

        /**
         * Word-boundary gap factor. A horizontal gap between two adjacent cells
         * in a line that exceeds this factor times the median intra-line
         * cell-to-cell spacing inserts a word boundary (1.5× median spacing).
         *
         * Unitless factor applied to the median intra-line cell spacing.
         *
         * _Requirements: 5.4_
         */
        const val WORD_BOUNDARY_SPACING_FACTOR: Float = 1.5f
    }

    /**
     * Camera working-distance bounds (Req 1.4). While the preview is active the
     * Camera_Module maintains focus on a document positioned within this range.
     */
    object Camera {

        /**
         * Nearest supported working distance from the lens.
         *
         * Centimeters.
         *
         * _Requirements: 1.4_
         */
        const val MIN_FOCUS_DISTANCE_CM: Int = 5

        /**
         * Farthest supported working distance from the lens.
         *
         * Centimeters.
         *
         * _Requirements: 1.4_
         */
        const val MAX_FOCUS_DISTANCE_CM: Int = 25
    }

    /**
     * Image-preprocessing geometry (Req 3).
     */
    object Preprocessing {

        /**
         * Minimum fraction of the frame area a detected document boundary must
         * enclose to be accepted; smaller candidate boundaries are treated as
         * "no boundary found" and trigger the unrectified graceful-degradation
         * path (Req 3.5).
         *
         * Fraction in `[0, 1]`. The requirements pin only that this minimum
         * exists, not its value, so this is an initial calibration default that
         * is tunable against the evaluation sets (Req 13).
         *
         * _Requirements: 3.1_
         */
        const val MIN_DOCUMENT_FRAME_AREA_FRACTION: Float = 0.10f
    }

    init {
        // Guard the single-source-of-truth invariants once at load time so a
        // bad edit fails fast rather than silently mis-tuning the pipeline.
        require(Alignment.MIN_FILL_FRACTION in 0f..1f) { "MIN_FILL_FRACTION must be in [0,1]" }
        require(Alignment.MAX_FILL_FRACTION in 0f..1f) { "MAX_FILL_FRACTION must be in [0,1]" }
        require(Alignment.MIN_FILL_FRACTION < Alignment.MAX_FILL_FRACTION) {
            "MIN_FILL_FRACTION must be below MAX_FILL_FRACTION"
        }
        require(Alignment.MAX_MOVEMENT_FRACTION_PER_CYCLE in 0f..1f) {
            "MAX_MOVEMENT_FRACTION_PER_CYCLE must be in [0,1]"
        }
        require(Alignment.MIN_LUMINANCE_FRACTION in 0f..1f) { "MIN_LUMINANCE_FRACTION must be in [0,1]" }
        require(Alignment.MAX_PLANE_TILT_DEGREES in 0f..90f) { "MAX_PLANE_TILT_DEGREES must be in [0,90]" }
        require(Alignment.MIN_EVALUATION_HZ > 0) { "MIN_EVALUATION_HZ must be positive" }

        require(Timing.READY_DEBOUNCE_MS > 0) { "READY_DEBOUNCE_MS must be positive" }
        require(Timing.DOCUMENT_EXIT_REACTION_MS > 0) { "DOCUMENT_EXIT_REACTION_MS must be positive" }
        require(Timing.GUIDANCE_CHANGE_DEADLINE_MS > 0) { "GUIDANCE_CHANGE_DEADLINE_MS must be positive" }

        require(Segmentation.LINE_GROUPING_CELL_HEIGHT_FACTOR > 0f) {
            "LINE_GROUPING_CELL_HEIGHT_FACTOR must be positive"
        }
        require(Segmentation.WORD_BOUNDARY_SPACING_FACTOR > 1f) {
            "WORD_BOUNDARY_SPACING_FACTOR must be greater than 1"
        }

        require(Camera.MIN_FOCUS_DISTANCE_CM > 0) { "MIN_FOCUS_DISTANCE_CM must be positive" }
        require(Camera.MIN_FOCUS_DISTANCE_CM < Camera.MAX_FOCUS_DISTANCE_CM) {
            "MIN_FOCUS_DISTANCE_CM must be below MAX_FOCUS_DISTANCE_CM"
        }

        require(Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION in 0f..1f) {
            "MIN_DOCUMENT_FRAME_AREA_FRACTION must be in [0,1]"
        }
    }
}
