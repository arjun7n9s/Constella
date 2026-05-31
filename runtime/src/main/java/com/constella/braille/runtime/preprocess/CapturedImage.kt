package com.constella.braille.runtime.preprocess

/**
 * A frame captured by the Camera_Module and handed to the Image_Preprocessor.
 *
 * In this skeleton it is modeled as a single-channel [ImageBuffer]; the design
 * sketches a richer `CapturedImage` (color planes, rotation, capture metadata),
 * but task 5.1 only needs the luminance buffer the boundary finder and warp
 * operate on. Camera plumbing (task 18) can extend this type without affecting
 * the preprocessing geometry, which depends only on [buffer].
 *
 * _Requirements: 3.1, 3.2_
 */
class CapturedImage(
    val buffer: ImageBuffer,
)
