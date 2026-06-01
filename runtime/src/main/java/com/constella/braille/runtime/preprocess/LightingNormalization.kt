package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.preprocess.IlluminationNormalizer

/**
 * Runtime adapter that applies the pure-domain [IlluminationNormalizer] to an
 * [ImageBuffer] (Req 3.3).
 *
 * The actual flat-field correction math lives in the framework-free
 * [IlluminationNormalizer] in the `:domain` layer so it is deterministic and
 * JVM-testable without OpenCV. This file is the thin seam that unwraps an
 * [ImageBuffer]'s luminance bytes, runs the normalizer, and rewraps the result —
 * keeping the preprocessing orchestration ([OpenCvImagePreprocessor]) readable.
 *
 * An OpenCV-backed implementation could later replace [IlluminationNormalizer]
 * with a native tiled CLAHE behind this same function without changing callers,
 * since the contract (reduce illumination variation below the defined threshold,
 * preserve local contrast) is identical.
 *
 * _Requirements: 3.3, 3.5_
 */
object LightingNormalization {

    /**
     * Returns a new [ImageBuffer], the same size as [image], with the
     * illumination gradient flattened by [IlluminationNormalizer.normalize].
     */
    fun normalize(image: ImageBuffer): ImageBuffer {
        val normalized = IlluminationNormalizer.normalize(image.pixels, image.size)
        return ImageBuffer(image.size, normalized)
    }

    /**
     * The illumination variation of [image] as a fraction of the 0..255 scale
     * (see [IlluminationNormalizer.illuminationVariation]). Exposed for
     * diagnostics/tests that assert the reduction achieved by [normalize].
     */
    fun illuminationVariation(image: ImageBuffer): Double =
        IlluminationNormalizer.illuminationVariation(image.pixels, image.size)
}
