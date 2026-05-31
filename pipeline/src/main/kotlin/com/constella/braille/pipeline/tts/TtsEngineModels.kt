package com.constella.braille.pipeline.tts

/**
 * Framework-free description of one TTS engine installed on the device, as seen
 * by the offline-engine selection rule.
 *
 * This is a plain value type with no `android.speech.tts` dependency so the
 * selection logic in [TtsEngineSelector] can be unit-tested on the JVM. The
 * thin Android wrapper builds these from `TextToSpeech.EngineInfo` plus a query
 * of which engines have usable offline voice data.
 *
 * @property id the engine package name (e.g. `com.google.android.tts`); the
 *   stable identifier used to instantiate a specific `TextToSpeech` engine.
 * @property isDefault whether this engine is the system default TTS engine.
 * @property hasOfflineVoiceData whether this engine has at least one installed
 *   voice whose data is available without a network connection (Req 11.1).
 */
data class TtsEngineInfo(
    val id: String,
    val isDefault: Boolean,
    val hasOfflineVoiceData: Boolean,
)

/**
 * The outcome of preparing the offline TTS_Engine (Req 11.4, 11.5).
 *
 * Either an offline-capable engine was selected ([Ready]) or no installed
 * engine has offline voice data ([NoVoiceData]). This mirrors the
 * `TtsReadiness` contract sketched in the design's TTS_Engine interface.
 */
sealed interface TtsReadiness {
    /**
     * An offline-capable engine was chosen.
     *
     * @property engineId the package name of the selected engine.
     * @property isDefaultEngine whether the selected engine is the system
     *   default (`false` indicates the fallback path of Req 11.4 was taken).
     */
    data class Ready(val engineId: String, val isDefaultEngine: Boolean) : TtsReadiness

    /**
     * No installed engine has offline voice data (Req 11.5). The System must
     * show the install-voice message, announce it if any audio path exists, and
     * offer a control to open device TTS settings — see [NoVoiceDataDecision].
     */
    data object NoVoiceData : TtsReadiness
}

/**
 * The deterministic decision the System makes when no offline TTS voice data is
 * installed (Req 11.5, 11.6).
 *
 * Pure data describing what the upper layers must do, with no Android types:
 *  - always present [message] on screen,
 *  - speak [message] only when [announce] is `true` (an audio path exists),
 *  - always offer the open-TTS-settings control ([offerOpenSettings]),
 *  - never interrupt audio that is already playing
 *    ([allowAlreadyPlayingAudioToContinue], Req 11.6).
 *
 * @property message the operator-facing "install a TTS voice" message; always
 *   non-blank.
 * @property announce whether to speak [message] aloud — `true` only when some
 *   audio output mechanism is available (Req 11.5).
 * @property offerOpenSettings whether to surface the control that opens device
 *   TTS settings; always `true` for this decision (Req 11.5).
 * @property allowAlreadyPlayingAudioToContinue whether audio already playing
 *   must be allowed to finish rather than being stopped; always `true`
 *   (Req 11.6).
 */
data class NoVoiceDataDecision(
    val message: String,
    val announce: Boolean,
    val offerOpenSettings: Boolean = true,
    val allowAlreadyPlayingAudioToContinue: Boolean = true,
) {
    init {
        require(message.isNotBlank()) { "no-voice-data message must not be blank" }
    }
}
