package fr.sevenskies.screenguard.enforcement

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * ScreenGuard 0.10.18 policy model. Pure Kotlin; not yet activated on-device.
 * Upstream accepts desktop:/flatpak:/snap:/steam:/minecraft:/wine:/exe: AppIds,
 * not android:. We map Android package names to desktop:android.<package>.
 */
object AppGroupPolicy {
    data class App(val id: String, val displayName: String, val categories: List<String>)
    data class Rule(val kind: String, val value: String, val exclude: Boolean)
    data class Group(
        val id: String, val name: String, val rules: List<Rule>,
        val dailyLimits: Map<Int, Int>, val adjustmentToday: Int
    )
    data class Config(
        val groups: List<Group>,
        val approved: Set<String>?,
        val denied: Set<String>
    )
    data class Decision(
        val groupId: String?, val blocked: Boolean, val needsApproval: Boolean,
        val remainingSeconds: Long?, val reason: String
    )

    fun appId(packageName: String): String? {
        if (!packageName.matches(Regex("[a-zA-Z_][a-zA-Z_0-9]*(\\.[a-zA-Z_][a-zA-Z_0-9]*)+"))) return null
        val id = "desktop:android.$packageName"
        return id.takeIf { it.length <= 255 }
    }

    fun fromUserConfig(user: JSONObject): Config {
        val groups = mutableListOf<Group>()
        val rawGroups = user.optJSONArray("app_groups") ?: JSONArray()
        for (i in 0 until rawGroups.length()) {
            val obj = rawGroups.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() && it.length <= 120 } ?: continue
            val rules = mutableListOf<Rule>()
            val rawRules = obj.optJSONArray("rules") ?: JSONArray()
            for (j in 0 until rawRules.length()) {
                val r = rawRules.optJSONObject(j) ?: continue
                val kind = r.optString("kind")
                val value = r.optString("value")
                if (kind !in setOf("app_id", "prefix", "category") || value.isBlank() || value.length > 255) continue
                rules.add(Rule(kind, value, r.optBoolean("exclude", false)))
            }
            val limits = mutableMapOf<Int, Int>()
            val rawLimits = obj.optJSONArray("daily_limits") ?: JSONArray()
            for (j in 0 until rawLimits.length()) {
                val limit = rawLimits.optJSONObject(j) ?: continue
                val day = limit.optInt("day_of_week", -1)
                val minutes = limit.optInt("allowed_minutes", -1)
                if (day in 0..6 && minutes in 0..1440) limits[day] = minutes
            }
            groups.add(Group(id, obj.optString("name").take(120), rules, limits,
                obj.optInt("adjustment_today", 0).coerceIn(-1440, 1440)))
        }
        val approval = user.optJSONObject("app_approval")
        fun strings(a: JSONArray?): Set<String> = if (a == null) emptySet() else
            (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() && s.length <= 255 } }.toSet()
        return Config(groups,
            approval?.let { strings(it.optJSONArray("approved")) },
            strings(approval?.optJSONArray("denied")))
    }

    private fun hits(rule: Rule, app: App): Boolean = when (rule.kind) {
        "app_id" -> app.id == rule.value
        "prefix" -> app.id.startsWith(rule.value)
        "category" -> app.categories.contains(rule.value)
        else -> false
    }

    fun assign(config: Config, app: App): Group? {
        fun eligible(g: Group) = g.rules.none { it.exclude && hits(it, app) }
        return config.groups.firstOrNull { g ->
            eligible(g) && g.rules.any { !it.exclude && it.kind == "app_id" && hits(it, app) }
        } ?: config.groups.firstOrNull { g ->
            eligible(g) && g.rules.any { !it.exclude && hits(it, app) }
        }
    }

    fun decide(
        config: Config, app: App, localGroupSeconds: Map<String, Long>,
        elsewhereSeconds: Map<String, Long>, today: LocalDate = LocalDate.now()
    ): Decision {
        val group = assign(config, app)
        val needsApproval = config.approved != null &&
            app.id !in config.approved && app.id !in config.denied
        val denied = config.approved != null && app.id in config.denied
        val minutes = group?.dailyLimits?.get(today.dayOfWeek.value - 1)
        val secondsRemaining = if (group == null || minutes == null) null else {
            if (minutes == 0) 0L else (
                (minutes + group.adjustmentToday).coerceAtLeast(0).toLong() * 60L -
                    (localGroupSeconds[group.id] ?: 0L).coerceAtLeast(0L) -
                    (elsewhereSeconds[group.id] ?: 0L).coerceAtLeast(0L)
            ).coerceAtLeast(0L)
        }
        val blocked = denied || needsApproval || secondsRemaining == 0L
        return Decision(group?.id, blocked, needsApproval, secondsRemaining,
            when {
                denied -> "parent_denied"
                needsApproval -> "approval_required"
                secondsRemaining == 0L -> "group_limit"
                else -> "allow"
            })
    }
}
