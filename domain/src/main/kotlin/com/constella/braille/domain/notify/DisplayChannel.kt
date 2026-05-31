package com.constella.braille.domain.notify

/**
 * The on-screen visual delivery channel.
 *
 * This is a framework-free abstraction implemented later by the UI layer
 * (`:app`) on top of Jetpack Compose. The domain depends only on this narrow
 * contract so the dual-channel [Notifier] can show a message without knowing
 * anything about composables or `liveRegion` semantics.
 *
 * Implementations are expected to be tolerant: showing a message should never
 * throw back into the [Notifier]. A channel that cannot currently render (for
 * example when no display surface is attached, Req 10.6) should report
 * [isAvailable] as `false` so the [Notifier] degrades gracefully to speech.
 *
 * _Requirements: 2.8, 14.5, 14.6_
 */
interface DisplayChannel {
    /** Whether an on-screen surface is currently available to show messages. */
    val isAvailable: Boolean

    /** Show [notification] on screen. Called only when [isAvailable] is `true`. */
    fun show(notification: Notification)
}
