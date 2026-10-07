package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.share.SetWhoActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetPharmacyTest {

    @Test
    fun needsANumber() {
        assertNull(SetWhoActivity.read("Corner Pharmacy", null, null))
        assertNull(SetWhoActivity.read("Corner Pharmacy", "  ", null))
        assertNull(SetWhoActivity.read("Corner Pharmacy", "call me", null))
    }

    @Test
    fun cleansWhatArrives() {
        val g = SetWhoActivity.read("  Corner\n Pharmacy ", " +1 555 0100 ", "content://com.android.contacts/contacts/lookup/abc/7")!!
        assertEquals("Corner Pharmacy", g.name)
        assertEquals("+1 555 0100", g.number)
        assertEquals("content://com.android.contacts/contacts/lookup/abc/7", g.contact)
    }

    @Test
    fun keepsOnlyAContactsEntry() {
        assertEquals("", SetWhoActivity.read("A", "555 0100", "https://example.com")!!.contact)
        assertEquals("", SetWhoActivity.read("A", "555 0100", "content://other.app/x")!!.contact)
        assertEquals("", SetWhoActivity.read(null, "555 0100", null)!!.name)
    }
}
