package com.constella.braille.domain.alignment

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.AlignmentGuidance
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Example-based JVM tests for [ReadyToScanStateMachine] (task 3.4), driven by a
 * virtual clock so the debounce/reaction timing is fully deterministic.
 *
 * Covers the four behaviours required of the ready-to-scan state machine:
 * - sub-[READY_DEBOUNCE_MS] fluctuations hold the ready state (Req 2.10),
 * - an out-of-threshold burst lasting [READY_DEBOUNCE_MS] leaves ready (Req 2.11),
 * - the document leaving the frame drops ready within [DOCUMENT_EXIT_REACTION_MS]
 *   (Req 2.12),
 * - readiness is always re-announced through an intervening not-ready cycle
 *   (Req 2.13).
 *
 * The dedicated property-based test for Property 3 is task 3.5.
 */
class ReadyToScanStateMachineTest : StringSpec({

    val debounce = ScanConstants.Timing.READY_DEBOUNCE_MS.toLong()
    val exitReaction = ScanConstants.Timing.DOCUMENT_EXIT_REACTION_MS.toLong()

    // Instantaneous decisions fed into the machine.
    val pass = AlignmentGuidance.ReadyToScan
    val misaligned = AlignmentGuidance.MoveCloser
    val absent = AlignmentGuidance.PointAtDocument

    "starts not ready and surfaces instantaneous guidance before any pass" {
        val sm = ReadyToScanStateMachine()
        sm.isReady.shouldBeFalse()

        val update = sm.update(nowMs = 0, instantaneous = misaligned)

        update.ready.shouldBeFalse()
        update.readyAnnounced.shouldBeFalse()
        update.guidance shouldBe misaligned
    }

    "enters ready immediately and announces it when all thresholds pass" {
        val sm = ReadyToScanStateMachine()

        val update = sm.update(nowMs = 0, instantaneous = pass)

        update.ready.shouldBeTrue()
        update.readyAnnounced.shouldBeTrue()
        update.guidance shouldBe AlignmentGuidance.ReadyToScan
        sm.isReady.shouldBeTrue()
    }

    // Req 2.10
    "holds ready through an out-of-threshold burst shorter than the debounce period" {
        val sm = ReadyToScanStateMachine()
        sm.update(nowMs = 0, instantaneous = pass).readyAnnounced.shouldBeTrue()

        // Fluctuation begins at t=100 and is still shorter than the debounce
        // window at every sampled point below.
        val held1 = sm.update(nowMs = 100, instantaneous = misaligned)
        val held2 = sm.update(nowMs = 100 + debounce - 1, instantaneous = misaligned)

        held1.ready.shouldBeTrue()
        held1.guidance shouldBe AlignmentGuidance.ReadyToScan
        held1.readyAnnounced.shouldBeFalse()

        held2.ready.shouldBeTrue()
        held2.guidance shouldBe AlignmentGuidance.ReadyToScan
        held2.readyAnnounced.shouldBeFalse()
        sm.isReady.shouldBeTrue()
    }

    // Req 2.10: a passing reading inside the window clears the burst, and a later
    // separate short burst is measured from its own start (timers do not accumulate).
    "a recovering pass clears the burst so a later short burst still holds ready" {
        val sm = ReadyToScanStateMachine()
        sm.update(nowMs = 0, instantaneous = pass)

        // First burst: 0.5 * debounce, then recover.
        sm.update(nowMs = 100, instantaneous = misaligned).ready.shouldBeTrue()
        sm.update(nowMs = 100 + debounce / 2, instantaneous = pass).ready.shouldBeTrue()

        // Second burst starts fresh; even though wall time is now well past one
        // debounce since the first burst, this burst alone is short → still held.
        val start2 = 100 + debounce / 2 + 50
        sm.update(nowMs = start2, instantaneous = misaligned).ready.shouldBeTrue()
        val held = sm.update(nowMs = start2 + debounce - 1, instantaneous = misaligned)
        held.ready.shouldBeTrue()
        held.guidance shouldBe AlignmentGuidance.ReadyToScan
    }

    // Req 2.11
    "leaves ready and resumes active guidance when a condition stays out beyond the debounce period" {
        val sm = ReadyToScanStateMachine()
        sm.update(nowMs = 0, instantaneous = pass)

        val burstStart = 200L
        sm.update(nowMs = burstStart, instantaneous = misaligned).ready.shouldBeTrue()

        val left = sm.update(nowMs = burstStart + debounce, instantaneous = misaligned)

        left.ready.shouldBeFalse()
        left.readyAnnounced.shouldBeFalse()
        left.guidance shouldBe misaligned // resumes active guidance, not ReadyToScan
        sm.isReady.shouldBeFalse()
    }

    // Req 2.12
    "leaves ready within the reaction window when the document leaves the frame" {
        val sm = ReadyToScanStateMachine()
        sm.update(nowMs = 0, instantaneous = pass)

        val exitStart = 1_000L
        // Document gone but reaction window not yet elapsed → still held.
        sm.update(nowMs = exitStart, instantaneous = absent).ready.shouldBeTrue()
        sm.update(nowMs = exitStart + exitReaction - 1, instantaneous = absent).ready.shouldBeTrue()

        // Reaction window elapsed → ready dropped, even though it is sooner than
        // the general debounce period.
        val left = sm.update(nowMs = exitStart + exitReaction, instantaneous = absent)

        (exitReaction < debounce).shouldBeTrue() // guards the intent of this test
        left.ready.shouldBeFalse()
        left.guidance shouldBe AlignmentGuidance.PointAtDocument
        sm.isReady.shouldBeFalse()
    }

    // Req 2.12 boundary: a document that reappears (now merely misaligned) before
    // the reaction window elapses falls back to the general debounce.
    "document reappearing before the reaction window falls back to the general debounce" {
        val sm = ReadyToScanStateMachine()
        sm.update(nowMs = 0, instantaneous = pass)

        val burstStart = 500L
        sm.update(nowMs = burstStart, instantaneous = absent).ready.shouldBeTrue()
        // Reappears as misaligned before the 500 ms exit window would fire.
        sm.update(nowMs = burstStart + exitReaction - 10, instantaneous = misaligned).ready.shouldBeTrue()

        // Past the exit window but still inside the debounce window → held,
        // because the document-exit timer was cancelled when it reappeared.
        sm.update(nowMs = burstStart + exitReaction + 10, instantaneous = misaligned).ready.shouldBeTrue()

        // Once the *continuous* burst reaches the debounce period, ready leaves.
        val left = sm.update(nowMs = burstStart + debounce, instantaneous = misaligned)
        left.ready.shouldBeFalse()
        left.guidance shouldBe misaligned
    }

    // Req 2.13
    "re-announces readiness only after passing through a not-ready cycle" {
        val sm = ReadyToScanStateMachine()

        // First announcement.
        sm.update(nowMs = 0, instantaneous = pass).readyAnnounced.shouldBeTrue()

        // Leave ready via a debounce-exceeding burst.
        sm.update(nowMs = 100, instantaneous = misaligned)
        sm.update(nowMs = 100 + debounce, instantaneous = misaligned).ready.shouldBeFalse()

        // Still not ready on a subsequent failing cycle (no announcement).
        val notReady = sm.update(nowMs = 100 + debounce + 50, instantaneous = misaligned)
        notReady.ready.shouldBeFalse()
        notReady.readyAnnounced.shouldBeFalse()

        // Passing again produces a fresh announcement (the intervening not-ready
        // cycle is what makes this a re-announcement rather than a held state).
        val reannounced = sm.update(nowMs = 100 + debounce + 100, instantaneous = pass)
        reannounced.ready.shouldBeTrue()
        reannounced.readyAnnounced.shouldBeTrue()
    }

    "never emits two readiness announcements without an intervening not-ready cycle" {
        val sm = ReadyToScanStateMachine()

        // A long run of consecutive passing reads must announce exactly once.
        var announcements = 0
        for (t in 0..10) {
            if (sm.update(nowMs = t * 100L, instantaneous = pass).readyAnnounced) announcements++
        }
        announcements shouldBe 1
    }

    "reset returns the machine to its initial not-ready state" {
        val sm = ReadyToScanStateMachine()
        sm.update(nowMs = 0, instantaneous = pass).ready.shouldBeTrue()

        sm.reset()
        sm.isReady.shouldBeFalse()

        // After reset the next pass is a fresh first announcement.
        sm.update(nowMs = 10, instantaneous = pass).readyAnnounced.shouldBeTrue()
    }

    "the metrics overload routes through the evaluator and debounces identically" {
        val sm = ReadyToScanStateMachine()
        val readyMetrics = AlignmentMetrics(
            documentPresent = true,
            documentFillFraction = 0.5f,
            movementPerCycle = 0.0f,
            luminance = 0.8f,
            planeTiltDegrees = 0f,
        )
        val tooFarMetrics = readyMetrics.copy(documentFillFraction = 0.05f) // < MIN_FILL → MoveCloser

        sm.update(nowMs = 0, metrics = readyMetrics).readyAnnounced.shouldBeTrue()

        // Short fluctuation holds ready.
        val held = sm.update(nowMs = 100, metrics = tooFarMetrics)
        held.ready.shouldBeTrue()
        held.guidance shouldBe AlignmentGuidance.ReadyToScan

        // Sustained beyond the debounce leaves ready with the active guidance.
        val left = sm.update(nowMs = 100 + debounce, metrics = tooFarMetrics)
        left.ready.shouldBeFalse()
        left.guidance shouldBe AlignmentGuidance.MoveCloser
    }
})
