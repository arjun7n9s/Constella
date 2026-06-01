package com.constella.braille.domain.preprocess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deterministic, framework-free lighting normalization for the Image_Preprocessor
 * (Req 3.3, 3.5).
 *
 * Raking light is intrinsically uneven — brighter near the LED — so a captured
 * frame carries a slow, large-scale illumination gradient on top of the fine
 * dot-vs-shadow micro-contrast the detector relies on. This object flattens that
 * gradient while preserving the high-frequency detail, using **background
 * illumination estimation and subtraction** (a flat-field correction):
 *
 * 1. Estimate the background illumination `B(x, y)` as the local mean luminance
 *    over a large window (a [box blur][estimateBackground]); a window that is a
 *    fraction of the smaller image dimension is wide enough to average across the
 *    fine dot texture yet still tracks the smooth illumination gradient.
 * 2. Re-centre each pixel about the global mean:
 *    `N(x, y) = clamp(I(x, y) - B(x, y) + globalMean, 0, 255)`.
 *
 * Because `B` captures only the low-frequency component, subtracting it removes
 * the gradient (flattening illumination) while the local dot/shadow contrast —
 * which is high-frequency and therefore not in `B` — survives. This is the
 * "background-illumination estimation / CLAHE-style local contrast
 * normalization" the design calls for, implemented in pure Kotlin so it is
 * deterministic and JVM-testable without OpenCV. The OpenCV-backed path may later
 * substitute a tiled CLAHE behind the same call site; the reduction-in-variation
 * contract verified by [illuminationVariation] is what matters.
 *
 * All operations are over a single-channel, row-major, unsigned-8-bit luminance
 * buffer expressed as a [ByteArray] (`value and 0xFF`) plus its [ImageSize],
 * matching the layout of the runtime `ImageBuffer` without depending on it (the
 * `:domain` layer must not depend on the runtime layer).
 *
 * _Requirements: 3.3, 3.5_
 */
object IlluminationNormalizer {

    /**
     * The defined illumination-uniformity threshold (Req 3.3): the maximum
     * acceptable [illuminationVariation] of a normalized image, expressed as a
     * fraction of the full 0..255 luminance scale.
     *
     * After normalization the spread of local (tile-averaged) illumination must
     * be at or below this fraction. The requirements pin only that such a
     * threshold exists, not its value, so this is an initial calibration default
     * that is tunable against the evaluation sets (Req 13). It lives here, beside
     * the algorithm it governs, rather than in the shared constants object.
     */
    const val ILLUMINATION_UNIFORMITY_THRESHOLD: Double = 0.15

    /**
     * Default background-estimation window radius as a fraction of the smaller
     * image dimension. The window diameter is `2 * radius + 1`. Kept small enough
     * that edge clamping does not bias the background away from the true gradient,
     * yet large enough to average across the fine dot texture (a dot is far
     * smaller than this window).
     */
    const val DEFAULT_BACKGROUND_WINDOW_FRACTION: Float = 0.10f

    /**
     * Default number of tiles per axis used by [illuminationVariation] to measure
     * the spread of local illumination. Tile averaging suppresses high-frequency
     * texture/noise so the metric reflects illumination (the low-frequency
     * component), not dot contrast.
     */
    const val DEFAULT_ILLUMINATION_TILE_COUNT: Int = 8

