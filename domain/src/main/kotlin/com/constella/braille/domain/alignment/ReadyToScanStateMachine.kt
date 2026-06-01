package com.constella.braille.domain.alignment

import com.constella.braille.domain.config.ScanConstants
import com.constella.braille.domain.model.AlignmentGuidance

/**
 * The outcome of feeding one alignment reading into the
 * [ReadyToScanStateMachine].
 *
 * @property guidance the debounced guidance to surface this cycle. While the
 *   ready-to-scan state is being **held** through a short fluctuation this is
 *   still [AlignmentGuidance.ReadyToScan] (Req 2.10); when the machine leaves
 *   ready it is the instantaneous active guidance that the Operator should now
 *   act on (Req 2.11).
 * @property ready whether the ready-to-scan state is active *after* this update.
 * @property readyAnnounced `true` only on the cycle where the machine
 *   *transitions* from not-ready into ready — i.e. a fresh readiness
 *   announcement. It is never `true` while the state is merely being held, so
 *   two `readyAnnounced` cycles can never occur without an intervening
 *   not-ready cycle (Req 2.13).
 */
data class ReadyStateUpdate(
    val guidance: AlignmentGuidance,
    val ready: Boolean,
    val readyAnnounced: Boolean,
)

/**
 * Virtual-clock-driven debounce state machine for the ready-to-scan state
 * (Req 2.10–2.13).
 *
 * It consumes the **instantaneous** decision produced by [AlignmentEvaluator]
 * (one [AlignmentGuidance] per evaluation cycle) together with the current
 * time, and produces the **debounced** guidance plus the ready-state edge
 * information. The machine is deterministic and fully time-injected: every
 * [update] is told "now" explicitly, so it never reads a wall clock and is
 * trivially exercised on the JVM. Pass a monotonically non-decreasing
 * `nowMs` (e.g. from the same source that timestamps frames).
 *
 * Behaviour:
 * - Entry is immediate: the first reading whose instantaneous guidance is
 *   [AlignmentGuidance.ReadyToScan] enters ready and announces it (Req 2.7 feeds
 *   this; there is no debounce on *entering* ready).
 * - While ready, an out-of-threshold reading starts a fluctuation "burst". The
 *   ready state is **held** (guidance stays [AlignmentGuidance.ReadyToScan])
 *   for as long as the continuous burst is shorter than [readyDebounceMs]
 *   (Req 2.10). A reading that passes again clears the burst and keeps ready.
 * - If a continuous out-of-threshold burst reaches [readyDebounceMs], the
 *   machine **leaves** ready and resumes active guidance (Req 2.11).
 * - If the document leaves the frame while ready (the instantaneous guidance is
 *   [AlignmentGuidance.PointAtDocument], which [AlignmentEvaluator] emits only
 *   when no document is present), the machine leaves ready once the document
 *   has been continuously absent for [documentExitReactionMs] — a tighter
 *   window than the general debounce so readiness is dropped within the reaction
 *   budget (Req 2.12). If the document reappears before that window elapses, the
 *   exit timer resets and only the general debounce governs.
 * - The machine always passes through a not-ready cycle before it can announce
 *   readiness again, so readiness is never re-announced without first being left
 *   (Req 2.13).
 *
 * The debounce and reaction windows default to the single source of truth in
 * [ScanConstants.Timing]; they are constructor parameters only so tests can make
 * the boundaries explicit. Nothing here hard-codes 750 or 500.
 *
 * This type is **stateful and not thread-safe**: drive a single instance from
 * the alignment loop's own thread (or guard it externally).
 *
 * _Requirements: 2.10, 2.11, 2.12, 2.13_
 *
 * @param readyDebounceMs how long a continuous out-of-threshold burst may last
 *   before ready is left (Req 2.10, 2.11). Defaults to
 *   [ScanConstants.Timing.READY_DEBOUNCE_MS].
 * @param documentExitReactionMs how long the document may be continuously absent
 *   before ready is left (Req 2.12). Defaults to
 *   [ScanConstants.Timing.DOCUMENT_EXIT_REACTION_MS].
 */
