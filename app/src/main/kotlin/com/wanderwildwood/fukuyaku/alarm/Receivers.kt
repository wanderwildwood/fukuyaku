package com.wanderwildwood.fukuyaku.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wanderwildwood.fukuyaku.data.Status

/** The one alarm going off: catch up, ring, set the next. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Log.i("fukuyaku", "alarm went off")
        Reminders.sync(context)
    }

    companion object {
        const val ACTION = "com.wanderwildwood.fukuyaku.ALARM"
    }
}

/** Taken, Skip and Snooze, pressed on a notification, from the lock screen or anywhere. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DOSE, -1L)
        if (id < 0) return
        if (id == Notifier.TEST_ID.toLong()) {
            // The test has nothing to mark; a press only shows the button works.
            Notifier.cancel(context, id)
            return
        }
        when (intent.action) {
            TAKEN -> Reminders.mark(context, id, Status.TAKEN)
            SKIP -> Reminders.mark(context, id, Status.SKIPPED)
            SNOOZE -> Reminders.snooze(context, id)
        }
    }

    companion object {
        const val TAKEN = "com.wanderwildwood.fukuyaku.TAKEN"
        const val SKIP = "com.wanderwildwood.fukuyaku.SKIP"
        const val SNOOZE = "com.wanderwildwood.fukuyaku.SNOOZE"
        const val EXTRA_DOSE = "dose"
    }
}

/**
 * Everything that clears or moves the alarm: the phone starting up, this app being updated,
 * the clock or the time zone being changed, the permission for exact alarms being given or
 * taken away. Each one sets it again from what is in the log.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("fukuyaku", "system: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Notifier.channels(context)
                Notifier.restore(context)
                Reminders.sync(context)
            }
            Intent.ACTION_LOCALE_CHANGED -> Notifier.channels(context)
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Reminders.sync(context)
        }
    }
}
