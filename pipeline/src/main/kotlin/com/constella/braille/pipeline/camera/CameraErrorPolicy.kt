package com.constella.braille.pipeline.camera

/**
 * The fully-resolved, Operator-facing presentation of a [CameraError].
 *
 * This is what the UI/Notifier needs to satisfy the dual-channel delivery
 * requirements (Req 1.6, 1.7, 1.9, 1.10, 1.11): a single human-readable
 * [message] delivered on *both* channels (on-screen text + spoken audio), the
 * [recoveryAction] control to offer, and whether the live preview must be
 * [preservePreview] kept running underneath the message.
 *
 * The [message] is intentionally channel-agnostic plain text: the same string
 * is shown on screen and handed to the TTS_Engine, so the two channels never
 * drift. It is always non-empty.
 */
data class CameraErrorPresentation(
    /** The condition this presentation describes. */
    val error: CameraError,
    /** Non-empty plain-text message for dual (text + speech) delivery. */
    val message: String,
    /** The recovery control to surface (open settings / retry / none). */
    val recoveryAction: RecoveryAction,
    /** Whether the live camera preview must be preserved underneath the error. */
    val preservePreview: Boolean,
)

/**
 * Pure, framework-free mapping from a typed [CameraError] to its deterministic
 * Operator-facing [CameraErrorPresentation].
 *
 * This is the single source of truth for "what does the Operator see/hear and
 * what can they do" for every camera/permission error, expressed as a plain
 * Kotlin table over plain values so it is exhaustively unit-testable on the JVM
 * with no CameraX (or Android) dependency. The thin CameraX wiring in
 * [CameraXCameraModule] only has to *detect* the condition and emit the matching
 * [CameraState.Error]; it never decides the message, control, or preview
 * behavior itself.
 *
 * The mapping encodes the Error Handling table from the design:
 *
 * | Condition                  | Recovery        | Preview preserved | Req   |
 * |----------------------------|-----------------|-------------------|-------|
 * | [CameraError.NO_TORCH]          | none        | yes (continues)   | 1.6   |
 * | [CameraError.PERMISSION_DENIED] | open settings | no              | 1.7   |
 * | [CameraError.NO_MACRO_FOCUS]    | none        | yes (continues)   | 1.9   |
 * | [CameraError.UNAVAILABLE]       | retry       | no                | 1.10  |
 * | [CameraError.CAPTURE_FAILED]    | retry       | yes (preserved)   | 1.11  |
 *
 * _Requirements: 1.6, 1.7, 1.9, 1.10, 1.11_
 */
object CameraErrorPolicy {

    /**
     * Resolves the deterministic [CameraErrorPresentation] for [error]:
     * the dual-channel message, the recovery control, and the
     * preview-preservation flag. Pure and total over [CameraError].
     */
    fun present(error: CameraError): CameraErrorPresentation = when (error) {
        // Req 1.6: inform that external low-angle lighting is required; keep scanning.
        CameraError.NO_TORCH -> CameraErrorPresentation(
            error = error,
            message = "This device has no controllable flashlight. Add external low-angle " +
                "lighting, such as a desk lamp angled across the page, then continue scanning.",
            recoveryAction = RecoveryAction.NONE,
            preservePreview = true,
        )

        // Req 1.7: inform that camera permission is required; offer open-settings.
        CameraError.PERMISSION_DENIED -> CameraErrorPresentation(
            error = error,
            message = "Camera permission is required to scan Braille. Open settings to allow " +
                "camera access for this app.",
            recoveryAction = RecoveryAction.OPEN_SETTINGS,
            preservePreview = false,
        )

        // Req 1.9: inform that close-range focus is unavailable; keep scanning.
        CameraError.NO_MACRO_FOCUS -> CameraErrorPresentation(
            error = error,
            message = "Close-range focus is not available on this camera. Move the document " +
                "slightly farther away until it looks sharp, then continue scanning.",
            recoveryAction = RecoveryAction.NONE,
            preservePreview = true,
        )

        // Req 1.10: inform that the camera is unavailable; offer retry.
        CameraError.UNAVAILABLE -> CameraErrorPresentation(
            error = error,
            message = "The camera is unavailable. Close any other app that may be using it, " +
                "then retry.",
            recoveryAction = RecoveryAction.RETRY,
            preservePreview = false,
        )

        // Req 1.11: inform that capture failed; preserve preview; offer retry.
        CameraError.CAPTURE_FAILED -> CameraErrorPresentation(
            error = error,
            message = "The capture could not be completed. The preview is still running, so you " +
                "can retry the capture.",
            recoveryAction = RecoveryAction.RETRY,
            preservePreview = true,
        )
    }
}
