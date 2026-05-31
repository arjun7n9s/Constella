package com.constella.braille.pipeline.tts

/**
 * The pure, deterministic offline-engine selection and no-voice-data decision
 * logic for the TTS_Engine (Req 11.4, 11.5, 11.6).
 *
 * Framework-free: nothing here touches `android.speech.tts`. The thin Android
 * wrapper ([AndroidTtsEngine]) enumerates installed engines and their offline
 * voice availability into [TtsEngineInfo] values, hands them here, and acts on
 * the returned [TtsReadiness] / [NoVoiceDataDecision]. That keeps the engine
 * choice rule and the missing-voice-data rule unit-testable on the JVM.
 *
 * Selection rule (Req 11.1, 11.4):
 * 1. Prefer the system **default** engine when it has offline voice data — the
 *    System uses Android built-in offline TTS by default (Req 11.1, 11.7).
 * 2. Otherwise fall back to any **other** installed engine that has offline
 *    voice data (Req 11.4). Among several offline-capable fallbacks the choice
 *    is deterministic: the first by ascending engine id, so the same device
 *    configuration always yields the same selection.
 * 3. If no installed engine has offline voice data, report
 *    [TtsReadiness.NoVoiceData] (Req 11.5).
 *
 * _Requirements: 11.1, 11.4, 11.5, 11.6, 11.7_
 */
object TtsEngineSelector {

    /**
     * Default operator-facing message used when no offline voice data is
     * installed. The Android wrapper may override it with a localized string
     * resource; the rule only requires a non-blank install-voice message
     * (Req 11.5).
     */
    const val DEFAULT_NO_VOICE_DATA_MESSAGE: String =
        "No text-to-speech voice is installed. Install a voice in the device " +
            "text-to-speech settings to hear scans read aloud."

    /**
     * Choose the offline engine to use, or report that none is available.
     *
     * @param engines all installed TTS engines with their default flag and
     *   offline-voice-data availability. May be empty.
     * @return [TtsReadiness.Ready] naming the selected engine (default-first,
     *   then the lowest-id offline fallback), or [TtsReadiness.NoVoiceData]
     *   when no engine has offline voice data.
     */
    fun select(engines: List<TtsEngineInfo>): TtsReadiness {
        val offlineCapable = engines.filter { it.hasOfflineVoiceData }
        if (offlineCapable.isEmpty()) return TtsReadiness.NoVoiceData

        // Req 11.1 / 11.7: prefer the system default engine when it is
        // offline-capable.
        offlineCapable.firstOrNull { it.isDefault }?.let {
            return TtsReadiness.Ready(engineId = it.id, isDefaultEngine = true)
        }

        // Req 11.4: fall back to another installed offline engine. Order by id
        // so the choice is deterministic across runs.
        val fallback = offlineCapable.minByOrNull { it.id }!!
        return TtsReadiness.Ready(engineId = fallback.id, isDefaultEngine = false)
    }

    /**
     * Build the [NoVoiceDataDecision] for the missing-voice-data case
     * (Req 11.5, 11.6).
     *
     * @param hasAudioOutput whether any audio output mechanism is available; the
     *   message is announced aloud only when this is `true` (Req 11.5).
     * @param message the install-voice message to present; defaults to
     *   [DEFAULT_NO_VOICE_DATA_MESSAGE]. Must be non-blank.
     * @return the decision describing the on-screen message, whether to announce
     *   it, the open-settings control, and the rule that already-playing audio
     *   continues (Req 11.6).
     */
    fun noVoiceDataDecision(
        hasAudioOutput: Boolean,
        message: String = DEFAULT_NO_VOICE_DATA_MESSAGE,
    ): NoVoiceDataDecision =
        NoVoiceDataDecision(
            message = message,
            announce = hasAudioOutput,
        )
}