    /**
     * Returns a new luminance buffer with the illumination gradient flattened via
     * background subtraction (see the class KDoc). The output has the same length
     * and [ImageSize] as the input and every value is clamped to `[0, 255]`.
     *
     * @param pixels row-major unsigned-8-bit luminance, `width * height` entries.
     * @param size the image dimensions; `pixels.size` must equal `width * height`.
     * @param windowFraction background-window radius as a fraction of the smaller
     *   image dimension; defaults to [DEFAULT_BACKGROUND_WINDOW_FRACTION].
     */
    fun normalize(
        pixels: ByteArray,
        size: ImageSize,
        windowFraction: Float = DEFAULT_BACKGROUND_WINDOW_FRACTION,
    ): ByteArray {
        requireMatchingLength(pixels, size)
        require(windowFraction > 0f) { "windowFraction must be > 0 but was $windowFraction" }

        val w = size.width
        val h = size.height
        val n = w * h

        val integral = buildIntegralImage(pixels, w, h)
        val globalMean = rectSum(integral, w, 0, 0, w - 1, h - 1).toDouble() / n
        val radius = backgroundRadius(size, windowFraction)

        val out = ByteArray(n)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val background = windowMean(integral, w, h, x, y, radius)
                val original = pixels[y * w + x].toInt() and 0xFF
                val corrected = (original - background + globalMean).roundToInt()
                out[y * w + x] = corrected.coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    /**
     * Measures the variation in illumination across [pixels] as a fraction of the
     * full 0..255 scale: the image is divided into a [tiles] × [tiles] grid (each
     * axis clamped to the image size), the mean luminance of each tile is
     * computed, and the result is `(maxTileMean - minTileMean) / 255`.
     *
     * Tile averaging deliberately ignores high-frequency dot/shadow contrast so
     * the value reflects the slow illumination gradient. A perfectly uniformly-lit
     * image returns `0`; a strong dark-to-bright gradient returns a large
     * fraction. After [normalize], this should be at or below
     * [ILLUMINATION_UNIFORMITY_THRESHOLD] (Req 3.3).
     */
    fun illuminationVariation(
        pixels: ByteArray,
        size: ImageSize,
        tiles: Int = DEFAULT_ILLUMINATION_TILE_COUNT,
    ): Double {
        requireMatchingLength(pixels, size)
        require(tiles > 0) { "tiles must be > 0 but was $tiles" }

        val w = size.width
        val h = size.height
        val integral = buildIntegralImage(pixels, w, h)

        val tilesX = minOf(tiles, w)
        val tilesY = minOf(tiles, h)

        var minMean = Double.POSITIVE_INFINITY
        var maxMean = Double.NEGATIVE_INFINITY
        for (ty in 0 until tilesY) {
            val y0 = (ty.toLong() * h / tilesY).toInt()
            val y1 = ((ty + 1).toLong() * h / tilesY).toInt() - 1
            for (tx in 0 until tilesX) {
                val x0 = (tx.toLong() * w / tilesX).toInt()
                val x1 = ((tx + 1).toLong() * w / tilesX).toInt() - 1
                val sum = rectSum(integral, w, x0, y0, x1, y1)
                val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
                val mean = sum.toDouble() / count
                if (mean < minMean) minMean = mean
                if (mean > maxMean) maxMean = mean
            }
        }
        return (maxMean - minMean) / 255.0
    }

    /**
     * Returns the estimated background illumination buffer (the per-pixel local
     * mean over the background window), same length and size as the input.
     * Exposed primarily so the background-estimation step can be inspected and
     * tested directly.
     */
    fun estimateBackground(
        pixels: ByteArray,
        size: ImageSize,
        windowFraction: Float = DEFAULT_BACKGROUND_WINDOW_FRACTION,
    ): ByteArray {
        requireMatchingLength(pixels, size)
        require(windowFraction > 0f) { "windowFraction must be > 0 but was $windowFraction" }

        val w = size.width
        val h = size.height
        val integral = buildIntegralImage(pixels, w, h)
        val radius = backgroundRadius(size, windowFraction)

        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[y * w + x] = windowMean(integral, w, h, x, y, radius)
                    .roundToInt()
                    .coerceIn(0, 255)
                    .toByte()
            }
        }
        return out
    }

    private fun backgroundRadius(size: ImageSize, windowFraction: Float): Int {
        val minDim = minOf(size.width, size.height)
        return max(1, (windowFraction * minDim).roundToInt())
    }

    private fun requireMatchingLength(pixels: ByteArray, size: ImageSize) {
        val expected = size.width.toLong() * size.height.toLong()
        require(pixels.size.toLong() == expected) {
            "pixels length ${pixels.size} does not match ${size.width}x${size.height} = $expected"
        }
    }

    /**
     * Builds a summed-area table (integral image) with a zeroed first row and
     * column, so a rectangle sum is four array lookups. Dimensions are
     * `(w + 1) * (h + 1)`; entry `(y + 1, x + 1)` is the sum of all luminance
     * values in `[0, x] x [0, y]`. Uses `Long` accumulation so large frames do
     * not overflow.
     */
    private fun buildIntegralImage(pixels: ByteArray, w: Int, h: Int): LongArray {
        val stride = w + 1
        val integral = LongArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val rowBase = y * w
            val outRow = (y + 1) * stride
            val prevRow = y * stride
            for (x in 0 until w) {
                rowSum += (pixels[rowBase + x].toInt() and 0xFF).toLong()
                integral[outRow + x + 1] = integral[prevRow + x + 1] + rowSum
            }
        }
        return integral
    }

    /** Sum of luminance over the inclusive rectangle `[x0, x1] x [y0, y1]`. */
    private fun rectSum(integral: LongArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val stride = w + 1
        val a = integral[y0 * stride + x0]
        val b = integral[y0 * stride + (x1 + 1)]
        val c = integral[(y1 + 1) * stride + x0]
        val d = integral[(y1 + 1) * stride + (x1 + 1)]
        return d - b - c + a
    }

    /**
     * Mean luminance over the window of [radius] around `(x, y)`, clamped to the
     * image bounds so border pixels average over the valid (smaller) window.
     */
    private fun windowMean(integral: LongArray, w: Int, h: Int, x: Int, y: Int, radius: Int): Double {
        val x0 = max(0, x - radius)
        val y0 = max(0, y - radius)
        val x1 = minOf(w - 1, x + radius)
        val y1 = minOf(h - 1, y + radius)
        val sum = rectSum(integral, w, x0, y0, x1, y1)
        val count = (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
        return sum.toDouble() / count
    }

    /**
     * Whether [a] and [b] differ by at most [maxAbsDelta] at every pixel. A small
     * test/diagnostic helper for asserting that two equal-length luminance buffers
     * are visually identical up to rounding.
     */
    internal fun maxAbsPixelDelta(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "buffers differ in length: ${a.size} vs ${b.size}" }
        var worst = 0
        for (i in a.indices) {
            val delta = abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
            if (delta > worst) worst = delta
        }
        return worst
    }
}
