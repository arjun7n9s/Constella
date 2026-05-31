package com.constella.braille.domain

/**
 * Minimal marker for the domain layer so the module has a compilable symbol
 * and the project skeleton is self-consistent before the real data models
 * (task 1.2) land. Holds nothing behavioral.
 */
object BuildInfo {
    /** Human-readable module identifier. */
    const val MODULE: String = "braille-scanner-domain"
}
