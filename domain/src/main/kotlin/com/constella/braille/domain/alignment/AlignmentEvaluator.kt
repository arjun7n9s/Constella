package com.constella.braille.domain.alignment

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.AlignmentGuidance

/**
 * Stateless mapping from a single [AlignmentMetrics] reading to exactly one
 * [AlignmentGuidance] (Req 2.2–2.7, 2.9, 2.14).
 *
 * Decision order:
 * 1. No document in frame → [AlignmentGuidance.PointAtDocument] (Req 2.14).
 * 2. One or more alignment conditions out of threshold → the single condition
 *    **furthest past its threshold** (Req 2.9). "Furthest" is measured on a
 *    normalized severity scale so conditions in different units (frame-area
 *    fraction, frame-width fraction, luminance fraction, degrees) are directly
 *    comparable: each violation's raw distance past its threshold is divided by
 *    the distance from that threshold to the physical extreme on the violated
 *    side, yielding a `[0, 1]` severity.
 * 3. All conditions pass → [AlignmentGuidance.ReadyToScan] (Req 2.7).
 *
 * This object is intentionally pure and time-free. The debounced ready-to-scan
 * timing (Req 2.10–2.13) is a separate concern implemented by the ready-state
 * machine (task 3.4), which consumes the instantaneous decision produced here.
 *
 * All thresholds are read from [ScanConstants.Alignment]; none are hard-coded.
 *
 * _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.9, 2.14_
 */
object AlignmentEvaluator {

    /**
     * The largest meaningful tilt severity is measured against this upper bound:
     * a document plane can deviate from parallel by at most 90 degrees, so the
     * range available past the tilt threshold runs up to 90°.
     */
    private const val MAX_TILT_DEGREES: Float = 90f

    /**
     * Map a single live-feed reading to the one piece of guidance to surface
     * this cycle.
     *
     * @return [AlignmentGuidance.PointAtDocument] when no document is present,
     *   the most-severe violated condition when one or more are out of
     *   threshold, otherwise [AlignmentGuidance.ReadyToScan].
     */
    fun evaluate(metrics: AlignmentMetrics): AlignmentGuidance {
        // Req 2.14: nothing to align to.
        if (!metrics.documentPresent) return AlignmentGuidance.PointAtDocument

        val violations = violations(metrics)

        // Req 2.7: every condition is within threshold this cycle.
        if (violations.isEmpty()) return AlignmentGuidance.ReadyToScan

        // Req 2.9: surface only the single condition furthest past its
        // threshold. maxByOrNull keeps the first element on ties, so iteration
        // order (the declaration order in [violations]) gives a deterministic,
        // stable tie-break.
        return violations.maxByOrNull { it.severity }!!.guidance
    }

    /**
     * A violated alignment condition and how far past its threshold it sits, on
     * a normalized `[0, 1]` scale.
     */
    private data class Violation(val guidance: AlignmentGuidance, val severity: Float)

    /**
     * Build the list of currently-violated conditions in a fixed declaration
     * order. The order doubles as the deterministic tie-break for equal
     * severities (see [evaluate]).
     *
     * The fill-fraction "too small" and "too large" conditions are mutually
     * exclusive because [ScanConstants.Alignment.MIN_FILL_FRACTION] is strictly
     * below [ScanConstants.Alignment.MAX_FILL_FRACTION], so at most one of them
     * is added.
     */
    private fun violations(m: AlignmentMetrics): List<Violation> {
        val a = ScanConstants.Alignment
        val result = ArrayList<Violation>(5)

        // Req 2.2: document too small in frame → move closer. Severity grows as
        // the fill shrinks toward an empty frame (the floor of the fill range).
        if (m.documentFillFraction < a.MIN_FILL_FRACTION) {
            result += Violation(
                AlignmentGuidance.MoveCloser,
                severityBelow(m.documentFillFraction, a.MIN_FILL_FRACTION, floor = 0f),
            )
        }

        // Req 2.3: document too large in frame → move farther. Severity grows as
        // the fill approaches a completely filled frame (the ceiling of 1.0).
        if (m.documentFillFraction > a.MAX_FILL_FRACTION) {
            result += Violation(
                AlignmentGuidance.MoveFarther,
                severityAbove(m.documentFillFraction, a.MAX_FILL_FRACTION, ceiling = 1f),
            )
        }

        // Req 2.4: too much apparent motion → hold steady.
        if (m.movementPerCycle > a.MAX_MOVEMENT_FRACTION_PER_CYCLE) {
            result += Violation(
                AlignmentGuidance.HoldSteady,
                severityAbove(m.movementPerCycle, a.MAX_MOVEMENT_FRACTION_PER_CYCLE, ceiling = 1f),
            )
        }

        // Req 2.5: frame too dark → add light.
        if (m.luminance < a.MIN_LUMINANCE_FRACTION) {
            result += Violation(
                AlignmentGuidance.AddLight,
                severityBelow(m.luminance, a.MIN_LUMINANCE_FRACTION, floor = 0f),
            )
        }

        // Req 2.6: document plane too tilted → flatten it.
        if (m.planeTiltDegrees > a.MAX_PLANE_TILT_DEGREES) {
            result += Violation(
                AlignmentGuidance.FlattenDocument,
                severityAbove(m.planeTiltDegrees, a.MAX_PLANE_TILT_DEGREES, ceiling = MAX_TILT_DEGREES),
            )
        }

        return result
    }

    /**
     * Normalized severity for a "value fell below its minimum threshold"
     * violation: the distance below the threshold as a fraction of the range
     * from the threshold down to [floor]. Clamped to `[0, 1]` so out-of-range
     * readings cannot produce a severity outside the comparable scale.
     */
    private fun severityBelow(value: Float, threshold: Float, floor: Float): Float {
        val range = threshold - floor
        if (range <= 0f) return 1f
        return ((threshold - value) / range).coerceIn(0f, 1f)
    }

    /**
     * Normalized severity for a "value rose above its maximum threshold"
     * violation: the distance above the threshold as a fraction of the range
     * from the threshold up to [ceiling]. Clamped to `[0, 1]` so out-of-range
     * readings cannot produce a severity outside the comparable scale.
     */
    private fun severityAbove(value: Float, threshold: Float, ceiling: Float): Float {
        val range = ceiling - threshold
        if (range <= 0f) return 1f
        return ((value - threshold) / range).coerceIn(0f, 1f)
    }
}
