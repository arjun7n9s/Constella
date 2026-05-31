package com.constella.braille.domain.model

/**
 * A segmented Braille cell occupying a single 2-column by 3-row grid.
 *
 * Produced by the Cell_Segmenter from spatially grouped [DetectedDot]s. A cell
 * holds at most six assigned dots (the six candidate positions of the grid).
 * [validGrid] is `false` for a *degraded* region whose dots could not be
 * grouped into a valid 2x3 grid; such a region is still emitted but carries a
 * sub-threshold [confidence] so downstream stages can flag it.
 *
 * [boundingBox] and [centerY] are in preprocessed-image pixel space; [centerY]
 * is retained explicitly because line grouping orders cells by their vertical
 * centers.
 *
 * _Requirements: 5.1, 5.5_
 */
data class BrailleCell(
    val dots: List<DetectedDot>,
    val boundingBox: BoundingBox,
    val centerY: Float,
    val validGrid: Boolean,
    val confidence: Confidence,
) {
    init {
        require(dots.size <= 6) {
            "A BrailleCell occupies a 2x3 grid and holds at most 6 dots but had ${dots.size}"
        }
    }
}
