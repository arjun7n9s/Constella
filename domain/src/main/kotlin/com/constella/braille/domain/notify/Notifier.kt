package com.constella.braille.domain.notify

/**
 * The centralized dual-channel notifier.
 *
 * Every operator-facing guidance, failure, and low-confidence message flows
 * through this single component so none can ever ship as visual-only or
 * audio-only: each [notify] call attempts delivery on **both** the
 * [SpeechChannel] and the [DisplayChannel] (Req 2.8, 14.5). When a channel is
 * absent or unavailable the message is still delivered through the other
 * (Req 14.6); when neither can deliver the call is a safe no-op rather than an
 * error.
 *
 * Degradation is handled three ways, all collapsing to "deliver on whatever can
 * deliver":
 *  - a channel may be `null` (not wired in this build/context),
 *  - a wired channel may report `isAvailable = false` (queried per message, so
 *    channels that appear/disappear mid-session — TTS voice installed later, a
 *    display surface detaching — are handled at the moment of delivery), and
 *  - a wired, available channel may throw while delivering — the failure is
 *    isolated so the other channel still receives the message.
 *
 * This class is pure Kotlin with no Android dependencies: the concrete speech
 * (Android `TextToSpeech`) and display (Compose UI) channels are injected from
 * upper layers. Generating the message *content* from a `ScanStatus` or an
 * alignment condition is a separate concern (task 15.3); this Notifier only
 * delivers a [Notification] it is handed.
 *
 * _Requirements: 2.8, 14.5, 14.6_
 *
 * @property speechChannel the spoken-audio delivery channel, or `null` when no
 *   speech channel is wired.
 * @property displayChannel the on-screen visual delivery channel, or `null`
 *   when no display channel is wired.
 */
class Notifier(
    private val speechChannel: SpeechChannel? = null,
    private val displayChannel: DisplayChannel? = null,
) {

    /**
     * Deliver [notification] on both channels, degrading to whichever is
     * available.
     *
     * A channel receives the notification only when it is wired and its
     * `isAvailable` reports `true` at call time. Delivery is attempted on each
     * channel independently, and a failure in one channel is isolated so it
     * never prevents the other from delivering (Req 14.6).
     *
     * @return a [DeliveryResult] reporting which channels presented the message.
     */
    fun notify(notification: Notification): DeliveryResult {
        val spoken = deliverSafely(speechChannel?.takeIf { it.isAvailable }) { it.speak(notification) }
        val displayed = deliverSafely(displayChannel?.takeIf { it.isAvailable }) { it.show(notification) }
        return DeliveryResult(spoken = spoken, displayed = displayed)
    }

    /**
     * Convenience overload that builds the [Notification] from its [text] and
     * [category] before delivering it on both channels.
     *
     * @return a [DeliveryResult] reporting which channels presented the message.
     */
    fun notify(text: String, category: NotificationCategory): DeliveryResult =
        notify(Notification(text, category))

    /**
     * Run [deliver] against [channel] when it is non-null (wired and available),
     * isolating any delivery failure.
     *
     * @return `true` only when [deliver] ran to completion without throwing; a
     *   `null` channel, or one that throws, yields `false`.
     */
    private inline fun <T> deliverSafely(channel: T?, deliver: (T) -> Unit): Boolean {
        if (channel == null) return false
        return try {
            deliver(channel)
            true
        } catch (e: Exception) {
            // A misbehaving channel must never stop the other channel from
            // delivering (Req 14.6). Treat a thrown delivery as "not delivered"
            // on this channel and carry on.
            false
        }
    }
}
