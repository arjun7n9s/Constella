package com.constella.braille.domain.alignment

/**
 * A single live-feed alignment reading: the measured distance, framing,
 * steadiness, lighting, and flatness of the document in the current evaluation
 * cycle (Req 2.1). The [AlignmentEvaluator] maps one reading to exactly one
 * piece of guidance.
 *
 * This is a plain, immutable value holder; it carries no time information. The
 * debounced ready-to-scan timing (Req 2.10–2.13) is layered on separately by
 * the ready-state machine (task 3.4), which consumes the stateless decision
 * this type feeds into.
 *
 * Field unit conventions (the evaluator interprets the fields against
 * `ScanConstants.Alignment`):
 * - [documentFillFraction], [movementPerCycle], [luminance] are fractions in
 *   the closed interval `[0, 1]`.
 * - [planeTiltDegrees] is an angle in degrees in `[0, 90]`.
 *
 * Values outside these ranges are tolerated (the evaluator clamps when
 * normalizing severities) but are not expected from a well-behaved frame
 * analyzer.
 *
 * _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.14_
 *
 * @property documentPresent whether any document is detected in the frame; when
 *   `false` the evaluator returns `PointAtDocument` regardless of the other
 *   fields (Req 2.14).
 * @property documentFillFraction fraction of the frame area the document
 *   occupies, in `[0, 1]` (Req 2.2, 2.3).
 * @property movementPerCycle apparent motion since the previous cycle, as a
 *   fraction of the frame width, in `[0, 1]` (Req 2.4).
 * @property luminance average frame luminance on a normalized `[0, 1]` scale
 *   (Req 2.5).
 * @property planeTiltDegrees deviation of the document plane from
 *   parallel-to-the-lens, in degrees (Req 2.6).
 */
data class AlignmentMetrics(
    val documentPresent: Boolean,
    val documentFillFraction: Float,
    val movementPerCycle: Float,
    val luminance: Float,
    val planeTiltDegrees: Float,
)
