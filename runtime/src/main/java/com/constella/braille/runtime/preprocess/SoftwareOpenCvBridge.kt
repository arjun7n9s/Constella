package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.preprocess.Homography
import com.constella.braille.domain.preprocess.ImageSize
import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Point2D
import com.constella.braille.domain.preprocess.Quad
import kotlin.math.floor

/**
 * A dependency-free [OpenCvBridge] used until the OpenCV native binaries are
 * bundled (see the final report / `runtime/src/main/jniLibs/README.md`).
 *
 * [warpToRectangle] is a complete, correct perspective warp implemented in pure
 * Kotlin: for each destination pixel it maps back through the inverse
 * homography and samples the source with bilinear interpolation — the same
 * inverse-mapping algorithm `cv::warpPerspective` uses. This means the rectified
 * output is real and testable on the JVM today; swapping in the OpenCV-backed
 * bridge later is a performance/quality change, not a correctness change.
 *
 * [findDocumentBoundary] returns `null`: robust document-contour detection on
 * raw pixels is the part that genuinely benefits from OpenCV's Canny/contour
 * machinery, so it is deferred to the OpenCV-backed implementation. A caller
 * that needs a boundary without OpenCV must supply one explicitly (e.g. via
 * [OpenCvImagePreprocessor]'s injectable boundary source in tests).
 *
 * _Requirements: 3.1, 3.2_
 */
class SoftwareOpenCvBridge : OpenCvBridge {

    override fun findDocumentBoundary(image: ImageBuffer): Quad? = null

    override fun warpToRectangle(
        image: ImageBuffer,
        plan: PerspectiveRectifier.RectificationPlan,
    ): ImageBuffer {
        val target: ImageSize = plan.targetSize
        val inverse: Homography = plan.homography.inverse()
        val out = ByteArray(target.width * target.height)

        for (dy in 0 until target.height) {
            for (dx in 0 until target.width) {
                // Sample at destination pixel centers for an unbiased mapping.
                val srcPoint: Point2D = inverse.apply(Point2D(dx + 0.5f, dy + 0.5f))
                val value = sampleBilinear(image, srcPoint.x - 0.5f, srcPoint.y - 0.5f)
                out[dy * target.width + dx] = value.toByte()
            }
        }
        return ImageBuffer(target, out)
    }

    /**
     * Bilinear sample of [image] at fractional coordinates `(fx, fy)` (in
     * pixel-center space), clamping to the image edges so out-of-bounds
     * destination pre-images read the nearest border pixel rather than crashing.
     * Returns an `Int` luminance in `[0, 255]`.
     */
    private fun sampleBilinear(image: ImageBuffer, fx: Float, fy: Float): Int {
        val x0 = floor(fx.toDouble()).toInt()
        val y0 = floor(fy.toDouble()).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1
        val wx = (fx - x0).toDouble()
        val wy = (fy - y0).toDouble()

        val p00 = clampedLuminance(image, x0, y0)
        val p10 = clampedLuminance(image, x1, y0)
        val p01 = clampedLuminance(image, x0, y1)
        val p11 = clampedLuminance(image, x1, y1)

        val top = p00 * (1 - wx) + p10 * wx
        val bottom = p01 * (1 - wx) + p11 * wx
        val value = top * (1 - wy) + bottom * wy
        return value.toInt().coerceIn(0, 255)
    }

    private fun clampedLuminance(image: ImageBuffer, x: Int, y: Int): Double {
        val cx = x.coerceIn(0, image.width - 1)
        val cy = y.coerceIn(0, image.height - 1)
        return image.luminanceAt(cx, cy).toDouble()
    }
}
