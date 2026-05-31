package com.constella.braille

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Skeleton instrumented test that confirms the `androidTest` source set,
 * AndroidJUnitRunner, and Espresso/Compose test dependencies are wired for the
 * app (UI) module. Real accessibility and UI tests (tasks 22.x, 16.x) replace
 * or extend this.
 */
@RunWith(AndroidJUnit4::class)
class AppInstrumentationSmokeTest {

    @Test
    fun usesBrailleScannerApplicationContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.constella.braille", context.packageName)
    }
}
