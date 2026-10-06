package com.wanderwildwood.fukuyaku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.fukuyaku.alarm.Notifier
import com.wanderwildwood.fukuyaku.alarm.Reminders
import com.wanderwildwood.fukuyaku.ui.MedicineApp
import com.wanderwildwood.fukuyaku.ui.monochrome

class MainActivity : ComponentActivity() {

    /** A medicine to open straight away, from a refill notice. */
    private val opening = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.channels(this)
        if (savedInstanceState == null) opening.value = medicineIn(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                MedicineApp(opening = opening.value, onOpened = { opening.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        medicineIn(intent)?.let { opening.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app is also a chance to catch up: if the alarm was lost, this is where the
        // doses it should have rung for are written down, and the alarm is set again.
        Reminders.sync(this)
    }

    private fun medicineIn(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_MEDICINE, -1L)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_MEDICINE = "medicine"
    }
}
