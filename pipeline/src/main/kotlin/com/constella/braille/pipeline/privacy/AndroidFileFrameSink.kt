package com.constella.braille.pipeline.privacy

import java.io.File
import java.io.IOException

/**
 * Thin Android filesystem-backed [FrameSink]: the real storage write performed
 * when the Operator explicitly saves a scan (Req 16.2).
 *
 * It is deliberately a minimal adapter over [java.io]/[java.nio] so the
 * deterministic retention rules live in (and are tested through) [FrameHolder]
 * with a fake sink. This class itself is not unit-tested.
 *
 * [directory] should be an app-private location — e.g. on Android pass
 * `context.filesDir` (or a subdirectory of it). Using app-private storage keeps
 * saved scans on the device under the app sandbox, consistent with the privacy
 * requirement that frames stay on the device.
 *
 * Note (no extra dependency added): this uses only the Android SDK and the Java
 * standard library, so it needs no new Gradle dependency. If a future task
 * wants `Context`-aware directory selection, inject the resolved [File]
 * directory from the `:app`/`:pipeline` wiring rather than importing
 * `android.content.Context` here.
 *
 * _Requirements: 16.2_
 */
class AndroidFileFrameSink(
    private val directory: File,
) : FrameSink {

    override fun write(frame: CameraFrame): SavedScan {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create scan storage directory: ${directory.absolutePath}")
        }
        val target = File(directory, fileName(frame))
        // Single explicit write of the in-memory bytes to device storage.
        target.outputStream().use { out -> out.write(frame.bytes) }
        return SavedScan(
            frameId = frame.id,
            location = target.absolutePath,
            byteCount = frame.bytes.size,
        )
    }

    private fun fileName(frame: CameraFrame): String {
        val safeId = frame.id.map { ch -> if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_' }
            .joinToString("")
        return "scan_${safeId}_${frame.capturedAtMillis}.${frame.format.fileExtension}"
    }
}
