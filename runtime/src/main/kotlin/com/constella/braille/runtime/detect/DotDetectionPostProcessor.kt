package com.constella.braille.runtime.detect

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.Confidence
import com.constella.braille.domain.model.DetectedDot
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic post-processing / policy layer for the Dot_Detector
 * (Req 4.3, 4.7).
 *
 * This object turns the **raw** numeric output of the object-detection model
 * (normalized boxes + per-box scores) into the System's [DetectorOutput]:
 *
 *  1. **Confidence filtering** — drop every detection whose score is below the
 *     minimum dot-detection confidence (default [ConfidenceThresholds.MIN_DOT_DETECTION]).
 *  2. **Coordinate mapping** — map each surviving normalized box to a
 *     [DetectedDot] whose `x`/`y` are pixel centers referenced to the
 *     preprocessed image, with a `radius` derived from the box size (Req 4.7).
 *  3. **Structure decision** — set [DetectorOutput.structureInferable] from the
 *     accepted dots so the pipeline can later distinguish "structure but no
 *     valid cell" (Req 4.5) from "no Braille recognized" (Req 4.6).
 *
 * It deliberately depends only on `:domain` types and the JDK, with **no**
 * TensorFlow Lite or Android references, so it is fully unit/property-testable
 * without a real model on the device. [TfLiteDotDetector] feeds its decoded
 * tensors straight into [process].
 *
 * _Requirements: 4.3, 4.7_
 */
object DotDetectionPostProcessor {

    /**
     * Minimum number of accepted dots required before any Braille-like spatial
     * structure is considered inferable. A lone dot carries no spatial
     * relationship, so the structure flag needs at least two accepted dots.
     */
    const val MIN_STRUCTURE_DOTS: Int = 2

    /**
     * Convert raw model detections into filtered, pixel-space dots plus the
     * structure flag.
     *
     * @param raw decoded model output (normalized boxes + scores).
     * @param imageWidthPx width of the preprocessed image the coordinates are
     *   referenced to (Req 4.7). Must be > 0.
     * @param imageHeightPx height of the preprocessed image. Must be > 0.
     * @param minConfidence minimum dot-detection confidence; detections scoring
     *   below it are discarded (Req 4.3). Defaults to the centralized
     *   [ConfidenceThresholds.MIN_DOT_DETECTION].
     * @return a well-formed [DetectorOutput] in preprocessed-image pixel space.
     */
    fun process(
        raw: RawDetections,
        imageWidthPx: Int,
        imageHeightPx: Int,
        minConfidence: Float = ConfidenceThresholds.MIN_DOT_DETECTION,
    ): DetectorOutput {
        require(imageWidthPx > 0) { "imageWidthPx must be > 0 but was $imageWidthPx" }
        require(imageHeightPx > 0) { "imageHeightPx must be > 0 but was $imageHeightPx" }

        val accepted = ArrayList<DetectedDot>(raw.size)
        for (i in 0 until raw.size) {
            val score = raw.score(i)
            // Reject NaN and any score that fails to meet the threshold (Req 4.3).
            if (score.isNaN() || score < minConfidence) continue

            val dot = mapToDot(raw.box(i), score, imageWidthPx, imageHeightPx)
            accepted.add(dot)
        }

        return DetectorOutput(
            dots = accepted,
            structureInferable = isStructureInferable(accepted),
        )
    }

    /**
     * Map a single normalized [box] + [score] to a pixel-space [DetectedDot]
     * referenced to an [imageWidthPx] x [imageHeightPx] image.
     *
     * The dot center is the box center; the radius is the average of the box's
     * half-width and half-height in pixels (a circle inscribed in the box).
     * All coordinates are clamped into the image bounds so a slightly
     * out-of-range model box can never produce an off-image dot, and the score
     * is clamped into `[0,1]` via [Confidence.of] (Req 4.3, 4.7).
     */
    private fun mapToDot(
        box: NormalizedBox,
        score: Float,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): DetectedDot {
        val maxX = (imageWidthPx - 1).toFloat()
        val maxY = (imageHeightPx - 1).toFloat()

        val leftPx = (box.xMin * maxX)
        val rightPx = (box.xMax * maxX)
        val topPx = (box.yMin * maxY)
        val bottomPx = (box.yMax * maxY)

        val centerX = ((leftPx + rightPx) / 2f).coerceIn(0f, maxX)
        val centerY = ((topPx + bottomPx) / 2f).coerceIn(0f, maxY)

        val halfWidth = (rightPx - leftPx) / 2f
        val halfHeight = (bottomPx - topPx) / 2f
        // Guard against an inverted/degenerate box producing a negative radius;
        // DetectedDot requires radius >= 0.
        val radius = max(0f, (halfWidth + halfHeight) / 2f)

        return DetectedDot(
            x = centerX,
            y = centerY,
            radius = radius,
            confidence = Confidence.of(score),
        )
    }

