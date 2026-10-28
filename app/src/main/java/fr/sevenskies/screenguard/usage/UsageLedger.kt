package fr.sevenskies.screenguard.usage

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.os.PowerManager
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Device-local, crash-safe usage ledger. We measure unlocked interactive time
 * (not simply a process running). UsageEvents backfills gaps after a service
 * restart, as long as the system still retains those events. No Accessibility
 * and no Device Owner required.
 *
 * Android may withhold events while the device is locked; in this case the
 * checkpoint remains unchanged and processing resumes on the next unlocked
 * sample. No usage is reported until PACKAGE_USAGE_STATS is granted.
 */
class UsageLedger(private val context: Context) {
    private val prefs = context.getSharedPreferences("screen_guard_usage", Context.MODE_PRIVATE)
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val zone get() = ZoneId.systemDefault()

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun collect(): Boolean = synchronized(LEDGER_LOCK) {
        if (!hasPermission() || !userManager.isUserUnlocked) return@synchronized false
        val now = System.currentTimeMillis()
        val currentAnchor = now - SystemClock.elapsedRealtime()
        val currentInteractive = power.isInteractive
        val currentUnlocked = !keyguard.isKeyguardLocked
        val previous = prefs.getLong("checkpoint", 0L)
        val priorAnchor = prefs.getLong("boot_anchor", 0L)
        if (previous == 0L || now <= previous || now - previous > MAX_REPLAY_MILLIS ||
            priorAnchor == 0L || abs(currentAnchor - priorAnchor) > MAX_BOOT_DRIFT_MILLIS) {
            persistCheckpoint(now, currentAnchor, currentInteractive, currentUnlocked, null)
            return@synchronized true
        }

        // Returns null when Android does not yet expose the usage history.
        val events = usageStats.queryEvents(previous, now) ?: return@synchronized false
        val totals = readTotals()
        var cursor = previous
        var interactive = prefs.getBoolean("interactive", false)
        var unlocked = prefs.getBoolean("unlocked", false)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val at = event.timeStamp.coerceIn(cursor, now)
            if (interactive && unlocked) {
                UsageAccounting.addActiveInterval(totals, cursor, at, zone)
            }
            cursor = at
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocked = true
                UsageEvents.Event.KEYGUARD_SHOWN -> unlocked = false
            }
        }
        if (interactive && unlocked) {
            UsageAccounting.addActiveInterval(totals, cursor, now, zone)
        }
        // Hardware is authoritative for the NEXT interval if vendor event
        // reporting is incomplete; never rewrite historical time retroactively.
        persistCheckpoint(now, currentAnchor, currentInteractive, currentUnlocked, totals)
        true
    }

    fun usageSnapshot(): Map<String, Long> = synchronized(LEDGER_LOCK) {
        readTotals().mapValues { (_, millis) -> millis / 1000L }
            .filterValues { it > 0L }
    }

    fun todaySeconds(): Long = synchronized(LEDGER_LOCK) {
        readTotals()[LocalDate.now(zone).toString()]?.div(1000L) ?: 0L
    }

    private fun readTotals(): MutableMap<String, Long> {
        val json = runCatching { JSONObject(prefs.getString("days", "{}")) }
            .getOrDefault(JSONObject())
        val totals = mutableMapOf<String, Long>()
        for (key in json.keys()) {
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            if (date.isBefore(LocalDate.now(zone).minusDays(30))) continue
            totals[key] = json.optLong(key, 0L).coerceAtLeast(0L)
        }
        return totals
    }

    private fun persistCheckpoint(
        now: Long,
        anchor: Long,
        interactive: Boolean,
        unlocked: Boolean,
        totals: Map<String, Long>?
    ) {
        val editor = prefs.edit()
            .putLong("checkpoint", now)
            .putLong("boot_anchor", anchor)
            .putBoolean("interactive", interactive)
            .putBoolean("unlocked", unlocked)
        if (totals != null) {
            val json = JSONObject()
            for ((date, millis) in totals) json.put(date, millis)
            editor.putString("days", json.toString())
        }
        check(editor.commit()) { "Unable to persist screen-time checkpoint" }
    }

    companion object {
        // Both foreground and accessibility services run in the same default
        // app process. A shared lock prevents overlapping read-modify-write
        // of the same persisted daily totals and usage checkpoint.
        private val LEDGER_LOCK = Any()
        private const val MAX_REPLAY_MILLIS = 3L * 24 * 60 * 60 * 1000
        private const val MAX_BOOT_DRIFT_MILLIS = 2L * 60 * 1000
    }
}
