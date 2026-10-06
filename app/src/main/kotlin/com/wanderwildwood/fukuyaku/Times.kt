package com.wanderwildwood.fukuyaku

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Times and days, written the way the phone writes them: its 12- or 24-hour clock, its language. */
object Times {

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    private fun clock(context: Context): DateTimeFormatter {
        val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(context), skeleton), locale(context))
    }

    fun time(context: Context, at: Long): String =
        clock(context).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

    fun time(context: Context, t: LocalTime): String = clock(context).format(t)

    /** "Mon 5 Oct", or with the year when it is not this year's. */
    fun day(context: Context, date: LocalDate): String {
        val skeleton = if (date.year == LocalDate.now().year) "EEEdMMM" else "EEEdMMMyyyy"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(context), skeleton), locale(context)).format(date)
    }

    fun date(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()

    /** "Today", "Yesterday", "Tomorrow", or the day itself. */
    fun relativeDay(context: Context, date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> context.getString(R.string.today)
            today.minusDays(1) -> context.getString(R.string.yesterday)
            today.plusDays(1) -> context.getString(R.string.tomorrow)
            else -> day(context, date)
        }
    }

    /** A time, with its day in front when that is not today: "08:00", "Yesterday 20:00". */
    fun whenShort(context: Context, at: Long): String {
        val d = date(at)
        return if (d == LocalDate.now()) time(context, at) else relativeDay(context, d) + " " + time(context, at)
    }
}
