package com.constella.braille.domain.model

/**
 * Maps a span of output characters back to the source cell(s) that produced it.
 *
 * Grade 2 contractions make the cell-to-character mapping non-1:1, so each span
 * records the half-open character range `[startIndex, endIndex)` in the
 * Recognized_Text together with the source [cellRefs] (indices into the
 * recognized-cell sequence) and the aggregated [confidence] for that span. This
 * lets the System propagate per-cell confidence to per-character display
 * marking even when one cell expands to several characters or several cells
 * collapse to one.
 *
 * [startIndex] must be non-negative and not exceed [endIndex]; an empty span
 * (`startIndex == endIndex`) is permitted.
 *
 * _Requirements: 10.3_
 */
data class CharSpan(
    val startIndex: Int,
    val endIndex: Int,
    val cellRefs: List<Int>,
    val confidence: Confidence,
) {
    init {
        require(startIndex >= 0) { "CharSpan startIndex must be >= 0 but was $startIndex" }
        require(startIndex <= endIndex) {
            "CharSpan startIndex ($startIndex) must be <= endIndex ($endIndex)"
        }
        require(cellRefs.all { it >= 0 }) {
            "CharSpan cellRefs must be non-negative indices but was $cellRefs"
        }
    }
}
