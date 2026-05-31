package com.constella.braille.pipeline.tts

/**
 * The pure, deterministic playback state machine behind the offline
 * TTS_Engine's replay / pause / stop controls (Req 11.2, 11.3).
 *
 * This class contains **no** `android.speech.tts` dependency. It models the
 * idle → playing → paused → stopped transitions and decides, for each Operator
 * command, both the next [TtsPlaybackState] and the side [TtsPlaybackEffect]
 * that the thin Android wrapper ([AndroidTtsEngine]) should carry out against
 * the real `TextToSpeech`. Keeping the decision here lets the whole control
 * surface be unit-tested on the JVM; the Android class only has to perform the
 * mechanical `speak()` / `stop()` calls the effect names.
 *
 * The machine is immutable: every transition returns a new [Transition] holding
 * the resulting machine and the effect to run. The last text spoken is retained
 * across pause and stop so it can be replayed from the beginning (Req 11.3 —
 * Android `TextToSpeech` has no native resume, so "replay" restarts the
 * utterance).
 *
 * _Requirements: 11.2, 11.3_
 *
 * @property state the current playback state.
 * @property lastText the most recent text handed to [speak]/[replay], retained
 *   so it can be replayed; `null` only before anything has been spoken.
 */
data class TtsPlaybackMachine(
    val state: TtsPlaybackState = TtsPlaybackState.IDLE,
    val lastText: String? = null,
) {

    /** The result of a transition: the new machine plus the effect to perform. */
    data class Transition(val machine: TtsPlaybackMachine, val effect: TtsPlaybackEffect)

    /**
     * Begin reading [text] aloud (Req 11.2). Valid from any state; it sets the
     * retained text and moves to [TtsPlaybackState.PLAYING], emitting
     * [TtsPlaybackEffect.Speak].
     *
     * Blank text is a no-op that leaves the machine unchanged, so an empty
     * Recognized_Text never drives the controls into a spurious playing state.
     */
    fun speak(text: String): Transition {
        if (text.isBlank()) return Transition(this, TtsPlaybackEffect.None)
        val next = copy(state = TtsPlaybackState.PLAYING, lastText = text)
        return Transition(next, TtsPlaybackEffect.Speak(text))
    }

    /**
     * Replay the retained text from the beginning (Req 11.3). Valid whenever
     * text has been spoken at least once; it moves to
     * [TtsPlaybackState.PLAYING] and re-speaks [lastText]. With no retained text
     * it is a no-op.
     */
    fun replay(): Transition {
        val text = lastText ?: return Transition(this, TtsPlaybackEffect.None)
        val next = copy(state = TtsPlaybackState.PLAYING)
        return Transition(next, TtsPlaybackEffect.Speak(text))
    }

    /**
     * Pause playback (Req 11.3). Only meaningful while
     * [TtsPlaybackState.PLAYING]; it halts the current audio and moves to
     * [TtsPlaybackState.PAUSED]. From any other state it is a no-op, so pausing
     * when nothing is playing changes nothing.
     */
    fun pause(): Transition =
        if (state == TtsPlaybackState.PLAYING) {
            Transition(copy(state = TtsPlaybackState.PAUSED), TtsPlaybackEffect.Pause)
        } else {
            Transition(this, TtsPlaybackEffect.None)
        }

    /**
     * Stop playback (Req 11.3). From [TtsPlaybackState.PLAYING] or
     * [TtsPlaybackState.PAUSED] it halts audio and moves to
     * [TtsPlaybackState.STOPPED], retaining the text for a later replay. From
     * [TtsPlaybackState.IDLE] or [TtsPlaybackState.STOPPED] there is nothing to
     * stop, so it is a no-op.
     */
    fun stop(): Transition =
        if (state == TtsPlaybackState.PLAYING || state == TtsPlaybackState.PAUSED) {
            Transition(copy(state = TtsPlaybackState.STOPPED), TtsPlaybackEffect.Stop)
        } else {
            Transition(this, TtsPlaybackEffect.None)
        }

    /**
     * Record that the current utterance finished on its own. Valid only while
     * [TtsPlaybackState.PLAYING]; it moves to [TtsPlaybackState.STOPPED] without
     * any further audio effect (the audio already ended). From any other state
     * it is a no-op. This keeps the machine's state honest when speech completes
     * without an explicit stop, so a subsequent replay behaves correctly.
     */
    fun onPlaybackCompleted(): Transition =
        if (state == TtsPlaybackState.PLAYING) {
            Transition(copy(state = TtsPlaybackState.STOPPED), TtsPlaybackEffect.None)
        } else {
            Transition(this, TtsPlaybackEffect.None)
        }
}

/**
 * A side effect the Android wrapper must perform after a [TtsPlaybackMachine]
 * transition.
 *
 * Framework-free on purpose: [AndroidTtsEngine] maps each effect onto the
 * corresponding `TextToSpeech` call ([Speak] → `speak`, [Pause]/[Stop] →
 * `stop`, [None] → nothing). [Pause] and [Stop] are distinct effects even
 * though both halt audio, so the wrapper and any observers can tell an
 * Operator-initiated pause from a full stop.
 */
sealed interface TtsPlaybackEffect {
    /** Speak the given text from the beginning. */
    data class Speak(val text: String) : TtsPlaybackEffect

    /** Halt the current audio because the Operator paused playback. */
    data object Pause : TtsPlaybackEffect

    /** Halt the current audio because the Operator stopped playback. */
    data object Stop : TtsPlaybackEffect

    /** Do nothing; the command had no effect in the current state. */
    data object None : TtsPlaybackEffect
}
