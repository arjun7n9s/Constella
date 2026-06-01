package com.constella.braille.ui.results

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.CharSpan

/**
 * A contiguous run of Recognized_Text that is uniformly certain or uncertain.
 *
 * The Results composable renders one styled span per segment: [isUncertain]
 * runs get the `uncertainMark` Design_Token treatment (Req 10.3) while the rest
 * render normally. Concatenating every [text] in order reproduces the original
 * Recognized_Text exactly.
 *
 * _Requirements: 10.3_
 */
data class TextSegment(
    val text: String,
    val isUncertain: Boolean,
)

/**
 * Pure mapping from a [ScanResult]'s Recognized_Text + `charSpans` to a minimal
 * list of [TextSegment]s, marking every character whose governing span sits
 * **below** the display-confidence threshold as uncertain (Req 10.3).
 *
 * This is deliberately a plain, Compose-free function so the
 * uncertainty-segmentation rule is JVM-unit-testable without rendering. The
 * `@Composable` is kept thin over it.
 *
 * Rules:
 *  - A character is marked uncertain iff at least one [CharSpan] covering its
 *    index has `confidence.value < threshold`. The comparison is strict, so a
 *    character whose span confidence is exactly at the threshold is treated as
 *    certain ("at/above" is not uncertain).
 *  - Spans use half-open ranges `[startIndex, endIndex)` and are clamped to the
 *    bounds of [text], so out-of-range or stale spans never crash rendering.
 *  - A character covered by no span has no confidence evidence and is treated
 *    as certain.
 *  - Adjacent characters that share the same uncertainty flag are coalesced
 *    into a single segment, yielding the minimal list of styled runs.
 *
 * The result satisfies: `segments.joinToString("") { it.text } == text`.
 *
 * @param text the Recognized_Text to segment.
 * @param charSpans the per-span confidence mapping back to source cells.
 * @param threshold the display-confidence threshold; defaults to the centralized
 *   [ConfidenceThresholds.DISPLAY_CONFIDENCE] single source of truth.
 *
 * _Requirements: 10.3, 10.5_
 */
fun segmentByUncertainty(
    text: String,
    charSpans: List<CharSpan>,
    threshold: Float = ConfidenceThresholds.DISPLAY_CONFIDENCE,
): List<TextSegment> {
    if (text.isEmpty()) return emptyList()

    // Per-character uncertainty: a char is uncertain if any covering span is
    // below the threshold. Default (uncovered) is certain.
    val uncertain = BooleanArray(text.length)
    for (span in charSpans) {
        if (span.confidence.value < threshold) {
            val start = span.startIndex.coerceIn(0, text.length)
            val end = span.endIndex.coerceIn(0, text.length)
            for (i in start until end) {
                uncertain[i] = true
            }
        }
    }

    // Coalesce consecutive characters with the same flag into runs.
    val segments = ArrayList<TextSegment>()
    var runStart = 0
    var runFlag = uncertain[0]
    for (i in 1 until text.length) {
        if (uncertain[i] != runFlag) {
            segments.add(TextSegment(text.substring(runStart, i), runFlag))
            runStart = i
            runFlag = uncertain[i]
        }
    }
    segments.add(TextSegment(text.substring(runStart), runFlag))
    return segments
}

/**
 * Format a per-scan Confidence_Score in `[0, 1]` as a whole-percent label
 * (e.g. `0.873f` -> `"87%"`) for display alongside the Recognized_Text
 * (Req 10.2, 13.9). Pure and Compose-free so the formatting is unit-testable.
 *
 * _Requirements: 10.2, 13.9_
 */
fun confidencePercentLabel(confidence: Float): String {
    val clamped = confidence.coerceIn(0f, 1f)
    return "${Math.round(clamped * 100f)}%"
}
