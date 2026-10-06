package com.wanderwildwood.fukuyaku.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The medicines and the log, in one small SQLite file in the app's own storage. Nothing else
 * reads it, and it is not backed up anywhere.
 *
 * Every write bumps [version], which is how the screens know to read again.
 */
class Store private constructor(context: Context) :
    SQLiteOpenHelper(context, "fukuyaku.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE medicine (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount TEXT NOT NULL DEFAULT '',
                schedule TEXT NOT NULL,
                left_count INTEGER,
                per_dose INTEGER NOT NULL DEFAULT 1,
                warn_at INTEGER,
                pharmacy_name TEXT NOT NULL DEFAULT '',
                pharmacy_number TEXT NOT NULL DEFAULT '',
                pharmacy_contact TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                paused INTEGER NOT NULL DEFAULT 0,
                caught_up INTEGER NOT NULL,
                refill_warned INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL(
            """CREATE TABLE dose (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                medicine_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                amount TEXT NOT NULL DEFAULT '',
                due INTEGER NOT NULL,
                status INTEGER NOT NULL DEFAULT 0,
                marked INTEGER,
                rang INTEGER,
                remind_at INTEGER,
                again INTEGER NOT NULL DEFAULT 0,
                as_needed INTEGER NOT NULL DEFAULT 0
            )""",
        )
        // One dose per medicine per due time: a dose taken early, before its time came, is
        // the same dose its reminder would have written, and is not written twice.
        db.execSQL("CREATE UNIQUE INDEX dose_due ON dose (medicine_id, due, as_needed)")
        db.execSQL("CREATE INDEX dose_open ON dose (status, due)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------------------------------------------------------------- medicines

    fun medicines(): List<Medicine> =
        readableDatabase.query("medicine", null, null, null, null, null, "name COLLATE NOCASE, id").use { c ->
            buildList { while (c.moveToNext()) medicineAt(c)?.let(::add) }
        }

    fun medicine(id: Long): Medicine? =
        readableDatabase.query("medicine", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToNext()) medicineAt(c) else null
        }

    /** Adds [m] when its id is 0, otherwise replaces it; returns its id. */
    fun save(m: Medicine): Long {
        val values = ContentValues().apply {
            put("name", m.name)
            put("amount", m.amount)
            put("schedule", Schedule.encode(m.schedule))
            if (m.left == null) putNull("left_count") else put("left_count", m.left)
            put("per_dose", m.perDose)
            if (m.warnAt == null) putNull("warn_at") else put("warn_at", m.warnAt)
            put("pharmacy_name", m.pharmacyName)
            put("pharmacy_number", m.pharmacyNumber)
            put("pharmacy_contact", m.pharmacyContact)
            put("notes", m.notes)
            put("paused", if (m.paused) 1 else 0)
            put("caught_up", m.caughtUp)
            put("refill_warned", if (m.refillWarned) 1 else 0)
        }
        val id = if (m.id == 0L) {
            writableDatabase.insertOrThrow("medicine", null, values)
        } else {
            writableDatabase.update("medicine", values, "id = ?", arrayOf(m.id.toString()))
            m.id
        }
        changed()
        return id
    }

    fun deleteMedicine(id: Long) {
        writableDatabase.delete("medicine", "id = ?", arrayOf(id.toString()))
        changed()
    }

    private fun medicineAt(c: Cursor): Medicine? {
        val schedule = Schedule.decode(c.str("schedule")) ?: return null
        return Medicine(
            id = c.long("id"),
            name = c.str("name"),
            amount = c.str("amount"),
            schedule = schedule,
            left = c.intOrNull("left_count"),
            perDose = c.int("per_dose"),
            warnAt = c.intOrNull("warn_at"),
            pharmacyName = c.str("pharmacy_name"),
            pharmacyNumber = c.str("pharmacy_number"),
            pharmacyContact = c.str("pharmacy_contact"),
            notes = c.str("notes"),
            paused = c.int("paused") != 0,
            caughtUp = c.long("caught_up"),
            refillWarned = c.int("refill_warned") != 0,
        )
    }

    // ---------------------------------------------------------------- the log

    /** Writes [d]; a dose already in the log for the same medicine and time is left as it was. Returns the new id, or -1. */
    fun addDose(d: Dose): Long {
        val id = writableDatabase.insertWithOnConflict("dose", null, doseValues(d), SQLiteDatabase.CONFLICT_IGNORE)
        changed()
        return id
    }

    fun updateDose(d: Dose) {
        writableDatabase.update("dose", doseValues(d), "id = ?", arrayOf(d.id.toString()))
        changed()
    }

    fun deleteDose(id: Long) {
        writableDatabase.delete("dose", "id = ?", arrayOf(id.toString()))
        changed()
    }

    /** Forgets a medicine's doses due after [after] that nobody marked. */
    fun forgetOpenAfter(medicineId: Long, after: Long) {
        writableDatabase.delete("dose", "medicine_id = ? AND status = 0 AND due > ?", arrayOf(medicineId.toString(), after.toString()))
        changed()
    }

    fun dose(id: Long): Dose? =
        readableDatabase.query("dose", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToNext()) doseAt(c) else null
        }

    /** Doses not yet marked, earliest first. */
    fun open(): List<Dose> = doses("status = 0", emptyArray(), "due")

    /** Every dose due in [from, until), earliest first. */
    fun between(from: Long, until: Long): List<Dose> =
        doses("due >= ? AND due < ?", arrayOf(from.toString(), until.toString()), "due")

    /** The whole log, newest first. */
    fun all(): List<Dose> = doses(null, null, "due DESC, id DESC")

    fun lastTaken(medicineId: Long): Dose? =
        doses("medicine_id = ? AND status = 1", arrayOf(medicineId.toString()), "marked DESC LIMIT 1").firstOrNull()

    /** Forgets every dose, and leaves the medicines as they are. */
    fun clearLog() {
        writableDatabase.delete("dose", null, null)
        changed()
    }

    private fun doses(where: String?, args: Array<String>?, order: String): List<Dose> =
        readableDatabase.query("dose", null, where, args, null, null, order).use { c ->
            buildList { while (c.moveToNext()) add(doseAt(c)) }
        }

    private fun doseValues(d: Dose) = ContentValues().apply {
        put("medicine_id", d.medicineId)
        put("name", d.name)
        put("amount", d.amount)
        put("due", d.due)
        put("status", d.status.code)
        if (d.marked == null) putNull("marked") else put("marked", d.marked)
        if (d.rang == null) putNull("rang") else put("rang", d.rang)
        if (d.remindAt == null) putNull("remind_at") else put("remind_at", d.remindAt)
        put("again", d.again)
        put("as_needed", if (d.asNeeded) 1 else 0)
    }

    private fun doseAt(c: Cursor) = Dose(
        id = c.long("id"),
        medicineId = c.long("medicine_id"),
        name = c.str("name"),
        amount = c.str("amount"),
        due = c.long("due"),
        status = Status.of(c.int("status")),
        marked = c.longOrNull("marked"),
        rang = c.longOrNull("rang"),
        remindAt = c.longOrNull("remind_at"),
        again = c.int("again"),
        asNeeded = c.int("as_needed") != 0,
    )

    private fun changed() {
        _version.value = _version.value + 1
    }

    companion object {
        private val _version = MutableStateFlow(0)

        /** Goes up by one with every write, from any part of the app. */
        val version: StateFlow<Int> = _version

        @Volatile private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}

private fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col)) ?: ""
private fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
private fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))
private fun Cursor.longOrNull(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
private fun Cursor.intOrNull(col: String): Int? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
