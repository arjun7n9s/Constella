package com.constella.braille

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldStartWith
import com.constella.braille.domain.BuildInfo

/**
 * Skeleton JVM unit test confirming the app module's local `test` source set
 * runs (kotest on the JUnit Platform) and can see the downward `:domain`
 * dependency. Replaced/extended by real UI-logic tests in later tasks.
 */
class AppModuleUnitTest : StringSpec({
    "app can reference the domain layer downward" {
        BuildInfo.MODULE shouldStartWith "braille-scanner"
    }
})
