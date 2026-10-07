package com.wanderwildwood.fukuyaku.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The log written out for somebody else to read: as plain text to share into a note or a
 * message, or as CSV to save as a file and open in a spreadsheet.
 */
object Export {

    /**
     * One row per dose, newest last. The column names and the status words are data, not
     * interface, and stay in English so a spreadsheet made from one phone reads the same as
     * one made from another. [doctors] is each medicine's doctor as it is set now, by id.
     */
    fun csv(doses: List<Dose>, zone: ZoneId, doctors: Map<Long, String> = emptyMap()): String = buildString {
        append("medicine,amount,due,status,marked,reminder_rang,as_needed,doctor\r\n")
        for (d in doses.sortedWith(compareBy({ it.due }, { it.id }))) {
            append(cell(d.name)).append(',')
            append(cell(d.amount)).append(',')
            append(stamp(d.due, zone)).append(',')
            append(
                when (d.status) {
                    Status.TAKEN -> "taken"
                    Status.SKIPPED -> "skipped"
                    Status.OPEN -> "not marked"
                },
            ).append(',')
            append(d.marked?.let { stamp(it, zone) } ?: "").append(',')
            append(d.rang?.let { stamp(it, zone) } ?: "").append(',')
            append(if (d.asNeeded) "yes" else "no").append(',')
            append(cell(doctors[d.medicineId].orEmpty()))
            append("\r\n")
        }
    }

    /** The words a text export is written in, supplied by whoever knows the language. */
    interface Words {
        fun title(): String
        fun doctors(): String
        fun day(date: LocalDate): String
        fun time(at: Long): String
        fun line(d: Dose): String
    }

    /**
     * Days newest first, and within a day in the order they were due. [doctors] (a medicine
     * and who prescribed it, each as it should read) comes first, under its own heading, when
     * any medicine has one.
     */
    fun text(doses: List<Dose>, zone: ZoneId, words: Words, doctors: List<Pair<String, String>> = emptyList()): String = buildString {
        append(words.title()).append('\n')
        if (doctors.isNotEmpty()) {
            append('\n').append(words.doctors()).append('\n')
            for ((medicine, doctor) in doctors) append(medicine).append(" — ").append(doctor).append('\n')
        }
        doses.groupBy { Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .forEach { (day, those) ->
                append('\n').append(words.day(day)).append('\n')
                for (d in those.sortedWith(compareBy({ it.due }, { it.id }))) {
                    append(words.time(d.due)).append("  ").append(words.line(d)).append('\n')
                }
            }
    }

    /** Each medicine's doctor, by id, for [csv]: the name, or the number when there is no name. */
    fun doctorsById(medicines: List<Medicine>): Map<Long, String> =
        medicines.filter { it.doctor.isNotBlank() }.associate { it.id to it.doctor }

    /** For [text]: "Lisinopril, 10 mg" and "Dr Ada Whitlock, 555 0100", for each that has one. */
    fun doctorLines(medicines: List<Medicine>): List<Pair<String, String>> =
        medicines.filter { it.doctor.isNotBlank() }.map { m ->
            m.label to listOf(m.doctorName, m.doctorNumber).filter { it.isNotBlank() }.joinToString(", ")
        }

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun stamp(at: Long, zone: ZoneId): String = STAMP.format(Instant.ofEpochMilli(at).atZone(zone))

    /**
     * A CSV cell, quoted when it holds a comma, a quote or a line break. A name a spreadsheet
     * would take for a formula (it starts with = + - or @) gets an apostrophe in front, which
     * spreadsheets read as "this is text".
     */
    internal fun cell(raw: String): String {
        val s = if (raw.firstOrNull() in FORMULA) "'$raw" else raw
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private val FORMULA = setOf('=', '+', '-', '@')
}
