package com.constella.braille.pipeline.tts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * JVM unit tests for the pure offline-engine selection rule and the
 * no-voice-data decision (task 19.1).
 *
 * Framework-free coverage of the default-then-fallback selection (Req 11.1,
 * 11.4, 11.7) and the missing-voice-data response (Req 11.5, 11.6).
 */
class TtsEngineSelectorTest : StringSpec({

    fun engine(id: String, isDefault: Boolean, offline: Boolean) =
        TtsEngineInfo(id = id, isDefault = isDefault, hasOfflineVoiceData = offline)

    "prefers the default engine when it has offline voice data" {
        val engines = listOf(
            engine("com.google.android.tts", isDefault = true, offline = true),
            engine("com.other.tts", isDefault = false, offline = true),
        )
        TtsEngineSelector.select(engines) shouldBe
            TtsReadiness.Ready(engineId = "com.google.android.tts", isDefaultEngine = true)
    }

    "falls back to another installed offline engine when the default lacks voice data" {
        val engines = listOf(
            engine("com.google.android.tts", isDefault = true, offline = false),
            engine("com.other.tts", isDefault = false, offline = true),
        )
        TtsEngineSelector.select(engines) shouldBe
            TtsReadiness.Ready(engineId = "com.other.tts", isDefaultEngine = false)
    }

    "fallback among several offline engines is deterministic by ascending id" {
        val engines = listOf(
            engine("com.zeta.tts", isDefault = false, offline = true),
            engine("com.alpha.tts", isDefault = false, offline = true),
            engine("com.google.android.tts", isDefault = true, offline = false),
        )
        TtsEngineSelector.select(engines) shouldBe
            TtsReadiness.Ready(engineId = "com.alpha.tts", isDefaultEngine = false)
    }

    "reports NoVoiceData when no engine has offline voice data" {
        val engines = listOf(
            engine("com.google.android.tts", isDefault = true, offline = false),
            engine("com.other.tts", isDefault = false, offline = false),
        )
        TtsEngineSelector.select(engines) shouldBe TtsReadiness.NoVoiceData
    }

    "reports NoVoiceData when no engines are installed" {
        TtsEngineSelector.select(emptyList()) shouldBe TtsReadiness.NoVoiceData
    }

    "no-voice-data decision announces when an audio path exists" {
        val decision = TtsEngineSelector.noVoiceDataDecision(hasAudioOutput = true)
        decision.announce shouldBe true
        decision.offerOpenSettings shouldBe true
        decision.allowAlreadyPlayingAudioToContinue shouldBe true
        decision.message shouldBe TtsEngineSelector.DEFAULT_NO_VOICE_DATA_MESSAGE
    }

    "no-voice-data decision does not announce when no audio path exists but still shows the message and settings control" {
        val decision = TtsEngineSelector.noVoiceDataDecision(hasAudioOutput = false)
        decision.announce shouldBe false
        decision.offerOpenSettings shouldBe true
        decision.allowAlreadyPlayingAudioToContinue shouldBe true
    }

    "no-voice-data decision accepts a custom localized message" {
        val decision = TtsEngineSelector.noVoiceDataDecision(
            hasAudioOutput = true,
            message = "Installez une voix",
        )
        decision.message shouldBe "Installez une voix"
    }
})
