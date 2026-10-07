package com.wanderwildwood.fukuyaku.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Export
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** What a medicine's last fortnight looked like, for the top of the log. */
private data class Summary(
    val name: String,
    val week: Plan.Tally,
    val month: Plan.Tally,
    val marks: List<Plan.Mark>,
    val asNeededWeek: Int,
)

/**
 * The log: how each medicine has gone over the last week and month, then every dose, newest
 * first, by day. It can be shared as text (into a note, a message, an email) or saved as a
 * CSV file for a spreadsheet or a doctor.
 */
@Composable
fun LogScreen() {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    val zone = ZoneId.systemDefault()
    val doses by produceState<List<Dose>?>(null, version) {
        value = withContext(Dispatchers.IO) { Store.get(context).all() }
    }
    var chosen by remember { mutableStateOf<Dose?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val all = Store.get(context).all()
        val doctors = Export.doctorsById(Store.get(context).medicines())
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Export.csv(all, zone, doctors).toByteArray()) }
                ?: error("no stream")
        }.onSuccess {
            Toast.makeText(context, context.getString(R.string.log_saved), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.log_not_saved), Toast.LENGTH_LONG).show()
        }
    }

    val list = doses
    if (list == null) return
    val now = remember(version) { System.currentTimeMillis() }
    val today = LocalDate.now(zone)
    val summaries = remember(list) { summaries(list, today, zone, now) }
    val byDay = remember(list) { list.groupBy { Times.date(it.due) }.toSortedMap(compareByDescending { it }) }

    LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (list.isEmpty()) {
            item(key = "empty") {
                TextMMD(
                    text = stringResource(R.string.log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            return@LazyColumnMMD
        }
        for (s in summaries) {
            item(key = "sum:${s.name}") { SummaryBlock(s) }
        }
        item(key = "export") {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                OutlinedButtonMMD(
                    onClick = {
                        val store = Store.get(context)
                        val text = Export.text(store.all(), zone, Words.export(context), Export.doctorLines(store.medicines()))
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_title))
                            .putExtra(Intent.EXTRA_TITLE, context.getString(R.string.export_title))
                            .putExtra(Intent.EXTRA_TEXT, text)
                        runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.log_share))) }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { TextMMD(text = stringResource(R.string.log_share), style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.width(12.dp))
                OutlinedButtonMMD(
                    onClick = {
                        runCatching { save.launch("medicine-log-$today.csv") }
                            .onFailure { Toast.makeText(context, context.getString(R.string.log_not_saved), Toast.LENGTH_LONG).show() }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { TextMMD(text = stringResource(R.string.log_save_csv), style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDividerMMD()
        }
        for ((day, those) in byDay) {
            item(key = "day:$day") { Heading(Times.relativeDay(context, day)) }
            for (d in those.sortedWith(compareBy({ it.due }, { it.id }))) {
                item(key = "dose:${d.id}") {
                    DoseRow(
                        time = Times.time(context, d.due),
                        label = d.label,
                        status = Words.status(context, d, now, underItsDay = true),
                        bold = false,
                    ) { chosen = d }
                }
            }
        }
        item(key = "foot") { Spacer(Modifier.height(24.dp)) }
    }

    chosen?.let { d -> DoseDialog(d, now, onDismiss = { chosen = null }) }
}

private fun summaries(doses: List<Dose>, today: LocalDate, zone: ZoneId, now: Long): List<Summary> {
    val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val weekAgo = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    val monthAgo = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    return doses.filter { it.due >= monthAgo && it.due <= now }
        .groupBy { it.medicineId }
        .map { (_, those) ->
            Summary(
                name = those.maxBy { it.due }.label,
                week = Plan.tally(those, weekAgo, tomorrow),
                month = Plan.tally(those, monthAgo, tomorrow),
                marks = Plan.marks(those, days, zone),
                asNeededWeek = those.count { it.asNeeded && it.due >= weekAgo },
            )
        }
        .sortedBy { it.name.lowercase() }
}

@Composable
private fun SummaryBlock(s: Summary) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        TextMMD(text = s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        if (s.month.due > 0) {
            TextMMD(text = tallyText(context, R.string.log_week, s.week), style = MaterialTheme.typography.labelSmall)
            TextMMD(text = tallyText(context, R.string.log_month, s.month), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Strip(s.marks)
        }
        if (s.asNeededWeek > 0 || s.month.due == 0) {
            TextMMD(
                text = context.resources.getQuantityString(R.plurals.log_as_needed_week, s.asNeededWeek, s.asNeededWeek.toString()),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun tallyText(context: android.content.Context, period: Int, t: Plan.Tally): String {
    val head = context.getString(period, t.taken.toString(), t.due.toString())
    val rest = listOfNotNull(
        t.skipped.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.log_skipped, it, it.toString()) },
        t.notMarked.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.log_not_marked, it, it.toString()) },
    )
    return (listOf(head) + rest).joinToString(" · ")
}

/**
 * Fourteen days, oldest on the left: a filled square for a day every dose was taken, an open
 * one for a day that was not, and a short line where nothing was due.
 */
@Composable
private fun Strip(marks: List<Plan.Mark>) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in marks) {
            when (m) {
                Plan.Mark.ALL -> Box(Modifier.size(16.dp).background(ink, RoundedCornerShape(2.dp)))
                Plan.Mark.SOME -> Box(Modifier.size(16.dp).border(BorderStroke(2.dp, ink), RoundedCornerShape(2.dp)))
                Plan.Mark.NONE -> Box(Modifier.size(16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Box(Modifier.size(width = 8.dp, height = 2.dp).background(ink))
                }
            }
        }
    }
}
