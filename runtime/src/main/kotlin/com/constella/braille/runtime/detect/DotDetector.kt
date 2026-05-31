package com.constella.braille.runtime.detect

import com.constella.braille.domain.model.DetectedDot
import java.nio.ByteBuffer

/**
 * Dot_Detector contract (Req 4).
 *
 * Runs the bundled object-detection model on a single preprocessed image and
 * returns every accepted Braille-dot detection together with a coarse
 * "Braille-like structure present" flag. Implementations execute entirely
 * on-device (Req 4.1, 4.2); the only implementation in this module is
 * [TfLiteDotDetector], backed by the TensorFlow Lite runtime.
 *
 * The contract is intentionally narrow so the stage can be wired into
 * `:pipeline` and tested in isolation. The *deterministic* part of the
 * contract — confidence-threshold filtering, coordinate mapping, and the
 * structure decision — lives in [DotDetectionPostProcessor] as a pure function
 * so it can be unit/property-tested without a real model on the device.
 *
 * `detect` is a `suspend` function because real inference is CPU/accelerator
 * bound and is expected to run off the main thread on the pipeline dispatcher
 * (see the design's concurrency model).
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.7_
 */
interface DotDetector {

    /**
     * Detect Braille dots in [image].
     *
     * @return a [DetectorOutput] whose dots are expressed in pixel coordinates
     *   referenced to the preprocessed image ([image]) and filtered to the
     *   configured minimum dot-detection confidence (Req 4.3, 4.7).
     * @throws ModelUnavailableException if the bundled `.tflite` model asset is
     *   missing or cannot be loaded (graceful, clearly-described failure).
     */
    suspend fun detect(image: DetectorImage): DetectorOutput
}

/**
 * A preprocessed image handed to the [DotDetector], expressed in the detector's
 * input space (the output of the Image_Preprocessor).
 *
 * Pixels are 8-bit RGB, row-major, three bytes per pixel (`R, G, B`), so the
 * backing buffer length is exactly `widthPx * heightPx * 3`. Detection output
 * coordinates are referenced back to this [widthPx] x [heightPx] grid (Req 4.7).
 *
 * This type is framework-free (only `java.nio.ByteBuffer`) so the pure
 * post-processing layer and its tests never need Android or a real model.
 */
class DetectorImage(
    val widthPx: Int,
    val heightPx: Int,
    val rgb: ByteBuffer,
) {
    init {
        require(widthPx > 0) { "DetectorImage widthPx must be > 0 but was $widthPx" }
        require(heightPx > 0) { "DetectorImage heightPx must be > 0 but was $heightPx" }
        val expected = widthPx.toLong() * heightPx.toLong() * BYTES_PER_PIXEL
        require(rgb.capacity().toLong() >= expected) {
            "DetectorImage rgb buffer capacity ${rgb.capacity()} is smaller than the " +
                "required $expected bytes for ${widthPx}x$heightPx RGB"
        }
    }

    companion object {
        /** Bytes per pixel for the 8-bit RGB layout. */
        const val BYTES_PER_PIXEL: Int = 3
    }
}

/**
 * Output of the Dot_Detector (Req 4.3, 4.5, 4.6, 4.7).
 *
 * @property dots accepted dot detections, in preprocessed-image pixel
 *   coordinates, each already filtered to the minimum dot-detection confidence.
 * @property structureInferable whether Braille-like spatial structure can be
 *   inferred from the detected dot candidates. This is a *coarse* pre-check used
 *   downstream (with the Cell_Segmenter result) to distinguish "structure but no
 *   valid cell" (Req 4.5) from "no Braille recognized" (Req 4.6).
 */
data class DetectorOutput(
    val dots: List<DetectedDot>,
    val structureInferable: Boolean,
) {
    companion object {
        /** Convenience empty result: no dots and no inferable structure. */
        val EMPTY: DetectorOutput = DetectorOutput(emptyList(), structureInferable = false)
    }
}

/**
 * Thrown when the bundled object-detection model asset cannot be loaded — most
 * commonly because the `.tflite` file has not yet been packaged into
 * `assets/models/` (see [TfLiteDotDetector.DEFAULT_MODEL_ASSET_PATH]).
 *
 * Carrying a clear, actionable message lets the orchestrating ScanCoordinator
 * surface a `ProcessingError` instead of crashing the session.
 */
class ModelUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when the loaded model runs but its output tensors do not match the
 * layout the wrapper expects (see [TfLiteDotDetector]). Kept distinct from
 * [ModelUnavailableException] so callers can tell "no model" from "wrong model".
 */
class ModelOutputFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
