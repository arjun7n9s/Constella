package com.constella.braille.pipeline.privacy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for the deterministic camera-privacy core ([FrameHolder]).
 *
 * The [RecordingFrameSink] is a fake storage seam that counts and records every
 * write, letting us assert that (a) capturing/holding frames never writes to
 * storage (Req 16.1) and (b) exactly one write occurs only when [saveScan] is
 * explicitly invoked (Req 16.2). No filesystem or Android types are involved.
 *
 * _Requirements: 16.1, 16.2_
 */
class FrameHolderTest : StringSpec({

    fun frame(id: String = "f1", bytes: ByteArray = byteArrayOf(1, 2, 3)) =
        CameraFrame(
            id = id,
            bytes = bytes,
            width = 4,
            height = 4,
            format = FrameFormat.JPEG,
            capturedAtMillis = 1_000L,
        )

    "a new holder starts empty and holds nothing" {
        val holder = FrameHolder(RecordingFrameSink())

        holder.state shouldBe FrameState.Empty
        holder.hasFrame.shouldBeFalse()
        holder.currentFrame.shouldBeNull()
        holder.isPersisted.shouldBeFalse()
    }

    "holding a frame keeps it in memory and writes nothing to storage" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)
        val captured = frame()

        holder.hold(captured)

        holder.state.shouldBeInstanceOf<FrameState.HeldInMemory>()
        holder.currentFrame shouldBe captured
        holder.hasFrame.shouldBeTrue()
        holder.isPersisted.shouldBeFalse()
        // The privacy invariant: capture/hold never touches storage (Req 16.1).
        sink.writeCount shouldBe 0
        sink.writes.isEmpty().shouldBeTrue()
    }

    "holding many frames in succession never writes to storage" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)

        repeat(50) { i -> holder.hold(frame(id = "frame-$i")) }

        sink.writeCount shouldBe 0
        // Only the most recently held frame is retained in memory.
        holder.currentFrame.shouldNotBeNull().id shouldBe "frame-49"
    }

    "explicit saveScan persists exactly once and transitions to Persisted" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)
        val captured = frame()
        holder.hold(captured)

        val saved = holder.saveScan()

        // Exactly one write, only on explicit save (Req 16.2).
        sink.writeCount shouldBe 1
        sink.writes.single().id shouldBe captured.id
        saved.shouldNotBeNull()
        saved.frameId shouldBe captured.id
        holder.isPersisted.shouldBeTrue()
        holder.state.shouldBeInstanceOf<FrameState.Persisted>()
    }

    "saveScan with nothing held writes nothing and returns null" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)

        val saved = holder.saveScan()

        saved.shouldBeNull()
        sink.writeCount shouldBe 0
        holder.state shouldBe FrameState.Empty
    }

    "saveScan is idempotent: a second save does not write again" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)
        holder.hold(frame())

        val first = holder.saveScan()
        val second = holder.saveScan()

        sink.writeCount shouldBe 1
        second shouldBe first
    }

    "holding a new frame after saving requires a fresh explicit save to persist" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)

        holder.hold(frame(id = "first"))
        holder.saveScan()
        sink.writeCount shouldBe 1

        // A newly captured frame is back to in-memory only; no auto-persist.
        holder.hold(frame(id = "second"))
        holder.isPersisted.shouldBeFalse()
        sink.writeCount shouldBe 1

        holder.saveScan()
        sink.writeCount shouldBe 2
        sink.writes.map { it.id } shouldBe listOf("first", "second")
    }

    "clear drops the in-memory frame without writing to storage" {
        val sink = RecordingFrameSink()
        val holder = FrameHolder(sink)
        holder.hold(frame())

        holder.clear()

        holder.state shouldBe FrameState.Empty
        holder.hasFrame.shouldBeFalse()
        sink.writeCount shouldBe 0
    }
})

/**
 * In-memory fake [FrameSink] that records every write instead of touching the
 * filesystem, so tests can assert the count and order of explicit saves.
 */
private class RecordingFrameSink : FrameSink {
    val writes = mutableListOf<CameraFrame>()
    val writeCount: Int get() = writes.size

    override fun write(frame: CameraFrame): SavedScan {
        writes += frame
        return SavedScan(
            frameId = frame.id,
            location = "memory://${frame.id}",
            byteCount = frame.bytes.size,
        )
    }
}
