package com.wanderwildwood.fukuyaku.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactEntryTest {
    @Test
    fun `only an entry in the phone's Contacts comes back from a backup`() {
        assertEquals("content://com.android.contacts/contacts/lookup/x/7", contactEntry(" content://com.android.contacts/contacts/lookup/x/7 "))
        assertEquals("", contactEntry("https://example.com/"))
        assertEquals("", contactEntry("content://com.example.other/7"))
        assertEquals("", contactEntry("content://com.android.contacts/" + "x".repeat(600)))
        assertEquals("", contactEntry(""))
    }
}
