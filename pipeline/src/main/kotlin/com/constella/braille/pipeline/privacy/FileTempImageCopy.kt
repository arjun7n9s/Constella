package com.constella.braille.pipeline.privacy

import java.io.File

/**
 * Thin filesystem-backed [TempImageCopy]: the real local temporary copy staged
 * for an attempted cloud transmission and deleted afterward by
 * [CloudTransmissionGate] (Req 16.4).
 *
 * It is deliberately a minimal adapter over [java.io.File] so the deterministic
 * cleanup policy lives in (and is tested through) [CloudTransmissionGate] with a
 * fake temp copy. This class itself is not unit-tested.
 *
 * [file] should live in an app-private temp/cache location so the staged image
 * never persists beyond the transmission attempt. Deletion is idempotent.
 *
 * Note (no extra dependency added): uses only the Java standard library, so no
 * new Gradle dependency is required.
 *
 * _Requirements: 16.4_
 */
class FileTempImageCopy(private val file: File) : TempImageCopy {

    override val location: String get() = file.absolutePath

    override fun exists(): Boolean = file.exists()

    override fun delete() {
        // File.delete() is already a no-op-returning-false when absent; this
        // keeps deletion idempotent so cleanup can be safely retried.
        if (file.exists()) {
            file.delete()
        }
    }

    companion object {
        /**
         * Stage [frame]'s bytes into a freshly written temp [file] and return a
         * [FileTempImageCopy] over it. The caller (cloud send path) passes the
         * result to [CloudTransmissionGate.transmit], which deletes it after the
         * attempt completes (Req 16.4).
         */
        fun stage(frame: CameraFrame, file: File): FileTempImageCopy {
            file.parentFile?.mkdirs()
            file.outputStream().use { out -> out.write(frame.bytes) }
            return FileTempImageCopy(file)
        }
    }
}
