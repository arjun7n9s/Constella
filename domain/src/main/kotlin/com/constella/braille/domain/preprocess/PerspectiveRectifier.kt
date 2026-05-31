package com.constella.braille.domain.preprocess

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The deterministic geometry of perspective correction (Req 3.2): given a
 * detected document [Quad], it computes the axis-aligned target rectangle the
 * document should be warped onto and the [Homography] that performs that warp.
 *
 * This is intentionally framework-free. The OpenCV-facing layer takes the
 * [RectificationPlan] produced here and applies the homography to actual image
 * pixels; all of the *math* that decides where each corner goes lives here so
 * it can be unit- and property-tested on the JVM.
 *
 * Target-rectangle sizing: the rectified width is the larger of the two
 * horizontal edge lengths of the source quad (top and bottom edges), and the
 * rectified height is the larger of the two vertical edge lengths (left and
 * right edges). Taking the max of each opposing pair preserves the most
 * detail (no source edge is compressed below its captured length), and the
 * rectangle's corners are placed at `(0,0)`, `(w,0)`, `(w,h)`, `(0,h)` so the
 * document edges become axis-aligned.
 *
 * _Requirements: 3.2_
 */
object PerspectiveRectifier {

    /**
     * A fully-specified perspective-correction plan: the [targetSize] of the
     * rectified image, the [targetQuad] (its axis-aligned corners), and the
     * [homography] mapping the source [sourceQuad] corners onto [targetQuad].
     */
    data class RectificationPlan(
        val sourceQuad: Quad,
        val targetSize: ImageSize,
        val targetQuad: Quad,
        val homography: Homography,
    )

    /**
     * Computes the [RectificationPlan] for [quad]. The target rectangle is sized
     * from the source quad's edge lengths (see class KDoc) and the homography
     * maps the four source corners onto the four axis-aligned target corners in
     * matching order.
     */
    fun plan(quad: Quad): RectificationPlan {
        val topWidth = quad.topLeft.distanceTo(quad.topRight)
        val bottomWidth = quad.bottomLeft.distanceTo(quad.bottomRight)
        val leftHeight = quad.topLeft.distanceTo(quad.bottomLeft)
        val rightHeight = quad.topRight.distanceTo(quad.bottomRight)

        // Round up to at least 1px so the target is always a valid image.
        val width = max(1, max(topWidth, bottomWidth).roundToInt())
        val height = max(1, max(leftHeight, rightHeight).roundToInt())
        val targetSize = ImageSize(width, height)

        val w = width.toFloat()
        val h = height.toFloat()
        val targetQuad = Quad(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(w, 0f),
            bottomRight = Point2D(w, h),
            bottomLeft = Point2D(0f, h),
        )

        val homography = Homography.solve(quad.corners, targetQuad.corners)
        return RectificationPlan(
            sourceQuad = quad,
            targetSize = targetSize,
            targetQuad = targetQuad,
            homography = homography,
        )
    }

    /**
     * The worst-case deviation, in degrees, of the rectified document's four
     * edges from the image axes after applying [plan]'s homography to the source
     * quad corners. The horizontal (top/bottom) edges are compared against the
     * x-axis and the vertical (left/right) edges against the y-axis; the result
     * is the maximum absolute deviation across all four edges.
     *
     * Req 3.2 requires the rectified document edges to be aligned to the image
     * axes "within a defined edge-alignment tolerance". This function quantifies
     * that deviation so the alignment can be asserted against a tolerance (the
     * Property 4 test, task 5.2). For an exact homography solve the mapped
     * corners land exactly on the axis-aligned target rectangle, so this value
     * is ~0 up to floating-point error.
     */
    fun maxEdgeMisalignmentDegrees(plan: RectificationPlan): Double {
        val tl = plan.homography.apply(plan.sourceQuad.topLeft)
        val tr = plan.homography.apply(plan.sourceQuad.topRight)
        val br = plan.homography.apply(plan.sourceQuad.bottomRight)
        val bl = plan.homography.apply(plan.sourceQuad.bottomLeft)

        val topDev = horizontalDeviationDegrees(tl, tr)
        val bottomDev = horizontalDeviationDegrees(bl, br)
        val leftDev = verticalDeviationDegrees(tl, bl)
        val rightDev = verticalDeviationDegrees(tr, br)

        return maxOf(topDev, bottomDev, leftDev, rightDev)
    }

    /** Absolute angle (degrees) of the edge `a -> b` away from the horizontal axis. */
    private fun horizontalDeviationDegrees(a: Point2D, b: Point2D): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        // Angle of the edge relative to horizontal, normalized to [0, 90].
        val angle = Math.toDegrees(atan2(abs(dy), abs(dx)))
        return angle
    }

    /** Absolute angle (degrees) of the edge `a -> b` away from the vertical axis. */
    private fun verticalDeviationDegrees(a: Point2D, b: Point2D): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        // Angle relative to vertical = 90 - angle-relative-to-horizontal, in [0, 90].
        val angleFromHorizontal = Math.toDegrees(atan2(abs(dy), abs(dx)))
        return abs(90.0 - angleFromHorizontal)
    }
}
