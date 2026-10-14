package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A dose written down as taken at a time of the person's choosing, rather than its own or now. */
class TakenAtTest {

    private val ny = ZoneId.of("America/New_York")
    private val min = 60_000L

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ny).toInstant().toEpochMilli()

    private val med = Medicine(
        id = 1,
        name = "Lisinopril",
        amount = "10 mg",
        schedule = Schedule.AtTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        caughtUp = at(2026, 10, 7, 21),
    )

    private val evening = Dose(
        id = 5,
        medicineId = 1,
        name = "Lisinopril",
        amount = "10 mg",
        due = at(2026, 10, 6, 20),
        rang = at(2026, 10, 6, 20),
        remindAt = at(2026, 10, 6, 20, 15),
        again = 1,
    )

    // ---------------------------------------------------------------- the moment chosen

    @Test fun yesterdayEveningIsYesterdayEvening() {
        val now = at(2026, 10, 7, 9, 30)
        assertEquals(
            at(2026, 10, 6, 21, 40),
            Plan.takenAt(LocalDate.of(2026, 10, 6), LocalTime.of(21, 40), ny, now),
        )
    }

    @Test fun aTimeStillToComeIsRefused() {
        val now = at(2026, 10, 7, 9, 30)
        assertNull(Plan.takenAt(LocalDate.of(2026, 10, 7), LocalTime.of(9, 31), ny, now))
        assertNull(Plan.takenAt(LocalDate.of(2026, 10, 8), LocalTime.of(1, 0), ny, now))
    }

    @Test fun thisVeryMinuteIsAllowed() {
        val now = at(2026, 10, 7, 9, 30) + 20_000L
        assertEquals(at(2026, 10, 7, 9, 30), Plan.takenAt(LocalDate.of(2026, 10, 7), LocalTime.of(9, 30), ny, now))
    }

    @Test fun aTimeTheClockSkippedMovesOnByTheGap() {
        // 8 March 2026: New York goes from 02:00 straight to 03:00.
        val now = at(2026, 3, 8, 12)
        assertEquals(at(2026, 3, 8, 3, 30), Plan.takenAt(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ny, now))
    }

    @Test fun theDaysOfferedAreTodayAndYesterday() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals(listOf(today, today.minusDays(1)), Plan.takenDays(today, null))
        assertEquals(listOf(today, today.minusDays(1)), Plan.takenDays(today, today.minusDays(1)))
    }

    @Test fun aDoseDueTodayCannotBeTakenTheDayBefore() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals(listOf(today), Plan.takenDays(today, today))
    }

    @Test fun anOlderDoseAlsoOffersItsOwnDay() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), Plan.takenDays(today, today.minusDays(2)))
    }

    // ---------------------------------------------------------------- what is written

    @Test fun aDoseMarkedTakenAtAChosenTimeKeepsItsDueTime() {
        val taken = Plan.marked(evening, Status.TAKEN, at(2026, 10, 6, 21, 40))
        assertEquals(Status.TAKEN, taken.status)
        assertEquals(at(2026, 10, 6, 21, 40), taken.marked)
        assertEquals(evening.due, taken.due)
        assertNull("it stops ringing", taken.remindAt)
        assertEquals(evening.rang, taken.rang)
    }

    @Test fun aChosenTimeBeforeTheDueTimeIsKeptAsGiven() {
        // Taken at 19:30, before its 20:00 reminder, and written down the next morning.
        val taken = Plan.marked(evening, Status.TAKEN, at(2026, 10, 6, 19, 30))
        assertEquals(at(2026, 10, 6, 19, 30), taken.marked)
        assertEquals(evening.due, taken.due)
    }

    @Test fun unmarkingForgetsTheChosenTime() {
        val taken = Plan.marked(evening, Status.TAKEN, at(2026, 10, 6, 21, 40))
        assertNull(Plan.marked(taken, Status.OPEN, at(2026, 10, 7, 9)).marked)
    }

    @Test fun aDoseTakenWhenNeededEarlierIsLoggedAtThatTime() {
        val headache = med.copy(id = 2, name = "Ibuprofen", amount = "200 mg", schedule = Schedule.AsNeeded)
        val d = Plan.asNeeded(headache, at(2026, 10, 6, 22, 15))
        assertEquals(at(2026, 10, 6, 22, 15), d.due)
        assertEquals(at(2026, 10, 6, 22, 15), d.marked)
        assertEquals(Status.TAKEN, d.status)
        assertTrue(d.asNeeded)
        assertEquals(2L, d.medicineId)
    }

    // ---------------------------------------------------------------- what follows from it

    @Test fun theNextDoseDoesNotMoveWithTheTimeItWasTaken() {
        // After the medicine's caughtUp (the 7th, 21:00), so the schedule is what decides.
        val now = at(2026, 10, 8, 9, 30)
        val before = Plan.nextUnlogged(med, now, ny, emptySet())
        assertEquals(at(2026, 10, 8, 20), before)
        assertEquals(before, Plan.nextUnlogged(med, now, ny, setOf(evening.due)))
    }

    @Test fun aDoseTakenLateStillCountsForTheDayItWasDue() {
        val taken = Plan.marked(evening, Status.TAKEN, at(2026, 10, 7, 0, 30))
        val t = Plan.tally(listOf(taken), at(2026, 10, 6, 0), at(2026, 10, 7, 0))
        assertEquals(1, t.taken)
        assertEquals(0, t.notMarked)
        val days = listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7))
        assertEquals(listOf(Plan.Mark.ALL, Plan.Mark.NONE), Plan.marks(listOf(taken), days, ny))
    }

    @Test fun aChosenTimeIsWholeMinutes() {
        val chosen = Plan.takenAt(LocalDate.of(2026, 10, 6), LocalTime.of(21, 40), ny, at(2026, 10, 7, 9))!!
        assertEquals(0L, chosen % min)
    }
}
