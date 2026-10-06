package com.wanderwildwood.fukuyaku.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.data.Dose
import com.wanderwildwood.fukuyaku.data.Settings
import com.wanderwildwood.fukuyaku.data.Status
import com.wanderwildwood.fukuyaku.data.Store
import com.wanderwildwood.fukuyaku.ui.monochrome

/**
 * The reminder as a whole screen, shown over the lock screen when a dose rings with the
 * screen off, and when a reminder's notification is pressed. Large buttons, the medicine's
 * name and its notes; nothing has to be unlocked to answer it. It lists every dose ringing
 * at the time, so three medicines due at 08:00 are one screen, not three.
 */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    Ringing(onDone = ::finish)
                }
            }
        }
    }
}

@Composable
private fun Ringing(onDone: () -> Unit) {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    val settings = remember { Settings(context) }
    val ids = remember(version) { Notifier.ringing(context) }
    val doses = remember(version) {
        Store.get(context).open().filter { it.id.toInt() in ids }
    }
    val test = Notifier.TEST_ID in ids
    LaunchedEffect(doses.isEmpty(), test) { if (doses.isEmpty() && !test) onDone() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        TextMMD(text = stringResource(R.string.ringing_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumnMMD(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (test) {
                item(key = "test") {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        TextMMD(text = stringResource(R.string.test_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        TextMMD(text = stringResource(R.string.test_screen), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            for (d in doses) {
                item(key = d.id) { RingingDose(d) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (doses.isNotEmpty()) {
            OutlinedButtonMMD(
                onClick = {
                    doses.forEach { Reminders.snooze(context, it.id) }
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { TextMMD(
                text = if (doses.size == 1) stringResource(R.string.action_snooze, settings.snoozeMinutes)
                else stringResource(R.string.action_snooze_all, settings.snoozeMinutes),
                style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButtonMMD(
            onClick = {
                if (test) Notifier.cancel(context, Notifier.TEST_ID.toLong())
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { TextMMD(text = stringResource(R.string.ringing_later), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun RingingDose(d: Dose) {
    val context = LocalContext.current
    val notes = remember(d.medicineId) { Store.get(context).medicine(d.medicineId)?.notes.orEmpty() }
    Column(Modifier.padding(vertical = 12.dp)) {
        TextMMD(text = d.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (d.amount.isNotBlank()) TextMMD(text = d.amount, style = MaterialTheme.typography.titleMedium)
        TextMMD(
            text = stringResource(R.string.notify_due, Times.whenShort(context, d.due)),
            style = MaterialTheme.typography.bodyMedium,
        )
        val last = remember(d.id) { Notifier.lastTaken(context, d) }
        if (last != null) TextMMD(text = last, style = MaterialTheme.typography.bodyMedium)
        if (notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            TextMMD(text = notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            ButtonMMD(
                onClick = { Reminders.mark(context, d.id, Status.TAKEN) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { TextMMD(text = stringResource(R.string.action_taken), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
            OutlinedButtonMMD(
                onClick = { Reminders.mark(context, d.id, Status.SKIPPED) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { TextMMD(text = stringResource(R.string.action_skip), style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDividerMMD()
    }
}
