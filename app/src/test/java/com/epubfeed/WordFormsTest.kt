package com.epubfeed

import com.epubfeed.domain.thesaurus.Rarity
import com.epubfeed.domain.thesaurus.WordForms
import com.epubfeed.domain.thesaurus.WordUses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFormsTest {

    private val passage =
        "He walked into the gloaming, where the household lamps were already lit. " +
            "She said it was ephemeral, and he agreed."

    @Test
    fun `a korean word keeps its meaning when the particle is stripped`() {
        assertTrue(WordForms.candidates("사랑은").contains("사랑"))
        assertTrue(WordForms.candidates("학교에서").contains("학교"))
        assertTrue(WordForms.candidates("사람들을").contains("사람들"))
    }

    @Test
    fun `the longest korean suffix is tried before a shorter one`() {
        // 학교에서 must answer as 학교 before the shorter 서 cut offers 학교에.
        val candidates = WordForms.candidates("학교에서")
        assertTrue(candidates.contains("학교"))
        assertTrue(candidates.indexOf("학교") < candidates.indexOf("학교에"))
    }

    @Test
    fun `a selection inside a word grows to the whole word`() {
        val span = WordForms.snap(passage, 20, 23)!!
        assertEquals("gloaming", passage.substring(span.start, span.end))
    }

    @Test
    fun `a selection that starts mid word grows backwards too`() {
        val start = passage.indexOf("household") + 5
        val span = WordForms.wordAt(passage, start)!!
        assertEquals("household", passage.substring(span.start, span.end))
    }

    @Test
    fun `a cursor in punctuation only selects nothing`() {
        assertNull(WordForms.snap("... , ,, ", 4, 4))
    }

    @Test
    fun `apostrophes and hyphens stay inside the word`() {
        val text = "It was a well-known fact that they don't listen."
        val hyphen = text.indexOf("well-known") + 6
        val span = WordForms.wordAt(text, hyphen)!!
        assertEquals("well-known", text.substring(span.start, span.end))
        val apostrophe = text.indexOf("don't") + 2
        val second = WordForms.wordAt(text, apostrophe)!!
        assertEquals("don't", text.substring(second.start, second.end))
    }

    @Test
    fun `inflected forms fall back to a dictionary headword`() {
        assertTrue(WordForms.candidates("houses").contains("house"))
        assertTrue(WordForms.candidates("ran").contains("run"))
        assertTrue(WordForms.candidates("running").contains("run"))
        assertTrue(WordForms.candidates("gloaming").contains("gloam"))
        assertTrue(WordForms.candidates("happily").contains("happy"))
        assertTrue(WordForms.candidates("written").contains("write"))
        assertTrue(WordForms.candidates("children").contains("child"))
    }

    @Test
    fun `an ordinary word is looked up as itself first`() {
        assertEquals("ephemeral", WordForms.candidates("Ephemeral,").first())
    }

    @Test
    fun `a phrase also offers its single words`() {
        val candidates = WordForms.candidates("the gloaming")
        assertTrue(candidates.contains("the gloaming"))
        assertTrue(candidates.contains("gloaming"))
    }

    @Test
    fun `use counting does not match inside longer words`() {
        assertEquals(0, WordForms.countUses(passage, "house"))
        assertEquals(1, WordForms.countUses(passage, "household"))
        assertEquals(1, WordForms.countUses(passage, "gloaming"))
        assertTrue(WordForms.containsWord(passage, "Ephemeral"))
    }

    @Test
    fun `the sentence around a word is one sentence`() {
        val span = WordForms.wordAt(passage, passage.indexOf("ephemeral"))!!
        assertEquals(
            "She said it was ephemeral, and he agreed.",
            WordForms.sentenceAt(passage, span),
        )
    }

    @Test
    fun `rarity bands follow the rank inside the frequency list`() {
        assertEquals(Rarity.COMMON, Rarity.fromRank(297, 200_000))
        assertEquals(Rarity.UNCOMMON, Rarity.fromRank(29_310, 200_000))
        assertEquals(Rarity.RARE, Rarity.fromRank(100_000, 200_000))
        assertEquals(Rarity.VERY_RARE, Rarity.fromRank(185_304, 200_000))
        assertEquals(Rarity.RARE, Rarity.notListed())
        assertNull(Rarity.fromRank(null, 200_000))
    }

    @Test
    fun `the use line names the chapters`() {
        assertEquals("3 uses, chapters 2 and 7", WordUses(listOf(1, 6), 3).label)
        assertEquals("15 uses, chapters 1, 7, 8 and 4 more", WordUses(listOf(0, 6, 7, 11, 12, 18, 22), 15).label)
        assertEquals("2 uses, chapter 4", WordUses(listOf(3), 2).label)
        assertEquals("1 use in this book", WordUses(listOf(3), 1).label)
        assertFalse(WordUses(emptyList(), 0).label.isNotEmpty())
    }

    @Test
    fun `normalising keeps the word and drops the punctuation around it`() {
        assertEquals("gloaming", WordForms.normalize("  \u201CGloaming\u201D,  "))
        assertEquals("don't", WordForms.normalize("don't"))
    }
}
