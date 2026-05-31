package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.model.ScanningMode

/**
 * The Image_Preprocessor pipeline stage (Req 3): detect the document boundary,
 * perspective-correct to a rectified, axis-aligned image, (later) normalize
 * lighting, and hand the result to the Dot_Detector.
 *
 * This narrow interface lets the stage be tested in isolation and lets the
 * OpenCV-backed implementation be swapped for a fake. Task 5.1 implements the
 * boundary-detection + perspective-correction behaviour
 * ([OpenCvImagePreprocessor]); lighting normalization and the no-boundary
 * graceful-degradation path are added by task 5.3.
 *
 * _Requirements: 3.1, 3.2_
 */
interface ImagePreprocessor {
    /**
     * Processes [input] using the parameters appropriate for [mode] and returns
     * the preprocessed image together with the rectification provenance
     * ([PreprocessOutput.rectified], [PreprocessOutput.documentQuadInPixels]).
     */
    suspend fun process(input: CapturedImage, mode: ScanningMode): PreprocessOutput
}
