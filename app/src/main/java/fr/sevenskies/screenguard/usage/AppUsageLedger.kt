package fr.sevenskies.screenguard.usage

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import fr.sevenskies.screenguard.enforcement.AppGroupPolicy
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Foreground Android app accounting. One resumed app accrues time while the
 * screen is interactive and unlocked. Raw Android usage events never leave
 * this process. Per-date cumulative totals survive restarts and reconnects.
 *
 * Separate checkpoint from UsageLedger (whole-device time) prevents any
 * change to existing screen quotas or double-counting their heartbeats.
 */
class AppUsageLedger(private val context: Context) {
    data class Total(val date: String, val app: AppGroupPolicy.App, val seconds: Long)
    data class GroupTotal(val date: String, val groupId: String, val seconds: Long)
    data class Snapshot(val apps: List<Total>, val groups: List<GroupTotal>)

    private val prefs = context.getSharedPreferences("screen_guard_app_usage", Context.MODE_PRIVATE)
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val zone get() = ZoneId.systemDefault()

    fun collect(config: AppGroupPolicy.Config): Boolean = synchronized(LOCK) {
        if (!UsageLedger(context).hasPermission() || !users.isUserUnlocked) return@synchronized false
        val now = System.currentTimeMillis()
        val anchor = now - SystemClock.elapsedRealtime()
        val old = prefs.getLong("checkpoint", 0)
        val priorAnchor = prefs.getLong("boot_anchor", 0)
        // Reconstruct today's foreground sessions at first install, after a
        // reboot, or after the Android event history was interrupted.
        // The previous implementation started with foreground=null and silently
        // ignored apps already running when ScreenGuard was first enabled.
        val bootstrap = old == 0L || now <= old || now - old > 3L * 24 * 3600 * 1000 ||
            priorAnchor == 0L || abs(priorAnchor - anchor) > 120_000L
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val queryFrom = if (bootstrap) dayStart else old
        val events = usage.queryEvents(queryFrom, now) ?: run {
            saveScan("events_unavailable", 0, 0, bootstrap)
            return@synchronized false
        }
        // Bootstrap replaces only today's totals: additive replay on top of old
        // counters would charge the same usage twice after an Android reboot.
        val apps = parseTotals("apps")
        val groups = parseTotals("groups")
        if (bootstrap) {
            val datePrefix = LocalDate.now(zone).toString() + "|"
            apps.keys.removeAll { it.startsWith(datePrefix) }
            groups.keys.removeAll { it.startsWith(datePrefix) }
        }
        var foreground = if (bootstrap) null else prefs.getString("foreground", null)
        var interactive = if (bootstrap) false else prefs.getBoolean("interactive", false)
        var unlocked = if (bootstrap) false else prefs.getBoolean("unlocked", false)
        var cursor = queryFrom
        var eventCount = 0
        var resumedCount = 0
        val event = UsageEvents.Event()
        fun credit(until: Long) {
            val pkg = foreground
            if (until > cursor && interactive && unlocked && pkg != null && eligible(pkg)) {
                val id = AppGroupPolicy.appId(pkg)
                if (id != null) {
                    addInterval(apps, id, cursor, until)
                    val app = info(pkg)
                    AppGroupPolicy.assign(config, app)?.let { addInterval(groups, it.id, cursor, until) }
                }
            }
            cursor = until
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCount++
            val time = event.timeStamp.coerceIn(cursor, now)
            credit(time)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foreground = event.packageName
                    resumedCount++
                    // A resumed activity is evidence the display was usable;
                    // pre-existing screen/keyguard events may be outside the
                    // first queried window on some Samsung firmware.
                    if (bootstrap) {
                        interactive = true
                        unlocked = true
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (foreground == event.packageName) foreground = null
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> interactive = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> interactive = false
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocked = true
                UsageEvents.Event.KEYGUARD_SHOWN -> unlocked = false
            }
        }
        credit(now)
        val next = foreground?.takeIf(::eligible)
        checkpoint(now, anchor, next, power.isInteractive,
            !keyguard.isKeyguardLocked, apps, groups)
        saveScan(if (apps.isEmpty()) "no_app_intervals" else "ok",
            eventCount, resumedCount, bootstrap)
        true
    }

    /**
     * Safe counters for server-initiated fetch_logs. Never expose app names,
     * package names, URLs, credentials or actual usage events.
     */
    fun diagnosticSummary(): String = synchronized(LOCK) {
        val day = LocalDate.now(zone).toString() + "|"
        val today = parseTotals("apps").filterKeys { it.startsWith(day) }
        val seconds = today.values.sumOf { it / 1000L }
        "apps_scan=" + prefs.getString("scan_status", "never") +
            " events=" + prefs.getInt("scan_events", 0) +
            " resumed=" + prefs.getInt("scan_resumed", 0) +
            " bootstrap=" + prefs.getBoolean("scan_bootstrap", false) +
            " app_count=" + today.size +
            " today_seconds=" + seconds +
            " last_scan_ms=" + prefs.getLong("scan_at", 0L)
    }

    private fun saveScan(result: String, eventCount: Int, resumedCount: Int,
                         bootstrap: Boolean) {
        prefs.edit()
            .putString("scan_status", result)
            .putInt("scan_events", eventCount)
            .putInt("scan_resumed", resumedCount)
            .putBoolean("scan_bootstrap", bootstrap)
            .putLong("scan_at", System.currentTimeMillis())
            .apply()
    }

