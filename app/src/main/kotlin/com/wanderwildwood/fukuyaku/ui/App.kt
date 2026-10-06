package com.wanderwildwood.fukuyaku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
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

    LaunchedEffect(opening) {
        if (opening != null) {
            settings = false
            editing = opening
            onOpened()
        }
    }

    val edit = editing
    when {
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
    if (about) AboutDialog(onDismiss = { about = false })
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
            PrimaryTabRowMMD(selectedTabIndex = tab) {
                listOf(R.string.tab_today, R.string.tab_medicines, R.string.tab_log).forEachIndexed { i, label ->
                    TabMMD(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        text = {
                            TextMMD(
                                text = stringResource(label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            when (tab) {
                0 -> TodayScreen(onAdd = { onEdit(NEW) }, onEdit = onEdit)
                1 -> MedicinesScreen(onAdd = { onEdit(NEW) }, onEdit = onEdit)
                else -> LogScreen()
            }
        }
    }
}
