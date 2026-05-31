package com.constella.braille.domain.notify

/**
 * A single operator-facing notification: a piece of [text] tagged with its
 * [category].
 *
 * This is a plain, immutable value holder handed to the [Notifier] for
 * dual-channel delivery. It deliberately carries no channel or delivery state —
 * the [Notifier] decides which channels receive it — and it does not constrain
 * how [text] was generated, because message-content generation from a
 * `ScanStatus` or alignment condition is a separate concern (task 15.3).
 *
 * The text must be non-blank: a message with nothing to say must never reach a
 * channel, since every reported guidance/failure/low-confidence condition is
 * required to carry a real, deliverable message (Req 14.4/14.5).
 *
 * _Requirements: 2.8, 14.5, 14.6_
 *
 * @property text the human-readable message content to speak and/or show.
 * @property category which family of message this is (see [NotificationCategory]),
 *   used by channels that want to present categories differently and by callers
 *   reasoning about what was delivered.
 */
data class Notification(
    val text: String,
    val category: NotificationCategory,
) {
    init {
        require(text.isNotBlank()) { "Notification text must not be blank" }
    }
}
