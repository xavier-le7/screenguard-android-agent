package fr.sevenskies.screenguard.tracking

import android.content.Context
import fr.sevenskies.screenguard.storage.TrackingStatus
import org.json.JSONArray
import java.time.Instant

/**
 * Application-owned diagnostic events only; never Android logcat, URL, token,
 * user name, message text, package name, raw exception or Settings content.
 * Bounded persistence lets the server's existing fetch_logs endpoint work after
 * an agent restart without enabling remote access to arbitrary device logs.
 */
class AgentDiagnostics(context: Context) {
    enum class Event {
        TRACKING_STARTED, TRACKING_STOPPED, NETWORK_CONNECTED, NETWORK_INTERRUPTED,
        POLICY_RECEIVED, LOCK_REQUESTED, PARENT_NOTIFICATION_RECEIVED,
        ACCESSIBILITY_CONNECTED, ACCESSIBILITY_STOPPED, AGENT_UNPAIRED,
        FETCH_LOGS_REQUESTED
    }
    private val prefs = context.getSharedPreferences("screen_guard_diagnostics", Context.MODE_PRIVATE)

    fun record(event: Event) = synchronized(LOCK) {
        val history = readRecords()
        history.add(Instant.now().toString() + " " + event.name.lowercase())
        save(history.takeLast(MAX_LINES))
    }

    /**
     * The first line is a current health summary (flags only, no identifying
     * values). Exactly the last 49 events follow it, so the upstream server's
     * fetch_agent_logs can return up to 50 lines within its 5-second timeout.
     */
    fun snapshot(state: TrackingStatus, usageAllowed: Boolean): List<String> = synchronized(LOCK) {
        val summary = Instant.now().toString() +
            " health tracking=" + state.trackingEnabled +
            " service=" + state.serviceRunning +
            " usage_permission=" + usageAllowed +
            " accessibility=" + state.accessibilityServiceConnected +
            " restrictions=" + state.restrictionsEnabled +
            " settings_filter=" + state.settingsGuardEnabled
        listOf(summary) + readRecords().takeLast(MAX_LINES - 1)
    }

    fun recordAppSync(apps: Int, groups: Int) {
        prefs.edit().putInt("sync_apps", apps)
            .putInt("sync_groups", groups)
            .putLong("sync_at_ms", System.currentTimeMillis()).apply()
    }

    fun appSyncSummary(): String =
        "sync_app_entries=" + prefs.getInt("sync_apps", 0) +
        " sync_group_entries=" + prefs.getInt("sync_groups", 0) +
        " sync_queued_at_ms=" + prefs.getLong("sync_at_ms", 0L) +
        " sync_ack=unsupported"

    private fun readRecords(): MutableList<String> {
        val result = mutableListOf<String>()
        val arr = runCatching { JSONArray(prefs.getString("events", "[]")) }
            .getOrDefault(JSONArray())
        val start = (arr.length() - MAX_LINES).coerceAtLeast(0)
        for (i in start until arr.length()) {
            val line = arr.optString(i, "")
            if (line.length <= MAX_LINE_LENGTH && line.isNotBlank()) result.add(line)
        }
        return result
    }

    private fun save(lines: List<String>) {
        val array = JSONArray()
        lines.forEach { array.put(it) }
        check(prefs.edit().putString("events", array.toString()).commit())
    }

    companion object {
        private val LOCK = Any()
        const val MAX_LINES = 50
        private const val MAX_LINE_LENGTH = 220
    }
}
