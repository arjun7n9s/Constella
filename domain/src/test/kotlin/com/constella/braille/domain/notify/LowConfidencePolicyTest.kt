package com.constella.braille.domain.notify

import com.constella.braille.domain.config.ConfidenceThresholds
import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.domain.model.ScanStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [LowConfidencePolicy] (task 15.3).
 *
 * Covers the rescan-recommendation rule (Req 14.2): below-threshold confidence
 * yields [ScanStatus.LowConfidence] carrying the failed alignment condition as
 * its likely cause, while at/above-threshold confidence does not flag a rescan.
 * The threshold is sourced from [ConfidenceThresholds.RESCAN_RECOMMENDATION].
 */
class LowConfidencePolicyTest : StringSpec({

    val threshold = ConfidenceThresholds.RESCAN_RECOMMENDATION

    "below-threshold confidence is classified LowConfidence with the failed condition as cause" {
        val cause = AlignmentGuidance.AddLight
        val status = LowConfidencePolicy.classifyConfidence(
            overallConfidence = threshold - 0.1f,
            failedAlignmentCondition = cause,
        )
        val low = status.shouldBeInstanceOf<ScanStatus.LowConfidence>()
        low.likelyCause shouldBe cause
    }

    "below-threshold classification carries a rescan recommendation in its message" {
        val status = LowConfidencePolicy.classifyConfidence(
            overallConfidence = threshold - 0.2f,
            failedAlignmentCondition = AlignmentGuidance.HoldSteady,
        )
        // The status's generated message both recommends a rescan and states the cause.
        val message = ScanStatusMessages.messageFor(status)
        message.shouldContainIgnoringCase("again")
        message.shouldContainIgnoringCase("low confidence")
    }

    "confidence exactly at the threshold is not flagged as low confidence (strict <)" {
        LowConfidencePolicy.isLowConfidence(threshold) shouldBe false
        LowConfidencePolicy.classifyConfidence(threshold, AlignmentGuidance.MoveCloser) shouldBe
            ScanStatus.Success
    }

    "above-threshold confidence is classified Success" {
        val status = LowConfidencePolicy.classifyConfidence(
            overallConfidence = (threshold + 0.25f).coerceAtMost(1f),
            failedAlignmentCondition = AlignmentGuidance.MoveFarther,
        )
        status shouldBe ScanStatus.Success
    }

    "the failed condition is preserved as the likely cause for every alignment value" {
        AlignmentGuidance::class.sealedSubclasses
            .mapNotNull { it.objectInstance }
            .forEach { cause ->
                val status = LowConfidencePolicy.classifyConfidence(0f, cause)
                val low = status.shouldBeInstanceOf<ScanStatus.LowConfidence>()
                low.likelyCause shouldBe cause
            }
    }

    "isLowConfidence agrees with the centralized threshold boundary" {
        LowConfidencePolicy.isLowConfidence(0f) shouldBe true
        LowConfidencePolicy.isLowConfidence(1f) shouldBe false
        // Just below the threshold is low; the threshold itself is not.
        LowConfidencePolicy.isLowConfidence(threshold - 0.00001f) shouldBe true
    }
})
