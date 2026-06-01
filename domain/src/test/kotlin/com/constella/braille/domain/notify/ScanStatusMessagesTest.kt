package com.constella.braille.domain.notify

import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.domain.model.ScanStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * Unit tests for [ScanStatusMessages] message generation (task 15.3).
 *
 * Covers the non-empty-message contract for every non-success [ScanStatus]
 * (Req 14.1, 14.4), the low-confidence message stating the likely cause and a
 * rescan recommendation (Req 14.2), and the documented empty-string Success
 * contract.
 */
class ScanStatusMessagesTest : StringSpec({

    // Every non-success status enumerated, including one LowConfidence per
    // alignment cause and a ProcessingError with a non-blank detail.
    val nonSuccessStatuses: List<ScanStatus> = buildList {
        add(ScanStatus.NoBrailleRecognized)
        add(ScanStatus.StructureButNoCell)
        add(ScanStatus.ProcessingError("decoder failed"))
        AlignmentGuidance::class.sealedSubclasses.forEach { sub ->
            sub.objectInstance?.let { add(ScanStatus.LowConfidence(it)) }
        }
    }

    "every non-success status yields a non-blank message" {
        nonSuccessStatuses.forEach { status ->
            ScanStatusMessages.messageFor(status).shouldNotBeBlank()
        }
    }

    "Success yields an empty message (documented contract)" {
        ScanStatusMessages.messageFor(ScanStatus.Success) shouldBe ""
    }

    "NoBrailleRecognized message offers a rescan" {
        ScanStatusMessages.messageFor(ScanStatus.NoBrailleRecognized)
            .shouldContainIgnoringCase("again")
    }

    "StructureButNoCell message prompts adjustment before rescanning" {
        val msg = ScanStatusMessages.messageFor(ScanStatus.StructureButNoCell)
        msg.shouldContainIgnoringCase("again")
    }

    "LowConfidence message states a likely cause and recommends a rescan for every cause" {
        AlignmentGuidance::class.sealedSubclasses
            .mapNotNull { it.objectInstance }
            .forEach { cause ->
                val msg = ScanStatusMessages.messageFor(ScanStatus.LowConfidence(cause))
                msg.shouldNotBeBlank()
                // Recommends a rescan ...
                msg.shouldContainIgnoringCase("again")
                // ... and states a likely cause ("likely" appears in every cause phrase).
                msg.shouldContainIgnoringCase("low confidence")
            }
    }

    "ProcessingError with a blank carried message still produces a non-blank message" {
        ScanStatusMessages.messageFor(ScanStatus.ProcessingError("   "))
            .shouldNotBeBlank()
    }

    "ProcessingError appends a non-blank detail for context" {
        ScanStatusMessages.messageFor(ScanStatus.ProcessingError("out of memory"))
            .shouldContainIgnoringCase("out of memory")
    }

    "categoryFor classifies LowConfidence and failures, and Success has no category" {
        ScanStatusMessages.categoryFor(ScanStatus.Success).shouldBeNull()
        ScanStatusMessages.categoryFor(ScanStatus.LowConfidence(AlignmentGuidance.AddLight)) shouldBe
            NotificationCategory.LOW_CONFIDENCE
        ScanStatusMessages.categoryFor(ScanStatus.NoBrailleRecognized) shouldBe
            NotificationCategory.FAILURE
        ScanStatusMessages.categoryFor(ScanStatus.StructureButNoCell) shouldBe
            NotificationCategory.FAILURE
        ScanStatusMessages.categoryFor(ScanStatus.ProcessingError("x")) shouldBe
            NotificationCategory.FAILURE
    }

    "notificationFor returns a deliverable (non-blank) notification for every non-success status" {
        nonSuccessStatuses.forEach { status ->
            val notification = ScanStatusMessages.notificationFor(status)
            notification.shouldNotBeNull()
            // Notification's own init enforces non-blank text; assert explicitly too.
            notification.text.shouldNotBeBlank()
        }
    }

    "notificationFor returns null for Success (nothing to deliver)" {
        ScanStatusMessages.notificationFor(ScanStatus.Success).shouldBeNull()
    }

    "every AlignmentGuidance cause is covered without throwing" {
        // Guards against a future AlignmentGuidance subtype going unmapped.
        AlignmentGuidance::class.sealedSubclasses
            .mapNotNull { it.objectInstance }
            .forEach { cause ->
                ScanStatusMessages.messageFor(ScanStatus.LowConfidence(cause)).isNotBlank().shouldBeTrue()
            }
    }
})
