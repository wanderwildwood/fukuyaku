package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Export
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlanTest {

    private val ny = ZoneId.of("America/New_York")
    private val min = 60_000L
    private val hour = 60 * min

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ny).toInstant().toEpochMilli()

    private val metformin = Medicine(
        id = 1,
        name = "Metformin",
        amount = "500 mg",
        schedule = Schedule.AtTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
        caughtUp = at(2026, 10, 6, 7),
    )

    private fun dose(due: Long, again: Int = 0, remindAt: Long? = null, status: Status = Status.OPEN, asNeeded: Boolean = false) =
        Dose(id = due, medicineId = 1, name = "Metformin", amount = "500 mg", due = due, status = status, again = again, remindAt = remindAt, asNeeded = asNeeded)

    // ---------------------------------------------------------------- catching up

    @Test fun anAlarmOnTimeRingsItsOneDose() {
        val c = Plan.catchUp(metformin, at(2026, 10, 6, 8), ny)
        assertEquals(listOf(at(2026, 10, 6, 8)), c.dues)
        assertEquals(at(2026, 10, 6, 8), c.rings)
    }

    @Test fun afterALongStopEveryDoseIsLoggedButOnlyTheNewestRings() {
        // Stopped since 07:00 Tuesday, opened again 21:00 Wednesday: four doses went by.
        val c = Plan.catchUp(metformin, at(2026, 10, 7, 21), ny)
        assertEquals(
            listOf(at(2026, 10, 6, 8), at(2026, 10, 6, 20), at(2026, 10, 7, 8), at(2026, 10, 7, 20)),
            c.dues,
        )
        assertEquals(at(2026, 10, 7, 20), c.rings)
    }

    @Test fun aDoseFromLongAgoIsLoggedAndDoesNotRing() {
        val c = Plan.catchUp(metformin.copy(schedule = Schedule.AtTimes(listOf(LocalTime.of(8, 0)))), at(2026, 10, 6, 21), ny)
        assertEquals(listOf(at(2026, 10, 6, 8)), c.dues)
        assertNull(c.rings)
    }

    @Test fun pausedAndAsNeededMedicinesNeverComeDue() {
        assertEquals(emptyList<Long>(), Plan.catchUp(metformin.copy(paused = true), at(2026, 10, 9, 0), ny).dues)
        assertEquals(emptyList<Long>(), Plan.catchUp(metformin.copy(schedule = Schedule.AsNeeded), at(2026, 10, 9, 0), ny).dues)
    }

    @Test fun catchingUpTwiceWritesNothingTwice() {
        val now = at(2026, 10, 6, 8)
        val again = Plan.catchUp(metformin.copy(caughtUp = now), now + min, ny)
        assertEquals(emptyList<Long>(), again.dues)
    }

    // ---------------------------------------------------------------- snooze and ringing again

    @Test fun snoozeIsMinutesFromNow() {
        val now = at(2026, 10, 6, 8, 3)
        assertEquals(at(2026, 10, 6, 8, 13), Plan.snooze(now, 10))
    }

    @Test fun aSnoozedDoseIsTheNextThingToRing() {
        val now = at(2026, 10, 6, 8, 3)
        val snoozed = dose(at(2026, 10, 6, 8), remindAt = Plan.snooze(now, 10))
        assertEquals(at(2026, 10, 6, 8, 13), Plan.nextWake(listOf(metformin.copy(caughtUp = now)), listOf(snoozed), now, ny))
    }

    @Test fun aSnoozeAcrossMidnightLandsTomorrow() {
        val now = at(2026, 10, 6, 23, 55)
        val wake = Plan.snooze(now, 10)
        assertEquals(at(2026, 10, 7, 0, 5), wake)
    }

    @Test fun ringingAgainStopsAtTheLimitAndAtTheNextDose() {
        val now = at(2026, 10, 6, 8)
        val next = at(2026, 10, 6, 20)
        assertEquals(now + 30 * min, Plan.again(dose(now), now, 30, next))
        assertNull(Plan.again(dose(now, again = Plan.MAX_AGAIN), now, 30, next))
        assertNull(Plan.again(dose(now), now, 0, next))
        // Ringing again at 20:00 would be the 20:00 dose's job.
        assertNull(Plan.again(dose(now), at(2026, 10, 6, 19, 30), 30, next))
    }

    @Test fun theNextWakeIsTheEarliestOfEverything() {
        val now = at(2026, 10, 6, 9)
        val m = metformin.copy(caughtUp = now)
        assertEquals(at(2026, 10, 6, 20), Plan.nextWake(listOf(m), emptyList(), now, ny))
        assertEquals(at(2026, 10, 6, 9, 30), Plan.nextWake(listOf(m), listOf(dose(at(2026, 10, 6, 8), remindAt = at(2026, 10, 6, 9, 30))), now, ny))
        assertEquals(now + min, Plan.nextWake(listOf(m), emptyList(), now, ny, test = now + min))
        assertNull(Plan.nextWake(listOf(m.copy(paused = true)), emptyList(), now, ny))
    }

    @Test fun aDoseTakenEarlyIsNotStillToCome() {
        val now = at(2026, 10, 6, 9)
        val m = metformin.copy(caughtUp = now)
        assertEquals(at(2026, 10, 7, 8), Plan.nextUnlogged(m, now, ny, setOf(at(2026, 10, 6, 20))))
    }

    @Test fun everyFewHoursAcrossMidnightRingsAtNight() {
        val m = metformin.copy(schedule = Schedule.EveryHours(6, LocalDateTime.of(2026, 10, 6, 6, 0)), caughtUp = at(2026, 10, 6, 18, 1))
        assertEquals(at(2026, 10, 7, 0), Plan.nextWake(listOf(m), emptyList(), at(2026, 10, 6, 18, 1), ny))
    }

    // ---------------------------------------------------------------- counting

    @Test fun countingDownAndBack() {
        val m = metformin.copy(left = 10, perDose = 2, warnAt = 8)
        assertEquals(8, Plan.count(m, taken = true))
        assertEquals(12, Plan.count(m, taken = false))
        assertEquals(0, Plan.count(m.copy(left = 1), taken = true))
        assertNull(Plan.count(metformin, taken = true))
        assertTrue(m.copy(left = 8).low)
    }

    @Test fun daysLeftIsAGuessFromTheSchedule() {
        assertEquals(10, Plan.daysLeft(metformin.copy(left = 20)))
        assertEquals(5, Plan.daysLeft(metformin.copy(left = 20, perDose = 2)))
        assertNull(Plan.daysLeft(metformin.copy(left = 20, schedule = Schedule.AsNeeded)))
        assertNull(Plan.daysLeft(metformin))
    }

    // ---------------------------------------------------------------- history

    @Test fun aTallyCountsOnlyScheduledDoses() {
        val doses = listOf(
            dose(at(2026, 10, 5, 8), status = Status.TAKEN),
            dose(at(2026, 10, 5, 20), status = Status.SKIPPED),
            dose(at(2026, 10, 6, 8)),
            dose(at(2026, 10, 6, 12), status = Status.TAKEN, asNeeded = true),
        )
        val t = Plan.tally(doses, at(2026, 10, 5, 0), at(2026, 10, 7, 0))
        assertEquals(Plan.Tally(taken = 1, skipped = 1, notMarked = 1), t)
        assertEquals(3, t.due)
    }

    @Test fun dayMarks() {
        val doses = listOf(
            dose(at(2026, 10, 4, 8), status = Status.TAKEN),
            dose(at(2026, 10, 4, 20), status = Status.TAKEN),
            dose(at(2026, 10, 5, 8), status = Status.TAKEN),
            dose(at(2026, 10, 5, 20)),
        )
        val days = listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5))
        assertEquals(listOf(Plan.Mark.NONE, Plan.Mark.ALL, Plan.Mark.SOME), Plan.marks(doses, days, ny))
    }

    // ---------------------------------------------------------------- export

    @Test fun csvQuotesWhatNeedsIt() {
        assertEquals("Metformin", Export.cell("Metformin"))
        assertEquals("\"1 tablet, with food\"", Export.cell("1 tablet, with food"))
        assertEquals("\"the \"\"blue\"\" one\"", Export.cell("the \"blue\" one"))
        assertEquals("'=SUM(A1)", Export.cell("=SUM(A1)"))
    }

    @Test fun csvHasOneRowPerDoseInOrder() {
        val doses = listOf(
            dose(at(2026, 10, 6, 8), status = Status.TAKEN).copy(marked = at(2026, 10, 6, 8, 4), rang = at(2026, 10, 6, 8)),
            dose(at(2026, 10, 5, 20)),
        )
        val lines = Export.csv(doses, ny).trimEnd().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("Metformin,500 mg,2026-10-05 20:00,not marked,,,no", lines[1])
        assertEquals("Metformin,500 mg,2026-10-06 08:00,taken,2026-10-06 08:04,2026-10-06 08:00,no", lines[2])
    }
}
