package com.constella.braille.domain.translate

import com.constella.braille.domain.model.Grade

/**
 * Resolves a resolved [Grade] to the bundled liblouis translation table used to
 * back-translate it (Req 7.2, 7.4).
 *
 * This is pure plumbing: it maps the *resolved* grade (never `Auto`) to the
 * file name of the English table that ships inside the Application_Package, and
 * deliberately offers **no** way to name a table that is not bundled, enforcing
 * "only liblouis translation tables bundled within the Application_Package"
 * (Req 7.4). The liblouis JNI wrapper in `:runtime` resolves these names against
 * its bundled `assets/liblouis/tables/` directory.
 *
 * The file names match the table assets documented in the runtime assets
 * README (`en-us-g1.ctb`, `en-us-g2.ctb`).
 */
enum class BrailleTable(
    /** The resolved grade this table translates. */
    val grade: Grade,
    /** The bundled liblouis table file name (under `liblouis/tables/`). */
    val fileName: String,
) {
    /** Grade 1 (uncontracted) English. */
    ENGLISH_GRADE_1(Grade.GRADE_1, "en-us-g1.ctb"),

    /** Grade 2 (contracted) English. */
    ENGLISH_GRADE_2(Grade.GRADE_2, "en-us-g2.ctb"),
    ;

    companion object {
        /**
         * The bundled table for [grade]. Total over [Grade] (every resolved
         * grade has exactly one bundled English table), so this never fails for
         * a valid resolved grade.
         *
         * _Requirements: 7.2, 7.4_
         */
        fun forGrade(grade: Grade): BrailleTable = when (grade) {
            Grade.GRADE_1 -> ENGLISH_GRADE_1
            Grade.GRADE_2 -> ENGLISH_GRADE_2
        }

        /** The set of bundled table file names — used to validate availability. */
        fun bundledFileNames(): Set<String> = entries.map { it.fileName }.toSet()
    }
}
