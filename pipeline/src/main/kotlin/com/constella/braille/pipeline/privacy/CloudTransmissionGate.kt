package com.constella.braille.pipeline.privacy

/**
 * The outcome of an attempt to transmit image data through the
 * [CloudTransmissionGate]. Every non-[Transmitted] case means **nothing left
 * the device**.
 *
 * _Requirements: 15.6, 16.3, 16.4_
 */
sealed interface TransmissionResult {

    /** True only when image data was actually sent to an approved provider. */
    val didTransmit: Boolean get() = this is Transmitted

    /**
     * The send completed to [provider]. The local temporary copy has been
     * deleted after transmission ([tempCopyDeleted] is `true`); Req 16.4.
     */
    data class Transmitted(
        val provider: CloudProvider,
        val tempCopyDeleted: Boolean,
    ) : TransmissionResult

    /**
     * The send was blocked because the explicit cloud opt-in was off (the
     * default). No bytes left the device (Req 16.3).
     */
    data object BlockedOptInDisabled : TransmissionResult

    /**
     * The send was blocked because the destination is not on the approved
     * third-party allowlist — including any developer-controlled host, which is
     * never valid (Req 15.6). No bytes left the device.
     */
    data class BlockedDestinationNotAllowed(val host: String) : TransmissionResult

    /**
     * The send was blocked because the underlying transmitter threw. Fail-closed:
     * the error is contained and treated as "did not transmit" (Req 16.3). The
     * temp copy is still cleaned up if a transmission was attempted.
     */
    data class BlockedByError(val message: String) : TransmissionResult
}

/**
 * Privacy-critical policy gate for any attempt to send image data off the
 * device. This is the single chokepoint that enforces, in order:
 *
 *  1. **Default-deny opt-in (Req 16.3):** if the explicit [CloudOptIn] is off
 *     (the default), the attempt is blocked and nothing leaves the device.
 *  2. **Approved-provider allowlist (Req 15.5, 15.6):** the destination host
 *     must resolve to an approved third-party provider. Unknown hosts and any
 *     developer-controlled host are always rejected — there is no first-party
 *     backend destination.
 *  3. **Fail-closed transport (Req 16.3):** the real network send is delegated
 *     to an injected [CloudTransmitter]; any exception is contained and the
 *     result is "did not transmit".
 *  4. **Post-transmission cleanup (Req 16.4):** once a transmission is
 *     attempted (whether it succeeds or throws), the local temporary copy of
 *     the image is deleted.
 *
 * The gate performs **policy, not transport**: it never opens a socket itself
 * and adds no networking dependency. It is pure Kotlin and JVM-testable with a
 * fake transmitter and a fake temp copy.
 *
 * Not thread-safe; callers serialize access on the coordinator dispatcher.
 *
 * _Requirements: 11.8, 15.3, 15.5, 15.6, 16.3, 16.4_
 */
class CloudTransmissionGate(
    private val optIn: CloudOptIn,
    private val allowlist: ProviderAllowlist,
    private val transmitter: CloudTransmitter,
) {

    /**
     * Attempt to transmit [frame] to the provider identified by [target],
     * staging it through [tempCopy].
     *
     * Guarantees:
     *  - Returns [TransmissionResult.BlockedOptInDisabled] without touching the
     *    transmitter or the temp copy when opt-in is off (the default).
     *  - Returns [TransmissionResult.BlockedDestinationNotAllowed] without
     *    transmitting when the destination is unknown or developer-controlled.
     *  - Only calls [CloudTransmitter.transmit] after both checks pass.
     *  - Deletes [tempCopy] after any attempted send (success or thrown error).
     *
     * @return a [TransmissionResult] describing exactly what happened.
     */
    fun transmit(
        frame: CameraFrame,
        target: TransmissionTarget,
        tempCopy: TempImageCopy,
    ): TransmissionResult {
        // (1) Default-deny: no opt-in, no transmission. Nothing is staged or sent.
        if (!optIn.isEnabled) {
            return TransmissionResult.BlockedOptInDisabled
        }

        // (2) Allowlist: resolve the destination to an approved third-party
        // provider. Unknown and developer-controlled hosts resolve to null.
        val provider = allowlist.resolve(target.host)
            ?: return TransmissionResult.BlockedDestinationNotAllowed(target.host)

        // (3) Fail-closed transport + (4) guaranteed cleanup.
        return try {
            transmitter.transmit(frame, provider)
            val deleted = safeDelete(tempCopy)
            TransmissionResult.Transmitted(provider = provider, tempCopyDeleted = deleted)
        } catch (t: Throwable) {
            // Even on a failed send, clean up the local temporary copy (Req 16.4).
            safeDelete(tempCopy)
            TransmissionResult.BlockedByError(t.message ?: t::class.simpleName ?: "transmission error")
        }
    }

    /**
     * Delete the temp copy, swallowing any deletion error so cleanup never
     * propagates. Returns whether the copy is gone afterward.
     */
    private fun safeDelete(tempCopy: TempImageCopy): Boolean {
        return try {
            tempCopy.delete()
            !tempCopy.exists()
        } catch (_: Throwable) {
            !runCatching { tempCopy.exists() }.getOrDefault(true)
        }
    }
}
