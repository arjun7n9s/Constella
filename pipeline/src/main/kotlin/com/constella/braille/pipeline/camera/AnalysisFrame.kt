package com.constella.braille.pipeline.camera

import com.constella.braille.runtime.preprocess.ImageBuffer

/**
 * A single low-resolution frame from the live analysis stream.
 *
 * The analysis stream feeds the Alignment_Guide (Req 2) and Live_Mode
 * recognition (Req 12): frames are deliberately low-resolution (see
 * [CameraPolicy.selectAnalysisResolution]) so the per-frame budget stays within
 * the sub-second live-update target (Req 12.2). The highest-resolution path is
 * reserved for the still capture (Req 1.5).
 *
 * [buffer] is the single-channel luminance image; [rotationDegrees] is the
 * clockwise rotation (0/90/180/270) that must be applied to display the frame
 * upright; [timestampMs] is a monotonic capture timestamp used for the
 * steadiness/movement evaluation in the Alignment_Guide. Frames are held in
 * memory only and never written to storage, consistent with the camera-privacy
 * requirement (Req 16.1).
 */
data class AnalysisFrame(
    val buffer: ImageBuffer,
    val rotationDegrees: Int,
    val timestampMs: Long,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be one of 0/90/180/270 but was $rotationDegrees"
        }
    }
}
