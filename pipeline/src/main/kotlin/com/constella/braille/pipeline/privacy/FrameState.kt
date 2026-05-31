package com.constella.braille.pipeline.privacy

/**
 * The privacy-relevant lifecycle state of the single frame the [FrameHolder]
 * retains. Modeled explicitly so tests and the UI can distinguish "this frame
 * lives only in RAM" from "the Operator chose to write this frame to storage".
 *
 * Capturing or holding a frame never leaves [Empty] -> [HeldInMemory] without a
 * storage write; only an explicit [FrameHolder.saveScan] moves a held frame to
 * [Persisted].
 *
 * _Requirements: 16.1, 16.2_
 */
sealed interface FrameState {

    /** No frame is currently held. */
    data object Empty : FrameState

    /**
     * A frame is held purely in device memory and has NOT been written to
     * storage. This is the state every captured frame is in until — and unless
     * — the Operator explicitly saves it.
     */
    data class HeldInMemory(val frame: CameraFrame) : FrameState

    /**
     * The held frame has been explicitly persisted to storage by the Operator.
     * The frame is still retained in memory; [saved] references where it was
     * written.
     */
    data class Persisted(val frame: CameraFrame, val saved: SavedScan) : FrameState

    /** The frame this state refers to, or `null` when [Empty]. */
    val frameOrNull: CameraFrame?
        get() = when (this) {
            Empty -> null
            is HeldInMemory -> frame
            is Persisted -> frame
        }

    /** True once the held frame has been written to storage via explicit save. */
    val isPersisted: Boolean
        get() = this is Persisted
}
