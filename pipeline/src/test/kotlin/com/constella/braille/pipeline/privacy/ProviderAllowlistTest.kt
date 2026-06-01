package com.constella.braille.pipeline.privacy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * JVM unit tests for the approved-provider [ProviderAllowlist] (task 20.3).
 *
 * Cover deny-by-default resolution, host normalization, the categorical ban on
 * developer-controlled hosts (so no first-party backend is ever a destination),
 * and the documented-retention-controls admission rule.
 *
 * _Requirements: 15.3, 15.5, 15.6, 16.4_
 */
class ProviderAllowlistTest : StringSpec({

    val acme = CloudProvider(
        id = "acme-ocr",
        displayName = "Acme OCR",
        host = "api.acme-ocr.com",
        hasDocumentedRetentionControls = true,
    )

    "an approved provider host resolves to that provider" {
        val allowlist = ProviderAllowlist(listOf(acme))
        allowlist.resolve("api.acme-ocr.com") shouldBe acme
        allowlist.isAllowedHost("api.acme-ocr.com").shouldBeTrue()
    }

    "resolution normalizes scheme, port, userinfo, and path" {
        val allowlist = ProviderAllowlist(listOf(acme))
        allowlist.resolve("https://user@API.Acme-OCR.com:443/v1/x?y=1#z") shouldBe acme
    }

    "an unknown host does not resolve (deny-by-default)" {
        val allowlist = ProviderAllowlist(listOf(acme))
        allowlist.resolve("api.other.com").shouldBeNull()
        allowlist.isAllowedHost("api.other.com").shouldBeFalse()
    }

    "a blank host never resolves and is treated as developer-controlled" {
        val allowlist = ProviderAllowlist(listOf(acme))
        allowlist.resolve("   ").shouldBeNull()
        allowlist.isDeveloperControlledHost("").shouldBeTrue()
    }

    "default developer-controlled (loopback) hosts are categorically rejected" {
        val allowlist = ProviderAllowlist(listOf(acme))
        for (host in ProviderAllowlist.DEFAULT_DEVELOPER_CONTROLLED_HOSTS) {
            allowlist.isDeveloperControlledHost(host).shouldBeTrue()
            allowlist.resolve(host).shouldBeNull()
        }
    }

    "a configured first-party host and its subdomains are rejected even if listed" {
        // Even if someone tries to admit a first-party host, construction fails;
        // here we verify the categorical check covers subdomains too.
        val allowlist = ProviderAllowlist(
            providers = listOf(acme),
            developerControlledHosts = setOf("internal.dev-backend.example"),
        )
        allowlist.isDeveloperControlledHost("internal.dev-backend.example").shouldBeTrue()
        allowlist.isDeveloperControlledHost("api.internal.dev-backend.example").shouldBeTrue()
        allowlist.resolve("api.internal.dev-backend.example").shouldBeNull()
    }

    "admitting a provider on a banned host throws" {
        runCatching {
            ProviderAllowlist(
                providers = listOf(acme.copy(host = "127.0.0.1")),
            )
        }.isFailure.shouldBeTrue()
    }

    "admitting a provider without retention controls throws" {
        runCatching {
            ProviderAllowlist(listOf(acme.copy(hasDocumentedRetentionControls = false)))
        }.isFailure.shouldBeTrue()
    }

    "an empty allowlist exposes no approved hosts and resolves nothing" {
        val allowlist = ProviderAllowlist.empty()
        allowlist.approvedHosts.isEmpty().shouldBeTrue()
        allowlist.resolve("api.acme-ocr.com").shouldBeNull()
    }

    "approvedHosts reflects the normalized admitted providers" {
        val second = CloudProvider(
            id = "beta-speech",
            displayName = "Beta Speech",
            host = "TTS.Beta.io",
            hasDocumentedRetentionControls = true,
        )
        val allowlist = ProviderAllowlist(listOf(acme, second))
        allowlist.approvedHosts shouldBe setOf("api.acme-ocr.com", "tts.beta.io")
    }
})
