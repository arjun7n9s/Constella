package com.constella.braille.pipeline.privacy

/**
 * Encoding of the raw bytes a [CameraFrame] holds, so a [FrameSink] can pick a
 * sensible file extension when (and only when) a scan is explicitly saved.
 */
enum class FrameFormat(val fileExtension: String) {
    JPEG("jpg"),
    PNG("png"),
    /** Single-channel / packed sensor bytes with no container. */
    RAW("bin"),
}

/**
 * A single camera frame held purely in device memory.
 *
 * This is a framework-free value type (no Android, OpenCV, or CameraX types) so
 * the in-memory retention core can be unit-tested on the JVM. Frames are kept in
 * memory only; nothing here writes to storage. Persistence happens exclusively
 * through an explicit [FrameHolder.saveScan] call (see Req 16.1, 16.2).
 *
 * [bytes] are retained by reference; callers must not mutate the array after
 * handing it to the store.
 *
 * _Requirements: 16.1, 16.2_
 */
class CameraFrame(
    /** Stable identifier for this frame within a session (used to name saved files). */
    val id: String,
    /** Encoded pixel bytes; kept in memory only until an explicit save. */
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val format: FrameFormat,
    /** Capture wall-clock time in milliseconds since the Unix epoch. */
    val capturedAtMillis: Long,
) {
    init {
        require(id.isNotBlank()) { "CameraFrame id must not be blank" }
        require(width > 0 && height > 0) {
            "CameraFrame dimensions must be positive but were ${width}x$height"
        }
    }

    /** Number of in-memory bytes this frame currently occupies. */
    val byteCount: Int get() = bytes.size
}
