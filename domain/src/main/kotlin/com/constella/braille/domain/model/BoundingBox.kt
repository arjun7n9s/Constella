package com.constella.braille.domain.model

/**
 * An axis-aligned rectangle in preprocessed-image pixel space.
 *
 * The design sketches [BrailleCell] with an Android `RectF`, but the `:domain`
 * module is pure Kotlin/JVM with no Android dependencies (dependencies point
 * downward only). [BoundingBox] is the framework-free equivalent so cell
 * geometry stays expressible in the deterministic, property-tested core.
 *
 * Coordinates are in the same pixel space as [DetectedDot] (referenced to the
 * preprocessed image). [left]/[top] are the minimum corner and must not exceed
 * [right]/[bottom] respectively, so width and height are always non-negative.
 *
 * _Requirements: 5.1_
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left <= right) { "BoundingBox left ($left) must be <= right ($right)" }
        require(top <= bottom) { "BoundingBox top ($top) must be <= bottom ($bottom)" }
    }

    /** Width of the box (`right - left`), always non-negative. */
    val width: Float get() = right - left

    /** Height of the box (`bottom - top`), always non-negative. */
    val height: Float get() = bottom - top

    /** Horizontal center of the box. */
    val centerX: Float get() = (left + right) / 2f

    /** Vertical center of the box. */
    val centerY: Float get() = (top + bottom) / 2f
}