    fun snapshot(): Snapshot = synchronized(LOCK) {
        val appTotals = parseTotals("apps")
        val groupTotals = parseTotals("groups")
        Snapshot(
            appTotals.entries.asSequence().mapNotNull { (key, millis) ->
                val parts = key.split("|", limit = 2)
                if (parts.size != 2 || millis < 1000L) null else {
                    val pkg = parts[1].removePrefix("desktop:android.")
                    Total(parts[0], info(pkg), millis / 1000L)
                }
            }.sortedWith(compareBy({ it.date }, { it.app.id })).take(MAX_RECORDS).toList(),
            groupTotals.entries.asSequence().mapNotNull { (key, millis) ->
                val parts = key.split("|", limit = 2)
                if (parts.size != 2 || millis < 1000L) null else GroupTotal(parts[0], parts[1], millis / 1000L)
            }.sortedWith(compareBy({ it.date }, { it.groupId })).take(MAX_RECORDS).toList()
        )
    }

    /** The last active package, for an unknown-app approval request. */
    fun foregroundApp(): AppGroupPolicy.App? = synchronized(LOCK) {
        if (!power.isInteractive || keyguard.isKeyguardLocked) return@synchronized null
        val pkg = prefs.getString("foreground", null) ?: return@synchronized null
        if (!eligible(pkg)) null else info(pkg)
    }

    fun todayGroupSeconds(): Map<String, Long> = synchronized(LOCK) {
        val day = LocalDate.now(zone).toString() + "|"
        parseTotals("groups").filterKeys { it.startsWith(day) }
            .mapKeys { it.key.substringAfter('|') }.mapValues { it.value / 1000L }
    }

    fun info(pkg: String): AppGroupPolicy.App {
        val id = AppGroupPolicy.appId(pkg) ?: ""
        val application = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
        val name = runCatching {
            application?.loadLabel(context.packageManager)?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(128) ?: pkg.take(128)
        val categories = mutableListOf<String>()
        if (application?.category == ApplicationInfo.CATEGORY_GAME) categories.add("Game")
        if (pkg in BROWSERS) categories.add("WebBrowser")
        return AppGroupPolicy.App(id, name, categories)
    }

    private fun eligible(pkg: String): Boolean {
        if (pkg == context.packageName || pkg.isBlank()) return false
        if (pkg == "android" || pkg in NON_APPS ||
            pkg.startsWith("com.android.providers.") ||
            pkg.startsWith("com.google.android.permissioncontroller") ||
            pkg.startsWith("com.samsung.android.settings") ||
            pkg.startsWith("com.sec.android.app.launcher")) return false
        return AppGroupPolicy.appId(pkg) != null
    }

    private fun addInterval(totals: MutableMap<String, Long>, id: String, from: Long, to: Long) {
        val pieces = mutableMapOf<String, Long>()
        UsageAccounting.addActiveInterval(pieces, from, to, zone)
        for ((date, millis) in pieces) {
            val key = "$date|$id"
            totals[key] = (totals[key] ?: 0L) + millis
        }
    }

    private fun parseTotals(key: String): MutableMap<String, Long> {
        val raw = runCatching { JSONObject(prefs.getString(key, "{}")) }.getOrDefault(JSONObject())
        val min = LocalDate.now(zone).minusDays(35)
        val result = mutableMapOf<String, Long>()
        for (record in raw.keys()) {
            val date = runCatching { LocalDate.parse(record.substringBefore('|')) }.getOrNull() ?: continue
            if (date < min || date > LocalDate.now(zone).plusDays(1)) continue
            val ms = raw.optLong(record, -1)
            if (ms >= 0 && result.size < MAX_RECORDS) result[record] = ms
        }
        return result
    }

    private fun checkpoint(now: Long, anchor: Long, foreground: String?,
                           interactive: Boolean, unlocked: Boolean,
                           apps: Map<String, Long>?, groups: Map<String, Long>?) {
        val edit = prefs.edit()
            .putLong("checkpoint", now).putLong("boot_anchor", anchor)
            .putString("foreground", foreground)
            .putBoolean("interactive", interactive).putBoolean("unlocked", unlocked)
        if (apps != null && groups != null) {
            val a = JSONObject()
            val g = JSONObject()
            apps.forEach { (k, v) -> a.put(k, v) }
            groups.forEach { (k, v) -> g.put(k, v) }
            edit.putString("apps", a.toString()).putString("groups", g.toString())
        }
        check(edit.commit()) { "Failed to save app usage checkpoint" }
    }

    companion object {
        private val LOCK = Any()
        private const val MAX_RECORDS = 3000
        private val NON_APPS = setOf(
            "com.android.settings", "com.android.systemui",
            "com.android.permissioncontroller", "com.android.launcher3",
            "com.android.phone", "com.android.dialer",
            "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
            "com.google.android.apps.nexuslauncher"
        )
        private val BROWSERS = setOf(
            "com.android.chrome", "org.mozilla.firefox", "org.mozilla.fenix",
            "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.brave.browser",
            "com.opera.browser", "com.opera.mini.native", "com.duckduckgo.mobile.android",
            "com.vivaldi.browser", "com.kiwibrowser.browser"
        )
    }
}
