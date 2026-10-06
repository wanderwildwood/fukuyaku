package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.data.Days
import com.wanderwildwood.fukuyaku.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {

    private val ny = ZoneId.of("America/New_York")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0, zone: ZoneId = ny): Instant =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant()

    private fun local(i: Instant) = i.atZone(ny).toLocalDateTime().toString()

    private val twiceDaily = Schedule.AtTimes(listOf(LocalTime.of(20, 0), LocalTime.of(8, 0)))

    @Test fun timesComeInOrderAcrossDays() {
        val got = twiceDaily.between(at(2026, 10, 5, 12), at(2026, 10, 7, 12), ny).map(::local)
        assertEquals(listOf("2026-10-05T20:00", "2026-10-06T08:00", "2026-10-06T20:00", "2026-10-07T08:00"), got)
    }

    @Test fun theStartIsExcludedAndTheEndIncluded() {
        val got = twiceDaily.between(at(2026, 10, 6, 8), at(2026, 10, 6, 20), ny).map(::local)
        assertEquals(listOf("2026-10-06T20:00"), got)
    }

    @Test fun midnightBelongsToTheNewDay() {
        val s = Schedule.AtTimes(listOf(LocalTime.MIDNIGHT), Days.OnDays(setOf(DayOfWeek.WEDNESDAY)))
        // 2026-10-07 is a Wednesday: its midnight is the one between Tuesday and Wednesday.
        val got = s.between(at(2026, 10, 6, 0, 1), at(2026, 10, 9, 0), ny).map(::local)
        assertEquals(listOf("2026-10-07T00:00"), got)
        assertEquals(at(2026, 10, 7, 0), s.next(at(2026, 10, 6, 23, 59), ny))
    }

    @Test fun nextAfterTheLastDoseOfTheDayIsTomorrowMorning() {
        assertEquals(at(2026, 10, 7, 8), twiceDaily.next(at(2026, 10, 6, 20), ny))
        assertEquals(at(2026, 10, 7, 8), twiceDaily.next(at(2026, 10, 6, 23, 59), ny))
    }

    // ---------------------------------------------------------------- clock changes, New York

    @Test fun springForwardMovesASkippedTimeToWhenTheClockComesBack() {
        // 2026-03-08: 02:00 EST jumps to 03:00 EDT. 02:30 does not exist that night.
        val s = Schedule.AtTimes(listOf(LocalTime.of(2, 30)))
        val got = s.between(at(2026, 3, 7, 12), at(2026, 3, 9, 12), ny)
        assertEquals(listOf("2026-03-08T03:30", "2026-03-09T02:30"), got.map(::local))
        // Exactly one dose that night, an hour after the time that never came.
        assertEquals(Duration.ofHours(23), Duration.between(got[0], got[1]))
    }

    @Test fun springForwardKeepsMorningDosesAtTheirClockTime() {
        val got = twiceDaily.between(at(2026, 3, 7, 12), at(2026, 3, 9, 12), ny)
        assertEquals(listOf("2026-03-07T20:00", "2026-03-08T08:00", "2026-03-08T20:00", "2026-03-09T08:00"), got.map(::local))
        // The night the clocks go forward is an hour short.
        assertEquals(Duration.ofHours(11), Duration.between(got[0], got[1]))
    }

    @Test fun fallBackRingsARepeatedTimeOnce() {
        // 2026-11-01: 02:00 EDT falls back to 01:00 EST. 01:30 happens twice; one dose.
        val s = Schedule.AtTimes(listOf(LocalTime.of(1, 30)))
        val got = s.between(at(2026, 10, 31, 12), at(2026, 11, 2, 12), ny)
        assertEquals(2, got.size)
        assertEquals("2026-11-01T01:30", local(got[0]))
        // The first of the two 01:30s, still on summer time.
        assertEquals(-4, got[0].atZone(ny).offset.totalSeconds / 3600)
        assertEquals(Duration.ofHours(25), Duration.between(got[0], got[1]))
    }

    @Test fun everyHoursCountsOnTheClockThroughFallBack() {
        val s = Schedule.EveryHours(8, LocalDateTime.of(2026, 10, 31, 6, 0))
        val got = s.between(at(2026, 10, 31, 0), at(2026, 11, 2, 0), ny)
        assertEquals(
            listOf(
                "2026-10-31T06:00", "2026-10-31T14:00", "2026-10-31T22:00",
                "2026-11-01T06:00", "2026-11-01T14:00", "2026-11-01T22:00",
            ),
            got.map(::local),
        )
        // The gap across the change is nine real hours; the rest are eight.
        assertEquals(Duration.ofHours(9), Duration.between(got[2], got[3]))
        assertEquals(Duration.ofHours(8), Duration.between(got[3], got[4]))
    }

    @Test fun everyHoursCountsOnTheClockThroughSpringForward() {
        val s = Schedule.EveryHours(12, LocalDateTime.of(2026, 3, 7, 21, 0))
        val got = s.between(at(2026, 3, 7, 0), at(2026, 3, 9, 0), ny)
        assertEquals(listOf("2026-03-07T21:00", "2026-03-08T09:00", "2026-03-08T21:00"), got.map(::local))
        assertEquals(Duration.ofHours(11), Duration.between(got[0], got[1]))
    }

    @Test fun everyHoursThatDoNotDivideADayWalkAcrossDays() {
        val s = Schedule.EveryHours(5, LocalDateTime.of(2026, 10, 5, 8, 0))
        val got = s.between(at(2026, 10, 5, 0), at(2026, 10, 6, 12), ny).map(::local)
        assertEquals(
            listOf("2026-10-05T08:00", "2026-10-05T13:00", "2026-10-05T18:00", "2026-10-05T23:00", "2026-10-06T04:00", "2026-10-06T09:00"),
            got,
        )
    }

    @Test fun everyHoursFarFromTheFirstDoseStillLandsOnTheGrid() {
        val s = Schedule.EveryHours(6, LocalDateTime.of(2026, 1, 1, 0, 0))
        // Some 300 days on, past a spring change: the clock times stay 00/06/12/18.
        val next = s.next(at(2026, 10, 27, 7), ny)!!
        assertEquals("2026-10-27T12:00", local(next))
    }

    @Test fun nothingIsDueBeforeTheFirstDose() {
        val s = Schedule.EveryHours(4, LocalDateTime.of(2026, 10, 6, 9, 0))
        assertEquals(at(2026, 10, 6, 9), s.next(at(2026, 10, 1, 0), ny))
        assertEquals(emptyList<Instant>(), s.between(at(2026, 10, 1, 0), at(2026, 10, 6, 8), ny))
    }

    // ---------------------------------------------------------------- days

    @Test fun someDaysOfTheWeek() {
        val s = Schedule.AtTimes(listOf(LocalTime.of(9, 0)), Days.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
        // From Tuesday 2026-10-06, the next is Friday the 9th, then Monday the 12th.
        assertEquals(at(2026, 10, 9, 9), s.next(at(2026, 10, 6, 10), ny))
        assertEquals(at(2026, 10, 12, 9), s.next(at(2026, 10, 9, 9), ny))
    }

    @Test fun everyOtherDayCountsFromItsStart() {
        val s = Schedule.AtTimes(listOf(LocalTime.of(9, 0)), Days.EveryN(2, LocalDate.of(2026, 10, 6)))
        val got = s.between(at(2026, 10, 1, 0), at(2026, 10, 11, 0), ny).map(::local)
        assertEquals(listOf("2026-10-06T09:00", "2026-10-08T09:00", "2026-10-10T09:00"), got)
    }

    @Test fun everyFewDaysFindsTheNextEvenFarAhead() {
        val s = Schedule.AtTimes(listOf(LocalTime.of(9, 0)), Days.EveryN(30, LocalDate.of(2026, 10, 6)))
        assertEquals(at(2026, 11, 5, 9), s.next(at(2026, 10, 6, 9), ny))
    }

    @Test fun everyFewDaysThatStartLaterWaitForTheirStart() {
        val s = Schedule.AtTimes(listOf(LocalTime.of(9, 0)), Days.EveryN(3, LocalDate.of(2026, 12, 1)))
        assertEquals(at(2026, 12, 1, 9), s.next(at(2026, 10, 6, 10), ny))
    }

    @Test fun asNeededNeverComesDue() {
        assertNull(Schedule.AsNeeded.next(at(2026, 10, 6, 9), ny))
        assertEquals(emptyList<Instant>(), Schedule.AsNeeded.between(at(2026, 10, 6, 0), at(2026, 10, 9, 0), ny))
    }

    @Test fun aTimeZoneChangeKeepsTheClockTime() {
        // Flown to Los Angeles: 08:00 is 08:00 there.
        val la = ZoneId.of("America/Los_Angeles")
        assertEquals(at(2026, 10, 7, 8, zone = la), twiceDaily.next(at(2026, 10, 6, 21, zone = la), la))
    }

    // ---------------------------------------------------------------- storage

    @Test fun everyScheduleSurvivesBeingStored() {
        val all = listOf(
            twiceDaily,
            Schedule.AtTimes(listOf(LocalTime.of(7, 5)), Days.OnDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY))),
            Schedule.AtTimes(listOf(LocalTime.of(22, 0)), Days.EveryN(3, LocalDate.of(2026, 10, 6))),
            Schedule.EveryHours(6, LocalDateTime.of(2026, 10, 6, 6, 30)),
            Schedule.AsNeeded,
        )
        for (s in all) {
            val back = Schedule.decode(Schedule.encode(s))
            // Times come back sorted, which is how they are kept.
            val expected = if (s is Schedule.AtTimes) s.copy(times = s.times.sorted()) else s
            assertEquals(expected, back)
        }
        assertNull(Schedule.decode("Q|nonsense"))
        assertNull(Schedule.decode("T|25:99|E"))
    }

    @Test fun perDay() {
        assertEquals(2.0, twiceDaily.perDay(), 1e-9)
        assertEquals(3.0, Schedule.EveryHours(8, LocalDateTime.of(2026, 1, 1, 0, 0)).perDay(), 1e-9)
        assertEquals(0.5, Schedule.AtTimes(listOf(LocalTime.NOON), Days.EveryN(2, LocalDate.of(2026, 1, 1))).perDay(), 1e-9)
        assertEquals(2.0 / 7, Schedule.AtTimes(listOf(LocalTime.NOON), Days.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))).perDay(), 1e-9)
    }
}
