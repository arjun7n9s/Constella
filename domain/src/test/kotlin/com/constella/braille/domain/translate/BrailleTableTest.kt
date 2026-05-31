package com.constella.braille.domain.translate

import com.constella.braille.domain.model.Grade
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Tests for [BrailleTable], the Grade -> bundled-table plumbing (Req 7.2, 7.4).
 */
class BrailleTableTest : StringSpec({

    "Grade 1 resolves to the bundled Grade 1 English table" {
        val table = BrailleTable.forGrade(Grade.GRADE_1)
        table shouldBe BrailleTable.ENGLISH_GRADE_1
        table.fileName shouldBe "en-us-g1.ctb"
        table.grade shouldBe Grade.GRADE_1
    }

    "Grade 2 resolves to the bundled Grade 2 English table" {
        val table = BrailleTable.forGrade(Grade.GRADE_2)
        table shouldBe BrailleTable.ENGLISH_GRADE_2
        table.fileName shouldBe "en-us-g2.ctb"
        table.grade shouldBe Grade.GRADE_2
    }

    "forGrade is total over all resolved grades" {
        // Every resolved Grade must map to a bundled table (no Auto here).
        Grade.entries.forEach { grade ->
            BrailleTable.forGrade(grade).grade shouldBe grade
        }
    }

    "bundled file names cover both English grades" {
        BrailleTable.bundledFileNames() shouldContainExactlyInAnyOrder
            listOf("en-us-g1.ctb", "en-us-g2.ctb")
    }
})
