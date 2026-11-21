package fr.sevenskies.screenguard.usage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/**
 * Local-only exclusion journal shared by the two usage ledgers and Accessibility.
 * Only a displayed quota/app overlay starts a block; policy alone does not.
 * A persisted last-confirmed timestamp bounds recovery after abrupt process death.
 * No app identifiers, screen contents or other child data are recorded here.
 */
object OverlayUsageGate {
    private const val RETENTION = 4L * 24 * 60 * 60 * 1000
    private const val CONFIRM_INTERVAL = 10_000L
    private var initialized = false
    private var activeSince: Long? = null
    private var lastConfirmed = 0L
    private val closed = mutableListOf<BlockedInterval>()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("screen_guard_overlay_usage", Context.MODE_PRIVATE)

    private fun load(context: Context, now: Long) {
        if (initialized) return
        val p = prefs(context)
        val history = runCatching { JSONArray(p.getString("closed", "[]")) }
            .getOrDefault(JSONArray())
        for (i in 0 until history.length()) {
            val item = history.optJSONArray(i) ?: continue
            val start = item.optLong(0, -1)
            val end = item.optLong(1, -1)
            if (start >= 0 && end > start && end <= now && end > now - RETENTION) {
                closed.add(BlockedInterval(start, end))
            }
        }
        // A new Android process cannot claim that an old overlay is still visible.
        val start = p.getLong("active_start", -1)
        val end = minOf(p.getLong("last_confirmed", -1), now)
        if (start >= 0 && end > start && end > now - RETENTION) {
            closed.add(BlockedInterval(start, end))
        }
        initialized = true
        persist(context, now)
    }

    private fun persist(context: Context, now: Long) {
        closed.removeAll { it.end <= now - RETENTION || it.start >= now }
        val history = JSONArray()
        closed.forEach { history.put(JSONArray().put(it.start).put(it.end)) }
        val ok = prefs(context).edit()
            .putString("closed", history.toString())
            .putLong("active_start", activeSince ?: -1L)
            .putLong("last_confirmed", lastConfirmed)
            .commit()
        if (!ok) Log.w("ScreenGuardUsage", "Overlay usage journal persistence failed")
    }

    @Synchronized
    fun begin(context: Context, now: Long = System.currentTimeMillis()) {
        load(context, now)
        if (activeSince != null) return
        activeSince = now
        lastConfirmed = now
        persist(context, now)
    }

    @Synchronized
    fun confirm(context: Context, now: Long = System.currentTimeMillis()) {
        load(context, now)
        val start = activeSince
        if (start == null || now < start || now < lastConfirmed) {
            activeSince = now
            lastConfirmed = now
            persist(context, now)
        } else if (now - lastConfirmed >= CONFIRM_INTERVAL) {
            lastConfirmed = now
            persist(context, now)
        }
    }

    @Synchronized
    fun end(context: Context, now: Long = System.currentTimeMillis()) {
        load(context, now)
        val start = activeSince ?: return
        if (now > start) closed.add(BlockedInterval(start, now))
        activeSince = null
        lastConfirmed = 0
        persist(context, now)
    }

    @Synchronized
    fun snapshot(context: Context, now: Long): List<BlockedInterval> {
        load(context, now)
        val result = closed.filter { it.end > now - RETENTION && it.start < now }.toMutableList()
        activeSince?.let { if (now > it) result.add(BlockedInterval(it, now)) }
        return result
    }
}
