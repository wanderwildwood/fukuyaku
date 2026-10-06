package com.wanderwildwood.fukuyaku.data

import android.content.Context

/** The few choices the app keeps, in its own preferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How long Snooze puts a reminder off. */
    var snoozeMinutes: Int
        get() = prefs.getInt("snooze", 10)
        set(v) = prefs.edit().putInt("snooze", v).apply()

    /** How long after a reminder it rings again if the dose is not marked; 0 for never. */
    var againMinutes: Int
        get() = prefs.getInt("again", 30)
        set(v) = prefs.edit().putInt("again", v).apply()

    /** A reminder fills the screen, over the lock screen, rather than only being a notification. */
    var fullScreen: Boolean
        get() = prefs.getBoolean("full_screen", true)
        set(v) = prefs.edit().putBoolean("full_screen", v).apply()

    /** The next dose on Glance's lock-screen panel. Off until asked for: it names medicines. */
    var onLockScreen: Boolean
        get() = prefs.getBoolean("glance", false)
        set(v) = prefs.edit().putBoolean("glance", v).apply()

    /** When the test reminder is to ring; 0 for none waiting. */
    var testAt: Long
        get() = prefs.getLong("test_at", 0L)
        set(v) = prefs.edit().putLong("test_at", v).apply()

    /** The person said Medicine is switched on in DuraSpeed's list. */
    var duraSpeedAllowed: Boolean
        get() = prefs.getBoolean("duraspeed_allowed", false)
        set(v) = prefs.edit().putBoolean("duraspeed_allowed", v).apply()

    /** The newest stop by the system already accounted for. */
    var stopSeen: Long
        get() = prefs.getLong("stop_seen", 0L)
        set(v) = prefs.edit().putLong("stop_seen", v).apply()

    /** When the app was last found stopped by the system, for the notice on the first screen; 0 for none to show. */
    var stoppedAt: Long
        get() = prefs.getLong("stopped_at", 0L)
        set(v) = prefs.edit().putLong("stopped_at", v).apply()
}
