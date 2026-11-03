package com.wanderwildwood.fukuyaku.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant

/**
 * The backup: the medicines with their schedules, the whole dose log and the few choices the
 * app keeps, as one plain JSON file written and read through the system's file picker. It goes
 * where the person puts it and nowhere else. Field names are data, not interface, and stay in
 * English.
 *
 * Not in the file: the first-start notice, the DuraSpeed answer, a waiting test reminder, and the
 * reminder sound, which is a choice made in the system's own notification settings.
 *
 * [read] checks the whole file and throws a [BackupException] for anything it cannot use; only
 * then does the caller touch the store, in one transaction.
 */
object Backup {

    const val FORMAT = "fukuyaku-backup"
    const val VERSION = 1

    /** The choices worth carrying to another phone. */
    data class Choices(
        val snoozeMinutes: Int = 10,
        val againMinutes: Int = 30,
        val fullScreen: Boolean = true,
        val asAlarmClock: Boolean = true,
        val onLockScreen: Boolean = false,
    )

    data class Contents(val medicines: List<Medicine>, val doses: List<Dose>, val choices: Choices)

    /** Why a file was refused, as a kind the screen turns into a plain sentence. */
    class BackupException(val kind: Kind, message: String = kind.name) : Exception(message) {
        enum class Kind { NOT_A_BACKUP, NEWER, DAMAGED }
    }

    fun write(medicines: List<Medicine>, doses: List<Dose>, choices: Choices, written: Instant = Instant.now()): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("written", written.toString())
        root.put("choices", JSONObject().apply {
            put("snooze_minutes", choices.snoozeMinutes)
            put("again_minutes", choices.againMinutes)
            put("full_screen", choices.fullScreen)
            put("alarm_clock", choices.asAlarmClock)
            put("lock_screen", choices.onLockScreen)
        })
        root.put("medicines", JSONArray().apply {
            for (m in medicines) put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("amount", m.amount)
                put("schedule", Schedule.encode(m.schedule))
                put("left", m.left ?: JSONObject.NULL)
                put("per_dose", m.perDose)
                put("warn_at", m.warnAt ?: JSONObject.NULL)
                put("pharmacy_name", m.pharmacyName)
                put("pharmacy_number", m.pharmacyNumber)
                put("pharmacy_contact", m.pharmacyContact)
                put("doctor_name", m.doctorName)
                put("doctor_number", m.doctorNumber)
                put("doctor_contact", m.doctorContact)
                put("notes", m.notes)
                put("paused", m.paused)
                put("caught_up", m.caughtUp)
                put("refill_warned", m.refillWarned)
            })
        })
        root.put("doses", JSONArray().apply {
            for (d in doses.sortedWith(compareBy({ it.due }, { it.id }))) put(JSONObject().apply {
                put("medicine_id", d.medicineId)
                put("name", d.name)
                put("amount", d.amount)
                put("due", d.due)
                put("status", d.status.code)
                put("marked", d.marked ?: JSONObject.NULL)
                put("rang", d.rang ?: JSONObject.NULL)
                put("remind_at", d.remindAt ?: JSONObject.NULL)
                put("again", d.again)
                put("as_needed", d.asNeeded)
            })
        })
        return root.toString(2)
    }

    /** Reads a backup whole, or throws a [BackupException]; nothing is half-read. */
    fun read(text: String): Contents {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(BackupException.Kind.NOT_A_BACKUP)
        }
        if (root.optString("format") != FORMAT) throw BackupException(BackupException.Kind.NOT_A_BACKUP)
        val version = root.optInt("version", -1)
        if (version > VERSION) throw BackupException(BackupException.Kind.NEWER)
        if (version < 1) throw BackupException(BackupException.Kind.DAMAGED)
        try {
            val c = root.getJSONObject("choices")
            val choices = Choices(
                snoozeMinutes = c.getInt("snooze_minutes").also { require(it in 1..1440) },
                againMinutes = c.getInt("again_minutes").also { require(it in 0..1440) },
                fullScreen = c.getBoolean("full_screen"),
                asAlarmClock = c.getBoolean("alarm_clock"),
                onLockScreen = c.getBoolean("lock_screen"),
            )
            val ma = root.getJSONArray("medicines")
            val medicines = (0 until ma.length()).map { i ->
                val o = ma.getJSONObject(i)
                val schedule = Schedule.decode(o.getString("schedule")) ?: throw IllegalArgumentException("schedule")
                val name = o.getString("name")
                require(name.isNotBlank())
                Medicine(
                    id = o.getLong("id").also { require(it > 0) },
                    name = name,
                    amount = o.getString("amount"),
                    schedule = schedule,
                    left = o.intOrNull("left"),
                    perDose = o.getInt("per_dose").also { require(it >= 1) },
                    warnAt = o.intOrNull("warn_at"),
                    pharmacyName = o.getString("pharmacy_name"),
                    pharmacyNumber = o.getString("pharmacy_number"),
                    pharmacyContact = o.getString("pharmacy_contact"),
                    doctorName = o.getString("doctor_name"),
                    doctorNumber = o.getString("doctor_number"),
                    doctorContact = o.getString("doctor_contact"),
                    notes = o.getString("notes"),
                    paused = o.getBoolean("paused"),
                    caughtUp = o.getLong("caught_up"),
                    refillWarned = o.getBoolean("refill_warned"),
                )
            }
            require(medicines.map { it.id }.toSet().size == medicines.size)
            val da = root.getJSONArray("doses")
            val doses = (0 until da.length()).map { i ->
                val o = da.getJSONObject(i)
                val code = o.getInt("status")
                require(code in 0..2)
                Dose(
                    medicineId = o.getLong("medicine_id"),
                    name = o.getString("name"),
                    amount = o.getString("amount"),
                    due = o.getLong("due"),
                    status = Status.of(code),
                    marked = o.longOrNull("marked"),
                    rang = o.longOrNull("rang"),
                    remindAt = o.longOrNull("remind_at"),
                    again = o.getInt("again").also { require(it >= 0) },
                    asNeeded = o.getBoolean("as_needed"),
                )
            }
            // The log holds one dose per medicine and time; a file that repeats one would
            // fail halfway through the restore, so it is refused here instead.
            require(doses.map { Triple(it.medicineId, it.due, it.asNeeded) }.toSet().size == doses.size)
            return Contents(medicines, doses, choices)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Kind.DAMAGED)
        }
    }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
