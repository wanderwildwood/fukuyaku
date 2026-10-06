package com.wanderwildwood.fukuyaku.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.wanderwildwood.fukuyaku.MainActivity
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Settings
import com.wanderwildwood.fukuyaku.data.Status
import com.wanderwildwood.fukuyaku.data.Store

/**
 * The notifications: one per dose that is ringing, with Taken, Skip and Snooze on it, which
 * work from the lock screen without unlocking. Its number is the dose's own, so ringing again
 * replaces it rather than adding a second.
 */
object Notifier {

    const val DOSES = "doses"
    const val REFILLS = "refills"

    /** The test reminder's number, which no dose can have. */
    const val TEST_ID = Int.MAX_VALUE - 1

    /** Refill notices are numbered below zero, by medicine, out of the doses' way. */
    private fun refillId(medicineId: Long) = -(medicineId.toInt() + 1)

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(DOSES, context.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_doses_about)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(REFILLS, context.getString(R.string.channel_refills), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /**
     * Rings for [dose]. [quiet] posts it again without a sound, which is what happens to doses
     * still waiting when the phone starts up again: their notifications went with the restart.
     */
    fun dose(context: Context, settings: Settings, dose: Dose, med: Medicine?, quiet: Boolean) {
        channels(context)
        val id = dose.id.toInt()
        val due = context.getString(R.string.notify_due, Times.whenShort(context, dose.due))
        val body = listOfNotNull(due, lastTaken(context, dose), med?.notes?.takeIf { it.isNotBlank() }).joinToString("\n")
        val public = NotificationCompat.Builder(context, DOSES)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.notify_public_title))
            .setContentText(due)
            .build()
        val b = NotificationCompat.Builder(context, DOSES)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(dose.label)
            .setContentText(due)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setWhen(dose.due)
            .setShowWhen(true)
            .setAutoCancel(false)
            .setSilent(quiet)
            .setContentIntent(screen(context, id))
            .addAction(0, context.getString(R.string.action_taken), action(context, ActionReceiver.TAKEN, dose.id))
            .addAction(0, context.getString(R.string.action_skip), action(context, ActionReceiver.SKIP, dose.id))
            .addAction(0, context.getString(R.string.action_snooze, settings.snoozeMinutes), action(context, ActionReceiver.SNOOZE, dose.id))
        if (settings.fullScreen && !quiet) b.setFullScreenIntent(screen(context, id), true)
        context.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    /**
     * "Last taken 13:55", when this medicine was taken in the last day: what the person needs to
     * see when a reminder comes soon after a dose, as one does after flying west. Said, not judged.
     */
    fun lastTaken(context: Context, dose: Dose): String? {
        val last = Store.get(context).lastTaken(dose.medicineId) ?: return null
        val at = last.marked ?: return null
        if (System.currentTimeMillis() - at > 24 * 3_600_000L) return null
        return context.getString(R.string.last_taken, Times.whenShort(context, at))
    }

    fun cancel(context: Context, doseId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(doseId.toInt())
    }

    /** Said once, when what is counted falls to the warning level. */
    fun refill(context: Context, med: Medicine) {
        channels(context)
        val left = med.left ?: return
        val text = context.resources.getQuantityString(R.plurals.refill_left, left, left.toString())
        val b = NotificationCompat.Builder(context, REFILLS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.refill_title, med.name))
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    refillId(med.id),
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_MEDICINE, med.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        if (med.pharmacyNumber.isNotBlank()) {
            b.addAction(
                0,
                context.getString(R.string.pharmacy_call),
                PendingIntent.getActivity(
                    context,
                    refillId(med.id) - 1_000_000,
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(med.pharmacyNumber))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        context.getSystemService(NotificationManager::class.java).notify(refillId(med.id), b.build())
    }

    /** The test reminder: the same channel, the same lock-screen screen, nothing to mark. */
    fun test(context: Context, settings: Settings, due: Long, now: Long) {
        channels(context)
        val text = context.getString(R.string.test_text, Times.time(context, due), Times.time(context, now))
        val b = NotificationCompat.Builder(context, DOSES)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.test_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(screen(context, TEST_ID))
            .addAction(0, context.getString(R.string.action_taken), action(context, ActionReceiver.TAKEN, TEST_ID.toLong()))
            .addAction(0, context.getString(R.string.action_skip), action(context, ActionReceiver.SKIP, TEST_ID.toLong()))
            .addAction(0, context.getString(R.string.action_snooze, settings.snoozeMinutes), action(context, ActionReceiver.SNOOZE, TEST_ID.toLong()))
        if (settings.fullScreen) b.setFullScreenIntent(screen(context, TEST_ID), true)
        context.getSystemService(NotificationManager::class.java).notify(TEST_ID, b.build())
    }

    /** The doses whose notifications are showing now. */
    fun ringing(context: Context): Set<Int> =
        context.getSystemService(NotificationManager::class.java).activeNotifications.map { it.id }.toSet()

    /**
     * After a restart, the doses that rang lately and were not marked get their notifications
     * back, without a sound: what the restart took away, and nothing more.
     */
    fun restore(context: Context, now: Long = System.currentTimeMillis()) {
        val store = Store.get(context)
        val settings = Settings(context)
        val medicines = store.medicines().associateBy { it.id }
        // The newest of each medicine, as when they rang.
        store.open()
            .filter { it.rang != null && now - it.due <= Plan.RING_WITHIN_MS && it.status == Status.OPEN }
            .groupBy { it.medicineId }
            .mapNotNull { (_, those) -> those.maxByOrNull { it.due } }
            .forEach { dose(context, settings, it, medicines[it.medicineId], quiet = true) }
    }

    private fun action(context: Context, what: String, doseId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ActionReceiver::class.java)
            .setAction(what)
            // Each dose and each button its own intent, so one never stands in for another.
            .setData(Uri.parse("fukuyaku://dose/$doseId/$what"))
            .putExtra(ActionReceiver.EXTRA_DOSE, doseId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun screen(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context,
        id,
        Intent(context, ReminderActivity::class.java)
            .setData(Uri.parse("fukuyaku://ringing/$id"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
