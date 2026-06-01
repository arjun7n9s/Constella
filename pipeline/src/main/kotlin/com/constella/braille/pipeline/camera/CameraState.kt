package com.constella.braille.pipeline.camera

/**
 * The observable lifecycle state of the [CameraModule].
 *
 * Task 18.1 established the core lifecycle ([Starting] / [Previewing] /
 * [Error]); task 18.2 extends [Error] into the typed camera/permission error
 * model so every failure carries the deterministic Operator-facing response —
 * a dual-channel message, a recovery control, and whether the preview must be
 * preserved (Req 1.6, 1.7, 1.9, 1.10, 1.11). The decision of *what* each error
 * kind means is owned by the pure [CameraErrorPolicy]; this type just carries
 * the resolved result so observers (UI + Notifier) can render it directly.
 *
 * _Requirements: 1.2, 1.4, 1.6, 1.7, 1.9, 1.10, 1.11_
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
     * The camera hit a typed error condition (Req 1.6, 1.7, 1.9, 1.10, 1.11).
     *
     * The state carries the resolved [CameraErrorPresentation] produced by
     * [CameraErrorPolicy.present]: the [error] kind, the dual-channel [message],
     * the [recoveryAction] control to offer, and whether the live preview is
     * [preservePreview] kept running underneath the error. Construct it via
     * [CameraState.error] so the presentation always comes from the single
     * source of truth and the fields can never drift.
     */
    data class Error(
        val error: CameraError,
        val message: String,
        val recoveryAction: RecoveryAction,
        val preservePreview: Boolean,
    ) : CameraState

    companion object {
        /**
         * Builds the typed [Error] state for [error] by resolving its
         * deterministic presentation through [CameraErrorPolicy]. This is the
         * only intended way to create an [Error] state so the message, recovery
         * control, and preview-preservation flag stay consistent with the
         * policy table.
         */
        fun error(error: CameraError): Error {
            val presentation = CameraErrorPolicy.present(error)
            return Error(
                error = presentation.error,
                message = presentation.message,
                recoveryAction = presentation.recoveryAction,
                preservePreview = presentation.preservePreview,
            )
        }
    }
}
