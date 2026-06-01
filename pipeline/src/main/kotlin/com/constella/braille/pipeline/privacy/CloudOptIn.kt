package com.constella.braille.pipeline.privacy

/**
 * The explicit, **default-off** Operator opt-in that gates every optional cloud
 * feature (cloud-boost recognition / enhanced cloud speech).
 *
 * Privacy stance (Req 15.5, 16.3): cloud features are compiled in but disabled
 * by default. No image data may leave the device unless this opt-in has been
 * explicitly enabled by the Operator. The whole scan-to-speech cycle works with
 * this flag off, so the flag is a pure additive boost and never a dependency
 * (Req 11.8, 15.5).
 *
 * This is a tiny, framework-free holder so the policy core ([CloudTransmissionGate])
 * is deterministic and JVM-testable. It is intentionally **default-deny**: a
 * freshly constructed instance is disabled.
 *
 * Not thread-safe; callers serialize access on the coordinator/settings
 * dispatcher.
 *
 * _Requirements: 11.8, 15.5, 16.3_
 */
class CloudOptIn(initiallyEnabled: Boolean = false) {

    /**
     * Whether the Operator has explicitly turned cloud features on. `false` on
     * construction — the default-off guarantee. The gate denies all
     * transmission while this is `false`.
     */
    var isEnabled: Boolean = initiallyEnabled
        private set

    /** Explicit Operator action to turn cloud features on. */
    fun enable() {
        isEnabled = true
    }

    /** Explicit Operator action to turn cloud features off (returns to default-deny). */
    fun disable() {
        isEnabled = false
    }
}
