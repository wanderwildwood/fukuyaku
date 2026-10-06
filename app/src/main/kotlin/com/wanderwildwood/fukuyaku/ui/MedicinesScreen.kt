package com.wanderwildwood.fukuyaku.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Store

/** Every medicine, with when it is taken and, where it is counted, what is left. */
@Composable
fun MedicinesScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val version by Store.version.collectAsState()
    val medicines = remember(version) { Store.get(context).medicines() }

    LazyColumnMMD(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item(key = "add") {
            OutlinedButtonMMD(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).height(48.dp)) {
                TextMMD(text = stringResource(R.string.add_medicine), style = MaterialTheme.typography.bodyMedium)
            }
        }
        for (m in medicines) {
            item(key = m.id) {
                MedicineRow(m) { onEdit(m.id) }
                HorizontalDividerMMD()
            }
        }
        item(key = "foot") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MedicineRow(m: Medicine, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        TextMMD(text = m.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        val schedule = Words.schedule(context, m.schedule)
        TextMMD(
            text = if (m.paused) stringResource(R.string.paused_then, schedule) else schedule,
            style = MaterialTheme.typography.labelSmall,
        )
        Words.supply(context, m)?.let {
            TextMMD(
                text = if (m.low) stringResource(R.string.refill_soon, it) else it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (m.low) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
