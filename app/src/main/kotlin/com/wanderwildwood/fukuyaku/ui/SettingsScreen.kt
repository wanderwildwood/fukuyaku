package com.wanderwildwood.fukuyaku.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.alarm.Notifier
import com.wanderwildwood.fukuyaku.alarm.Reminders
import com.wanderwildwood.fukuyaku.data.Settings
import com.wanderwildwood.fukuyaku.data.Store
import com.wanderwildwood.fukuyaku.glance.GlanceProvider
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Settings(context) }
    var v by remember { mutableIntStateOf(0) }
    var check by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        check++
        onPauseOrDispose { }
    }
    val problems = rememberProblems(check) { check++ }
    var pickSnooze by remember { mutableStateOf(false) }
    var pickAgain by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    // Read again after every change; [v] is only here to make that happen.
    val snooze = remember(v) { s.snoozeMinutes }
    val again = remember(v) { s.againMinutes }
    val fullScreen = remember(v) { s.fullScreen }
    val glance = remember(v) { s.onLockScreen }
    val testAt = remember(v, check) { s.testAt }
    // Once the test has rung, the row goes back to saying what it does.
    LaunchedEffect(testAt) {
        val wait = testAt - System.currentTimeMillis()
        if (testAt > 0 && wait > 0) {
            delay(wait + 1_000L)
            v++
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout) },
            )
        },
    ) { padding ->
        LazyColumnMMD(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            problems.forEachIndexed { i, p -> item(key = "problem:$i") { ProblemCard(p) } }

            item(key = "snooze") {
                SettingRow(stringResource(R.string.settings_snooze), minutes(context, snooze)) { pickSnooze = true }
            }
            item(key = "again") {
                SettingRow(
                    stringResource(R.string.settings_again),
                    if (again <= 0) stringResource(R.string.settings_again_never) else stringResource(R.string.settings_again_after, minutes(context, again)),
                ) { pickAgain = true }
            }
            item(key = "full") {
                SwitchRow(stringResource(R.string.settings_full_screen), fullScreen) {
                    s.fullScreen = it
                    v++
                }
            }
            item(key = "sound") {
                SettingRow(stringResource(R.string.settings_sound), null) {
                    Notifier.channels(context)
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, Notifier.DOSES),
                        )
                    }
                }
            }
            item(key = "glance") {
                SwitchRow(stringResource(R.string.settings_glance), glance, note = stringResource(R.string.settings_glance_note)) {
                    s.onLockScreen = it
                    GlanceProvider.changed(context)
                    v++
                }
            }
            item(key = "test") {
                SettingRow(
                    stringResource(R.string.settings_test),
                    if (testAt > System.currentTimeMillis()) stringResource(R.string.settings_test_set, Times.time(context, testAt))
                    else stringResource(R.string.settings_test_note),
                ) {
                    Reminders.test(context)
                    v++
                }
            }
            item(key = "clear") {
                ArmedRow(stringResource(R.string.settings_clear_log), stringResource(R.string.settings_clear_log_armed)) {
                    Store.get(context).clearLog()
                    Reminders.sync(context)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (pickSnooze) {
        OptionsDialog(
            title = stringResource(R.string.settings_snooze),
            options = listOf(5, 10, 15, 30).map { it to minutes(context, it) },
            chosen = snooze,
            onPick = {
                s.snoozeMinutes = it
                v++
            },
            onDismiss = { pickSnooze = false },
        )
    }
    if (pickAgain) {
        OptionsDialog(
            title = stringResource(R.string.settings_again),
            options = listOf(0 to stringResource(R.string.settings_again_never)) +
                listOf(15, 30, 60).map { it to context.getString(R.string.settings_again_after, minutes(context, it)) },
            chosen = again,
            onPick = {
                s.againMinutes = it
                v++
            },
            onDismiss = { pickAgain = false },
        )
    }
}

private fun minutes(context: android.content.Context, n: Int): String =
    context.resources.getQuantityString(R.plurals.minutes, n, n.toString())
