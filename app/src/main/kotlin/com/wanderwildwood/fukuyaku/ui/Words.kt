package com.wanderwildwood.fukuyaku.ui

import android.content.Context
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.data.Days
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Export
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Status
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** How the app says a schedule, a dose's state and a supply in words. */
object Words {

    fun dayName(context: Context, d: DayOfWeek): String =
        d.getDisplayName(TextStyle.SHORT, context.resources.configuration.locales[0])

    /** "Every day at 08:00 and 20:00", "Every 8 hours from 06:00", "When needed". */
    fun schedule(context: Context, s: Schedule): String = when (s) {
        is Schedule.AsNeeded -> context.getString(R.string.when_needed)
        is Schedule.EveryHours -> context.getString(
            R.string.summary_every_hours,
            s.hours.toString(),
            Times.time(context, s.from.toLocalTime()),
        )
        is Schedule.AtTimes -> {
            val times = s.times.sorted().joinToString(", ") { Times.time(context, it) }
            when (val d = s.days) {
                is Days.Every -> context.getString(R.string.summary_every_day, times)
                is Days.OnDays -> context.getString(
                    R.string.summary_on_days,
                    DayOfWeek.entries.filter { it in d.days }.joinToString(", ") { dayName(context, it) },
                    times,
                )
                is Days.EveryN -> context.getString(R.string.summary_every_n_days, d.n.toString(), times)
            }
        }
    }

    /** The second line of a medicine in the list: what is left, and about how long it lasts. */
    fun supply(context: Context, m: Medicine): String? {
        val left = m.left ?: return null
        val count = context.resources.getQuantityString(R.plurals.left, left, left.toString())
        val days = Plan.daysLeft(m)
        val lasts = days?.let { context.resources.getQuantityString(R.plurals.about_days, it, it.toString()) }
        return listOfNotNull(count, lasts).joinToString(" · ")
    }

    /** What happened to a dose, in a few words. */
    fun status(context: Context, d: Dose, now: Long, underItsDay: Boolean = false): String = when (d.status) {
        Status.TAKEN -> {
            val at = d.marked ?: d.due
            // Under a day's heading, "Yesterday" again beside a time would say the day twice.
            val written = if (underItsDay) absolute(context, d.due, at) else Times.whenShort(context, at)
            if (d.asNeeded) context.getString(R.string.status_taken_as_needed)
            else context.getString(R.string.status_taken_at, written)
        }
        Status.SKIPPED -> context.getString(R.string.status_skipped)
        Status.OPEN -> when {
            d.remindAt != null && d.remindAt > now -> context.getString(R.string.status_rings_again, Times.time(context, d.remindAt))
            d.rang == null -> context.getString(R.string.status_not_marked_no_reminder)
            d.rang - d.due > LATE_MS -> context.getString(R.string.status_not_marked_late, Times.whenShort(context, d.rang))
            else -> context.getString(R.string.status_not_marked)
        }
    }

    /** A reminder that rang this long after its time is said to have rung late. */
    const val LATE_MS = 10 * 60_000L

    /** The words a text export is written in. */
    fun export(context: Context): Export.Words = object : Export.Words {
        override fun title() = context.getString(R.string.export_title)
        override fun day(date: LocalDate) = Times.day(context, date)
        override fun time(at: Long) = Times.time(context, at)
        override fun line(d: Dose): String = d.label + " — " + when (d.status) {
            Status.TAKEN ->
                if (d.asNeeded) context.getString(R.string.status_taken_as_needed)
                else context.getString(R.string.status_taken_at, absolute(context, d.due, d.marked ?: d.due))
            Status.SKIPPED -> context.getString(R.string.status_skipped)
            Status.OPEN -> context.getString(R.string.status_not_marked)
        }
    }

    /** A time under a day's heading: the time alone, or with its own day when that differs. */
    private fun absolute(context: Context, under: Long, at: Long): String =
        if (Times.date(under) == Times.date(at)) Times.time(context, at)
        else Times.day(context, Times.date(at)) + " " + Times.time(context, at)
}
