package com.wanderwildwood.fukuyaku.share

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.alarm.Reminders
import com.wanderwildwood.fukuyaku.data.Medicine
import com.wanderwildwood.fukuyaku.data.Store
import com.wanderwildwood.fukuyaku.ui.CheckRow
import com.wanderwildwood.fukuyaku.ui.monochrome

/**
 * A pharmacy or a doctor handed over by another app: Contacts' "Set as pharmacy in Medicine"
 * and "Set as doctor in Medicine". Both belong to each medicine, so this asks which ones,
 * every medicine ticked to start, and nothing changes until Set is pressed. Then each ticked
 * medicine gets it exactly as if it had been chosen from Contacts on its own page.
 *
 * Actions `com.wanderwildwood.fukuyaku.action.SET_PHARMACY` ([SetPharmacyActivity]) and
 * `….SET_DOCTOR` ([SetDoctorActivity]), with [EXTRA_NAME] and [EXTRA_NUMBER] (a number is
 * needed), and [EXTRA_CONTACT], the contact's lookup URI, when there is one. Set returns
 * RESULT_OK; anything else RESULT_CANCELED.
 */
abstract class SetWhoActivity(
    private val title: Int,
    private val ask: Int,
    private val none: Int,
    private val yes: Int,
    private val done: Int,
) : ComponentActivity() {

    /** [m] with the one that arrived set on it. */
    abstract fun apply(m: Medicine, given: Given): Medicine

    /** Who [m] has now, shown under it so a medicine with someone else can be unticked. */
    abstract fun current(m: Medicine): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val given = read(
            intent.getStringExtra(EXTRA_NAME),
            intent.getStringExtra(EXTRA_NUMBER),
            intent.getStringExtra(EXTRA_CONTACT),
        )
        if (given == null) {
            finish()
            return
        }
        val medicines = Store.get(this).medicines()
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                BackHandler { finish() }
                var ticked by rememberSaveable { mutableStateOf(medicines.map { it.id }) }
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
                        TextMMD(text = stringResource(title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        TextMMD(
                            text = listOf(given.name, given.number).filter { it.isNotEmpty() }.joinToString("  "),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextMMD(
                            text = stringResource(if (medicines.isEmpty()) none else ask),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LazyColumnMMD(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            medicines.forEach { m ->
                                item(key = m.id) {
                                    val now = current(m).takeIf { it.isNotBlank() && it != given.name.ifBlank { given.number } }
                                    CheckRow(if (now == null) m.label else m.label + " (" + now + ")", m.id in ticked) { on ->
                                        ticked = if (on) ticked + m.id else ticked - m.id
                                    }
                                }
                            }
                        }
                        if (medicines.isNotEmpty()) {
                            ButtonMMD(
                                onClick = {
                                    val chosen = medicines.filter { it.id in ticked }
                                    chosen.forEach { m ->
                                        Reminders.save(this@SetWhoActivity, apply(m, given))
                                    }
                                    Toast.makeText(
                                        this@SetWhoActivity,
                                        resources.getQuantityString(done, chosen.size, chosen.size),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    setResult(Activity.RESULT_OK)
                                    finish()
                                },
                                enabled = ticked.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { TextMMD(text = stringResource(yes), style = MaterialTheme.typography.bodyMedium) }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButtonMMD(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            TextMMD(
                                text = stringResource(if (medicines.isEmpty()) R.string.close else R.string.cancel),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    /** A pharmacy or doctor as it arrived, cleaned. */
    data class Given(val name: String, val number: String, val contact: String)

    companion object {
        const val EXTRA_NAME = "com.wanderwildwood.fukuyaku.extra.NAME"
        const val EXTRA_NUMBER = "com.wanderwildwood.fukuyaku.extra.NUMBER"
        const val EXTRA_CONTACT = "com.wanderwildwood.fukuyaku.extra.CONTACT"

        /**
         * What arrived, or null when there is no number to call. Names and numbers are cut to
         * a sensible length; the contact is kept only if it is an entry in the phone's own
         * contacts, since "Open in Contacts" opens it later.
         */
        fun read(name: String?, number: String?, contact: String?): Given? {
            val n = number?.replace(Regex("\\s+"), " ")?.trim()?.take(40).orEmpty()
            if (n.none { it.isDigit() }) return null
            val who = name?.replace(Regex("\\s+"), " ")?.trim()?.take(100).orEmpty()
            val entry = contact?.trim()?.takeIf { it.startsWith("content://com.android.contacts/") && it.length <= 500 }.orEmpty()
            return Given(who, n, entry)
        }
    }
}

/** Contacts' "Set as pharmacy in Medicine". */
class SetPharmacyActivity : SetWhoActivity(
    R.string.edit_pharmacy, R.string.pharmacy_set_ask, R.string.pharmacy_set_none, R.string.pharmacy_set_yes, R.plurals.pharmacy_set_done,
) {
    override fun apply(m: Medicine, given: Given) =
        m.copy(pharmacyName = given.name, pharmacyNumber = given.number, pharmacyContact = given.contact)

    override fun current(m: Medicine) = m.pharmacyName.ifBlank { m.pharmacyNumber }
}

/** Contacts' "Set as doctor in Medicine". */
class SetDoctorActivity : SetWhoActivity(
    R.string.edit_doctor, R.string.doctor_set_ask, R.string.doctor_set_none, R.string.doctor_set_yes, R.plurals.doctor_set_done,
) {
    override fun apply(m: Medicine, given: Given) =
        m.copy(doctorName = given.name, doctorNumber = given.number, doctorContact = given.contact)

    override fun current(m: Medicine) = m.doctor
}
