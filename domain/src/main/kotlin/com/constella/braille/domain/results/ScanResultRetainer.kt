package com.constella.braille.domain.results

import com.constella.braille.domain.model.ScanResult

/**
 * Retains a completed [ScanResult] when the display is unavailable (Req 10.6).
 *
 * If recognition completes while the UI surface is not visible (the device is
 * locked, the app is in the background, or the Compose surface has detached),
 * the [ScanResult] must be retained in memory and presented when the display
 * becomes available. This ensures the Operator never loses a completed scan
 * because they happened to look away at the moment it finished.
 *
 * ### Flow
 *
 *  1. The ScanCoordinator calls [retain] with the completed result when the
 *     display is unavailable.
 *  2. When the UI surface resumes, it calls [consume] to retrieve and clear the
 *     retained result.
 *  3. Only the most recent result is kept: a new [retain] overwrites any
 *     unconsumed previous result, so the Operator always sees the latest scan.
 *
 * This class is pure Kotlin with no Android dependency. It is not thread-safe;
 * callers should synchronize via the coordinator dispatcher or the UI thread.
 *
 * _Requirements: 10.6_
 */
class ScanResultRetainer {

    /**
     * The retained [ScanResult], or `null` when no result is waiting.
     */
    private var pending: ScanResult? = null

    /**
     * Whether a result is currently retained and waiting for the display.
     */
    val hasPendingResult: Boolean
        get() = pending != null

    /**
     * Retain [result] for later presentation. If a previous result was retained
     * but not yet consumed, it is silently replaced — the Operator gets the most
     * recent scan.
     *
     * @param result the completed [ScanResult] to retain.
     */
    fun retain(result: ScanResult) {
        pending = result
    }

    /**
     * Consume and return the retained result, clearing the held reference.
     *
     * @return the retained [ScanResult], or `null` if nothing was waiting.
     */
    fun consume(): ScanResult? {
        val result = pending
        pending = null
        return result
    }

    /**
     * Discard any retained result without presenting it. Use when the retained
     * result is no longer relevant (e.g. the Operator started a new scan).
     */
    fun clear() {
        pending = null
    }
}