    /**
     * Decide whether the accepted [dots] show Braille-like spatial structure.
     *
     * Coarse, deterministic heuristic (the precise valid-cell decision belongs
     * to the Cell_Segmenter, Req 5): structure is inferable only when there are
     * at least [MIN_STRUCTURE_DOTS] accepted dots **and** those dots occupy a
     * positive spatial extent (they are not all stacked at a single point).
     * This keeps the flag honest for the downstream Req 4.5 / 4.6 split.
     */
    private fun isStructureInferable(dots: List<DetectedDot>): Boolean {
        if (dots.size < MIN_STRUCTURE_DOTS) return false

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (dot in dots) {
            minX = min(minX, dot.x)
            maxX = max(maxX, dot.x)
            minY = min(minY, dot.y)
            maxY = max(maxY, dot.y)
        }
        val spanX = maxX - minX
        val spanY = maxY - minY
        return spanX > 0f || spanY > 0f
    }
}

/**
 * A single axis-aligned detection box in **normalized** `[0,1]` image
 * coordinates (origin top-left). Independent of the underlying tensor ordering,
 * which [RawDetections] is responsible for decoding.
 */
data class NormalizedBox(
    val yMin: Float,
    val xMin: Float,
    val yMax: Float,
    val xMax: Float,
)

/**
 * Ordering of the four box coordinates as emitted by a particular model's
 * detection-output tensor. The canonical TensorFlow Lite SSD post-processing
 * op emits `[yMin, xMin, yMax, xMax]`; some exports use `[xMin, yMin, xMax, yMax]`.
 */
enum class BoxFormat {
    /** `[yMin, xMin, yMax, xMax]` — TensorFlow Lite detection default. */
    YMIN_XMIN_YMAX_XMAX,

    /** `[xMin, yMin, xMax, yMax]`. */
    XMIN_YMIN_XMAX_YMAX,
}

/**
 * Decoded, model-agnostic view of a detection model's raw output: [size]
 * candidate detections, each with four normalized box coordinates (in [boxes],
 * laid out as `size * 4` floats) and a confidence [scores] entry.
 *
 * This is the seam between the TFLite-facing wrapper and the pure
 * [DotDetectionPostProcessor]: the wrapper copies interpreter output tensors
 * into these plain float arrays, and everything after this point is pure Kotlin.
 *
 * @param boxes normalized coordinates, length must equal `4 * size`.
 * @param scores per-detection confidence, length must equal `size`.
 * @param format coordinate ordering within each 4-tuple of [boxes].
 */
class RawDetections(
    val boxes: FloatArray,
    val scores: FloatArray,
    val format: BoxFormat = BoxFormat.YMIN_XMIN_YMAX_XMAX,
) {
    /** Number of candidate detections. */
    val size: Int get() = scores.size

    init {
        require(boxes.size == scores.size * COORDS_PER_BOX) {
            "boxes length ${boxes.size} must equal 4 * scores length ${scores.size}"
        }
    }

    /** Confidence score for detection [i]. */
    fun score(i: Int): Float = scores[i]

    /** Decode detection [i]'s box into the canonical [NormalizedBox] form. */
    fun box(i: Int): NormalizedBox {
        val base = i * COORDS_PER_BOX
        val a = boxes[base]
        val b = boxes[base + 1]
        val c = boxes[base + 2]
        val d = boxes[base + 3]
        return when (format) {
            BoxFormat.YMIN_XMIN_YMAX_XMAX ->
                NormalizedBox(yMin = a, xMin = b, yMax = c, xMax = d)
            BoxFormat.XMIN_YMIN_XMAX_YMAX ->
                NormalizedBox(yMin = b, xMin = a, yMax = d, xMax = c)
        }
    }

    companion object {
        /** Coordinates per detection box. */
        const val COORDS_PER_BOX: Int = 4
    }
}
