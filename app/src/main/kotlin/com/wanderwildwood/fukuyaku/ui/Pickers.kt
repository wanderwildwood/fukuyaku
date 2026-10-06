package com.wanderwildwood.fukuyaku.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.mudita.mmd.components.time.TimeInputMMD
import com.mudita.mmd.components.time.rememberDatePickerMMDState
import com.mudita.mmd.components.time.rememberTimeInputMMDState
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Mudita's time input: the hour and the minute typed, the way the Kompakt's own clock sets
 * them. Always on the 24-hour clock, whatever the phone uses: in 12-hour mode MMD 1.0.2 sets a
 * typed hour as it stands, so "6" with PM showing came out as 6 in the morning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimeInputMMDState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    EInkDialog(onDismiss = onDismiss) {
        TimeInputMMD(state = state, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        DialogButtons(onCancel = onDismiss, onOk = {
            onPick(LocalTime.of(state.hour, state.minute))
            onDismiss()
        })
    }
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
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
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
