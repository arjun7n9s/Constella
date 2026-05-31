package com.constella.braille.domain.notify

/**
 * The kind of message the [Notifier] is asked to deliver.
 *
 * The category lets concrete channels (and later the UI) style or prioritize a
 * message — for example rendering a [FAILURE] differently from routine
 * [GUIDANCE] — without the [Notifier] itself needing to know any presentation
 * detail. It does not change the dual-channel delivery rule: every category is
 * delivered to whichever channels are available.
 *
 * _Requirements: 2.8, 14.5, 14.6_
 */
enum class NotificationCategory {
    /** Live alignment guidance while aiming the camera (Req 2.8). */
    GUIDANCE,

    /** A scan failure or hardware/processing error (Req 14.5, 14.6). */
    FAILURE,

    /** A low-confidence result with a rescan recommendation (Req 14.5, 14.6). */
    LOW_CONFIDENCE,
}
