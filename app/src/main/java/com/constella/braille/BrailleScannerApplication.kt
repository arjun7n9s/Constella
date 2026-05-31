package com.constella.braille

import android.app.Application

/**
 * Application entry point for the Braille Scanner (Constella).
 *
 * UI layer. This class wires nothing application-wide yet; it exists so the
 * manifest has a concrete [Application] and the four-layer skeleton compiles.
 * Dependency wiring (Camera_Module, pipeline stages, runtimes) is added in
 * later tasks via the ScanCoordinator.
 */
class BrailleScannerApplication : Application()
