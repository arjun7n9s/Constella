package com.constella.braille.pipeline.camera

/**
 * The recovery affordance the UI must surface alongside a [CameraError].
 *
 * Each typed camera error (Req 1.6, 1.7, 1.9, 1.10, 1.11) is delivered to the
 * Operator on both channels (on-screen text + speech) and offers exactly one
 * recovery control determined purely by the error kind:
 *
 *  - [NONE] — the condition is informational and scanning continues, so there
 *    is no corrective control to offer. Used for missing controllable Torch
 *    (Req 1.6) and missing close-range focus (Req 1.9), both of which keep the
 *    live preview running.
 *  - [OPEN_SETTINGS] — offer a control that deep-links to the device
 *    permission/app settings. Used when camera permission was denied (Req 1.7),
 *    where the only way forward is for the Operator to grant access.
 *  - [RETRY] — offer a control that re-attempts the failed operation. Used when
 *    the camera is unavailable for a non-permission reason (Req 1.10) and when a
 *    still capture fails (Req 1.11).
 *
 * This is a pure value with no Android/CameraX dependency so the deterministic
 * error-to-recovery mapping in [CameraErrorPolicy] can be unit-tested on the
 * JVM.
 */
enum class RecoveryAction {
    /** No recovery control; the error is informational and scanning continues. */
    NONE,

    /** Offer a control that opens the device permission settings (Req 1.7). */
    OPEN_SETTINGS,

    /** Offer a control that re-attempts the failed operation (Req 1.10, 1.11). */
    RETRY,
}
