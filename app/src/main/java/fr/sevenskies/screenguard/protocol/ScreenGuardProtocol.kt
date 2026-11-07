package fr.sevenskies.screenguard.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant

/**
 * JSON wire contract checked against upstream adambie/screenguard v0.10.18:
 * crates/common/src/{protocol,messages,models}.rs.
 * No server changes or custom message types are permitted.
 */
object ScreenGuardProtocol {
    const val LOCAL_UID = 1000

    private val secureRandom = SecureRandom()
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newPairingCode(): String = buildString(6) {
        repeat(6) { append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]) }
    }

    fun envelope(type: String, payload: JSONObject): String =
        JSONObject()
            .put("type", type)
            .put("timestamp", Instant.now().toString())
            .put("payload", payload)
            .toString()

    fun pairingRequest(machineId: String, hostname: String, code: String): String =
        envelope(
            "pairing_request",
            JSONObject()
                .put("machine_id", machineId)
                .put("hostname", hostname)
                .put("pairing_code", code)
        )

    fun agentHello(machineId: String, hostname: String, timezone: String, versionName: String,
                   capabilities: List<String> = emptyList()): String {
        require(versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            "Android app version must be a numeric ScreenGuard-compatible semver"
        }
        return envelope(
            "agent_hello",
            JSONObject()
                .put("machine_id", machineId)
                .put("hostname", hostname)
                .put("timezone", timezone)
                .put("agent_version", versionName)
                .put("last_config_version", 0)
                .put("capabilities", JSONArray().apply { capabilities.forEach(::put) })
        )
    }

    fun userListUpdate(child: String): String =
        envelope(
            "user_list_update",
            JSONObject()
                .put(
                    "users",
                    JSONArray().put(
                        JSONObject()
                            .put("local_uid", LOCAL_UID)
                            .put("username", child.lowercase())
                            .put("display_name", child)
                    )
                )
                .put("removed_uids", JSONArray())
        )

    /**
     * The server v0.10.17 calculates remaining time ONLY for heartbeat.users.
     * An empty list yields remaining_update.users=[], even for linked profiles.
     * Declare our stable logical user with ZERO active seconds in this phase:
     * it requests the quota without falsely consuming screen time.
     */
    /**
     * Upstream ScreenGuard v0.10.18 fetch_logs -> log_response {lines}.
     * Caller must supply only sanitized, application-owned diagnostics.
     * The server expects at most 50 short lines in a 5-second request window.
     */
    fun logResponse(lines: List<String>): String {
        require(lines.size <= 50) { "Too many diagnostic lines" }
        require(lines.all { it.length <= 512 && !it.contains('\n') && !it.contains('\r') }) {
            "Diagnostic lines must be short and single-line"
        }
        val items = JSONArray()
        lines.forEach { items.put(it) }
        return envelope("log_response", JSONObject().put("lines", items))
    }

    fun quotaHeartbeat(): String =
        envelope(
            "heartbeat",
            JSONObject().put(
                "users",
                JSONArray().put(
                    JSONObject()
                        .put("local_uid", LOCAL_UID)
                        .put("active_seconds_since_last", 0)
                        .put("idle", true)
                        .put("session_count", 0)
                )
            )
        )

    /**
     * Per-device cumulative seconds for each local date, NOT an additive
     * heartbeat delta. Server v0.10.17 stores MAX(old, snapshot) for each
     * agent_user_id/date; retransmission is therefore safe.
     */
    data class AppUsage(
        val date: String, val id: String, val name: String,
        val categories: List<String>, val seconds: Long
    )
    data class GroupUsage(val date: String, val id: String, val seconds: Long)

    fun appUsageSync(apps: List<AppUsage>, groups: List<GroupUsage>): String {
        val entries = JSONArray()
        for (app in apps) {
            require(app.seconds >= 0 && app.id.length in 1..255)
            java.time.LocalDate.parse(app.date)
            entries.put(JSONObject()
                .put("local_uid", LOCAL_UID).put("date", app.date)
                .put("app_id", app.id).put("display_name", app.name.take(128))
                .put("categories", JSONArray().apply { app.categories.forEach(::put) })
                .put("seconds_total", app.seconds))
        }
        val groupEntries = JSONArray()
        for (group in groups) {
            require(group.seconds >= 0 && group.id.isNotBlank())
            java.time.LocalDate.parse(group.date)
            groupEntries.put(JSONObject().put("local_uid", LOCAL_UID)
                .put("date", group.date).put("group_id", group.id)
                .put("seconds_total", group.seconds))
        }
        return envelope("app_usage_sync", JSONObject()
            .put("entries", entries).put("groups", groupEntries))
    }

    fun appRequest(id: String, name: String, categories: List<String>): String {
        require(id.length in 1..255 && name.isNotBlank())
        return envelope("app_request", JSONObject()
            .put("local_uid", LOCAL_UID).put("app_id", id)
            .put("display_name", name.take(128))
            .put("categories", JSONArray().apply { categories.forEach(::put) }))
    }

    fun usageSync(dailySeconds: Map<String, Long>): String {
        val records = JSONArray()
        for ((date, seconds) in dailySeconds.toSortedMap()) {
            require(seconds >= 0L)
            java.time.LocalDate.parse(date)
            records.put(
                JSONObject()
                    .put("local_uid", LOCAL_UID)
                    .put("date", date)
                    .put("used_seconds", seconds)
            )
        }
        return envelope("usage_sync", JSONObject().put("usage", records))
    }

    /**
     * Server v0.10.17 NotifyUser: local_uid, summary, body.
     * Server sends one event per linked local user (no queue / reply).
     * Never display messages directed at a different Android agent user.
     */
    data class ParentMessage(val summary: String, val body: String)

    fun parentMessageForLocalUser(message: Incoming): ParentMessage? {
        require(message.type == "notify_user")
        val payload = message.requirePayload()
        if (payload.optInt("local_uid", -1) != LOCAL_UID) return null
        val body = payload.optString("body", "").trim()
        if (body.isEmpty()) return null
        val summary = payload.optString("summary", "").trim()
            .ifBlank { "Message du parent" }
        // Limit message size for Android notifications but keep UTF-8 content.
        return ParentMessage(summary.take(160), body.take(4096))
    }

    data class RemainingDecision(
        val minutes: Int,
        val enforce: String,
        val currentWindowEndsAt: String?
    )

    /**
     * Use the matching local UID instead of assuming array position zero.
     * Null means no matching result was returned, NOT an unpaired user.
     */
    fun remainingForLocalUser(message: Incoming): RemainingDecision? {
        require(message.type == "remaining_update")
        val users = message.requirePayload().optJSONArray("users") ?: return null
        for (index in 0 until users.length()) {
            val entry = users.optJSONObject(index) ?: continue
            if (entry.optInt("local_uid", -1) != LOCAL_UID) continue
            return RemainingDecision(
                minutes = entry.getInt("remaining_minutes"),
                enforce = entry.getString("enforce"),
                currentWindowEndsAt = entry.optString("current_window_ends_at")
                    .takeIf { it.isNotEmpty() && it != "null" }
            )
        }
        return null
    }

    /** Return thresholds belonging only to our local user. */
    fun warningThresholds(message: Incoming): List<Int>? {
        require(message.type == "config_push")
        val users = message.requirePayload().optJSONArray("users") ?: return null
        for (i in 0 until users.length()) {
            val user = users.optJSONObject(i) ?: continue
            if (user.optInt("local_uid", -1) != LOCAL_UID) continue
            val arr = user.optJSONArray("warning_thresholds_minutes") ?: return null
            return (0 until arr.length()).map { arr.getInt(it) }
        }
        return null
    }

    /**
     * v0.10.17 sends JSON application-level "pong" with payload:null when the
     * WebSocket transport receives a PING. Do not require a JSONObject for it.
     */
    fun parse(text: String): Incoming {
        val obj = JSONObject(text)
        val raw = obj.opt("payload")
        val payload = when (raw) {
            null, JSONObject.NULL -> null
            is JSONObject -> raw
            else -> throw IllegalArgumentException("Unexpected non-object payload")
        }
        return Incoming(type = obj.getString("type"), payload = payload)
    }

    data class Incoming(val type: String, val payload: JSONObject?) {
        fun requirePayload(): JSONObject =
            requireNotNull(payload) { "Message $type requires an object payload" }
    }
}
