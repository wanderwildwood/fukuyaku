package com.wanderwildwood.fukuyaku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.fukuyaku.R

/**
 * The whole app: three tabs (today, the medicines, the log), with a medicine's page and the
 * settings opening over them. A medicine being edited is [NEW] when it is a new one.
 */
@Composable
fun MedicineApp(opening: Long?, onOpened: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var licences by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(opening) {
        if (opening != null) {
            settings = false
            editing = opening
            onOpened()
        }
    }

    val edit = editing
    BackHandler(enabled = licences) { licences = false }
    when {
        licences -> LicencesScreen(onClose = { licences = false })
        edit != null -> EditScreen(id = edit, onDone = { editing = null })
        settings -> SettingsScreen(onBack = { settings = false }, onAbout = { about = true })
        else -> {
            BackHandler(enabled = tab != 0) { tab = 0 }
            MainScreen(
                tab = tab,
                onTab = { tab = it },
                onEdit = { editing = it },
                onSettings = { settings = true },
                onAbout = { about = true },
            )
        }
    }
    if (about) AboutDialog(onDismiss = { about = false }, onLicences = { about = false; licences = true })

    // Said once, on the first start: what this is not. About carries the same words for good.
    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettings = remember { com.wanderwildwood.fukuyaku.data.Settings(context) }
    var firstRun by rememberSaveable { mutableStateOf(!appSettings.disclaimerSeen) }
    if (firstRun) {
        EInkDialog(onDismiss = {}) {
            TextMMD(
                text = stringResource(R.string.first_run_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            TextMMD(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
            TextMMD(
                text = stringResource(R.string.about_privacy_short),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
            ButtonMMD(
                onClick = { appSettings.disclaimerSeen = true; firstRun = false },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(62.dp),
            ) { TextMMD(text = stringResource(R.string.first_run_ok), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

const val NEW = 0L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    tab: Int,
    onTab: (Int) -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = {
                    BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
                    BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StoneTabs(
                listOf(R.string.tab_today, R.string.tab_medicines, R.string.tab_log).map { stringResource(it) },
                tab,
                onTab,
            )
            when (tab) {
                0 -> TodayScreen(onAdd = { onEdit(NEW) }, onEdit = onEdit)
                1 -> MedicinesScreen(onAdd = { onEdit(NEW) }, onEdit = onEdit)
                else -> LogScreen()
            }
        }
    }
}
