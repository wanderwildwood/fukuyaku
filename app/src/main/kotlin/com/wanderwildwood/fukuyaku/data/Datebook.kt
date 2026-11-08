package com.wanderwildwood.fukuyaku.data

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract

/**
 * Datebook (com.wanderwildwood.koyomi), the only calendar "Put the refill in the calendar"
 * talks to.
 *
 * The request is addressed to Datebook by package, so it can never reach another calendar app:
 * a calendar that stumbles on it (Mudita's own crashed on this kind of event) is one Medicine
 * has not been checked against, and a crash there happens in that app, where Medicine cannot
 * catch it. Datebook reads the whole of it, the link back to the medicine ([MedicineLink])
 * included. Without Datebook the button is not offered, and the refill date can be copied.
 */
object Datebook {

    const val PACKAGE = "com.wanderwildwood.koyomi"

    /** The apps that answer Datebook's request ([request]), by package. */
    fun interface Answering {
        fun packages(): List<String>
    }

    /** Android's "add an event" request, for Datebook alone. */
    fun request(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .setPackage(PACKAGE)

    /** Whether Datebook is installed and takes the request; false when that cannot be asked. */
    fun takes(answering: Answering): Boolean = runCatching {
        PACKAGE in answering.packages()
    }.getOrDefault(false)

    /** The real answer, from the package manager. */
    fun answering(pm: PackageManager) = Answering {
        pm.queryIntentActivities(request(), PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
    }
}
