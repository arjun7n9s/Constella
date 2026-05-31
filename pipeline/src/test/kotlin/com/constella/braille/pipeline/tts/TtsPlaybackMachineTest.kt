package com.constella.braille.pipeline.tts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * JVM unit tests for the pure, framework-free playback state machine behind the
 * TTS_Engine's replay / pause / stop controls (task 19.1).
 *
 * These exercise every idle → playing → paused → stopped transition and the
 * effect each command emits, on the JVM with no `android.speech.tts`
 * dependency (Req 11.2, 11.3).
 */
class TtsPlaybackMachineTest : StringSpec({

    "starts idle with no retained text" {
        val machine = TtsPlaybackMachine()
        machine.state shouldBe TtsPlaybackState.IDLE
        machine.lastText shouldBe null
    }

    "speak moves to playing, retains text, and emits Speak" {
        val t = TtsPlaybackMachine().speak("hello")
        t.machine.state shouldBe TtsPlaybackState.PLAYING
        t.machine.lastText shouldBe "hello"
        t.effect shouldBe TtsPlaybackEffect.Speak("hello")
    }

    "speak with blank text is a no-op" {
        val t = TtsPlaybackMachine().speak("   ")
        t.machine shouldBe TtsPlaybackMachine()
        t.effect shouldBe TtsPlaybackEffect.None
    }

    "pause while playing moves to paused and emits Pause" {
        val playing = TtsPlaybackMachine().speak("hi").machine
        val t = playing.pause()
        t.machine.state shouldBe TtsPlaybackState.PAUSED
        t.effect shouldBe TtsPlaybackEffect.Pause
    }

    "pause when not playing is a no-op" {
        val t = TtsPlaybackMachine().pause()
        t.machine.state shouldBe TtsPlaybackState.IDLE
        t.effect shouldBe TtsPlaybackEffect.None
    }

    "stop while playing moves to stopped, retains text, and emits Stop" {
        val playing = TtsPlaybackMachine().speak("read me").machine
        val t = playing.stop()
        t.machine.state shouldBe TtsPlaybackState.STOPPED
        t.machine.lastText shouldBe "read me"
        t.effect shouldBe TtsPlaybackEffect.Stop
    }

    "stop while paused moves to stopped and emits Stop" {
        val paused = TtsPlaybackMachine().speak("read me").machine.pause().machine
        val t = paused.stop()
        t.machine.state shouldBe TtsPlaybackState.STOPPED
        t.effect shouldBe TtsPlaybackEffect.Stop
    }

    "stop when idle or already stopped is a no-op" {
        TtsPlaybackMachine().stop().effect shouldBe TtsPlaybackEffect.None
        val stopped = TtsPlaybackMachine().speak("x").machine.stop().machine
        stopped.stop().effect shouldBe TtsPlaybackEffect.None
    }

    "replay re-speaks retained text from paused" {
        val paused = TtsPlaybackMachine().speak("again").machine.pause().machine
        val t = paused.replay()
        t.machine.state shouldBe TtsPlaybackState.PLAYING
        t.effect shouldBe TtsPlaybackEffect.Speak("again")
    }

    "replay re-speaks retained text after stop" {
        val stopped = TtsPlaybackMachine().speak("again").machine.stop().machine
        val t = stopped.replay()
        t.machine.state shouldBe TtsPlaybackState.PLAYING
        t.effect shouldBe TtsPlaybackEffect.Speak("again")
    }

    "replay with nothing spoken yet is a no-op" {
        val t = TtsPlaybackMachine().replay()
        t.machine.state shouldBe TtsPlaybackState.IDLE
        t.effect shouldBe TtsPlaybackEffect.None
    }

    "natural completion while playing moves to stopped with no audio effect" {
        val playing = TtsPlaybackMachine().speak("done").machine
        val t = playing.onPlaybackCompleted()
        t.machine.state shouldBe TtsPlaybackState.STOPPED
        t.machine.lastText shouldBe "done"
        t.effect shouldBe TtsPlaybackEffect.None
    }

    "natural completion when not playing is a no-op" {
        TtsPlaybackMachine().onPlaybackCompleted().effect shouldBe TtsPlaybackEffect.None
    }

    "full replay/pause/stop cycle keeps text available throughout" {
        var m = TtsPlaybackMachine()
        m = m.speak("worksheet").machine
        m = m.pause().machine
        m.state shouldBe TtsPlaybackState.PAUSED
        m = m.replay().machine
        m.state shouldBe TtsPlaybackState.PLAYING
        m = m.stop().machine
        m.state shouldBe TtsPlaybackState.STOPPED
        val replay = m.replay()
        replay.effect.shouldBeInstanceOf<TtsPlaybackEffect.Speak>()
        (replay.effect as TtsPlaybackEffect.Speak).text shouldBe "worksheet"
    }
})
