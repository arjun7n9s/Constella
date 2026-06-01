package com.constella.braille.pipeline.privacy

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * JVM unit tests for the privacy-critical cloud [CloudTransmissionGate] (task
 * 20.3).
 *
 * These exercise the policy core with a fake [RecordingTransmitter] (no real
 * HTTP, no networking dependency) and a fake [FakeTempImageCopy] whose deletion
 * is observable. They assert the default-deny opt-in (Req 16.3), the
 * approved-provider allowlist with categorical rejection of any
 * developer-controlled / unknown destination (Req 15.5, 15.6), fail-closed
 * behavior on transport errors (Req 16.3), and post-transmission cleanup of the
 * local temporary copy (Req 16.4).
 *
 * _Requirements: 11.8, 15.3, 15.5, 15.6, 16.3, 16.4_
 */
class CloudTransmissionGateTest : StringSpec({

    fun frame(id: String = "f1") = CameraFrame(
        id = id,
        bytes = byteArrayOf(7, 7, 7),
        width = 8,
        height = 8,
        format = FrameFormat.JPEG,
        capturedAtMillis = 1_000L,
    )

    val approvedProvider = CloudProvider(
        id = "acme-ocr",
        displayName = "Acme OCR",
        host = "api.acme-ocr.com",
        hasDocumentedRetentionControls = true,
    )

    fun allowlistOf(vararg providers: CloudProvider) = ProviderAllowlist(providers.toList())

    // --- (a) Default-off opt-in gate (Req 16.3) ------------------------------

    "a freshly constructed opt-in is off by default" {
        CloudOptIn().isEnabled.shouldBeFalse()
    }

    "with opt-in OFF (default), transmission is blocked and nothing is sent or staged" {
        val transmitter = RecordingTransmitter()
        val temp = FakeTempImageCopy()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(),
            allowlist = allowlistOf(approvedProvider),
            transmitter = transmitter,
        )

        val result = gate.transmit(frame(), TransmissionTarget(approvedProvider.host), temp)

        result shouldBe TransmissionResult.BlockedOptInDisabled
        result.didTransmit.shouldBeFalse()
        transmitter.sent.isEmpty().shouldBeTrue()
        // Default-deny must not even touch the staged copy.
        temp.deleteCalls shouldBe 0
        temp.exists().shouldBeTrue()
    }

    "disabling a previously enabled opt-in returns to default-deny" {
        val optIn = CloudOptIn(initiallyEnabled = true)
        optIn.disable()
        val transmitter = RecordingTransmitter()
        val gate = CloudTransmissionGate(optIn, allowlistOf(approvedProvider), transmitter)

        val result = gate.transmit(frame(), TransmissionTarget(approvedProvider.host), FakeTempImageCopy())

        result shouldBe TransmissionResult.BlockedOptInDisabled
        transmitter.sent.isEmpty().shouldBeTrue()
    }

    // --- (b) Gate allows only allowlisted providers when opt-in is ON --------

    "with opt-in ON, an approved provider is reachable and the send is recorded" {
        val transmitter = RecordingTransmitter()
        val temp = FakeTempImageCopy()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = allowlistOf(approvedProvider),
            transmitter = transmitter,
        )

        val result = gate.transmit(frame("scan-1"), TransmissionTarget(approvedProvider.host), temp)

        result.shouldBeInstanceOf<TransmissionResult.Transmitted>()
        result.provider shouldBe approvedProvider
        result.didTransmit.shouldBeTrue()
        transmitter.sent.single().first.id shouldBe "scan-1"
        transmitter.sent.single().second shouldBe approvedProvider
    }

    "host matching ignores scheme, port, and path so an approved provider still resolves" {
        val transmitter = RecordingTransmitter()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = allowlistOf(approvedProvider),
            transmitter = transmitter,
        )

        val result = gate.transmit(
            frame(),
            TransmissionTarget("HTTPS://api.acme-ocr.com:443/v1/recognize"),
            FakeTempImageCopy(),
        )

        result.shouldBeInstanceOf<TransmissionResult.Transmitted>()
    }

    // --- (c) Unknown / developer-controlled destinations are always rejected -

    "an unknown destination is rejected even with opt-in ON" {
        val transmitter = RecordingTransmitter()
        val temp = FakeTempImageCopy()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = allowlistOf(approvedProvider),
            transmitter = transmitter,
        )

        val result = gate.transmit(frame(), TransmissionTarget("evil.example.com"), temp)

        result shouldBe TransmissionResult.BlockedDestinationNotAllowed("evil.example.com")
        transmitter.sent.isEmpty().shouldBeTrue()
    }

    "a developer-controlled (loopback) destination is always rejected" {
        val transmitter = RecordingTransmitter()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = allowlistOf(approvedProvider),
            transmitter = transmitter,
        )

        for (host in listOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "localhost:8080")) {
            val result = gate.transmit(frame(), TransmissionTarget(host), FakeTempImageCopy())
            result.shouldBeInstanceOf<TransmissionResult.BlockedDestinationNotAllowed>()
        }
        transmitter.sent.isEmpty().shouldBeTrue()
    }

    "an empty allowlist permits no destination (deny-by-default)" {
        val transmitter = RecordingTransmitter()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = ProviderAllowlist.empty(),
            transmitter = transmitter,
        )

        val result = gate.transmit(frame(), TransmissionTarget(approvedProvider.host), FakeTempImageCopy())

        result.shouldBeInstanceOf<TransmissionResult.BlockedDestinationNotAllowed>()
        transmitter.sent.isEmpty().shouldBeTrue()
    }

    "a provider whose host is developer-controlled cannot be admitted to the allowlist" {
        val ex = runCatching {
            ProviderAllowlist(
                listOf(
                    CloudProvider(
                        id = "sneaky-backend",
                        displayName = "Dev Backend",
                        host = "localhost",
                        hasDocumentedRetentionControls = true,
                    ),
                ),
            )
        }.exceptionOrNull()
        ex.shouldBeInstanceOf<IllegalArgumentException>()
    }

    "a provider without documented retention controls cannot be admitted (Req 16.4)" {
        val ex = runCatching {
            ProviderAllowlist(
                listOf(approvedProvider.copy(id = "no-retention", hasDocumentedRetentionControls = false)),
            )
        }.exceptionOrNull()
        ex.shouldBeInstanceOf<IllegalArgumentException>()
    }

    // --- (d) Post-transmission cleanup of the local temp copy (Req 16.4) -----

    "the local temporary copy is deleted after a successful transmission" {
        val temp = FakeTempImageCopy()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = allowlistOf(approvedProvider),
            transmitter = RecordingTransmitter(),
        )

        val result = gate.transmit(frame(), TransmissionTarget(approvedProvider.host), temp)

        result.shouldBeInstanceOf<TransmissionResult.Transmitted>()
        result.tempCopyDeleted.shouldBeTrue()
        temp.deleteCalls shouldBe 1
        temp.exists().shouldBeFalse()
    }

    // --- Fail-closed transport (Req 16.3) ------------------------------------

    "a transmitter error fails closed and still cleans up the temp copy" {
        val temp = FakeTempImageCopy()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(initiallyEnabled = true),
            allowlist = allowlistOf(approvedProvider),
            transmitter = ThrowingTransmitter(),
        )

        val result = gate.transmit(frame(), TransmissionTarget(approvedProvider.host), temp)

        result.shouldBeInstanceOf<TransmissionResult.BlockedByError>()
        result.didTransmit.shouldBeFalse()
        // Cleanup still runs on a failed send (Req 16.4).
        temp.exists().shouldBeFalse()
    }

    "a blocked attempt never deletes a temp copy because none was staged or sent" {
        val temp = FakeTempImageCopy()
        val gate = CloudTransmissionGate(
            optIn = CloudOptIn(), // off
            allowlist = allowlistOf(approvedProvider),
            transmitter = RecordingTransmitter(),
        )

        gate.transmit(frame(), TransmissionTarget(approvedProvider.host), temp)

        temp.deleteCalls shouldBe 0
        temp.exists().shouldBeTrue()
    }
})

/** Fake [CloudTransmitter] that records sends instead of touching the network. */
private class RecordingTransmitter : CloudTransmitter {
    val sent = mutableListOf<Pair<CameraFrame, CloudProvider>>()
    override fun transmit(frame: CameraFrame, provider: CloudProvider) {
        sent += frame to provider
    }
}

/** Fake [CloudTransmitter] that simulates a transport failure (fail-closed test). */
private class ThrowingTransmitter : CloudTransmitter {
    override fun transmit(frame: CameraFrame, provider: CloudProvider): Unit =
        throw RuntimeException("network down")
}

/** In-memory fake [TempImageCopy] whose deletion is observable. */
private class FakeTempImageCopy(override val location: String = "memory://temp") : TempImageCopy {
    private var present = true
    var deleteCalls = 0
        private set

    override fun exists(): Boolean = present
    override fun delete() {
        deleteCalls++
        present = false
    }
}
