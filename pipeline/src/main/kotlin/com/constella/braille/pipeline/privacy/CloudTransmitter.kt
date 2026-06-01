package com.constella.braille.pipeline.privacy

/**
 * A local temporary copy of an image staged for an attempted cloud transmission.
 *
 * The policy core only needs to be able to *delete* this copy after a send
 * completes (Req 16.4); it deliberately does not read or own the bytes. The
 * real implementation is backed by a temp file, but this abstraction keeps the
 * gate framework-free and lets tests use an in-memory fake whose deletion can be
 * observed.
 *
 * _Requirements: 16.4_
 */
interface TempImageCopy {
    /** Opaque identifier/location of the temp copy (e.g. an absolute path). */
    val location: String

    /** True while the temp copy still exists on the device. */
    fun exists(): Boolean

    /**
     * Delete the temp copy from local storage. Implementations MUST be
     * idempotent (deleting an already-deleted copy is a no-op) so cleanup can be
     * safely retried.
     */
    fun delete()
}

/**
 * The destination chosen for an attempted transmission, expressed as the
 * provider [host]. The gate validates this host against the [ProviderAllowlist]
 * before any send; it is never a developer-controlled host (Req 15.3, 15.6).
 *
 * _Requirements: 15.5, 15.6_
 */
data class TransmissionTarget(val host: String)

/**
 * The actual network send, modeled behind an interface so the privacy policy
 * (gating, allowlist, cleanup) can be tested without any real HTTP client or
 * networking dependency. The gate's job is **policy, not transport**: it only
 * invokes [transmit] after every privacy check has passed.
 *
 * Implementations are responsible solely for moving the already-approved bytes
 * to the already-approved [CloudProvider]; they MUST NOT bypass the gate.
 *
 * _Requirements: 16.3, 16.4_
 */
interface CloudTransmitter {
    /**
     * Transmit the [frame]'s image bytes to the approved [provider]. Called by
     * [CloudTransmissionGate] only when opt-in is enabled and the destination is
     * on the allowlist. Returns when the transmission has completed.
     */
    fun transmit(frame: CameraFrame, provider: CloudProvider)
}
