package com.wanderwildwood.fukuyaku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.alarm.Reminders
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Status
import com.wanderwildwood.fukuyaku.data.Store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A line on the first screen: a dose in the log, or one still to come today. */
private data class Line(val at: Long, val dose: Dose?, val med: Medicine?)

/**
 * Today: anything wrong with the setup first, then any dose from the last two days that was
 * never marked, then today's doses in the order they are due, then the medicines taken only
 * when needed. Nothing here moves on its own; it is read again when something changes.
 */
@Composable
fun TodayScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    val problems = rememberProblems(check) { check++ }

    val store = remember { Store.get(context) }
    val now = remember(version, check) { System.currentTimeMillis() }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val medicines = remember(version) { store.medicines() }
    val byId = remember(medicines) { medicines.associateBy { it.id } }
    val logged = remember(version) { store.between(startOfDay, endOfDay) }
    val earlier = remember(version) {
        store.open().filter { it.due < startOfDay && it.due >= startOfDay - 2 * 86_400_000L && !it.asNeeded }
    }
    val lines = remember(version, now) {
        val inLog = logged.filter { !it.asNeeded }.map { Line(it.due, it, byId[it.medicineId]) }
        val keys = logged.map { it.medicineId to it.due }.toSet()
        val toCome = medicines.filter { it.reminds }.flatMap { m ->
            m.schedule.between(Instant.ofEpochMilli(maxOf(now, m.caughtUp)), Instant.ofEpochMilli(endOfDay - 1), zone)
                .map { it.toEpochMilli() }
                .filter { (m.id to it) !in keys }
                .map { Line(it, null, m) }
        }
        (inLog + toCome).sortedWith(compareBy({ it.at }, { it.med?.name ?: it.dose?.name }))
    }
    val next = remember(version, now) {
        if (lines.any { it.dose == null }) null
        else {
            val later = store.between(endOfDay, endOfDay + 8 * 86_400_000L).groupBy({ it.medicineId }, { it.due })
            medicines.mapNotNull { m -> Plan.nextUnlogged(m, endOfDay - 1, zone, later[m.id].orEmpty().toSet())?.let { it to m } }
                .minByOrNull { it.first }
        }
    }
    val asNeeded = medicines.filter { it.schedule is Schedule.AsNeeded && !it.paused }

    var chosen by remember { mutableStateOf<Line?>(null) }
    var taking by remember { mutableStateOf<Medicine?>(null) }

    LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item(key = "top") { Spacer(Modifier.height(8.dp)) }
        problems.forEachIndexed { i, p -> item(key = "problem:$i") { ProblemCard(p) } }

        if (medicines.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(vertical = 16.dp)) {
                    TextMMD(text = stringResource(R.string.today_empty), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButtonMMD(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        TextMMD(text = stringResource(R.string.add_medicine), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (earlier.isNotEmpty()) {
            item(key = "earlier") { Heading(stringResource(R.string.today_earlier)) }
            for (d in earlier) {
                item(key = "e:${d.id}") {
                    DoseRow(
                        time = Times.whenShort(context, d.due),
                        label = d.label,
                        status = Words.status(context, d, now),
                        bold = true,
                    ) { chosen = Line(d.due, d, byId[d.medicineId]) }
                }
            }
        }

        if (lines.isNotEmpty()) {
            item(key = "today") { Heading(stringResource(R.string.today_heading, Times.day(context, today))) }
            for (l in lines) {
                item(key = "t:${l.dose?.id ?: ("c" + l.med?.id + ":" + l.at)}") {
                    val d = l.dose
                    DoseRow(
                        time = Times.time(context, l.at),
                        label = d?.label ?: l.med?.label.orEmpty(),
                        status = if (d != null) Words.status(context, d, now) else stringResource(R.string.status_later),
                        bold = d != null && d.status == Status.OPEN,
                    ) { chosen = l }
                }
            }
        } else if (medicines.any { it.reminds }) {
            item(key = "none today") {
                TextMMD(
                    text = stringResource(R.string.today_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        next?.let { (at, m) ->
            item(key = "next") {
                TextMMD(
                    text = stringResource(R.string.today_next, Times.whenShort(context, at), m.label),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }

        if (asNeeded.isNotEmpty()) {
            item(key = "needed") { Heading(stringResource(R.string.today_as_needed)) }
            for (m in asNeeded) {
                item(key = "n:${m.id}") {
                    val last = remember(version) { store.lastTaken(m.id) }
                    DoseRow(
                        time = "",
                        label = m.label,
                        status = last?.let { stringResource(R.string.last_taken, Times.whenShort(context, it.marked ?: it.due)) }
                            ?: stringResource(R.string.never_taken),
                        bold = false,
                    ) { taking = m }
                }
            }
        }
        item(key = "foot") { Spacer(Modifier.height(24.dp)) }
    }

    chosen?.let { l ->
        val d = l.dose
        if (d != null) {
            DoseDialog(d, now, onDismiss = { chosen = null }, onEdit = l.med?.let { m -> { chosen = null; onEdit(m.id) } })
        } else if (l.med != null) {
            EarlyDialog(l.med, l.at, onDismiss = { chosen = null })
        }
    }
    taking?.let { m ->
        EInkDialog(onDismiss = { taking = null }) {
            TextMMD(text = m.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (m.notes.isNotBlank()) TextMMD(text = m.notes, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            DialogButton(stringResource(R.string.take_now), filled = true) {
                Reminders.takeAsNeeded(context, m)
                taking = null
            }
            DialogButton(stringResource(R.string.cancel)) { taking = null }
        }
    }
}

/** One dose: its time in a column of its own, then what it is and what became of it. */
@Composable
fun DoseRow(time: String, label: String, status: String, bold: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        if (time.isNotEmpty()) {
            TextMMD(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(92.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            TextMMD(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            )
            TextMMD(text = status, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * A dose in the log, pressed: mark it, or change what it says. A dose marked well after its
 * time asks whether it was taken then or only now, because the log should say which.
 */
@Composable
fun DoseDialog(d: Dose, now: Long, onDismiss: () -> Unit, onEdit: (() -> Unit)? = null) {
    val context = LocalContext.current
    fun mark(status: Status, at: Long = System.currentTimeMillis()) {
        Reminders.mark(context, d.id, status, at)
        onDismiss()
    }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = d.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        TextMMD(
            text = if (d.asNeeded) Words.status(context, d, now)
            else stringResource(R.string.due_at, Times.whenShort(context, d.due)) + " · " + Words.status(context, d, now),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(12.dp))
        when (d.status) {
            Status.OPEN -> {
                if (d.due > now) {
                    DialogButton(stringResource(R.string.take_early), filled = true) { mark(Status.TAKEN) }
                } else if (now - d.due > 30 * 60_000L) {
                    DialogButton(stringResource(R.string.taken_then, Times.time(context, d.due)), filled = true) { mark(Status.TAKEN, d.due) }
                    DialogButton(stringResource(R.string.taken_now)) { mark(Status.TAKEN) }
                } else {
                    DialogButton(stringResource(R.string.action_taken), filled = true) { mark(Status.TAKEN) }
                }
                DialogButton(stringResource(R.string.action_skip)) { mark(Status.SKIPPED) }
            }
            else -> {
                if (d.asNeeded) {
                    DialogButton(stringResource(R.string.remove_from_log)) {
                        Reminders.remove(context, d.id)
                        onDismiss()
                    }
                } else {
                    DialogButton(stringResource(R.string.unmark)) { mark(Status.OPEN) }
                }
                if (d.status == Status.TAKEN && !d.asNeeded) DialogButton(stringResource(R.string.mark_skipped)) { mark(Status.SKIPPED) }
                if (d.status == Status.SKIPPED) DialogButton(stringResource(R.string.action_taken)) { mark(Status.TAKEN) }
            }
        }
        if (onEdit != null) DialogButton(stringResource(R.string.open_medicine)) { onEdit() }
        DialogButton(stringResource(R.string.close)) { onDismiss() }
    }
}

/** A dose still to come, pressed: it can be taken now, before its time. */
@Composable
private fun EarlyDialog(m: Medicine, at: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = m.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        TextMMD(text = stringResource(R.string.due_at, Times.whenShort(context, at)), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        DialogButton(stringResource(R.string.take_early), filled = true) {
            Reminders.takeEarly(context, m, at)
            onDismiss()
        }
        DialogButton(stringResource(R.string.close)) { onDismiss() }
    }
}
