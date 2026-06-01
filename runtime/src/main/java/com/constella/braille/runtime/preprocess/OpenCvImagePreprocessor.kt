package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.ImageSize
import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Quad

/**
 * [ImagePreprocessor] implementation covering document **boundary detection**
 * and **perspective correction** (task 5.1, Req 3.1, 3.2) plus **lighting
 * normalization** and the **no-boundary graceful-degradation** path (task 5.3,
 * Req 3.3, 3.4, 3.5).
 *
 * Flow for a captured frame:
 * 1. Ask the [OpenCvBridge] to find a candidate document boundary ([Quad]).
 * 2. Apply the Req 3.1 minimum-frame-area acceptance test
 *    ([Quad.enclosesAtLeastFraction] against
 *    [ScanConstants.Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION]). A boundary
 *    that encloses less than the minimum fraction is rejected.
 * 3. When a boundary is accepted, compute the deterministic
 *    [PerspectiveRectifier.RectificationPlan] (axis-aligned target rectangle +
 *    homography), warp the frame onto it, then apply lighting normalization to
 *    the **rectified** image and hand that to the Dot_Detector, returning
 *    `rectified = true` and recording the boundary in
 *    [PreprocessOutput.documentQuadInPixels] (Req 3.2, 3.3, 3.4).
 * 4. When **no** boundary is accepted, apply lighting normalization to the
 *    **unrectified** frame, return it with `rectified = false` and
 *    `documentQuadInPixels = null`, recording that perspective correction was
 *    skipped (Req 3.5). Normalization is applied on this path too, so the
 *    detector always receives an illumination-flattened image.
 *
 * Design boundaries:
 *  - The split of deterministic geometry/pixel-math (pure `:domain`) vs. native
 *    pixel work (the [OpenCvBridge]) keeps both the warp math and the lighting
 *    normalization JVM-testable without OpenCV. Lighting normalization is the
 *    framework-free [com.constella.braille.domain.preprocess.IlluminationNormalizer],
 *    applied here via [LightingNormalization].
 *
 * The default [bridge] is the dependency-free [SoftwareOpenCvBridge] so the
 * warp produces a real rectified image before the OpenCV `.so` is bundled;
 * production wiring substitutes [NativeOpenCvBridge] once the binary is
 * packaged. The [minDocumentFrameAreaFraction] is injectable purely so tests
 * can exercise the acceptance threshold; it defaults to the single source of
 * truth in [ScanConstants].
 *
 * _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
 */
class OpenCvImagePreprocessor(
    private val bridge: OpenCvBridge = SoftwareOpenCvBridge(),
    private val minDocumentFrameAreaFraction: Float =
        ScanConstants.Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION,
) : ImagePreprocessor {

    init {
        require(minDocumentFrameAreaFraction in 0f..1f) {
            "minDocumentFrameAreaFraction must be in [0,1] but was $minDocumentFrameAreaFraction"
        }
    }

    override suspend fun process(input: CapturedImage, mode: ScanningMode): PreprocessOutput {
        val frame = input.buffer
        val frameSize = ImageSize(frame.width, frame.height)

        val boundary: Quad? = bridge.findDocumentBoundary(frame)
        val accepted = boundary?.takeIf {
            it.enclosesAtLeastFraction(frameSize, minDocumentFrameAreaFraction)
        }

        return if (accepted != null) {
            val plan = PerspectiveRectifier.plan(accepted)
            val rectified = bridge.warpToRectangle(frame, plan)
            // Normalize illumination on the rectified image and hand it to the
            // Dot_Detector (Req 3.3, 3.4).
            val normalized = LightingNormalization.normalize(rectified)
            PreprocessOutput(
                image = normalized,
                rectified = true,
                documentQuadInPixels = accepted,
            )
        } else {
            // No boundary met the minimum-area test: normalize the *unrectified*
            // frame, record that perspective correction was skipped, and still
            // hand a lighting-normalized image to the Dot_Detector (Req 3.5).
            val normalized = LightingNormalization.normalize(frame)
            PreprocessOutput(
                image = normalized,
                rectified = false,
                documentQuadInPixels = null,
            )
        }
    }
}
