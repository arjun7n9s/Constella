package com.constella.braille.pipeline.privacy

/**
 * A reference to a frame that has been persisted to device storage by an
 * explicit save. Returned by [FrameSink.write]; framework-free so the core can
 * be tested on the JVM with a fake sink.
 *
 * _Requirements: 16.2_
 */
data class SavedScan(
    /** Id of the [CameraFrame] that was persisted. */
    val frameId: String,
    /**
     * Opaque storage location of the persisted bytes (for the Android
     * implementation this is an absolute file path). Treated as informational
     * by the core; only the act of writing is policy-relevant.
     */
    val location: String,
    /** Number of bytes written to storage. */
    val byteCount: Int,
)

/**
 * Abstraction over the actual file write so the in-memory retention core can be
 * unit-tested with a fake implementation that records writes rather than
 * touching the filesystem.
 *
 * The core never calls [write] on capture or while merely holding a frame; it
 * calls it exactly once, only when the Operator explicitly requests to save a
 * scan ([FrameHolder.saveScan]). This is the seam that lets a test assert that
 * no write occurs during in-memory handling (Req 16.1) and that exactly one
 * write occurs on explicit save (Req 16.2).
 *
 * _Requirements: 16.1, 16.2_
 */
interface FrameSink {
    /**
     * Persist [frame] to storage and return a [SavedScan] describing the result.
     *
     * Implementations MUST only be invoked from an explicit save path. This
     * interface intentionally has no "auto-persist" or "cache" operation, so
     * there is no API by which capture could write to storage implicitly.
     */
    fun write(frame: CameraFrame): SavedScan
}
