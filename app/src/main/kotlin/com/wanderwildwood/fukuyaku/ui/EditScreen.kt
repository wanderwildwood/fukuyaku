package com.wanderwildwood.fukuyaku.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.alarm.Reminders
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Store
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A medicine's page, new or existing: its name, when it is taken, what is left of it, its
 * pharmacy, notes. Back saves it, as Save does. When it cannot be saved yet the page says
 * why, and a second Back within four seconds leaves without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(id: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store.get(context) }
    val original = remember(id) { if (id == NEW) null else store.medicine(id) }
    val start = remember(original) { Draft.of(original) }
    var d by rememberSaveable(id, stateSaver = Draft.saver) { mutableStateOf(start) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Int?>(null) }
    var pickHoursFrom by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }

    LaunchedEffect(leaving) {
        if (leaving) {
            delay(4000)
            leaving = false
        }
    }

    fun save(): Boolean {
        val p = d.problem()
        if (p != null) {
            problem = p
            return false
        }
        Reminders.save(context, d.toMedicine(original))
        return true
    }

    fun back() {
        when {
            d == start -> onDone()
            leaving -> onDone()
            save() -> onDone()
            else -> leaving = true
        }
    }
    BackHandler { back() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(2), c.getString(3))
                    d = d.copy(
                        pharmacyName = c.getString(0).orEmpty(),
                        pharmacyNumber = c.getString(1).orEmpty(),
                        pharmacyContact = lookup?.toString().orEmpty(),
                    )
                }
            }
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.pharmacy_unreadable), Toast.LENGTH_LONG).show()
        }
    }

    fun open(intent: Intent, failure: Int) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, context.getString(failure), Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = original?.name ?: stringResource(R.string.edit_new_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back)) { back() } },
            )
        },
    ) { padding ->
        LazyColumnMMD(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            problem?.let { p ->
                item(key = "problem") {
                    TextMMD(
                        text = stringResource(p) + if (leaving) " " + stringResource(R.string.edit_back_again) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item(key = "name") {
                Field(stringResource(R.string.edit_name), d.name, { d = d.copy(name = it); problem = null }, hint = stringResource(R.string.edit_name_hint))
            }
            item(key = "amount") {
                Field(stringResource(R.string.edit_amount), d.amount, { d = d.copy(amount = it) }, hint = stringResource(R.string.edit_amount_hint), words = false)
            }

            item(key = "when") { Heading(stringResource(R.string.edit_when)) }
            item(key = "k1") { ChoiceRow(stringResource(R.string.edit_kind_times), d.kind == Kind.TIMES) { d = d.copy(kind = Kind.TIMES) } }
            item(key = "k2") { ChoiceRow(stringResource(R.string.edit_kind_hours), d.kind == Kind.HOURS) { d = d.copy(kind = Kind.HOURS) } }
            item(key = "k3") { ChoiceRow(stringResource(R.string.edit_kind_needed), d.kind == Kind.NEEDED) { d = d.copy(kind = Kind.NEEDED) } }

            when (d.kind) {
                Kind.TIMES -> {
                    item(key = "times") { Heading(stringResource(R.string.edit_times)) }
                    d.times.forEachIndexed { i, t ->
                        item(key = "time:$i") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                TextMMD(
                                    text = Times.time(context, t),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f).clickable { pickTime = i }.padding(vertical = 10.dp),
                                )
                                BarButton(Icons.Close, stringResource(R.string.cd_remove_time)) {
                                    d = d.copy(times = d.times.filterIndexed { j, _ -> j != i })
                                }
                            }
                        }
                    }
                    item(key = "add time") {
                        SettingRow(stringResource(R.string.edit_add_time), null) { pickTime = -1 }
                    }
                    item(key = "days") { Heading(stringResource(R.string.edit_days)) }
                    item(key = "d1") { ChoiceRow(stringResource(R.string.edit_days_every), d.dayKind == DayKind.EVERY) { d = d.copy(dayKind = DayKind.EVERY) } }
                    item(key = "d2") { ChoiceRow(stringResource(R.string.edit_days_week), d.dayKind == DayKind.WEEK) { d = d.copy(dayKind = DayKind.WEEK) } }
                    if (d.dayKind == DayKind.WEEK) {
                        item(key = "week") { WeekPicker(d.week) { d = d.copy(week = it) } }
                    }
                    item(key = "d3") { ChoiceRow(stringResource(R.string.edit_days_every_n), d.dayKind == DayKind.EVERY_N) { d = d.copy(dayKind = DayKind.EVERY_N) } }
                    if (d.dayKind == DayKind.EVERY_N) {
                        item(key = "every n") {
                            Field(stringResource(R.string.edit_every_n), d.everyN, { d = d.copy(everyN = it.filter(Char::isDigit).take(3)) }, number = true)
                        }
                        item(key = "from") {
                            SettingRow(stringResource(R.string.edit_starting), Times.day(context, d.from)) { pickFrom = true }
                        }
                    }
                }
                Kind.HOURS -> {
                    item(key = "hours") {
                        Field(stringResource(R.string.edit_hours), d.hours, { d = d.copy(hours = it.filter(Char::isDigit).take(3)) }, number = true)
                    }
                    item(key = "hours from") {
                        SettingRow(stringResource(R.string.edit_first_dose), Times.time(context, d.hoursFrom)) { pickHoursFrom = true }
                    }
                    item(key = "hours note") {
                        TextMMD(text = stringResource(R.string.edit_hours_note), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Kind.NEEDED -> item(key = "needed note") {
                    TextMMD(
                        text = stringResource(R.string.edit_needed_note),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            item(key = "count head") { Heading(stringResource(R.string.edit_supply)) }
            item(key = "counting") {
                SwitchRow(stringResource(R.string.edit_counting), d.counting) { d = d.copy(counting = it) }
            }
            if (d.counting) {
                item(key = "left") {
                    Field(stringResource(R.string.edit_left), d.left, { d = d.copy(left = it.filter(Char::isDigit).take(5)) }, number = true)
                }
                item(key = "per dose") {
                    Field(stringResource(R.string.edit_per_dose), d.perDose, { d = d.copy(perDose = it.filter(Char::isDigit).take(3)) }, number = true)
                }
                item(key = "warn") {
                    Field(stringResource(R.string.edit_warn_at), d.warnAt, { d = d.copy(warnAt = it.filter(Char::isDigit).take(5)) }, number = true, hint = stringResource(R.string.edit_warn_hint))
                }
                val counted = if (d.problem() == null) d.toMedicine(original) else null
                val days = counted?.let { Plan.daysLeft(it) }
                if (counted != null && days != null) {
                    item(key = "lasts") {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            TextMMD(
                                text = context.resources.getQuantityString(R.plurals.lasts_about, days, days.toString(), Times.day(context, LocalDate.now().plusDays(days.toLong()))),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButtonMMD(
                                onClick = {
                                    val on = LocalDate.now().plusDays(maxOf(0, days - 3).toLong())
                                    val begin = on.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    open(
                                        Intent(Intent.ACTION_INSERT)
                                            .setData(CalendarContract.Events.CONTENT_URI)
                                            .putExtra(CalendarContract.Events.TITLE, context.getString(R.string.refill_event_title, counted.name))
                                            .putExtra(
                                                CalendarContract.Events.DESCRIPTION,
                                                context.resources.getQuantityString(R.plurals.refill_event_text, counted.left ?: 0, (counted.left ?: 0).toString(), Times.day(context, LocalDate.now())),
                                            )
                                            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 86_400_000L),
                                        R.string.no_calendar,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { TextMMD(text = stringResource(R.string.refill_to_calendar), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            item(key = "pharmacy head") { Heading(stringResource(R.string.edit_pharmacy)) }
            item(key = "pharmacy name") {
                Field(stringResource(R.string.edit_pharmacy_name), d.pharmacyName, { d = d.copy(pharmacyName = it, pharmacyContact = "") })
            }
            item(key = "pharmacy number") {
                Field(stringResource(R.string.edit_pharmacy_number), d.pharmacyNumber, { d = d.copy(pharmacyNumber = it, pharmacyContact = "") }, phone = true)
            }
            item(key = "pharmacy buttons") {
                Column(Modifier.padding(vertical = 4.dp)) {
                    OutlinedButtonMMD(
                        onClick = {
                            runCatching {
                                pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                            }.onFailure {
                                Toast.makeText(context, context.getString(R.string.no_contacts), Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { TextMMD(text = stringResource(R.string.pharmacy_choose), style = MaterialTheme.typography.bodySmall) }
                    if (d.pharmacyNumber.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth()) {
                            OutlinedButtonMMD(
                                onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(d.pharmacyNumber))), R.string.no_dialer) },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { TextMMD(text = stringResource(R.string.pharmacy_call), style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButtonMMD(
                                onClick = {
                                    open(
                                        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(d.pharmacyNumber)))
                                            .putExtra("sms_body", context.getString(R.string.pharmacy_text_body, d.toMedicine(original).label)),
                                        R.string.no_messaging,
                                    )
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { TextMMD(text = stringResource(R.string.pharmacy_text), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (d.pharmacyContact.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButtonMMD(
                            onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(d.pharmacyContact)), R.string.no_contacts) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { TextMMD(text = stringResource(R.string.pharmacy_open_contact), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            item(key = "notes head") { Heading(stringResource(R.string.edit_notes)) }
            item(key = "notes") {
                Field(stringResource(R.string.edit_notes_label), d.notes, { d = d.copy(notes = it) }, hint = stringResource(R.string.edit_notes_hint), singleLine = false)
            }

            if (original != null && d.kind != Kind.NEEDED) {
                item(key = "paused") {
                    SwitchRow(stringResource(R.string.edit_paused), d.paused, note = stringResource(R.string.edit_paused_note)) { d = d.copy(paused = it) }
                }
            }

            item(key = "save") {
                ButtonMMD(
                    onClick = { if (save()) onDone() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(52.dp),
                ) { TextMMD(text = stringResource(R.string.save), style = MaterialTheme.typography.bodyMedium) }
            }

            if (original != null) {
                item(key = "delete") {
                    ArmedRow(stringResource(R.string.edit_delete), stringResource(R.string.edit_delete_armed)) {
                        Reminders.delete(context, original.id)
                        onDone()
                    }
                }
            }
            item(key = "foot") { Spacer(Modifier.height(24.dp)) }
        }
    }

    pickTime?.let { i ->
        val initial = if (i >= 0) d.times[i] else d.times.maxOrNull()?.plusHours(4)?.takeIf { it.isAfter(d.times.max()) } ?: LocalTime.of(8, 0)
        TimeDialog(
            initial = initial,
            onPick = { t ->
                d = d.copy(times = (if (i >= 0) d.times.mapIndexed { j, old -> if (j == i) t else old } else d.times + t).distinct().sorted())
                problem = null
            },
            onDismiss = { pickTime = null },
        )
    }
    if (pickHoursFrom) {
        TimeDialog(d.hoursFrom, onPick = { d = d.copy(hoursFrom = it) }, onDismiss = { pickHoursFrom = false })
    }
    if (pickFrom) {
        DateDialog(d.from, onPick = { d = d.copy(from = it) }, onDismiss = { pickFrom = false })
    }
}

/** The seven days, each pressed on or off; a chosen day is filled. */
@Composable
private fun WeekPicker(chosen: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val context = LocalContext.current
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    // Monday first, as ISO counts them; a week that starts on Sunday reads the same set.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)) {
        for (day in DayOfWeek.entries) {
            val on = day in chosen
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(BorderStroke(2.dp, ink), RoundedCornerShape(8.dp))
                    .background(if (on) ink else paper, RoundedCornerShape(8.dp))
                    .clickable { onChange(if (on) chosen - day else chosen + day) },
            ) {
                TextMMD(
                    text = Words.dayName(context, day).take(2),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) paper else ink,
                )
            }
        }
    }
}
