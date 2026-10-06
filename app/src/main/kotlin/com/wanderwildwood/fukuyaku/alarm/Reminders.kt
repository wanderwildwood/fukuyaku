package com.wanderwildwood.fukuyaku.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wanderwildwood.fukuyaku.MainActivity
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.DuraSpeed
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Settings
import com.wanderwildwood.fukuyaku.data.Status
import com.wanderwildwood.fukuyaku.data.Store
import com.wanderwildwood.fukuyaku.glance.GlanceProvider
import java.time.ZoneId

/**
 * Everything that makes a reminder ring, and everything a dose being marked changes.
 *
 * There is only ever one alarm, set for the next moment anything is due. When it goes off, or
 * when the app is opened, or the phone starts, or the clock or the time zone changes, [sync]
 * writes into the log every dose that has come due since the last time, rings for them, and
 * sets the alarm again. So a reminder that could not ring on time — the app was stopped, the
 * phone was off — is never lost: it is in the log as not marked, and the newest one rings as
 * soon as it can.
 */
object Reminders {

    private const val TAG = "fukuyaku"
    private val lock = Any()

    /** Catches the log up to now, rings what is due, and sets the next alarm. */
    fun sync(context: Context, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val store = Store.get(context)
        val settings = Settings(context)
        val zone = ZoneId.systemDefault()

        DuraSpeed.newStop(context, settings)?.let {
            Log.w(TAG, "stopped by the system at $it; alarms set before then were cancelled")
            settings.stoppedAt = it
        }

        for (found in store.medicines()) {
            // A clock set back (by hand, or by a network that had it wrong) leaves a medicine
            // caught up to a moment that has not come yet, and nothing would ring until it did.
            // Doses already in the log are never written twice, so starting again from now is safe.
            // Doses the wrong clock wrote into the log and nobody marked are not real yet: left
            // in place, they would stand in for the real ones and stop them ringing.
            val m = if (found.caughtUp > now) {
                store.forgetOpenAfter(found.id, now)
                found.copy(caughtUp = now).also { store.save(it) }
            } else {
                found
            }
            val caught = Plan.catchUp(m, now, zone)
            for (due in caught.dues) {
                val dose = Dose(medicineId = m.id, name = m.name, amount = m.amount, due = due)
                val id = store.addDose(dose)
                if (id > 0 && due == caught.rings) ring(context, store, settings, dose.copy(id = id), m, now, zone)
            }
            // Kept close to now even when nothing came due, so the next catch-up has little to walk.
            if (m.reminds && (caught.dues.isNotEmpty() || now - m.caughtUp > 6 * 3_600_000L)) {
                store.save(m.copy(caughtUp = now))
            }
        }

        val medicines = store.medicines().associateBy { it.id }
        for (d in store.open()) {
            val at = d.remindAt ?: continue
            if (at > now) continue
            val m = medicines[d.medicineId]
            if (m == null || m.paused) {
                store.updateDose(d.copy(remindAt = null))
                continue
            }
            ring(context, store, settings, d.copy(again = d.again + 1), m, now, zone)
        }

        val test = settings.testAt
        if (test in 1..now) {
            settings.testAt = 0
            Notifier.test(context, settings, test, now)
        }

        schedule(context, store, settings, now, zone)
        GlanceProvider.changed(context)
    }

    private fun ring(context: Context, store: Store, settings: Settings, dose: Dose, med: Medicine, now: Long, zone: ZoneId) {
        // An older dose of the same medicine stops ringing: the new one speaks for both, and
        // the older stays in the log as not marked.
        for (o in store.open()) {
            if (o.medicineId == med.id && o.id != dose.id && o.due < dose.due) {
                if (o.remindAt != null) store.updateDose(o.copy(remindAt = null))
                Notifier.cancel(context, o.id)
            }
        }
        val again = Plan.again(dose, now, settings.againMinutes, Plan.nextDue(med, now, zone))
        val rung = dose.copy(rang = dose.rang ?: now, remindAt = again)
        store.updateDose(rung)
        Log.i(TAG, "rang dose ${dose.id} due ${dose.due} at $now, again at $again")
        Notifier.dose(context, settings, rung, med, quiet = false)
    }

    /** Sets the one alarm for the next thing due, or clears it when nothing is. */
    private fun schedule(context: Context, store: Store, settings: Settings, now: Long, zone: ZoneId) {
        val wake = Plan.nextWake(store.medicines(), store.open(), now, zone, settings.testAt.takeIf { it > 0 })
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = alarm(context)
        if (wake == null) {
            am.cancel(pending)
            Log.i(TAG, "nothing due; no alarm")
            return
        }
        val at = maxOf(wake, now + 1_000L)
        if (am.canScheduleExactAlarms()) {
            // An alarm clock is exact, is let through Doze, and wakes the phone early to deliver it.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, open(context)), pending)
            Log.i(TAG, "alarm clock set for $at")
        } else {
            // Without the permission an exact alarm cannot be set at all. This one may come some
            // minutes late; the first screen says so and offers the permission.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            Log.w(TAG, "no exact alarms allowed; inexact alarm set for $at")
        }
    }

    /** Re-sets the alarm without catching anything up. */
    fun reschedule(context: Context) = synchronized(lock) {
        schedule(context, Store.get(context), Settings(context), System.currentTimeMillis(), ZoneId.systemDefault())
    }

