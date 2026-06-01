package com.constella.braille.domain.notify

import com.constella.braille.domain.model.AlignmentGuidance
import com.constella.braille.domain.model.ScanStatus

/**
 * Pure, framework-free generation of operator-facing message content from a
 * [ScanStatus].
 *
 * The [Notifier] (task 15.1) only *delivers* a [Notification]; deciding *what*
 * a given scan outcome should say is this object's job (task 15.3). Keeping the
 * two concerns apart means the same message text can be produced and unit-tested
 * without any speech/display channel wired in.
 *
 * **Non-empty contract (Req 14.4, Property 25).** Every non-success
 * [ScanStatus] maps to a non-blank, deliverable message — including
 * [ScanStatus.ProcessingError] whose carried `message` may itself be blank, in
 * which case a fixed base sentence still guarantees real content. This matches
 * [Notification]'s own non-blank requirement: anything [messageFor] returns for
 * a non-success status can be handed straight to a [Notification].
 *
 * **Success contract.** [ScanStatus.Success] is *not* a failure or
 * low-confidence condition, so it has no message to deliver and [messageFor]
 * returns the empty string for it. Callers building a notification should treat
 * an empty result as "nothing to announce" and skip delivery (see
 * [notificationFor], which returns `null` for [ScanStatus.Success]). A
 * successful scan's text is surfaced through the normal results presentation
 * (task 16), not through the failure/low-confidence Notifier path.
 *
 * For a [ScanStatus.LowConfidence] the generated message both states the likely
 * cause derived from the failed alignment condition **and** recommends a rescan
 * (Req 14.2). The low-confidence *decision* itself (when confidence is low
 * enough to warrant this status) lives in [LowConfidencePolicy].
 *
 * _Requirements: 14.1, 14.2, 14.4_
 */
object ScanStatusMessages {

    /**
     * Build the operator-facing message for [status].
     *
     * @return a non-blank message for every non-success [ScanStatus]; the empty
     *   string for [ScanStatus.Success] (which carries no failure/low-confidence
     *   message — see the class contract).
     */
    fun messageFor(status: ScanStatus): String = when (status) {
        // A successful scan has nothing to report through the failure /
        // low-confidence path; its text is presented via the results flow.
        ScanStatus.Success -> ""

        // Req 4.6 / 14.1: tell the Operator no Braille was found and offer a rescan.
        ScanStatus.NoBrailleRecognized ->
            "No Braille was recognized. Reposition the document and scan again."

        // Req 4.5: structure was visible but no complete cell formed — prompt the
        // Operator to improve conditions before requesting a full rescan.
        ScanStatus.StructureButNoCell ->
            "Braille-like structure was found, but no complete cell could be read. " +
                "Adjust alignment, lighting, or distance, then scan again."

        // Req 14.2: recommend a rescan and state the likely cause derived from the
        // failed alignment condition.
        is ScanStatus.LowConfidence ->
            "Low confidence in this scan — ${likelyCausePhrase(status.likelyCause)}. " +
                "Scan again for a more reliable result."

        // Req 14.3: inform the Operator the scan could not be completed. The
        // carried message may be blank, so the fixed base sentence guarantees the
        // result is never empty; any non-blank detail is appended for context.
        is ScanStatus.ProcessingError -> {
            val base = "The scan could not be completed. Please try again."
            val detail = status.message.trim()
            if (detail.isEmpty()) base else "$base ($detail)"
        }
    }

    /**
     * Classify [status] into the [NotificationCategory] used by the [Notifier]
     * and channels for styling/prioritization.
     *
     * [ScanStatus.LowConfidence] is the [NotificationCategory.LOW_CONFIDENCE]
     * family; every other non-success outcome is a [NotificationCategory.FAILURE].
     * [ScanStatus.Success] has no category because it produces no message.
     *
     * @return the category for a non-success [status], or `null` for
     *   [ScanStatus.Success].
     */
    fun categoryFor(status: ScanStatus): NotificationCategory? = when (status) {
        ScanStatus.Success -> null
        is ScanStatus.LowConfidence -> NotificationCategory.LOW_CONFIDENCE
        ScanStatus.NoBrailleRecognized,
        ScanStatus.StructureButNoCell,
        is ScanStatus.ProcessingError -> NotificationCategory.FAILURE
    }

    /**
     * Convenience pairing of [messageFor] with [categoryFor] ready for the
     * [Notifier].
     *
     * @return a [Notification] for any non-success [status], or `null` for
     *   [ScanStatus.Success] (nothing to deliver). The returned notification's
     *   text is guaranteed non-blank, satisfying [Notification]'s own contract.
     */
    fun notificationFor(status: ScanStatus): Notification? {
        if (status is ScanStatus.Success) return null
        val category = categoryFor(status) ?: return null
        return Notification(messageFor(status), category)
    }

    /**
     * Translate a failed [AlignmentGuidance] condition into the "likely cause"
     * clause embedded in a low-confidence message (Req 14.2).
     *
     * Every [AlignmentGuidance] is mapped, including the non-failure values
     * [AlignmentGuidance.PointAtDocument] and [AlignmentGuidance.ReadyToScan]:
     * if a caller has no specific failed condition to attribute, the result
     * still reads as a sensible, non-empty cause.
     */
    private fun likelyCausePhrase(cause: AlignmentGuidance): String = when (cause) {
        AlignmentGuidance.MoveCloser -> "the document was likely too far away"
        AlignmentGuidance.MoveFarther -> "the document was likely too close"
        AlignmentGuidance.HoldSteady -> "the camera likely moved during the scan"
        AlignmentGuidance.AddLight -> "the scene was likely too dark"
        AlignmentGuidance.FlattenDocument -> "the document plane was likely too tilted"
        AlignmentGuidance.PointAtDocument -> "the camera likely was not aimed at the document"
        AlignmentGuidance.ReadyToScan -> "the overall scan quality was low"
    }
}
