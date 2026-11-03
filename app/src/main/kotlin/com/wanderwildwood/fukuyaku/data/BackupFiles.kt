package com.wanderwildwood.fukuyaku.data

import android.content.Context
import android.net.Uri
import com.wanderwildwood.fukuyaku.alarm.Reminders

/** Backup and restore against a file the person picked: the Android half of [Backup]. */
object BackupFiles {

    sealed interface Result {
        data class Done(val medicines: Int, val doses: Int) : Result
        data class Refused(val kind: Backup.BackupException.Kind) : Result
        data object Unreadable : Result
    }

    /** Writes the backup to [uri]; false, with the file removed if it was left empty, when that fails. */
    fun save(context: Context, uri: Uri): Boolean {
        val store = Store.get(context)
        val text = Backup.write(store.medicines(), store.all(), Settings(context).choices())
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }.isSuccess
        if (!ok) runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
        return ok
    }

    /**
     * Reads the whole file first; only a file that passes replaces anything, and the store
     * replaces in one transaction. A file over 20 MB is refused unread: a log of years is far smaller.
     */
    fun restore(context: Context, uri: Uri): Result {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readCapped(it) }
        }.getOrNull() ?: return Result.Unreadable
        if (bytes.size > MAX_BYTES) return Result.Refused(Backup.BackupException.Kind.NOT_A_BACKUP)
        val contents = try {
            Backup.read(String(bytes, Charsets.UTF_8))
        } catch (e: Backup.BackupException) {
            return Result.Refused(e.kind)
        }
        runCatching { Store.get(context).replaceAll(contents) }.onFailure { return Result.Unreadable }
        Settings(context).apply(contents.choices)
        com.wanderwildwood.fukuyaku.glance.GlanceProvider.changed(context)
        Reminders.sync(context)
        return Result.Done(contents.medicines.size, contents.doses.size)
    }

    /** At most MAX_BYTES + 1 bytes, so a file that is too big can be told from one that just fits. */
    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() <= MAX_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private const val MAX_BYTES = 20 * 1024 * 1024
}
