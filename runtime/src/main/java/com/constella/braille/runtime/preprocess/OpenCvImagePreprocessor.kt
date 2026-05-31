package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.ScanningMode
import com.constella.braille.domain.preprocess.ImageSize
import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Quad

/**
 * [ImagePreprocessor] implementation for task 5.1: document **boundary
 * detection** and **perspective correction**.
 *
 * Flow for a captured frame:
 * 1. Ask the [OpenCvBridge] to find a candidate document boundary ([Quad]).
 * 2. Apply the Req 3.1 minimum-frame-area acceptance test
 *    ([Quad.enclosesAtLeastFraction] against
 *    [ScanConstants.Preprocessing.MIN_DOCUMENT_FRAME_AREA_FRACTION]). A boundary
 *    that encloses less than the minimum fraction is rejected.
 * 3. When a boundary is accepted, compute the deterministic
 *    [PerspectiveRectifier.RectificationPlan] (axis-aligned target rectangle +
 *    homography) and warp the frame onto it, returning `rectified = true` and
 *    recording the boundary in [PreprocessOutput.documentQuadInPixels] (Req 3.2).
 *
 * Boundaries between this task and later ones:
 *  - The split of deterministic geometry (pure `:domain`) vs. native pixel work
 *    (the [OpenCvBridge]) keeps the warp math JVM-testable without OpenCV.
 *  - **Lighting normalization** (Req 3.3) and the **no-boundary
 *    graceful-degradation** path (Req 3.5) are intentionally *not* implemented
 *    here; they are task 5.3. To keep the stage usable in the meantime, when no
 *    boundary is accepted this returns the input image unchanged with
 *    `rectified = false` and `documentQuadInPixels = null`. Task 5.3 replaces
 *    that pass-through with lighting-normalized output.
 *
 * The default [bridge] is the dependency-free [SoftwareOpenCvBridge] so the
 * warp produces a real rectified image before the OpenCV `.so` is bundled;
 * production wiring substitutes [NativeOpenCvBridge] once the binary is
 * packaged. The [minDocumentFrameAreaFraction] is injectable purely so tests
 * can exercise the acceptance threshold; it defaults to the single source of
 * truth in [ScanConstants].
 *
 * _Requirements: 3.1, 3.2_
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
            PreprocessOutput(
                image = rectified,
                rectified = true,
                documentQuadInPixels = accepted,
            )
        } else {
            // Task 5.3 will replace this pass-through with lighting normalization
            // of the unrectified frame (Req 3.5).
            PreprocessOutput(
                image = frame,
                rectified = false,
                documentQuadInPixels = null,
            )
        }
    }
}
