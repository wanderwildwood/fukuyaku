package com.wanderwildwood.fukuyaku.ui

import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Status
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LogSummaryTest {

    private val ny = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 11, 10)
    private val now = at(10, 21)

    private fun at(d: Int, h: Int): Long =
        ZonedDateTime.of(2026, 11, d, h, 0, 0, 0, ny).toInstant().toEpochMilli()

    private fun dose(id: Long, medicineId: Long, due: Long, name: String = "Metformin", amount: String = "500 mg") =
        Dose(id = id, medicineId = medicineId, name = name, amount = amount, due = due, status = Status.TAKEN, marked = due)

    @Test
    fun aMedicineDeletedAndAddedAgainGetsTwoSummariesWithTheirOwnKeys() {
        // Medicine 1 was deleted on the 5th; medicine 2 is the same name and amount, added again.
        val doses = listOf(
            dose(1, medicineId = 1, due = at(3, 8)),
            dose(2, medicineId = 1, due = at(4, 8)),
            dose(3, medicineId = 2, due = at(8, 8)),
            dose(4, medicineId = 2, due = at(9, 8)),
        )
        val s = summaries(doses, today, ny, now)
        assertEquals(listOf("Metformin, 500 mg", "Metformin, 500 mg"), s.map { it.name })
        assertEquals("one key each, so the list never sees the same key twice", 2, s.map { it.medicineId }.toSet().size)
        assertEquals("the entry in use comes first", listOf(2L, 1L), s.map { it.medicineId })
    }

    @Test
    fun summariesStillSortByName() {
        val doses = listOf(
            dose(1, medicineId = 1, due = at(9, 8), name = "Vitamin D", amount = ""),
            dose(2, medicineId = 2, due = at(9, 8), name = "aspirin", amount = "81 mg"),
        )
        assertEquals(listOf(2L, 1L), summaries(doses, today, ny, now).map { it.medicineId })
    }
}
