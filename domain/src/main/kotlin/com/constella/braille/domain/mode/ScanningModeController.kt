package com.constella.braille.domain.mode

import com.constella.braille.domain.model.ScanningMode

/**
 * Manages the active [ScanningMode] and tracks the session-scoped state needed
 * for mode-specific behaviours (Req 9.1–9.7).
 *
 * Responsibilities:
 *
 *  1. **Mode management (Req 9.1, 9.2).** Holds the active [ScanningMode],
 *     defaulting to [ScanningMode.EMBOSSED] at construction — the high-accuracy
 *     tier and session default.
 *
 *  2. **Active-mode display (Req 9.4).** Exposes [activeModeName] as a
 *     human-readable label the UI can render beside the mode selector.
 *
 *  3. **Lower-confidence labeling (Req 9.5).** [handwrittenTierLabel] is the
 *     second-tier label to attach to every Handwritten_Mode result rendering.
 *     It is non-null only when the active mode is Handwritten.
 *
 *  4. **First-handwritten-scan spoken reliability notice (Req 9.6).** The first
 *     time the Operator scans in Handwritten_Mode this session, the System
 *     speaks a reliability notice. [consumeFirstHandwrittenNotice] fires once
 *     and returns `true` on the first call after switching to Handwritten; all
 *     subsequent calls return `false` until the mode is changed away and back.
 *
 *  5. **Per-session disable (Req 9.7).** [disableHandwrittenNotice] lets the
 *     Operator suppress the spoken notice for the rest of the session.
 *
 * This class is pure Kotlin with no Android dependency. It is not thread-safe;
 * callers serialize access on the UI/coordinator thread.
 *
 * _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_
 */
class ScanningModeController(
    initialMode: ScanningMode = ScanningMode.EMBOSSED,
) {

    /**
     * The currently active scanning mode. Starts as [ScanningMode.EMBOSSED] —
     * the session default (Req 9.1, 9.2).
     */
    var currentMode: ScanningMode = initialMode
        private set

    /**
     * Whether the spoken reliability notice has been delivered this session for
     * the *current* handwritten engagement. Reset on each [selectMode] call that
     * re-enters Handwritten.
     */
    private var handwrittenNoticeDelivered: Boolean = false

    /**
     * Whether the Operator has disabled the handwritten notice for the rest of
     * this session (Req 9.7).
     */
    private var handwrittenNoticeSuppressed: Boolean = false

    /** `true` when the active mode is [ScanningMode.HANDWRITTEN]. */
    val isHandwritten: Boolean
        get() = currentMode == ScanningMode.HANDWRITTEN

    /**
     * Human-readable name of the active mode for display (Req 9.4).
     */
    val activeModeName: String
        get() = when (currentMode) {
            ScanningMode.EMBOSSED -> "Embossed"
            ScanningMode.HANDWRITTEN -> "Handwritten"
        }

    /**
     * The lower-confidence tier label to attach to every Handwritten_Mode result
     * rendering (Req 9.5), or `null` when the active mode is Embossed (no label
     * needed).
     */
    val handwrittenTierLabel: String?
        get() = if (isHandwritten) {
            "Lower-confidence result (Handwritten mode)"
        } else {
            null
        }

    /**
     * Switch the active mode to [mode] and apply mode parameters to subsequent
     * scans (Req 9.3). The detector parameter selection itself happens via
     * `DetectorParams.paramsFor(mode)` at scan time; this controller only holds
     * the selection state.
     *
     * When switching *to* Handwritten, the first-scan notice tracker is reset so
     * the first scan in this engagement triggers the spoken reliability notice
     * (Req 9.6) — unless the Operator has suppressed it via
     * [disableHandwrittenNotice].
     */
    fun selectMode(mode: ScanningMode) {
        if (mode != currentMode) {
            currentMode = mode
            if (mode == ScanningMode.HANDWRITTEN) {
                // Reset the notice tracker for this new Handwritten engagement.
                handwrittenNoticeDelivered = false
            }
        }
    }

    /**
     * Consume the first-handwritten-scan spoken reliability notice (Req 9.6).
     *
     * Returns `true` **once** per Handwritten engagement — on the first call
     * after [selectMode] switched to Handwritten — so the caller speaks the
     * notice at that moment. All subsequent calls until the next mode switch
     * return `false`.
     *
     * Returns `false` immediately if:
     * - The active mode is not Handwritten.
     * - The notice was already consumed this engagement.
     * - The Operator has suppressed the notice for this session (Req 9.7).
     */
    fun consumeFirstHandwrittenNotice(): Boolean {
        if (!isHandwritten) return false
        if (handwrittenNoticeDelivered) return false
        if (handwrittenNoticeSuppressed) return false
        handwrittenNoticeDelivered = true
        return true
    }

    /**
     * The spoken notice text for the first Handwritten scan, or `null` if no
     * notice is due. Combines the check and the text generation.
     */
    fun firstHandwrittenNoticeText(): String? {
        return if (consumeFirstHandwrittenNotice()) {
            "Handwritten Braille recognition has lower accuracy than embossed. " +
                "Results may be less reliable."
        } else {
            null
        }
    }

    /**
     * Suppress the spoken reliability notice for the rest of this session
     * (Req 9.7). Subsequent calls to [consumeFirstHandwrittenNotice] return
     * `false` even after mode switches.
     */
    fun disableHandwrittenNotice() {
        handwrittenNoticeSuppressed = true
    }
}
