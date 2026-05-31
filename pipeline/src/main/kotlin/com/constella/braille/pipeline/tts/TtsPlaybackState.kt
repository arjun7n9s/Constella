package com.constella.braille.pipeline.tts

/**
 * The playback states of the offline TTS_Engine.
 *
 * These four states are the complete, deterministic state space for the
 * replay / pause / stop controls the System exposes over spoken Recognized_Text
 * (Req 11.3). They are framework-free: nothing here depends on
 * `android.speech.tts`, so the transition logic can be exercised on the JVM by
 * [TtsPlaybackMachine] tests.
 *
 * - [IDLE]    Nothing has been spoken yet this session; there is no audio.
 * - [PLAYING] An utterance is being read aloud.
 * - [PAUSED]  Playback was paused by the Operator and can be resumed by
 *   replaying, or ended with stop.
 * - [STOPPED] Playback was stopped by the Operator or finished naturally; the
 *   last text is retained so it can be replayed.
 *
 * _Requirements: 11.2, 11.3_
 */
enum class TtsPlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    STOPPED,
}
