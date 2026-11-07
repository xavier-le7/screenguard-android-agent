package fr.sevenskies.screenguard.storage

import android.content.Context
import fr.sevenskies.screenguard.enforcement.AppGroupPolicy
import org.json.JSONArray
import fr.sevenskies.screenguard.protocol.ScreenGuardProtocol
import org.json.JSONObject
import java.time.LocalDate

/**
 * Cached v0.10.18 per-profile policy, shared between network and accessibility.
 * The server sends full configuration for local_uid=1000; no server modification.
 */
class AppPolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("screen_guard_app_policy", Context.MODE_PRIVATE)

    fun config(): AppGroupPolicy.Config? {
        val raw = prefs.getString("user_config", null) ?: return null
        return runCatching { AppGroupPolicy.fromUserConfig(JSONObject(raw)) }.getOrNull()
    }

    fun blockedDomains(): Set<String> {
        val raw = prefs.getString("user_config", null) ?: return emptySet()
        val arr = runCatching { JSONObject(raw).optJSONArray("blocked_domains") }.getOrNull()
            ?: return emptySet()
        return (0 until arr.length()).mapNotNull { index ->
            arr.optString(index).lowercase().trim('.').takeIf {
                it.isNotBlank() && it.length <= 253 &&
                    it.matches(Regex("[a-z0-9.-]+"))
            }
        }.toSet()
    }

    fun applyConfig(message: ScreenGuardProtocol.Incoming) {
        require(message.type == "config_push")
        val users = message.requirePayload().optJSONArray("users") ?: return
        for (i in 0 until users.length()) {
            val user = users.optJSONObject(i) ?: continue
            if (user.optInt("local_uid", -1) == ScreenGuardProtocol.LOCAL_UID) {
                check(prefs.edit().putString("user_config", user.toString())
                    .putLong("config_received_at", System.currentTimeMillis()).commit())
                val config = AppGroupPolicy.fromUserConfig(user)
                // Keep queued requests until the server confirms a decision.
                val outstanding = pendingRequests().filter {
                    config.approved != null &&
                        it.id !in config.approved && it.id !in config.denied
                }
                saveRequests(outstanding)
                return
            }
        }
    }

    fun applyRemaining(message: ScreenGuardProtocol.Incoming) {
        require(message.type == "remaining_update")
        val users = message.requirePayload().optJSONArray("users") ?: return
        for (i in 0 until users.length()) {
            val item = users.optJSONObject(i) ?: continue
            if (item.optInt("local_uid", -1) != ScreenGuardProtocol.LOCAL_UID) continue
            val groups = item.optJSONArray("group_usage_elsewhere") ?: org.json.JSONArray()
            val result = JSONObject()
            for (j in 0 until groups.length()) {
                val group = groups.optJSONObject(j) ?: continue
                val id = group.optString("group_id").takeIf { it.isNotBlank() } ?: continue
                result.put(id, group.optLong("used_seconds", 0L).coerceAtLeast(0L))
            }
            check(prefs.edit()
                .putString("elsewhere", result.toString())
                .putString("elsewhere_date", LocalDate.now().toString())
                .commit())
            return
        }
    }

    fun elsewhere(): Map<String, Long> {
        if (prefs.getString("elsewhere_date", null) != LocalDate.now().toString()) return emptyMap()
        val raw = runCatching { JSONObject(prefs.getString("elsewhere", "{}")) }
            .getOrDefault(JSONObject())
        return raw.keys().asSequence().associateWith { raw.optLong(it, 0L).coerceAtLeast(0L) }
    }

    /**
     * Accessibility may block an app before the network timer runs, so persist
     * approval requests even if the child immediately leaves that app.
     */
    @Synchronized fun queueApproval(app: AppGroupPolicy.App) {
        if (app.id.isBlank() || config()?.approved == null) return
        val old = pendingRequests()
        if (old.any { it.id == app.id } || old.size >= 50) return
        saveRequests(old + app)
    }

    @Synchronized fun pendingRequests(): List<AppGroupPolicy.App> {
        val raw = runCatching { JSONArray(prefs.getString("requests", "[]")) }
            .getOrDefault(JSONArray())
        return (0 until minOf(raw.length(), 50)).mapNotNull { i ->
            val item = raw.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank() || id.length > 255) return@mapNotNull null
            val categories = item.optJSONArray("categories") ?: JSONArray()
            AppGroupPolicy.App(id, item.optString("name").take(128),
                (0 until categories.length()).map { categories.optString(it) }
                    .filter { it.isNotBlank() }.take(12))
        }
    }

    private fun saveRequests(requests: List<AppGroupPolicy.App>) {
        val result = JSONArray()
        for (app in requests.take(50)) {
            result.put(JSONObject().put("id", app.id)
                .put("name", app.displayName.take(128))
                .put("categories", JSONArray().apply {
                    app.categories.take(12).forEach { put(it) }
                }))
        }
        check(prefs.edit().putString("requests", result.toString()).commit())
    }

    fun clear() { check(prefs.edit().clear().commit()) }
}
