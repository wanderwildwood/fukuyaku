package com.wanderwildwood.fukuyaku

import com.wanderwildwood.fukuyaku.data.Backup
import com.wanderwildwood.fukuyaku.data.Backup.BackupException.Kind
import com.wanderwildwood.fukuyaku.data.Days
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Status
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BackupTest {

    private val medicines = listOf(
        Medicine(
            id = 3, name = "Lisinopril", amount = "10 mg",
            schedule = Schedule.AtTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), Days.OnDays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))),
            left = 12, perDose = 2, warnAt = 4,
            pharmacyName = "Corner Pharmacy", pharmacyNumber = "+1 555 010 0101", pharmacyContact = "content://com.android.contacts/contacts/lookup/x/1",
            doctorName = "Dr Ada Whitlock", doctorNumber = "+1 555 010 0102", doctorContact = "",
            notes = "With food, \"not\" on an empty stomach\nsecond line", paused = false, caughtUp = 1_793_610_000_000, refillWarned = true,
        ),
        Medicine(id = 5, name = "Ibuprofen", schedule = Schedule.AsNeeded, caughtUp = 5),
        Medicine(id = 9, name = "Vitamin D", amount = "1000 IU", schedule = Schedule.EveryHours(24, LocalDateTime.of(2026, 1, 1, 9, 0)), paused = true),
        Medicine(id = 11, name = "Iron", schedule = Schedule.AtTimes(listOf(LocalTime.of(7, 0)), Days.EveryN(2, LocalDate.of(2026, 3, 1))), caughtUp = 1_793_516_400_000),
    )

    private val doses = listOf(
        Dose(medicineId = 3, name = "Lisinopril", amount = "10 mg", due = 1_000, status = Status.TAKEN, marked = 1_500, rang = 1_000, remindAt = null, again = 1),
        Dose(medicineId = 3, name = "Lisinopril", amount = "10 mg", due = 2_000, status = Status.SKIPPED, marked = 2_100),
        Dose(medicineId = 5, name = "Ibuprofen", amount = "", due = 3_000, status = Status.TAKEN, marked = 3_000, asNeeded = true),
        Dose(medicineId = 3, name = "Lisinopril", amount = "10 mg", due = 4_000, remindAt = 4_600),
        // A medicine deleted since: its history stays readable.
        Dose(medicineId = 77, name = "Tomas's old tablet", amount = "5 mg", due = 500, status = Status.TAKEN, marked = 520),
    )

    private val choices = Backup.Choices(snoozeMinutes = 15, againMinutes = 0, fullScreen = false, asAlarmClock = false, onLockScreen = true)

    private fun file() = Backup.write(medicines, doses, choices, Instant.parse("2026-11-02T10:00:00Z"))

    private fun refused(text: String, kind: Kind) {
        try {
            Backup.read(text)
            fail("should have been refused")
        } catch (e: Backup.BackupException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun roundTripGivesTheSameData() {
        val back = Backup.read(file())
        assertEquals(medicines, back.medicines)
        assertEquals(choices, back.choices)
        val sort = compareBy<Dose>({ it.due }, { it.medicineId })
        assertEquals(doses.sortedWith(sort), back.doses.sortedWith(sort))
    }

    @Test
    fun anEmptyAppRoundTrips() {
        val back = Backup.read(Backup.write(emptyList(), emptyList(), Backup.Choices()))
        assertTrue(back.medicines.isEmpty() && back.doses.isEmpty())
        assertEquals(Backup.Choices(), back.choices)
    }

    @Test
    fun theLogCsvIsNotABackup() {
        refused("medicine,amount,due,status\r\nLisinopril,10 mg,2026-01-01 08:00,taken\r\n", Kind.NOT_A_BACKUP)
    }

    @Test
    fun otherJsonAndNonsenseAreNotABackup() {
        refused("", Kind.NOT_A_BACKUP)
        refused("{\"format\":\"nikka-backup\",\"version\":1}", Kind.NOT_A_BACKUP)
        refused("[1,2,3]", Kind.NOT_A_BACKUP)
        refused("{}", Kind.NOT_A_BACKUP)
    }

    @Test
    fun aNewerVersionIsRefusedAsNewer() {
        refused(JSONObject(file()).put("version", 2).toString(), Kind.NEWER)
    }

    @Test
    fun aMissingVersionIsDamaged() {
        refused(JSONObject(file()).apply { remove("version") }.toString(), Kind.DAMAGED)
    }

    @Test
    fun aCutOffFileIsNotABackup() {
        val text = file()
        refused(text.substring(0, text.length / 2), Kind.NOT_A_BACKUP)
    }

    @Test
    fun aBrokenScheduleMakesTheWholeFileDamaged() {
        val o = JSONObject(file())
        o.getJSONArray("medicines").getJSONObject(1).put("schedule", "X|nonsense")
        refused(o.toString(), Kind.DAMAGED)
    }

    @Test
    fun aScheduleThePageCannotSetIsDamaged() {
        val o = JSONObject(file())
        o.getJSONArray("medicines").getJSONObject(3).put("schedule", "T|07:00|N2000000000@2026-03-01")
        refused(o.toString(), Kind.DAMAGED)
        val p = JSONObject(file())
        p.getJSONArray("medicines").getJSONObject(2).put("schedule", "H|0|2026-01-01T09:00")
        refused(p.toString(), Kind.DAMAGED)
        val q = JSONObject(file())
        q.getJSONArray("medicines").getJSONObject(2).put("schedule", "H|169|2026-01-01T09:00")
        refused(q.toString(), Kind.DAMAGED)
    }

    @Test
    fun aMedicineThatRemindsIsNotCaughtUpFromLongAgo() {
        val o = JSONObject(file())
        val ms = o.getJSONArray("medicines")
        for (i in 0 until ms.length()) ms.getJSONObject(i).put("caught_up", 0)
        val back = Backup.read(o.toString()).medicines.associateBy { it.id }
        // Written 2026-11-02T10:00Z, so no earlier than 31 days before.
        assertEquals(1_790_935_200_000, back.getValue(3).caughtUp)
        assertEquals(1_790_935_200_000, back.getValue(11).caughtUp)
        // As needed and paused ones do not ring, so they are left as they were.
        assertEquals(0L, back.getValue(5).caughtUp)
        assertEquals(0L, back.getValue(9).caughtUp)
        // Without a readable time of writing, the fixed floor still holds.
        o.put("written", "long ago")
        assertEquals(Backup.EARLIEST_CAUGHT_UP, Backup.read(o.toString()).medicines.first { it.id == 11L }.caughtUp)
    }

    @Test
    fun aMissingFieldIsDamaged() {
        val o = JSONObject(file())
        o.getJSONArray("medicines").getJSONObject(0).remove("name")
        refused(o.toString(), Kind.DAMAGED)
        val p = JSONObject(file())
        p.remove("doses")
        refused(p.toString(), Kind.DAMAGED)
    }

    @Test
    fun aBadStatusOrChoiceIsDamaged() {
        val o = JSONObject(file())
        o.getJSONArray("doses").getJSONObject(0).put("status", 9)
        refused(o.toString(), Kind.DAMAGED)
        val p = JSONObject(file())
        p.getJSONObject("choices").put("snooze_minutes", 0)
        refused(p.toString(), Kind.DAMAGED)
    }

    @Test
    fun repeatedIdsOrRepeatedDosesAreDamaged() {
        val o = JSONObject(file())
        o.getJSONArray("medicines").getJSONObject(1).put("id", 3)
        refused(o.toString(), Kind.DAMAGED)
        val p = JSONObject(file())
        val ds = p.getJSONArray("doses")
        ds.put(JSONObject(ds.getJSONObject(0).toString()))
        refused(p.toString(), Kind.DAMAGED)
    }
}
