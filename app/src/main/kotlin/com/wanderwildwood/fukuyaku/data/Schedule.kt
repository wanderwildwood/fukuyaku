package com.wanderwildwood.fukuyaku.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Which days a medicine taken at set times is taken on. */
sealed interface Days {
    fun includes(date: LocalDate): Boolean

    data object Every : Days {
        override fun includes(date: LocalDate) = true
    }

    /** Only on these days of the week. */
    data class OnDays(val days: Set<DayOfWeek>) : Days {
        override fun includes(date: LocalDate) = date.dayOfWeek in days
    }

    /** Every [n] days, counting from [from]: every other day is n = 2. */
    data class EveryN(val n: Int, val from: LocalDate) : Days {
        override fun includes(date: LocalDate): Boolean {
            if (n < 1) return false
            val d = ChronoUnit.DAYS.between(from, date)
            return d >= 0 && d % n == 0L
        }
    }
}

/**
 * When a medicine is due.
 *
 * Every time here is a time on the clock, in whichever zone the phone is in when it is asked:
 * 08:00 stays 08:00 across a clock change and after a flight. That is also true of
 * [EveryHours], which counts its hours on the clock from the first dose, so on the two nights a
 * year the clocks change one gap is an hour longer or shorter, and the doses after it are at the
 * same clock times as before.
 *
 * Where the clock skips a time (02:30 on the spring night), the dose is due when the clock
 * comes back, an hour on; where it repeats one (01:30 in the autumn), it is due the first time.
 */
sealed interface Schedule {

    /** At these times of day, on the [days] chosen. */
    data class AtTimes(val times: List<LocalTime>, val days: Days = Days.Every) : Schedule

    /** Every [hours] hours, on the clock, from [from]. */
    data class EveryHours(val hours: Int, val from: LocalDateTime) : Schedule

    /** No reminders; doses are written down when they are taken. */
    data object AsNeeded : Schedule

    /** Due times after [after] and at or before [until], earliest first. */
    fun between(after: Instant, until: Instant, zone: ZoneId): List<Instant> {
        if (!until.isAfter(after)) return emptyList()
        return when (this) {
            is AsNeeded -> emptyList()
            is AtTimes -> {
                if (times.isEmpty()) return emptyList()
                val sorted = times.distinct().sorted()
                val out = sortedSetOf<Instant>()
                var date = after.atZone(zone).toLocalDate().minusDays(1)
                val last = until.atZone(zone).toLocalDate().plusDays(1)
                while (!date.isAfter(last)) {
                    if (days.includes(date)) {
                        for (t in sorted) {
                            val at = ZonedDateTime.of(date, t, zone).toInstant()
                            if (at.isAfter(after) && !at.isAfter(until)) out += at
                        }
                    }
                    date = date.plusDays(1)
                }
                out.toList()
            }
            is EveryHours -> {
                if (hours < 1) return emptyList()
                val out = sortedSetOf<Instant>()
                val start = from.atZone(zone).toInstant()
                // Hours on the clock and hours elapsed differ by at most the zone's offset swing,
                // so starting two steps early can never skip a dose.
                val k0 = maxOf(0L, ChronoUnit.HOURS.between(start, after) / hours - 2)
                var k = k0
                while (true) {
                    val at = from.plusHours(k * hours).atZone(zone).toInstant()
                    if (at.isAfter(until)) break
                    if (at.isAfter(after)) out += at
                    k++
                }
                out.toList()
            }
        }
    }

    /** The first due time after [after], or null when there is none (as needed, nothing chosen). */
    fun next(after: Instant, zone: ZoneId): Instant? {
        // A schedule that has not started yet: look from where it starts, however far off.
        val begins = when (this) {
            is EveryHours -> from.atZone(zone).toInstant().minusMillis(1)
            is AtTimes -> (days as? Days.EveryN)?.from?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)
            is AsNeeded -> null
        }
        if (begins != null && begins.isAfter(after)) return next(begins, zone)
        val horizonDays = when (this) {
            is AsNeeded -> return null
            is EveryHours -> (hours / 24L) + 3
            is AtTimes -> when (val d = days) {
                is Days.EveryN -> d.n.toLong() + 3
                else -> 9L
            }
        }
        // Look a little ahead first, which is nearly always enough, then as far as can matter.
        val near = between(after, after.plus(2, ChronoUnit.DAYS), zone).firstOrNull()
        if (near != null) return near
        return between(after, after.plus(horizonDays, ChronoUnit.DAYS), zone).firstOrNull()
    }

    /** How many doses a day, on average: for guessing when a counted supply runs out. */
    fun perDay(): Double = when (this) {
        is AsNeeded -> 0.0
        is EveryHours -> if (hours < 1) 0.0 else 24.0 / hours
        is AtTimes -> {
            val n = times.distinct().size.toDouble()
            when (val d = days) {
                is Days.Every -> n
                is Days.OnDays -> n * d.days.size / 7.0
                is Days.EveryN -> if (d.n < 1) 0.0 else n / d.n
            }
        }
    }

    companion object {
        /**
         * Stored as one line of text, so the format can be read in a database browser and
         * survives a schema that never has to change for it:
         * `T|08:00,20:00|E`, `T|08:00|W1,3,5` (ISO day numbers), `T|08:00|N2@2026-10-06`,
         * `H|8|2026-10-06T08:00`, `A`.
         */
        fun encode(s: Schedule): String = when (s) {
            is AsNeeded -> "A"
            is EveryHours -> "H|${s.hours}|${s.from}"
            is AtTimes -> {
                val times = s.times.distinct().sorted().joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }
                val days = when (val d = s.days) {
                    is Days.Every -> "E"
                    is Days.OnDays -> "W" + d.days.map { it.value }.sorted().joinToString(",")
                    is Days.EveryN -> "N${d.n}@${d.from}"
                }
                "T|$times|$days"
            }
        }

        /** Null for a line this version cannot read, rather than a guess at it. */
        fun decode(text: String): Schedule? = runCatching {
            val parts = text.split('|')
            when (parts[0]) {
                "A" -> AsNeeded
                "H" -> EveryHours(parts[1].toInt(), LocalDateTime.parse(parts[2]))
                "T" -> {
                    val times = if (parts[1].isEmpty()) emptyList() else parts[1].split(',').map { LocalTime.parse(it) }
                    val d = parts[2]
                    val days = when {
                        d == "E" -> Days.Every
                        d.startsWith("W") -> Days.OnDays(
                            d.drop(1).split(',').filter { it.isNotEmpty() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
                        )
                        d.startsWith("N") -> {
                            val (n, from) = d.drop(1).split('@')
                            Days.EveryN(n.toInt(), LocalDate.parse(from))
                        }
                        else -> return null
                    }
                    AtTimes(times, days)
                }
                else -> null
            }
        }.getOrNull()
    }
}
