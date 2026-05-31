package com.constella.braille.domain.preprocess

import com.constella.braille.domain.config.ScanConstants
import kotlin.math.abs

/**
 * A convex quadrilateral document boundary in preprocessed-image pixel space,
 * stored with its four corners in a canonical reading order:
 * [topLeft], [topRight], [bottomRight], [bottomLeft] (clockwise starting from
 * the top-left). This is the framework-free equivalent of the design's `Quad`
 * (the `documentQuadInPixels` recorded on the preprocess output, Req 3.2).
 *
 * The companion [ordered] factory takes four corners in *any* order and sorts
 * them into the canonical layout, so callers (including the OpenCV-facing
 * boundary finder) need not pre-sort the contour points. Corner ordering is the
 * deterministic foundation of the perspective warp: the rectifier maps these
 * four corners onto the four corners of an axis-aligned target rectangle, so a
 * stable ordering is what makes the rectified document edge-aligned to the
 * image axes (Req 3.2).
 *
 * The polygon [area] uses the shoelace formula, and [enclosesAtLeastFraction]
 * is the deterministic area-fraction acceptance test for Req 3.1.
 *
 * _Requirements: 3.1, 3.2_
 */
data class Quad(
    val topLeft: Point2D,
    val topRight: Point2D,
    val bottomRight: Point2D,
    val bottomLeft: Point2D,
) {
    /** The four corners in canonical clockwise reading order. */
    val corners: List<Point2D> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /**
     * Polygon area in pixels via the shoelace formula, always non-negative
     * regardless of corner winding. Returned as [Double] so the area-fraction
     * comparison against the frame area is exact for large frames.
     */
    val area: Double
        get() {
            val pts = corners
            var sum = 0.0
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                sum += a.x.toDouble() * b.y.toDouble() - b.x.toDouble() * a.y.toDouble()
            }
            return abs(sum) / 2.0
        }

    /**
     * Whether this boundary encloses at least [fraction] of the area of a frame
     * of size [frameSize]. This is the Req 3.1 minimum-frame-area acceptance
     * test: a candidate boundary smaller than the minimum is rejected (treated
     * as "no boundary found").
     *
     * [fraction] defaults to the calibrated
     * [ScanConstants.Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION] so the
     * single-source-of-truth threshold is applied by default, while tests can
     * pass an explicit value.
     */
    fun enclosesAtLeastFraction(
        frameSize: ImageSize,
        fraction: Float = ScanConstants.Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION,
    ): Boolean {
        require(fraction in 0f..1f) { "fraction must be in [0,1] but was $fraction" }
        return area >= fraction.toDouble() * frameSize.area
    }

    companion object {
        /**
         * Builds a [Quad] from four corners given in any order by sorting them
         * into the canonical top-left, top-right, bottom-right, bottom-left
         * layout.
         *
         * Ordering rule (robust for any non-degenerate convex quad):
         * 1. Split the points into a top pair and a bottom pair by `y`
         *    (smaller `y` is higher in image space).
         * 2. Within the top pair the smaller `x` is the top-left, the larger is
         *    the top-right; likewise for the bottom pair.
         *
         * Requires exactly four points.
         */
        fun ordered(points: List<Point2D>): Quad {
            require(points.size == 4) { "A Quad needs exactly 4 corners but got ${points.size}" }
            val byY = points.sortedBy { it.y }
            val top = byY.subList(0, 2).sortedBy { it.x }
            val bottom = byY.subList(2, 4).sortedBy { it.x }
            return Quad(
                topLeft = top[0],
                topRight = top[1],
                bottomRight = bottom[1],
                bottomLeft = bottom[0],
            )
        }

        /** Convenience overload accepting the four corners as varargs. */
        fun ordered(a: Point2D, b: Point2D, c: Point2D, d: Point2D): Quad =
            ordered(listOf(a, b, c, d))
    }
}