    // ---------------------------------------------------------------- marking

    /**
     * Marks a dose taken, skipped, or back to not marked. [at] is when it was taken, which may
     * be its due time when it is marked late. Counting follows: what is counted goes down by a
     * dose when it is taken, and back up if that is undone.
     */
    fun mark(context: Context, doseId: Long, status: Status, at: Long = System.currentTimeMillis()) = synchronized(lock) {
        val store = Store.get(context)
        val dose = store.dose(doseId) ?: return@synchronized
        store.updateDose(
            dose.copy(
                status = status,
                marked = if (status == Status.OPEN) null else at,
                remindAt = null,
            ),
        )
        Notifier.cancel(context, doseId)
        store.medicine(dose.medicineId)?.let {
            recount(context, store, it, dose.status == Status.TAKEN, status == Status.TAKEN)
        }
        schedule(context, store, Settings(context), System.currentTimeMillis(), ZoneId.systemDefault())
        GlanceProvider.changed(context)
    }

    /** Takes a dose written down as taken when needed back out of the log, and gives back what it counted. */
    fun remove(context: Context, doseId: Long) = synchronized(lock) {
        val store = Store.get(context)
        val dose = store.dose(doseId) ?: return@synchronized
        store.deleteDose(doseId)
        store.medicine(dose.medicineId)?.let { recount(context, store, it, dose.status == Status.TAKEN, false) }
        GlanceProvider.changed(context)
    }

    /** Puts a ringing dose off for the snooze length. */
    fun snooze(context: Context, doseId: Long) = synchronized(lock) {
        val store = Store.get(context)
        val settings = Settings(context)
        val dose = store.dose(doseId) ?: return@synchronized
        if (dose.status != Status.OPEN) return@synchronized
        val now = System.currentTimeMillis()
        store.updateDose(dose.copy(remindAt = Plan.snooze(now, settings.snoozeMinutes)))
        Notifier.cancel(context, doseId)
        schedule(context, store, settings, now, ZoneId.systemDefault())
    }

    /**
     * A dose taken before its time: written into the log now as that due time's dose, so when
     * the time comes there is nothing left to ring for.
     */
    fun takeEarly(context: Context, med: Medicine, due: Long) = synchronized(lock) {
        val store = Store.get(context)
        val now = System.currentTimeMillis()
        val id = store.addDose(
            Dose(medicineId = med.id, name = med.name, amount = med.amount, due = due, status = Status.TAKEN, marked = now),
        )
        if (id > 0) recount(context, store, med, wasTaken = false, nowTaken = true)
        schedule(context, store, Settings(context), now, ZoneId.systemDefault())
        GlanceProvider.changed(context)
    }

    /** A dose of a medicine taken as needed, written down as taken now. */
    fun takeAsNeeded(context: Context, med: Medicine, at: Long = System.currentTimeMillis()) = synchronized(lock) {
        val store = Store.get(context)
        val id = store.addDose(
            Dose(medicineId = med.id, name = med.name, amount = med.amount, due = at, status = Status.TAKEN, marked = at, asNeeded = true),
        )
        if (id > 0) recount(context, store, med, wasTaken = false, nowTaken = true)
        GlanceProvider.changed(context)
    }

    private fun recount(context: Context, store: Store, med: Medicine, wasTaken: Boolean, nowTaken: Boolean) {
        if (wasTaken == nowTaken) return
        val left = Plan.count(med, nowTaken) ?: return
        var m = med.copy(left = left)
        if (m.low && !m.refillWarned) {
            m = m.copy(refillWarned = true)
            Notifier.refill(context, m)
        } else if (!m.low && m.refillWarned) {
            m = m.copy(refillWarned = false)
        }
        store.save(m)
    }

    // ---------------------------------------------------------------- medicines

    /**
     * Saves a medicine, new or changed. What came due under the old schedule is written into
     * the log first, so a change made now never rewrites the past and never rings for a time
     * that went by before it was made.
     */
    fun save(context: Context, med: Medicine): Long {
        sync(context)
        return synchronized(lock) {
            val store = Store.get(context)
            val now = System.currentTimeMillis()
            var m = med.copy(caughtUp = now)
            // More than the warning level again, after a refill: the warning can be given next time.
            if (!m.low) m = m.copy(refillWarned = false)
            val id = store.save(m)
            if (m.paused || !m.reminds) quieten(context, store, id)
            id
        }.also { sync(context) }
    }

    fun delete(context: Context, id: Long) {
        synchronized(lock) {
            val store = Store.get(context)
            quieten(context, store, id)
            store.deleteMedicine(id)
        }
        sync(context)
    }

    /** Stops every dose of a medicine ringing; they stay in the log as they are. */
    private fun quieten(context: Context, store: Store, medicineId: Long) {
        for (d in store.open()) {
            if (d.medicineId != medicineId) continue
            if (d.remindAt != null) store.updateDose(d.copy(remindAt = null))
            Notifier.cancel(context, d.id)
        }
    }

    /** A reminder that rings in a minute, through the same alarm as a real one. */
    fun test(context: Context) {
        Settings(context).testAt = System.currentTimeMillis() + 60_000L
        reschedule(context)
    }

    // ---------------------------------------------------------------- intents

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** What the system's alarm icon opens when it is pressed: the app. */
    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
