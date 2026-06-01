package com.constella.braille.pipeline.privacy

/**
 * An approved third-party cloud provider that the Operator may send image data
 * to when cloud features are explicitly enabled.
 *
 * Providers are **third parties chosen by the Operator/institution** — there is
 * no first-party / developer-operated endpoint anywhere in this model (Req 15.3,
 * 15.6). A provider is only usable if it declares documented data-retention
 * controls compatible with the project's privacy requirements (Req 16.4).
 *
 * Framework-free value type so the allowlist policy is deterministic and
 * JVM-testable.
 *
 * _Requirements: 15.5, 15.6, 16.4_
 */
data class CloudProvider(
    /** Stable identifier for the approved provider (e.g. "acme-ocr"). */
    val id: String,
    /** Human-readable name shown to the Operator when choosing a provider. */
    val displayName: String,
    /** Network host this provider is reachable at (e.g. "api.acme-ocr.com"). */
    val host: String,
    /**
     * True only when the provider has documented data-retention controls
     * compatible with the project's privacy requirements. Required by Req 16.4;
     * a provider without this MUST NOT be admitted to the allowlist.
     */
    val hasDocumentedRetentionControls: Boolean,
)

/**
 * The set of approved third-party destinations image data may be sent to when
 * cloud features are enabled — and the structural guarantee that **no
 * developer-controlled backend is ever a valid destination** (Req 15.3, 15.6).
 *
 * Design guarantees:
 *  - **Deny-by-default:** only hosts explicitly admitted here resolve; every
 *    other (unknown) host is rejected. An empty allowlist permits nothing.
 *  - **No first-party endpoint exists:** this type has no default provider, no
 *    hardcoded endpoint, and no "developer backend" constant. The app ships
 *    with no server the developer must run (Req 15.3). The strongest guarantee
 *    is the absence of any first-party destination in the codebase.
 *  - **Defense in depth:** as an additional safeguard, a registry of
 *    developer-controlled / loopback hosts ([developerControlledHosts]) is
 *    categorically banned. Any provider whose host matches it is rejected at
 *    construction, so a developer backend cannot be admitted even by mistake,
 *    and the gate independently re-checks at send time.
 *  - **Retention controls required:** every admitted provider must declare
 *    documented data-retention controls (Req 16.4).
 *
 * Framework-free and immutable; safe to share.
 *
 * _Requirements: 15.3, 15.5, 15.6, 16.4_
 */
class ProviderAllowlist(
    providers: List<CloudProvider>,
    /**
     * Hosts categorically treated as developer-controlled and therefore never a
     * valid destination. Defaults to loopback / unspecified addresses — a
     * privacy-first, no-backend app never "phones home" to a developer-run
     * server. Callers may extend this to forbid additional first-party hosts.
     */
    val developerControlledHosts: Set<String> = DEFAULT_DEVELOPER_CONTROLLED_HOSTS,
) {

    private val byHost: Map<String, CloudProvider>

    init {
        val normalizedBanned = developerControlledHosts.map { normalizeHost(it) }.toSet()
        val map = LinkedHashMap<String, CloudProvider>(providers.size)
        for (provider in providers) {
            require(provider.id.isNotBlank()) { "CloudProvider id must not be blank" }
            require(provider.host.isNotBlank()) { "CloudProvider host must not be blank" }
            require(provider.hasDocumentedRetentionControls) {
                "Provider '${provider.id}' lacks documented data-retention controls (Req 16.4)"
            }
            val host = normalizeHost(provider.host)
            require(!matchesAny(host, normalizedBanned)) {
                "Provider '${provider.id}' host '${provider.host}' is developer-controlled and " +
                    "can never be an approved destination (Req 15.3, 15.6)"
            }
            map[host] = provider
        }
        byHost = map
    }

    /** The approved providers, keyed access via [resolve]/[isAllowedHost]. */
    val providers: List<CloudProvider> get() = byHost.values.toList()

    /** Normalized hosts of every approved provider. */
    val approvedHosts: Set<String> get() = byHost.keys.toSet()

    /**
     * True when [host] is categorically developer-controlled (e.g. loopback or a
     * configured first-party host) and therefore never a valid destination,
     * regardless of the allowlist contents (Req 15.3, 15.6).
     */
    fun isDeveloperControlledHost(host: String): Boolean {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return true // fail-closed: a blank host is never valid.
        return matchesAny(normalized, developerControlledHosts.map { normalizeHost(it) }.toSet())
    }

    /**
     * True only when [host] is an explicitly approved third-party destination
     * and is not developer-controlled. Deny-by-default for anything else.
     */
    fun isAllowedHost(host: String): Boolean = resolve(host) != null

    /**
     * Resolve [host] to its approved [CloudProvider], or `null` when the host is
     * unknown or developer-controlled. The single source of truth for whether a
     * destination may be contacted.
     */
    fun resolve(host: String): CloudProvider? {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return null
        if (isDeveloperControlledHost(normalized)) return null
        return byHost[normalized]
    }

    companion object {
        /**
         * Loopback / unspecified hosts treated as developer-controlled by
         * default. A privacy-first, no-backend app never contacts these; they
         * stand in for "a server the developer runs" and are categorically
         * banned destinations.
         */
        val DEFAULT_DEVELOPER_CONTROLLED_HOSTS: Set<String> =
            setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")

        /** An allowlist with no approved providers: deny-by-default permits nothing. */
        fun empty(): ProviderAllowlist = ProviderAllowlist(emptyList())

        /**
         * Normalize a host for comparison: trim, lowercase, drop any scheme,
         * userinfo, path, and port so "HTTPS://API.Acme.com:443/x" == "api.acme.com".
         */
        internal fun normalizeHost(raw: String): String {
            var h = raw.trim().lowercase()
            if (h.isEmpty()) return ""
            // Strip scheme.
            val scheme = h.indexOf("://")
            if (scheme >= 0) h = h.substring(scheme + 3)
            // Strip userinfo.
            val at = h.indexOf('@')
            if (at >= 0) h = h.substring(at + 1)
            // Strip path / query / fragment.
            h = h.substringBefore('/').substringBefore('?').substringBefore('#')
            // Strip port (but keep IPv6 literals intact when bracketed).
            if (h.startsWith("[")) {
                val close = h.indexOf(']')
                if (close >= 0) h = h.substring(1, close)
            } else {
                h = h.substringBefore(':')
            }
            return h.trim('.')
        }

        /** A host matches a banned entry when equal or a subdomain of it. */
        private fun matchesAny(normalizedHost: String, normalizedBanned: Set<String>): Boolean {
            if (normalizedHost in normalizedBanned) return true
            return normalizedBanned.any { banned ->
                banned.isNotEmpty() && normalizedHost.endsWith(".$banned")
            }
        }
    }
}