class ReadyToScanStateMachine(
    private val readyDebounceMs: Long = ScanConstants.Timing.READY_DEBOUNCE_MS.toLong(),
    private val documentExitReactionMs: Long = ScanConstants.Timing.DOCUMENT_EXIT_REACTION_MS.toLong(),
) {

    init {
        require(readyDebounceMs > 0) { "readyDebounceMs must be positive" }
        require(documentExitReactionMs > 0) { "documentExitReactionMs must be positive" }
    }

    /** Whether the ready-to-scan state is currently active. */
    var isReady: Boolean = false
        private set

    /**
     * Absolute time the current continuous out-of-threshold burst began while
     * ready, or `null` when no burst is in progress (i.e. the last reading
     * passed or the machine is not ready).
     */
    private var outSinceMs: Long? = null

    /**
     * Absolute time the document became continuously absent during the current
     * burst, or `null` when the document is present. Reset as soon as the
     * document reappears so the [documentExitReactionMs] window only applies
     * while the document is actually gone.
     */
    private var documentExitSinceMs: Long? = null

    /** Return the machine to its initial not-ready state, discarding any burst. */
    fun reset() {
        isReady = false
        outSinceMs = null
        documentExitSinceMs = null
    }

    /**
     * Feed one instantaneous alignment decision at time [nowMs] and obtain the
     * debounced result.
     *
     * @param nowMs the current time on the (monotonically non-decreasing)
     *   virtual clock, in milliseconds.
     * @param instantaneous the instantaneous guidance for this cycle, typically
     *   straight from [AlignmentEvaluator.evaluate].
     */
    fun update(nowMs: Long, instantaneous: AlignmentGuidance): ReadyStateUpdate {
        val passing = instantaneous == AlignmentGuidance.ReadyToScan

        if (!isReady) {
            // Not ready: no burst is tracked. Entering ready is immediate.
            clearBurst()
            return if (passing) {
                isReady = true
                ReadyStateUpdate(AlignmentGuidance.ReadyToScan, ready = true, readyAnnounced = true)
            } else {
                ReadyStateUpdate(instantaneous, ready = false, readyAnnounced = false)
            }
        }

        // Ready: a passing reading ends any fluctuation and keeps ready without
        // re-announcing (Req 2.10 — the state was held continuously).
        if (passing) {
            clearBurst()
            return ReadyStateUpdate(AlignmentGuidance.ReadyToScan, ready = true, readyAnnounced = false)
        }

        // Ready but out of threshold this cycle: track the continuous burst.
        val burstStart = outSinceMs ?: nowMs.also { outSinceMs = it }

        if (instantaneous == AlignmentGuidance.PointAtDocument) {
            // Document absent (Req 2.12). Start the exit timer once and let it run.
            if (documentExitSinceMs == null) documentExitSinceMs = nowMs
        } else {
            // Document is back (just misaligned): the exit reaction no longer
            // applies; fall back to the general debounce only.
            documentExitSinceMs = null
        }

        val leaveForDebounce = nowMs - burstStart >= readyDebounceMs           // Req 2.11
        val leaveForDocumentExit = documentExitSinceMs?.let {                  // Req 2.12
            nowMs - it >= documentExitReactionMs
        } ?: false

        return if (leaveForDebounce || leaveForDocumentExit) {
            isReady = false
            clearBurst()
            ReadyStateUpdate(instantaneous, ready = false, readyAnnounced = false)
        } else {
            // Still inside the debounce window: hold the ready state (Req 2.10).
            ReadyStateUpdate(AlignmentGuidance.ReadyToScan, ready = true, readyAnnounced = false)
        }
    }

    /**
     * Convenience overload that evaluates [metrics] through [AlignmentEvaluator]
     * and feeds the resulting instantaneous guidance into [update].
     */
    fun update(nowMs: Long, metrics: AlignmentMetrics): ReadyStateUpdate =
        update(nowMs, AlignmentEvaluator.evaluate(metrics))

    private fun clearBurst() {
        outSinceMs = null
        documentExitSinceMs = null
    }
}
