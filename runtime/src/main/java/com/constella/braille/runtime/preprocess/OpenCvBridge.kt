package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.preprocess.PerspectiveRectifier
import com.constella.braille.domain.preprocess.Quad

/**
 * The thin native (OpenCV) seam for image preprocessing. Only the two
 * operations that genuinely need pixel-level native code sit behind this
 * interface; all of the deterministic geometry (corner ordering, the
 * minimum-area test, target-rectangle sizing, the homography solve, and the
 * edge-alignment metric) lives in the pure `:domain` `preprocess` package and
 * in [OpenCvImagePreprocessor], which is what makes those parts JVM-testable
 * without OpenCV.
 *
 * Implementations:
 *  - The production implementation is OpenCV-backed (Canny / contour detection
 *    for [findDocumentBoundary] and `cv::warpPerspective` for [warpToRectangle]).
 *    It requires the bundled `libopencv_java4.so` (see
 *    `runtime/src/main/jniLibs/README.md`) which is **not yet packaged**.
 *  - [SoftwareOpenCvBridge] is a dependency-free fallback used until the OpenCV
 *    binaries are wired in: it performs the warp with a pure-Kotlin inverse-map
 *    sampler (mathematically identical to `warpPerspective`) and returns `null`
 *    from [findDocumentBoundary] (boundary finding is left to the OpenCV
 *    implementation).
 *
 * _Requirements: 3.1, 3.2_
 */
interface OpenCvBridge {

    /**
     * Detects the most likely document boundary in [image] and returns it as an
     * ordered [Quad], or `null` if no quadrilateral document boundary can be
     * found. The returned quad is in [image] pixel coordinates; the caller
     * applies the Req 3.1 minimum-frame-area acceptance test
     * ([Quad.enclosesAtLeastFraction]) — this method only finds candidate
     * geometry.
     */
    fun findDocumentBoundary(image: ImageBuffer): Quad?

    /**
     * Warps [image] onto the axis-aligned target rectangle described by [plan],
     * producing a rectified [ImageBuffer] of size `plan.targetSize`. The
     * transform is `plan.homography`; implementations sample destination pixels
     * via its inverse.
     */
    fun warpToRectangle(image: ImageBuffer, plan: PerspectiveRectifier.RectificationPlan): ImageBuffer
}
