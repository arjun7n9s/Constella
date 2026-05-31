package com.constella.braille.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Skeleton smoke test that verifies the JVM property-based testing framework
 * (kotest-property) and the unit/test source sets are correctly wired for the
 * domain module.
 *
 * This is NOT one of the design's numbered Correctness Properties (1–35); those
 * are implemented by their dedicated tasks. This test only confirms the test
 * harness runs example assertions and generated-input property checks. It can
 * be deleted once the first real domain tests (task 1.4) are in place.
 */
class PropertyTestingHarnessTest : StringSpec({

    "domain module marker is present (unit-style example)" {
        BuildInfo.MODULE shouldBe "braille-scanner-domain"
    }

    "kotest-property generates and checks inputs (property-style)" {
        // Addition is commutative across all generated Int pairs — proves the
        // generator + checkAll machinery is on the classpath and runs.
        checkAll(Arb.int(), Arb.int()) { a, b ->
            (a + b) shouldBe (b + a)
        }
    }
})
