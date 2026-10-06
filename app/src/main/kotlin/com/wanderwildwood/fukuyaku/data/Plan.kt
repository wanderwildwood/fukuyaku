package com.wanderwildwood.fukuyaku.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The decisions about when things ring, kept apart from Android so they can be tested on
 * their own. Nothing in here reads a clock: every function is told what time it is.
 */
object Plan {

    /** A dose not marked rings again at most this many times. */
    const val MAX_AGAIN = 3

    /**
     * A dose that came due while the app could not ring still rings when it next can, but only
     * the newest of each medicine, and only within this long. Older ones are in the log as not
     * marked, which is the truth about them; ringing for yesterday's 08:00 now would not be.
     */
    const val RING_WITHIN_MS = 12 * 60 * 60 * 1000L

    data class CaughtUp(val dues: List<Long>, val rings: Long?)

    /** What came due for [med] since it was last caught up, and which of those rings now. */
    fun catchUp(med: Medicine, now: Long, zone: ZoneId): CaughtUp {
        if (!med.reminds || now <= med.caughtUp) return CaughtUp(emptyList(), null)
        val dues = med.schedule
            .between(Instant.ofEpochMilli(med.caughtUp), Instant.ofEpochMilli(now), zone)
            .map { it.toEpochMilli() }
        val newest = dues.lastOrNull()?.takeIf { now - it <= RING_WITHIN_MS }
        return CaughtUp(dues, newest)
    }

    /**
     * When a dose that has just rung at [now] rings again if it is still not marked: never when
     * that is switched off, never more than [MAX_AGAIN] times, and never once the medicine's
     * next dose is due, which rings for itself.
     */
    fun again(dose: Dose, now: Long, againMinutes: Int, nextDue: Long?): Long? {
        if (againMinutes <= 0 || dose.again >= MAX_AGAIN) return null
        val at = now + againMinutes * 60_000L
        if (nextDue != null && at >= nextDue) return null
        return at
    }

    /** A snooze: the same dose, rung again [minutes] from [now]. */
    fun snooze(now: Long, minutes: Int): Long = now + minutes * 60_000L

    /** The next due time of [med] after [now], or null for none. */
    fun nextDue(med: Medicine, now: Long, zone: ZoneId): Long? {
        if (!med.reminds) return null
        return med.schedule.next(Instant.ofEpochMilli(maxOf(now, med.caughtUp)), zone)?.toEpochMilli()
    }

    /**
     * The next due time of [med] after [now] that is not already in the log ([logged] holds the
     * due times that are, for this medicine): a dose taken early is not still to come.
     */
    fun nextUnlogged(med: Medicine, now: Long, zone: ZoneId, logged: Set<Long>): Long? {
        var t = nextDue(med, now, zone) ?: return null
        repeat(32) {
            if (t !in logged) return t
            t = med.schedule.next(Instant.ofEpochMilli(t), zone)?.toEpochMilli() ?: return null
        }
        return null
    }

    /**
     * The next moment anything has to ring: a medicine coming due, a dose snoozed or ringing
     * again, or the test reminder. A time already past comes back as it is; whoever sets the
     * alarm sets it for at once.
     */
    fun nextWake(medicines: List<Medicine>, open: List<Dose>, now: Long, zone: ZoneId, test: Long? = null): Long? {
        val dues = medicines.mapNotNull { nextDue(it, now, zone) }
        val again = open.mapNotNull { it.remindAt }
        return (dues + again + listOfNotNull(test)).minOrNull()
    }

    /** Roughly how many whole days a counted supply lasts at the medicine's schedule; null when unknown. */
    fun daysLeft(med: Medicine): Int? {
        val left = med.left ?: return null
        val perDay = med.schedule.perDay() * med.perDose.coerceAtLeast(1)
        if (perDay <= 0.0) return null
        return (left / perDay).toInt()
    }

    /** What is left after a dose is marked taken ([taken] true) or that mark is taken back. */
    fun count(med: Medicine, taken: Boolean): Int? {
        val left = med.left ?: return null
        val step = med.perDose.coerceAtLeast(1)
        return if (taken) maxOf(0, left - step) else left + step
    }

    /** How the scheduled doses due in a stretch of time went. As-needed doses are not counted. */
    data class Tally(val taken: Int, val skipped: Int, val notMarked: Int) {
        val due: Int get() = taken + skipped + notMarked
    }

    fun tally(doses: List<Dose>, from: Long, until: Long): Tally {
        var taken = 0
        var skipped = 0
        var open = 0
        for (d in doses) {
            if (d.asNeeded || d.due < from || d.due >= until) continue
            when (d.status) {
                Status.TAKEN -> taken++
                Status.SKIPPED -> skipped++
                Status.OPEN -> open++
            }
        }
        return Tally(taken, skipped, open)
    }

    /** A day in the history strip: nothing was due, every dose was taken, or not every one. */
    enum class Mark { NONE, ALL, SOME }

    fun marks(doses: List<Dose>, days: List<LocalDate>, zone: ZoneId): List<Mark> {
        val byDay = doses.filter { !it.asNeeded }.groupBy { Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() }
        return days.map { day ->
            val those = byDay[day].orEmpty()
            when {
                those.isEmpty() -> Mark.NONE
                those.all { it.status == Status.TAKEN } -> Mark.ALL
                else -> Mark.SOME
            }
        }
    }
}
