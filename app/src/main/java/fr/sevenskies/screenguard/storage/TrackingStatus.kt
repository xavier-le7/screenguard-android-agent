package fr.sevenskies.screenguard.storage

import android.content.Context
import fr.sevenskies.screenguard.enforcement.EnforcementPolicy
import fr.sevenskies.screenguard.protocol.ScreenGuardProtocol
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persistent policy receipt, shared by the network foreground service,
 * accessibility UI, and the parent diagnostic screen.
 * Updates that form one receipt are committed atomically.
 */
class TrackingStatus(context: Context) {
    private val prefs = context.getSharedPreferences("screen_guard_status", Context.MODE_PRIVATE)

    var trackingEnabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    /** Local 60-second parent diagnostics; never sent to the server. */
    var testLockUntilMillis: Long
        get() = prefs.getLong("test_lock_until", 0L)
        set(value) { prefs.edit().putLong("test_lock_until", value).commit() }

    var accessibilityServiceConnected: Boolean
        get() = prefs.getBoolean("accessibility_service_connected", false)
        set(value) { prefs.edit().putBoolean("accessibility_service_connected", value).apply() }

    var lastGuardPackage: String
        get() = prefs.getString("last_guard_package", "aucune") ?: "aucune"
        set(value) { prefs.edit().putString("last_guard_package", value).apply() }

    var lastGuardReason: String
        get() = prefs.getString("last_guard_reason", "Aucune évaluation") ?: "Aucune évaluation"
        set(value) { prefs.edit().putString("last_guard_reason", value).apply() }

    var lastGuardEvaluatedAt: Long
        get() = prefs.getLong("last_guard_evaluated_at", 0L)
        set(value) { prefs.edit().putLong("last_guard_evaluated_at", value).apply() }

    /** Opt-in: overlay only on settings that could disable child protection. */
    var settingsGuardEnabled: Boolean
        get() = prefs.getBoolean("settings_guard_enabled", false)
        set(value) { prefs.edit().putBoolean("settings_guard_enabled", value).commit() }

    /** Diagnostic only. Does not store Settings screen content or user-entered data. */
    var lastSettingsGuardDiagnostic: String
        get() = prefs.getString("settings_diagnostic", "Aucune page vérifiée") ?: "Aucune page vérifiée"
        set(value) { prefs.edit().putString("settings_diagnostic", value).apply() }

    var lastSettingsGuardCheckedAt: Long
        get() = prefs.getLong("settings_diagnostic_at", 0L)
        set(value) { prefs.edit().putLong("settings_diagnostic_at", value).apply() }

    /** Short parent-authorized window to reach restricted system settings. */
    var settingsAuthorizedUntilMillis: Long
        get() = prefs.getLong("settings_authorized_until", 0L)
        set(value) { prefs.edit().putLong("settings_authorized_until", value).commit() }

    var restrictionsEnabled: Boolean
        get() = prefs.getBoolean("restrictions_enabled", false)
        set(value) { prefs.edit().putBoolean("restrictions_enabled", value).commit() }

    var serviceRunning: Boolean
        get() = prefs.getBoolean("running", false)
        set(value) { prefs.edit().putBoolean("running", value).apply() }

    var message: String
        get() = prefs.getString("message", "Non démarré") ?: "Non démarré"
        set(value) { prefs.edit().putString("message", value).apply() }

    var remainingMinutes: Int
        get() = prefs.getInt("remaining", -1)
        set(value) { prefs.edit().putInt("remaining", value).apply() }

    var enforce: String
        get() = prefs.getString("enforce", "?") ?: "?"
        set(value) { prefs.edit().putString("enforce", value).apply() }

    var lastHealthAlertMillis: Long
        get() = prefs.getLong("last_health_alert", 0L)
        set(value) { prefs.edit().putLong("last_health_alert", value).apply() }

    var lastHealthIssue: String
        get() = prefs.getString("last_health_issue", "") ?: ""
        set(value) { prefs.edit().putString("last_health_issue", value).apply() }

    var lastFallbackCollectionMillis: Long
        get() = prefs.getLong("last_fallback", 0L)
        set(value) { prefs.edit().putLong("last_fallback", value).apply() }

    var lastCollectionMillis: Long
        get() = prefs.getLong("last_sample", 0L)
        set(value) { prefs.edit().putLong("last_sample", value).apply() }

    var lastServerUpdateMillis: Long
        get() = prefs.getLong("updated_at", 0L)
        set(value) { prefs.edit().putLong("updated_at", value).apply() }

    var lastWarnedThreshold: Int
        get() = prefs.getInt("warned_threshold", Int.MAX_VALUE)
        set(value) { prefs.edit().putInt("warned_threshold", value).apply() }

    fun updateFromServer(
        decision: ScreenGuardProtocol.RemainingDecision,
        localUsedSeconds: Long
    ) {
        val newDay = LocalDate.now().toString()
        val changed = newDay != prefs.getString("quota_date", null)
        val previouslyAllowed = prefs.getInt("remaining", -1)
        val bonusGranted = !changed && previouslyAllowed >= 0 &&
            decision.minutes > previouslyAllowed + 1
        check(prefs.edit()
            .putString("quota_date", newDay)
            .putInt("remaining", decision.minutes)
            .putString("enforce", decision.enforce)
            .putLong("local_usage_at_receipt", localUsedSeconds)
            .putString("window_end", decision.currentWindowEndsAt)
            .putLong("updated_at", System.currentTimeMillis())
            .apply {
                if (changed || bonusGranted) putInt("warned_threshold", Int.MAX_VALUE)
            }
            .commit())
    }

    fun warningThresholds(): List<Int> =
        prefs.getString("warning_thresholds", null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() } ?: listOf(15, 5, 1)

    fun setWarningThresholds(values: List<Int>) {
        val list = values.filter { it > 0 }.distinct().sortedDescending()
        check(prefs.edit().putString("warning_thresholds", list.joinToString(",")).commit())
    }

    fun policyAt(todayUsed: Long, nowDate: LocalDate = LocalDate.now(),
                 nowTime: LocalTime = LocalTime.now()): EnforcementPolicy.Result {
        val thresholds = prefs.getString("warning_thresholds", null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }
            ?: listOf(15, 5, 1)
        val day = prefs.getString("quota_date", null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        return EnforcementPolicy.evaluate(
            EnforcementPolicy.Snapshot(
                date = day,
                remainingMinutes = remainingMinutes,
                enforce = enforce,
                localUsedSecondsAtReceipt = prefs.getLong("local_usage_at_receipt", 0L),
                currentWindowEndsAt = prefs.getString("window_end", null),
                thresholds = thresholds
            ), nowDate, nowTime, todayUsed
        )
    }
}
