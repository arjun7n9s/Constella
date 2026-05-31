package com.constella.braille.pipeline.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Thin Android-backed [TtsEngine] over `android.speech.tts.TextToSpeech`
 * (Req 11.1–11.7).
 *
 * This class is deliberately minimal: it owns the `TextToSpeech` lifecycle and
 * translates between the framework API and the pure, JVM-testable logic. All
 * decisions live in that pure logic, not here:
 *  - the replay/pause/stop transitions are decided by [TtsPlaybackMachine],
 *  - the default-then-fallback offline-engine choice is decided by
 *    [TtsEngineSelector.select] (Req 11.1, 11.4, 11.7),
 *  - the missing-voice-data response is decided by
 *    [TtsEngineSelector.noVoiceDataDecision] (Req 11.5, 11.6).
 *
 * The wrapper only gathers facts ([enumerateEngines]) and performs the
 * mechanical `speak()` / `stop()` calls the [TtsPlaybackEffect]s name. Because
 * `android.speech.tts` is unavailable on the JVM, this class is not unit-tested;
 * its logic is covered through the pure classes it delegates to.
 *
 * Threading: playback state is mutated under [lock] because
 * [UtteranceProgressListener] callbacks arrive on a binder thread while the
 * controls are invoked from the caller's thread.
 *
 * _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_
 *
 * @property appContext an application context used to construct engines and to
 *   launch the device TTS settings screen.
 * @property noVoiceDataMessage the operator-facing install-voice message;
 *   upper layers may pass a localized string. Defaults to the rule's message.
 */
class AndroidTtsEngine(
    private val appContext: Context,
    private val noVoiceDataMessage: String = TtsEngineSelector.DEFAULT_NO_VOICE_DATA_MESSAGE,
) : TtsEngine {

    private val lock = Any()

    /** The active `TextToSpeech` once [prepare] has selected an offline engine. */
    private var tts: TextToSpeech? = null

    /** Whether any audio output path exists, used by the no-voice-data rule. */
    private var hasAudioOutput: Boolean = true

    private var machine: TtsPlaybackMachine = TtsPlaybackMachine()

    override val playbackState: TtsPlaybackState
        get() = synchronized(lock) { machine.state }

    /**
     * Initialize speech output and pick an offline engine. Enumerates installed
     * engines and their offline voice availability, then defers the choice to
     * [TtsEngineSelector.select]. When a non-default offline engine is selected
     * (Req 11.4), the active `TextToSpeech` is rebound to that engine.
     */
    override suspend fun prepare(): TtsReadiness {
        val engines = enumerateEngines()
        return when (val readiness = TtsEngineSelector.select(engines)) {
            is TtsReadiness.Ready -> {
                bindEngine(readiness.engineId)
                readiness
            }
            TtsReadiness.NoVoiceData -> {
                // Req 11.6: do not stop any already-playing audio here.
                readiness
            }
        }
    }

    override fun speak(text: String) = applyTransition(machine.speak(text))

    override fun replay() = applyTransition(synchronized(lock) { machine.replay() })

    override fun pause() = applyTransition(synchronized(lock) { machine.pause() })

    override fun stop() = applyTransition(synchronized(lock) { machine.stop() })

    override fun noVoiceDataDecision(): NoVoiceDataDecision =
        TtsEngineSelector.noVoiceDataDecision(
            hasAudioOutput = synchronized(lock) { hasAudioOutput },
            message = noVoiceDataMessage,
        )

    /**
     * Open the device Text-to-Speech settings (Req 11.5) so the Operator can
     * install voice data. Launched as a new task because [appContext] is an
     * application context.
     */
    override fun openTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    override fun shutdown() {
        synchronized(lock) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    /**
     * Apply a [TtsPlaybackMachine.Transition]: commit the new machine state and
     * perform the named [TtsPlaybackEffect] against the active `TextToSpeech`.
     */
    private fun applyTransition(transition: TtsPlaybackMachine.Transition) {
        synchronized(lock) { machine = transition.machine }
        when (val effect = transition.effect) {
            is TtsPlaybackEffect.Speak -> speakNow(effect.text)
            TtsPlaybackEffect.Pause -> tts?.stop()
            TtsPlaybackEffect.Stop -> tts?.stop()
            TtsPlaybackEffect.None -> Unit
        }
    }

    /** Queue [text] for synthesis, flushing any in-progress utterance. */
    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /**
     * Build a [TtsReadiness]-ready view of every installed engine by querying,
     * for each, whether it exposes at least one offline (non-network) voice.
     *
     * Each candidate engine is briefly instantiated to inspect its voices; the
     * default flag comes from the system default engine package. The resulting
     * [TtsEngineInfo] list is handed to the pure [TtsEngineSelector].
     */
    private suspend fun enumerateEngines(): List<TtsEngineInfo> {
        val probe = awaitTts(enginePackage = null) ?: return emptyList()
        val defaultEngineId = probe.defaultEngine
        val installed = probe.engines.orEmpty()
        // Reuse the probe (default-engine) instance for its own offline check.
        val result = ArrayList<TtsEngineInfo>(installed.size)
        for (engine in installed) {
            val offline = if (engine.name == defaultEngineId) {
                hasOfflineVoiceData(probe)
            } else {
                val instance = awaitTts(engine.name)
                val has = instance?.let { hasOfflineVoiceData(it) } ?: false
                instance?.shutdown()
                has
            }
            result += TtsEngineInfo(
                id = engine.name,
                isDefault = engine.name == defaultEngineId,
                hasOfflineVoiceData = offline,
            )
        }
        probe.shutdown()
        return result
    }

    /** Bind [tts] to the selected engine and set the default speaking locale. */
    private suspend fun bindEngine(engineId: String) {
        val instance = awaitTts(engineId) ?: return
        instance.setOnUtteranceProgressListener(progressListener)
        runCatching { instance.language = Locale.getDefault() }
        synchronized(lock) {
            tts?.shutdown()
            tts = instance
        }
    }

    /**
     * Construct a `TextToSpeech` for [enginePackage] (or the system default when
     * `null`) and suspend until its init callback fires. Returns `null` when the
     * engine fails to initialize.
     */
    private suspend fun awaitTts(enginePackage: String?): TextToSpeech? =
        suspendCancellableCoroutine { cont ->
            var ref: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    cont.resume(ref)
                } else {
                    ref?.shutdown()
                    cont.resume(null)
                }
            }
            ref = if (enginePackage == null) {
                TextToSpeech(appContext, listener)
            } else {
                TextToSpeech(appContext, listener, enginePackage)
            }
            cont.invokeOnCancellation { runCatching { ref?.shutdown() } }
        }

    /**
     * Whether [instance] has at least one installed voice usable offline: it
     * does not require a network connection and is not flagged as not-installed.
     */
    private fun hasOfflineVoiceData(instance: TextToSpeech): Boolean =
        runCatching {
            instance.voices.orEmpty().any { it.isOfflineUsable() }
        }.getOrDefault(false)

    private fun Voice.isOfflineUsable(): Boolean =
        !isNetworkConnectionRequired &&
            !features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    /** Marks an utterance complete so the machine reflects natural finish. */
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            applyTransition(synchronized(lock) { machine.onPlaybackCompleted() })
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            applyTransition(synchronized(lock) { machine.onPlaybackCompleted() })
        }
    }

    private companion object {
        const val UTTERANCE_ID = "braille-scanner-recognized-text"
    }
}
