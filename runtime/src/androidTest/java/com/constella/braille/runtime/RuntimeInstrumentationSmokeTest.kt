package com.constella.braille.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Skeleton instrumented test confirming the `androidTest` source set is wired
 * for the native/runtime module. On-device tests for OpenCV/TFLite/liblouis
 * (tasks 5, 6, 10) and the offline bundling smoke tests (task 23.3) extend this.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeInstrumentationSmokeTest {

    @Test
    fun hasInstrumentationContext() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assertTrue(context.packageName.isNotEmpty())
    }
}
