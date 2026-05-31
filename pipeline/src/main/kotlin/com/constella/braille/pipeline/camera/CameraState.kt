package com.constella.braille.pipeline.camera

/**
 * The minimal observable lifecycle state of the [CameraModule].
 *
 * This is intentionally a *small* surface covering only the core lifecycle that
 * task 18.1 needs: the camera is starting up, actively previewing, or has hit a
 * problem. The full typed error hierarchy (`NoTorch`, `PermissionDenied`,
 * `NoMacroFocus`, `Unavailable`, `CaptureFailed`) with recovery affordances is
 * the responsibility of task 18.2, which will extend [Error] (or replace this
 * sealed hierarchy with its richer typed-error model). Keeping the surface
 * narrow here means 18.2 can add error kinds without reworking the lifecycle.
 *
 * _Requirements: 1.2, 1.4_
 */
sealed interface CameraState {

    /** The camera is binding/initializing and the preview is not yet live. */
    data object Starting : CameraState

    /**
     * The live preview is active. [torchEnabled] reflects the currently applied
     * Torch state (Req 1.2, 1.3, 1.8) and [focusDistanceCm] the working
     * distance focus is currently biased toward, within the supported window
     * (Req 1.4).
     */
    data class Previewing(
        val torchEnabled: Boolean,
        val focusDistanceCm: Float,
    ) : CameraState

    /**
     * The camera could not start or continue. This is a deliberately minimal
     * placeholder carrying only a human-readable [reason]; task 18.2 supplies
     * the typed error kinds and recovery controls.
     */
    data class Error(val reason: String) : CameraState
}
