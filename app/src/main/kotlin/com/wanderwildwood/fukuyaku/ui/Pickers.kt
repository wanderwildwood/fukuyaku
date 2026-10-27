package com.wanderwildwood.fukuyaku.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.time.DatePickerFormatterMMD
import com.mudita.mmd.components.time.DatePickerMMD
import com.mudita.mmd.components.time.rememberDatePickerMMDState
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.data.Plan
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The hour and the minute typed in two plain boxes ([TimeBoxes]). Always on the 24-hour clock,
 * whatever the phone uses: in 12-hour mode MMD's input set a typed hour as it stands, so "6"
 * with PM showing came out as 6 in the morning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    EInkDialog(onDismiss = onDismiss) {
        TimeBoxes(hour, minute, { h, m -> hour = h; minute = m })
        Spacer(Modifier.height(8.dp))
        DialogButtons(onCancel = onDismiss, onOk = {
            onPick(LocalTime.of(hour, minute))
            onDismiss()
        })
    }
}

/**
 * When a dose was taken, chosen in place in the dialog that asked rather than in a second one
 * over it: the day, from today, yesterday and the day it was due, never before that day, then
 * the hour and minute on Mudita's time input, on the 24-hour clock as [TimeDialog] is. It starts
 * at now; a time still to come is refused, and says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.TakenAtPicker(due: Long?, onPick: (Long) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val now = remember { LocalDateTime.now(zone) }
    val days = remember { Plan.takenDays(now.toLocalDate(), due?.let { Times.date(it) }) }
    var day by remember { mutableStateOf(days.first()) }
    var ahead by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    TextMMD(text = stringResource(R.string.taken_when), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
    for (d in days) {
        ChoiceRow(Times.relativeDay(context, d), d == day) {
            day = d
            ahead = false
        }
    }
    Spacer(Modifier.height(8.dp))
    TimeBoxes(hour, minute, { h, m -> hour = h; minute = m })
    if (ahead) {
        TextMMD(
            text = stringResource(R.string.taken_ahead),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    DialogButtons(onCancel = onCancel, onOk = {
        val at = Plan.takenAt(day, LocalTime.of(hour, minute), zone, System.currentTimeMillis())
        if (at == null) ahead = true else onPick(at)
    })
}

/**
 * Mudita's own date picker, on the whole screen: it is Material's picker underneath and wants
 * 360dp of width, which a dialog's rim on a 366dp screen does not leave it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberDatePickerMMDState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    // MMD hands its formatter UTC midnight; read in the phone's zone it is the day before
    // anywhere west of Greenwich, so it is read in UTC here.
    val formatter = object : DatePickerFormatterMMD {
        override fun formatMonthYear(monthMillis: Long?, locale: java.util.Locale): String? =
            monthMillis?.let {
                val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(d)
            }

        override fun formatDate(dateMillis: Long?, locale: java.util.Locale, forContentDescription: Boolean): String? =
            dateMillis?.let { Times.day(context, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                // Dimming by nothing still counts as dimming: while a dimming window is up,
                // Android turns the status-bar icons white, and on the white bar they vanish.
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
                DatePickerMMD(state = state, dateFormatter = formatter, title = null, headline = null, showModeToggle = false)
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(horizontal = 20.dp)) {
                    DialogButtons(onCancel = onDismiss, onOk = {
                        state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        onDismiss()
                    })
                }
            }
        }
    }
}

@Composable
fun DialogButtons(onCancel: () -> Unit, onOk: () -> Unit, okLabel: String = stringResource(R.string.ok)) {
    Row(Modifier.fillMaxWidth()) {
        OutlinedButtonMMD(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) {
            TextMMD(text = stringResource(R.string.cancel), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        ButtonMMD(onClick = onOk, modifier = Modifier.weight(1f).height(48.dp)) {
            TextMMD(text = okLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A short list to choose one from; the chosen one is bold. */
@Composable
fun <T> OptionsDialog(title: String, options: List<Pair<T, String>>, chosen: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        for ((value, label) in options) {
            ChoiceRow(label, value == chosen) {
                onPick(value)
                onDismiss()
            }
        }
    }
}

/** A full-width button inside a dialog. */
@Composable
fun DialogButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp)
    if (filled) {
        ButtonMMD(onClick = onClick, modifier = modifier) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium) }
    } else {
        OutlinedButtonMMD(onClick = onClick, modifier = modifier) { TextMMD(text = text, style = MaterialTheme.typography.bodyMedium) }
    }
}
