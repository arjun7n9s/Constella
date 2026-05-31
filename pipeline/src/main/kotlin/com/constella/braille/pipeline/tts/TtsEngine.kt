package com.constella.braille.pipeline.tts

/**
 * The offline Text-to-Speech engine contract (Req 11).
 *
 * Reads Recognized_Text aloud using on-device, offline speech and exposes the
 * replay / pause / stop controls (Req 11.1, 11.2, 11.3). Concrete
 * implementations select an offline engine via [TtsEngineSelector] — preferring
 * the Android built-in default and falling back to another installed offline
 * engine (Req 11.4) — and drive playback through the deterministic
 * [TtsPlaybackMachine].
 *
 * This interface is intentionally framework-free (no `android.speech.tts`
 * types). The Android-backed implementation lives in [AndroidTtsEngine]; tests
 * and upper layers depend only on this contract.
 *
 * _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_
 */
interface TtsEngine {

    /** The current playback state, for controls and accessibility state. */
    val playbackState: TtsPlaybackState

    /**
     * Initialize speech output and choose an offline engine.
     *
     * @return [TtsReadiness.Ready] naming the selected offline engine, or
     *   [TtsReadiness.NoVoiceData] when no installed engine has offline voice
     *   data (Req 11.5).
     */
    suspend fun prepare(): TtsReadiness

    /**
     * Read [text] aloud from the beginning (Req 11.2). A blank string is a
     * no-op.
     */
    fun speak(text: String)

    /** Replay the most recently spoken text from the beginning (Req 11.3). */
    fun replay()

    /** Pause the current playback (Req 11.3). */
    fun pause()

    /** Stop the current playback (Req 11.3), retaining text for replay. */
    fun stop()

    /**
     * Build the decision describing what to do when no offline voice data is
     * installed (Req 11.5, 11.6): the on-screen message, whether to announce it,
     * the open-settings affordance, and the let-already-playing-audio-continue
     * rule.
     */
    fun noVoiceDataDecision(): NoVoiceDataDecision

    /**
     * Open the device Text-to-Speech settings so the Operator can install a
     * voice (Req 11.5). The framework-free contract names the action; the
     * Android implementation launches the corresponding settings screen.
     */
    fun openTtsSettings()

    /** Release native speech resources. */
    fun shutdown()
}
