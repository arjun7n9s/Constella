/**
 * Camera-frame privacy boundary for the Braille Scanner (Req 16.1, 16.2).
 *
 * The System processes camera frames in device memory and retains them on the
 * device; a captured frame is persisted to storage **only** when the Operator
 * explicitly requests to save the scan. This package separates that policy from
 * any platform IO so the rule is deterministic and unit-testable on the JVM:
 *
 *  - [com.constella.braille.pipeline.privacy.CameraFrame] — framework-free,
 *    in-memory value type for a single captured frame.
 *  - [com.constella.braille.pipeline.privacy.FrameState] — explicit lifecycle:
 *    `Empty` -> `HeldInMemory` -> (`Persisted` only on explicit save).
 *  - [com.constella.braille.pipeline.privacy.FrameHolder] — pure-Kotlin core
 *    that retains the current frame in memory and persists it solely via an
 *    explicit `saveScan()` call, writing through a [FrameSink].
 *  - [com.constella.braille.pipeline.privacy.FrameSink] — the storage seam; a
 *    fake records writes in tests, the real
 *    [com.constella.braille.pipeline.privacy.AndroidFileFrameSink] writes bytes
 *    to app-private storage.
 *
 * NOTE: Cloud transmission (opt-in gate, provider allowlist, temp-copy cleanup)
 * is intentionally NOT implemented here; that is task 20.3 / Req 16.3, 16.4.
 *
 * _Requirements: 16.1, 16.2_
 */
package com.constella.braille.pipeline.privacy
