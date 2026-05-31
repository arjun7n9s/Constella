/**
 * Deterministic image-preprocessing geometry for the Braille Scanner (Req 3.1,
 * 3.2).
 *
 * This package holds the framework-free, JVM-testable core of the
 * Image_Preprocessor:
 *  - [com.constella.braille.domain.preprocess.Point2D] /
 *    [com.constella.braille.domain.preprocess.ImageSize] — pixel-space geometry types.
 *  - [com.constella.braille.domain.preprocess.Quad] — the document boundary, its
 *    canonical corner ordering, shoelace area, and the Req 3.1 minimum
 *    frame-area acceptance test.
 *  - [com.constella.braille.domain.preprocess.Homography] — the 4-point
 *    projective-transform solve underpinning the warp.
 *  - [com.constella.braille.domain.preprocess.PerspectiveRectifier] — the Req 3.2
 *    target-rectangle math, homography construction, and edge-alignment metric.
 *
 * The actual pixel boundary-finding and warp *execution* live behind a thin
 * native (OpenCV) interface in the `:runtime` layer; only the geometry that can
 * be reasoned about and tested without OpenCV lives here. Dependency rule:
 * pure Kotlin, no Android, no other project module.
 */
package com.constella.braille.domain.preprocess
