package com.constella.braille.domain.notify

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the centralized dual-channel [Notifier] delivery mechanism
 * (task 15.1). These are example-based tests covering the dual-channel and
 * graceful-degradation behavior of Req 2.8 / 14.5 / 14.6. The universal
 * Property 26 is implemented separately (task 15.2).
 */
class NotifierTest : StringSpec({

    /** Records every notification it is asked to speak/show, and is toggleable. */
    class FakeSpeechChannel(override var isAvailable: Boolean = true) : SpeechChannel {
        val spoken = mutableListOf<Notification>()
        override fun speak(notification: Notification) {
            spoken += notification
        }
    }

    class FakeDisplayChannel(override var isAvailable: Boolean = true) : DisplayChannel {
        val shown = mutableListOf<Notification>()
        override fun show(notification: Notification) {
            shown += notification
        }
    }

    val message = Notification("Move the camera closer", NotificationCategory.GUIDANCE)

    "delivers on both channels when both are available" {
        val speech = FakeSpeechChannel()
        val display = FakeDisplayChannel()
        val notifier = Notifier(speech, display)

        val result = notifier.notify(message)

        result shouldBe DeliveryResult(spoken = true, displayed = true)
        result.deliveredOnBothChannels shouldBe true
        speech.spoken shouldContainExactly listOf(message)
        display.shown shouldContainExactly listOf(message)
    }

    "degrades to speech when display channel is null" {
        val speech = FakeSpeechChannel()
        val notifier = Notifier(speechChannel = speech, displayChannel = null)

        val result = notifier.notify(message)

        result shouldBe DeliveryResult(spoken = true, displayed = false)
        result.delivered shouldBe true
        speech.spoken shouldContainExactly listOf(message)
    }

    "degrades to display when speech channel is null" {
        val display = FakeDisplayChannel()
        val notifier = Notifier(speechChannel = null, displayChannel = display)

        val result = notifier.notify(message)

        result shouldBe DeliveryResult(spoken = false, displayed = true)
        result.delivered shouldBe true
        display.shown shouldContainExactly listOf(message)
    }

    "degrades to display when speech channel is present but unavailable" {
        val speech = FakeSpeechChannel(isAvailable = false)
        val display = FakeDisplayChannel()
        val notifier = Notifier(speech, display)

        val result = notifier.notify(message)

        result shouldBe DeliveryResult(spoken = false, displayed = true)
        speech.spoken.isEmpty() shouldBe true
        display.shown shouldContainExactly listOf(message)
    }

    "degrades to speech when display channel is present but unavailable" {
        val speech = FakeSpeechChannel()
        val display = FakeDisplayChannel(isAvailable = false)
        val notifier = Notifier(speech, display)

        val result = notifier.notify(message)

        result shouldBe DeliveryResult(spoken = true, displayed = false)
        display.shown.isEmpty() shouldBe true
        speech.spoken shouldContainExactly listOf(message)
    }

    "does not crash and reports nothing delivered when no channels are available" {
        val notifierWithNullChannels = Notifier()
        val notifierWithUnavailableChannels =
            Notifier(FakeSpeechChannel(isAvailable = false), FakeDisplayChannel(isAvailable = false))

        notifierWithNullChannels.notify(message) shouldBe DeliveryResult(spoken = false, displayed = false)
        notifierWithUnavailableChannels.notify(message).delivered shouldBe false
    }

    "isolates a failing channel so the other still receives the message" {
        val throwingSpeech = object : SpeechChannel {
            override val isAvailable: Boolean = true
            override fun speak(notification: Notification) = throw RuntimeException("tts boom")
        }
        val display = FakeDisplayChannel()
        val notifier = Notifier(throwingSpeech, display)

        val result = notifier.notify(message)

        result shouldBe DeliveryResult(spoken = false, displayed = true)
        display.shown shouldContainExactly listOf(message)
    }

    "convenience overload builds the notification from text and category" {
        val speech = FakeSpeechChannel()
        val display = FakeDisplayChannel()
        val notifier = Notifier(speech, display)

        notifier.notify("Scan could not be completed", NotificationCategory.FAILURE)

        val expected = Notification("Scan could not be completed", NotificationCategory.FAILURE)
        speech.spoken shouldContainExactly listOf(expected)
        display.shown shouldContainExactly listOf(expected)
    }

    "rejects a blank notification text" {
        shouldThrow<IllegalArgumentException> {
            Notification("   ", NotificationCategory.GUIDANCE)
        }
    }
})
