package com.constella.braille.pipeline.camera

/**
 * The closed set of typed camera/permission error conditions the System
 * recognizes and recovers from (Req 1.6, 1.7, 1.9, 1.10, 1.11).
 *
 * Each kind corresponds to exactly one acceptance criterion and drives a
 * deterministic Operator-facing response — an on-screen + spoken message, a
 * recovery control, and whether the live preview must be preserved — computed
 * by [CameraErrorPolicy]. Keeping the conditions as a small enum (rather than
 * folding the data inline) means the mapping is a pure, exhaustively testable
 * table with no CameraX or Android dependency.
 */
enum class CameraError {

    /**
     * The device has no controllable Torch (Req 1.6). The System informs the
     * Operator (text + speech) that external low-angle lighting is required and
     * continues to allow scanning — so the preview is preserved and there is no
     * corrective control to offer.
     */
    NO_TORCH,

    /**
     * Camera access was denied because the runtime permission was not granted
     * (Req 1.7). The System informs the Operator (text + speech) that camera
     * permission is required and offers a control to open the device permission
     * settings. The preview cannot run without the permission.
     */
    PERMISSION_DENIED,

    /**
     * The camera cannot engage macro / close-range focus (Req 1.9). The System
     * informs the Operator (text + speech) that focus at close range is
     * unavailable and continues to allow scanning — the preview is preserved
     * and there is no corrective control to offer.
     */
    NO_MACRO_FOCUS,

    /**
     * The camera is unavailable for a reason other than denied permission
     * (Req 1.10) — for example it is in use by another app or failed to bind.
     * The System informs the Operator (text + speech) and offers a retry
     * control. The preview is not running, so there is nothing to preserve.
     */
    UNAVAILABLE,

    /**
     * A still capture failed (Req 1.11). The System informs the Operator
     * (text + speech) that the capture could not be completed, preserves the
     * live preview, and offers a retry control.
     */
    CAPTURE_FAILED,
}
