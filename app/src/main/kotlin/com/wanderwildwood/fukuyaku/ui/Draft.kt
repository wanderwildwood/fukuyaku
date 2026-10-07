package com.wanderwildwood.fukuyaku.ui

import androidx.compose.runtime.saveable.Saver
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.data.Days
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class Kind { TIMES, HOURS, NEEDED }
enum class DayKind { EVERY, WEEK, EVERY_N }

/**
 * A medicine while it is being written: every field as typed, numbers still as text, so a
 * half-typed form survives the app being put away and brought back.
 */
data class Draft(
    val name: String = "",
    val amount: String = "",
    val kind: Kind = Kind.TIMES,
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val dayKind: DayKind = DayKind.EVERY,
    val week: Set<DayOfWeek> = emptySet(),
    val everyN: String = "2",
    val from: LocalDate = LocalDate.now(),
    val hours: String = "8",
    val hoursFrom: LocalTime = LocalTime.of(8, 0),
    val hoursFromDate: LocalDate = LocalDate.now(),
    val counting: Boolean = false,
    val left: String = "",
    val perDose: String = "1",
    val warnAt: String = "",
    val pharmacyName: String = "",
    val pharmacyNumber: String = "",
    val pharmacyContact: String = "",
    val doctorName: String = "",
    val doctorNumber: String = "",
    val doctorContact: String = "",
    val notes: String = "",
    val paused: Boolean = false,
) {
    /** What stops it being saved, as a string resource; null when nothing does. */
    fun problem(): Int? = when {
        name.isBlank() -> R.string.edit_needs_name
        kind == Kind.TIMES && times.isEmpty() -> R.string.edit_needs_time
        kind == Kind.TIMES && dayKind == DayKind.WEEK && week.isEmpty() -> R.string.edit_needs_day
        kind == Kind.TIMES && dayKind == DayKind.EVERY_N && (everyN.toIntOrNull() ?: 0) !in 1..365 -> R.string.edit_needs_every_n
        kind == Kind.HOURS && (hours.toIntOrNull() ?: 0) !in 1..168 -> R.string.edit_needs_hours
        counting && left.toIntOrNull() == null -> R.string.edit_needs_left
        counting && (perDose.toIntOrNull() ?: 0) < 1 -> R.string.edit_needs_per_dose
        else -> null
    }

    fun schedule(): Schedule = when (kind) {
        Kind.NEEDED -> Schedule.AsNeeded
        Kind.HOURS -> Schedule.EveryHours(hours.toInt(), hoursFromDate.atTime(hoursFrom))
        Kind.TIMES -> Schedule.AtTimes(
            times.distinct().sorted(),
            when (dayKind) {
                DayKind.EVERY -> Days.Every
                DayKind.WEEK -> Days.OnDays(week)
                DayKind.EVERY_N -> Days.EveryN(everyN.toInt(), from)
            },
        )
    }

    /** The medicine this describes, built over [base] so what the form does not show is kept. */
    fun toMedicine(base: Medicine?): Medicine {
        val m = base ?: Medicine(name = "", schedule = Schedule.AsNeeded)
        return m.copy(
            name = name.trim(),
            amount = amount.trim(),
            schedule = schedule(),
            left = if (counting) left.toIntOrNull() else null,
            perDose = if (counting) perDose.toIntOrNull()?.coerceAtLeast(1) ?: 1 else 1,
            warnAt = if (counting) warnAt.toIntOrNull() else null,
            pharmacyName = pharmacyName.trim(),
            pharmacyNumber = pharmacyNumber.trim(),
            pharmacyContact = pharmacyContact,
            doctorName = doctorName.trim(),
            doctorNumber = doctorNumber.trim(),
            doctorContact = doctorContact,
            notes = notes.trim(),
            paused = paused,
        )
    }

    companion object {
        fun of(m: Medicine?): Draft {
            if (m == null) return Draft()
            var d = Draft(
                name = m.name,
                amount = m.amount,
                counting = m.left != null,
                left = m.left?.toString().orEmpty(),
                perDose = m.perDose.toString(),
                warnAt = m.warnAt?.toString().orEmpty(),
                pharmacyName = m.pharmacyName,
                pharmacyNumber = m.pharmacyNumber,
                pharmacyContact = m.pharmacyContact,
                doctorName = m.doctorName,
                doctorNumber = m.doctorNumber,
                doctorContact = m.doctorContact,
                notes = m.notes,
                paused = m.paused,
            )
            d = when (val s = m.schedule) {
                is Schedule.AsNeeded -> d.copy(kind = Kind.NEEDED)
                is Schedule.EveryHours -> d.copy(
                    kind = Kind.HOURS,
                    hours = s.hours.toString(),
                    hoursFrom = s.from.toLocalTime(),
                    hoursFromDate = s.from.toLocalDate(),
                )
                is Schedule.AtTimes -> when (val days = s.days) {
                    is Days.Every -> d.copy(kind = Kind.TIMES, times = s.times, dayKind = DayKind.EVERY)
                    is Days.OnDays -> d.copy(kind = Kind.TIMES, times = s.times, dayKind = DayKind.WEEK, week = days.days)
                    is Days.EveryN -> d.copy(kind = Kind.TIMES, times = s.times, dayKind = DayKind.EVERY_N, everyN = days.n.toString(), from = days.from)
                }
            }
            return d
        }

        private const val SEP = "\u0001"

        /** Kept across the app being put away, as one string of its fields. */
        val saver: Saver<Draft, String> = Saver(
            save = { d ->
                listOf(
                    d.name, d.amount, d.kind.name,
                    d.times.joinToString(",") { it.toString() },
                    d.dayKind.name, d.week.joinToString(",") { it.value.toString() }, d.everyN, d.from.toString(),
                    d.hours, d.hoursFrom.toString(), d.hoursFromDate.toString(),
                    d.counting.toString(), d.left, d.perDose, d.warnAt,
                    d.pharmacyName, d.pharmacyNumber, d.pharmacyContact, d.notes, d.paused.toString(),
                    d.doctorName, d.doctorNumber, d.doctorContact,
                ).joinToString(SEP)
            },
            restore = { s ->
                runCatching {
                    val f = s.split(SEP)
                    Draft(
                        name = f[0], amount = f[1], kind = Kind.valueOf(f[2]),
                        times = f[3].split(',').filter { it.isNotEmpty() }.map { LocalTime.parse(it) },
                        dayKind = DayKind.valueOf(f[4]),
                        week = f[5].split(',').filter { it.isNotEmpty() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
                        everyN = f[6], from = LocalDate.parse(f[7]),
                        hours = f[8], hoursFrom = LocalTime.parse(f[9]), hoursFromDate = LocalDate.parse(f[10]),
                        counting = f[11].toBoolean(), left = f[12], perDose = f[13], warnAt = f[14],
                        pharmacyName = f[15], pharmacyNumber = f[16], pharmacyContact = f[17], notes = f[18],
                        paused = f[19].toBoolean(),
                        doctorName = f.getOrElse(20) { "" }, doctorNumber = f.getOrElse(21) { "" }, doctorContact = f.getOrElse(22) { "" },
                    )
                }.getOrNull()
            },
        )
    }
}
