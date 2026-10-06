package com.wanderwildwood.fukuyaku.glance

import android.content.Context
import com.wanderwildwood.fukuyaku.R
import com.wanderwildwood.fukuyaku.Times
import com.wanderwildwood.fukuyaku.data.Plan
import com.wanderwildwood.fukuyaku.data.Settings
import com.wanderwildwood.fukuyaku.data.Store
import java.time.ZoneId

/**
 * The lock-screen lines for Glance: a dose that rang and has not been marked, in bold, then
 * the next one due. Off until switched on in Settings, because it names medicines to anyone
 * who picks the phone up.
 */
class NextDoseOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = Settings(context).onLockScreen

    override fun lines(context: Context): List<Line> {
        val store = Store.get(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val heading = context.getString(R.string.app_name)
        val out = mutableListOf<Line>()

        store.open()
            .filter { it.rang != null && !it.asNeeded && now - it.due <= Plan.RING_WITHIN_MS && it.due <= now }
            .groupBy { it.medicineId }
            .mapNotNull { (_, those) -> those.maxByOrNull { it.due } }
            .sortedBy { it.due }
            .take(2)
            .forEach {
                out += Line(
                    text = context.getString(R.string.glance_not_marked, it.label),
                    lead = Times.whenShort(context, it.due),
                    bold = true,
                )
            }

        val medicines = store.medicines()
        val logged = store.between(now, now + 8 * 24 * 3_600_000L).groupBy({ it.medicineId }, { it.due })
        val next = medicines.mapNotNull { m ->
            Plan.nextUnlogged(m, now, zone, logged[m.id].orEmpty().toSet())?.let { it to m }
        }
        val soonest = next.minOfOrNull { it.first }
        if (soonest != null) {
            val those = next.filter { it.first == soonest }.map { it.second.label }
            out += Line(text = those.joinToString(", "), lead = Times.whenShort(context, soonest))
        }
        return out.take(3).mapIndexed { i, l -> if (i == 0) l.copy(heading = heading) else l }
    }
}
