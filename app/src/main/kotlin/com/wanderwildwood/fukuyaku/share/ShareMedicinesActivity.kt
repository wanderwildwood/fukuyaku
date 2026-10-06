package com.wanderwildwood.fukuyaku.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.data.Days
import com.wanderwildwood.fukuyaku.data.Schedule
import com.wanderwildwood.fukuyaku.data.Store
import com.wanderwildwood.fukuyaku.ui.Words
import com.wanderwildwood.fukuyaku.ui.monochrome

/**
 * The medicine list, for another app that asks: Field Kit's emergency card first. The person
 * sees exactly what would go, and which app asked, and nothing goes until Share is pressed.
 *
 * Started for a result with action `com.wanderwildwood.fukuyaku.action.SHARE_MEDICINES` and
 * this package set. Share returns RESULT_OK with `Intent.EXTRA_TEXT`: one medicine per line,
 * "Lisinopril 10 mg, every day at 8:00 AM". Anything else returns RESULT_CANCELED. Paused
 * medicines are left out; nothing from the log, the supply or the pharmacy is included.
 */
class ShareMedicinesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val lines = lines(this)
        val asker = callingPackage?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                BackHandler { finish() }
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
                        TextMMD(text = stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        TextMMD(
                            text = when {
                                lines.isEmpty() -> stringResource(R.string.share_none)
                                asker != null -> stringResource(R.string.share_ask, asker)
                                else -> stringResource(R.string.share_ask_unknown)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LazyColumnMMD(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            lines.forEachIndexed { i, line ->
                                item(key = i) {
                                    TextMMD(text = line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                        if (lines.isNotEmpty()) {
                            ButtonMMD(
                                onClick = {
                                    setResult(Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n")))
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { TextMMD(text = stringResource(R.string.share_yes), style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButtonMMD(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            TextMMD(
                                text = stringResource(if (lines.isEmpty()) R.string.close else R.string.share_no),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** "Lisinopril 10 mg, every day at 8:00 AM", one per medicine that is not paused. */
        fun lines(context: Context): List<String> =
            Store.get(context).medicines().filter { !it.paused }.map { m ->
                val name = listOf(m.name, m.amount).filter { it.isNotBlank() }.joinToString(" ")
                val words = Words.schedule(context, m.schedule)
                // "every day at …" reads on from the name; a list of days keeps its capitals.
                val schedule = if (m.schedule is Schedule.AtTimes && (m.schedule as Schedule.AtTimes).days is Days.OnDays) words
                else words.replaceFirstChar { it.lowercase() }
                "$name, $schedule"
            }
    }
}
