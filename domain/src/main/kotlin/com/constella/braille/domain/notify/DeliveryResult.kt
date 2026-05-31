package com.constella.braille.domain.notify

/**
 * The outcome of delivering a single [Notification] through the [Notifier].
 *
 * It records which channels actually received the message so callers (and the
 * Property 26 test in task 15.2) can verify the dual-channel and graceful
 * degradation rules: when both channels are available a message reaches both;
 * when exactly one is available it still reaches that one; when none are
 * available nothing is delivered and no error is raised.
 *
 * _Requirements: 2.8, 14.5, 14.6_
 */
data class DeliveryResult(
    /** `true` if the message was spoken on the [SpeechChannel]. */
    val spoken: Boolean,
    /** `true` if the message was shown on the [DisplayChannel]. */
    val displayed: Boolean,
) {
    /** `true` if the message reached at least one channel. */
    val delivered: Boolean get() = spoken || displayed

    /** `true` if the message reached both channels (the normal, non-degraded case). */
    val deliveredOnBothChannels: Boolean get() = spoken && displayed
}
