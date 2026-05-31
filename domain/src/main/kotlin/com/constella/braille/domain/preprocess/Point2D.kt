package com.constella.braille.domain.preprocess

import kotlin.math.hypot

/**
 * A point in preprocessed-image pixel space, expressed with [Float] components
 * so it shares the same coordinate convention as
 * [com.constella.braille.domain.model.DetectedDot] and
 * [com.constella.braille.domain.model.BoundingBox].
 *
 * This is the framework-free 2D point used by the deterministic image-geometry
 * core (document-boundary ordering, perspective target-rectangle math, and the
 * homography solve). Keeping it in the pure `:domain` layer lets every piece of
 * that geometry be unit- and property-tested on the JVM without OpenCV or any
 * Android type.
 *
 * _Requirements: 3.1, 3.2_
 */
data class Point2D(
    val x: Float,
    val y: Float,
) {
    /** Euclidean distance to [other], computed in double precision. */
    fun distanceTo(other: Point2D): Float =
        hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()
}
