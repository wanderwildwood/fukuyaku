package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.ui.SOURCE_URL
import com.wanderwildwood.fukuyaku.ui.components
import com.wanderwildwood.fukuyaku.ui.licenceParagraphs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicencesTest {
    private val raw = File("src/main/res/raw")

    @Test
    fun builtWithIsNotEmptyAndEveryEntryHasALinkAndALicence() {
        assertTrue(components.size >= 6)
        for (c in components) {
            assertTrue(c.name, c.link.startsWith("https://"))
            assertTrue(c.name, c.licence.isNotBlank())
        }
    }

    @Test
    fun theSourceLinkIsThePublicRepo() {
        assertEquals("https://github.com/wanderwildwood/fukuyaku", SOURCE_URL)
    }

    @Test
    fun theFullTextsAreBundledAndPage() {
        val gpl = File(raw, "gpl_3_0.txt").readText()
        assertTrue(gpl.contains("END OF TERMS AND CONDITIONS"))
        assertEquals(File("../LICENSE").readText(), gpl)
        assertTrue(licenceParagraphs(gpl).size > 50)
        assertTrue(File(raw, "apache_2_0.txt").readText().contains("Apache License"))
        assertTrue(File(raw, "ofl_1_1.txt").readText().contains("SIL OPEN FONT LICENSE"))
    }

    @Test
    fun paragraphsJoinWrappedLines() {
        assertEquals(listOf("one two", "three"), licenceParagraphs("one\ntwo\n\n three \n"))
    }
}
