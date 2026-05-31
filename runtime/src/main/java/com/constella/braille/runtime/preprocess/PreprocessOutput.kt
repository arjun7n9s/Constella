package com.constella.braille.runtime.preprocess

import com.constella.braille.domain.preprocess.Quad

/**
 * The result of preprocessing one captured frame, handed to the Dot_Detector.
 *
 * Mirrors the design's `PreprocessOutput`:
 *  - [image] is the processed image in detector-input space.
 *  - [rectified] is `true` when a document boundary was found and perspective
 *    correction was applied; `false` records that perspective correction was
 *    skipped (the Req 3.5 graceful-degradation path, implemented in task 5.3).
 *  - [documentQuadInPixels] is the boundary used for the warp, or `null` when no
 *    boundary met the minimum-area test (Req 3.1, 3.2).
 *
 * _Requirements: 3.1, 3.2_
 */
data class PreprocessOutput(
    val image: ImageBuffer,
    val rectified: Boolean,
    val documentQuadInPixels: Quad?,
)
