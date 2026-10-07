package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.share.SetPharmacyActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetPharmacyTest {

    @Test
    fun needsANumber() {
        assertNull(SetPharmacyActivity.read("Corner Pharmacy", null, null))
        assertNull(SetPharmacyActivity.read("Corner Pharmacy", "  ", null))
        assertNull(SetPharmacyActivity.read("Corner Pharmacy", "call me", null))
    }

    @Test
    fun cleansWhatArrives() {
        val g = SetPharmacyActivity.read("  Corner\n Pharmacy ", " +1 555 0100 ", "content://com.android.contacts/contacts/lookup/abc/7")!!
        assertEquals("Corner Pharmacy", g.name)
        assertEquals("+1 555 0100", g.number)
        assertEquals("content://com.android.contacts/contacts/lookup/abc/7", g.contact)
    }

    @Test
    fun keepsOnlyAContactsEntry() {
        assertEquals("", SetPharmacyActivity.read("A", "555 0100", "https://example.com")!!.contact)
        assertEquals("", SetPharmacyActivity.read("A", "555 0100", "content://other.app/x")!!.contact)
        assertEquals("", SetPharmacyActivity.read(null, "555 0100", null)!!.name)
    }
}
