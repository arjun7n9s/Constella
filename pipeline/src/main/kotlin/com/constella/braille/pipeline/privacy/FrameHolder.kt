package com.constella.braille.pipeline.privacy

/**
 * Deterministic, framework-free core of the camera-privacy boundary (Req 16.1,
 * 16.2).
 *
 * The [FrameHolder] retains the current camera frame **in device memory only**.
 * Capturing or replacing the held frame never writes to storage. A frame is
 * persisted to storage exactly once, and only when [saveScan] is explicitly
 * invoked — modeling the rule that the System persists a captured frame solely
 * when the Operator explicitly requests to save the scan.
 *
 * This class is pure Kotlin with no Android/IO dependencies; the only path to
 * storage is the injected [FrameSink], which is the seam under test. The real
 * Android filesystem-backed sink ([com.constella.braille.pipeline.privacy
 * .AndroidFileFrameSink]) is a thin adapter that need not be unit-tested.
 *
 * Single-frame model: holding a new frame discards the previous one from memory
 * (the live camera retains only the current frame). This type is not
 * thread-safe; callers serialize access on the capture/coordinator dispatcher.
 *
 * _Requirements: 16.1, 16.2_
 */
class FrameHolder(private val sink: FrameSink) {

    private var _state: FrameState = FrameState.Empty

    /** The current privacy-lifecycle state of the held frame. */
    val state: FrameState get() = _state

    /** The frame currently held in memory, or `null` when nothing is held. */
    val currentFrame: CameraFrame? get() = _state.frameOrNull

    /** True when a frame is held in memory (whether or not it was saved). */
    val hasFrame: Boolean get() = _state !is FrameState.Empty

    /** True when the held frame has been explicitly persisted to storage. */
    val isPersisted: Boolean get() = _state.isPersisted

    /**
     * Retain [frame] in memory as the current frame. This is the capture path.
     *
     * This NEVER writes to storage — it only updates in-memory state — which is
     * what keeps camera frames on the device and out of storage until an
     * explicit save (Req 16.1). Any previously held frame is dropped from memory
     * and, importantly, its prior persisted status does not carry over: the new
     * frame starts in [FrameState.HeldInMemory].
     */
    fun hold(frame: CameraFrame) {
        _state = FrameState.HeldInMemory(frame)
    }

    /**
     * Explicitly persist the currently held frame to storage via the [FrameSink]
     * and transition to [FrameState.Persisted]. This is the ONLY operation that
     * writes a frame to storage (Req 16.2).
     *
     * Behavior:
     *  - No frame held ([FrameState.Empty]): returns `null` and writes nothing.
     *  - Frame already persisted: idempotent — returns the existing [SavedScan]
     *    and does NOT write again, so a double-tap of "save" cannot produce two
     *    writes.
     *  - Frame held but not yet saved: writes exactly once and records the result.
     *
     * @return the [SavedScan] for the persisted frame, or `null` if nothing was held.
     */
    fun saveScan(): SavedScan? {
        return when (val current = _state) {
            FrameState.Empty -> null
            is FrameState.Persisted -> current.saved
            is FrameState.HeldInMemory -> {
                val saved = sink.write(current.frame)
                _state = FrameState.Persisted(current.frame, saved)
                saved
            }
        }
    }

    /**
     * Drop the held frame from memory and return to [FrameState.Empty].
     *
     * This releases the in-memory bytes. It does not delete anything already
     * written to storage by a prior explicit [saveScan]; it only governs the
     * in-memory retention of the current frame.
     */
    fun clear() {
        _state = FrameState.Empty
    }
}
