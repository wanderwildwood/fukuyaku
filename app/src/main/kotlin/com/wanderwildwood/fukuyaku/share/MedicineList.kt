package com.wanderwildwood.fukuyaku.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Store

/**
 * The medicines being taken now, by name and amount, for another app to copy from: Field Kit's
 * "in case of emergency" card is the first. Read only, and only by an app the person has given
 * the "Read your medicine list" permission (`com.wanderwildwood.fukuyaku.permission.READ_MEDICINES`,
 * declared in the manifest as a dangerous permission, so Android asks the person).
 *
 * `content://com.wanderwildwood.fukuyaku.medicines/current`, one row per medicine that is not
 * paused, sorted by name: `name` (text), `amount` (text, may be empty), `as_needed` (1 or 0).
 * Nothing about the log, the times, the supply or the pharmacy is handed over.
 */
class MedicineList : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.pathSegments != listOf(PATH)) return null
        val context = context ?: return null
        val cursor = MatrixCursor(COLUMNS)
        Store.get(context).medicines()
            .filter { !it.paused }
            .forEach { cursor.addRow(arrayOf<Any?>(it.name, it.amount, if (it.schedule is Schedule.AsNeeded) 1 else 0)) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val PATH = "current"
        val COLUMNS = arrayOf("name", "amount", "as_needed")
    }
}
