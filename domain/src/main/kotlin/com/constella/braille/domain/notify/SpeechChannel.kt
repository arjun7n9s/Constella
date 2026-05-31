package com.constella.braille.domain.notify

/**
 * The spoken-audio delivery channel.
 *
 * This is a framework-free abstraction implemented later by the TTS layer
 * (`:pipeline`/`:runtime`) on top of Android `TextToSpeech`. The domain depends
 * only on this narrow contract so the dual-channel [Notifier] can speak a
 * message without knowing anything about the concrete speech engine.
 *
 * Implementations are expected to be tolerant: speaking a message should never
 * throw back into the [Notifier]. A channel that is momentarily unable to speak
 * should report [isAvailable] as `false` rather than failing in [speak], which
 * lets the [Notifier] degrade gracefully to the other channel.
 *
 * _Requirements: 2.8, 14.5, 14.6_
 */
interface SpeechChannel {
    /**
     * Whether spoken audio can currently be produced.
     *
     * For example, this is `false` when no offline voice data is installed
     * (Req 11.5) so the [Notifier] knows to rely on the display channel.
     */
    val isAvailable: Boolean

    /** Speak [notification] aloud. Called only when [isAvailable] is `true`. */
    fun speak(notification: Notification)
}
