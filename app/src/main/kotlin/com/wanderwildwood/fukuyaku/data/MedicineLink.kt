package com.wanderwildwood.fukuyaku.data

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * The link from a refill event in the calendar back to the medicine it was made from, in
 * Android's own place for one: the event's `CUSTOM_APP_PACKAGE` and `CUSTOM_APP_URI`. Datebook
 * keeps them, shows an "Open in Medicine" button on the event and opens the medicine with
 * `CalendarContract.ACTION_HANDLE_CUSTOM_EVENT`. A calendar that knows nothing of them leaves
 * them out, and the event is as it was.
 */
object MedicineLink {

    private const val PREFIX = "fukuyaku://medicine/"

    fun uri(id: Long): String = PREFIX + id

    /** Puts the link to medicine [id] on an "add to calendar" request. */
    fun put(context: Context, intent: Intent, id: Long): Intent = intent
        .putExtra(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
        .putExtra(CalendarContract.Events.CUSTOM_APP_URI, uri(id))

    /** The medicine a calendar event asks for, or null when [intent] is no such request. */
    fun id(intent: Intent?): Long? {
        if (intent?.action != CalendarContract.ACTION_HANDLE_CUSTOM_EVENT) return null
        val uri = intent.getStringExtra(CalendarContract.EXTRA_CUSTOM_APP_URI) ?: return null
        if (!uri.startsWith(PREFIX)) return null
        return uri.removePrefix(PREFIX).toLongOrNull()?.takeIf { it > 0 }
    }
}
