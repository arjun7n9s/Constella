/**
 * Deterministic Braille-grade detection and override for the Braille Scanner
 * (Req 8.1, 8.4).
 *
 * This package holds the framework-free, JVM-testable core of the
 * Grade_Detector:
 *  - [com.constella.braille.domain.grade.GradeDetector] — the contract for
 *    estimating Grade 1 vs Grade 2 ([com.constella.braille.domain.grade.GradeDetector.detectGrade])
 *    and resolving the grade to translate with for a given
 *    [com.constella.braille.domain.model.GradeMode]
 *    ([com.constella.braille.domain.grade.GradeDetector.resolveGrade]).
 *  - [com.constella.braille.domain.grade.HeuristicGradeDetector] — the default
 *    estimate: it scores unambiguous contraction-signal patterns against a
 *    Grade-1-only interpretation and picks Grade 2 when the signal ratio crosses
 *    a threshold, else Grade 1.
 *  - [com.constella.braille.domain.grade.GradeOverride] — the one-tap override
 *    path that re-runs **only** translation on the already-recognized cells, so
 *    a grade change never triggers a rescan (Req 8.4).
 *
 * The estimate is explicitly a best guess, always backed by the one-tap manual
 * override (Req 8.3, 8.5). Everything here is pure Kotlin: it depends only on
 * the `:domain` models and the [com.constella.braille.domain.translate.TranslationEngine]
 * contract, and never touches the camera, detector, or segmenter.
 */
package com.constella.braille.domain.grade
