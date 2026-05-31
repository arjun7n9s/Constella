package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Quad

/**
 * OpenCV-backed [OpenCvBridge] — the production boundary finder and warp.
 *
 * **STUB — not yet linkable.** The OpenCV Android native library
 * (`libopencv_java4.so`) is intentionally not bundled in the current skeleton
 * (see `runtime/src/main/jniLibs/README.md`; binaries are dropped in by the
 * consolidated packaging pass). Until then this class is a clearly-marked stub:
 * its methods describe exactly the OpenCV calls they will make and throw
 * [NotImplementedError] if invoked, so nothing silently runs a half-wired
 * native path. The pipeline uses [SoftwareOpenCvBridge] in the meantime, which
 * produces a real (pure-Kotlin) rectified image.
 *
 * Intended OpenCV implementation once the `.so` is available:
 *  - [findDocumentBoundary]: convert the buffer to a `Mat`, grayscale + blur,
 *    `Imgproc.Canny`, `Imgproc.findContours`, pick the largest 4-point
 *    `approxPolyDP` convex contour, and return its corners as an ordered [Quad]
 *    via [Quad.ordered].
 *  - [warpToRectangle]: build the source/destination `MatOfPoint2f` from
 *    `plan.sourceQuad`/`plan.targetQuad`, call
 *    `Imgproc.getPerspectiveTransform` (or load `plan.homography`), then
 *    `Imgproc.warpPerspective` into a `Mat` of `plan.targetSize`, and copy back
 *    into an [ImageBuffer].
 *
 * _Requirements: 3.1, 3.2_
 */
class NativeOpenCvBridge : OpenCvBridge {

    override fun findDocumentBoundary(image: ImageBuffer): Quad? =
        throw NotImplementedError(NOT_LINKED_MESSAGE)

    override fun warpToRectangle(
        image: ImageBuffer,
        plan: PerspectiveRectifier.RectificationPlan,
    ): ImageBuffer =
        throw NotImplementedError(NOT_LINKED_MESSAGE)

    private companion object {
        const val NOT_LINKED_MESSAGE: String =
            "NativeOpenCvBridge requires libopencv_java4.so, which is not yet bundled. " +
                "Use SoftwareOpenCvBridge until the OpenCV binaries are packaged " +
                "(see runtime/src/main/jniLibs/README.md)."
    }
}
