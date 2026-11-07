package fr.sevenskies.screenguard.enforcement

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AppGroupPolicyTest {
    private fun config(): AppGroupPolicy.Config =
        AppGroupPolicy.fromUserConfig(JSONObject("""
          {"app_groups":[
            {"id":"games","name":"Games","rules":[
                {"kind":"category","value":"Game"},
                {"kind":"prefix","value":"desktop:android.com.example."}
              ],"daily_limits":[{"day_of_week":0,"allowed_minutes":30}]},
            {"id":"school","name":"School","rules":[
                {"kind":"app_id","value":"desktop:android.com.example.game"},
                {"kind":"app_id","value":"desktop:android.com.example.other","exclude":true}
              ],"daily_limits":[]}
            ],
            "app_approval":{"approved":["desktop:android.com.example.game"],
                            "denied":["desktop:android.com.bad.app"]}
          }
        """.trimIndent()))

    @Test fun androidPackageHasAValidUpstreamAppId() {
        assertEquals("desktop:android.com.example.game", AppGroupPolicy.appId("com.example.game"))
        assertNull(AppGroupPolicy.appId("com/example"))
        assertNull(AppGroupPolicy.appId("com.example.game/SomeActivity"))
        assertNull(AppGroupPolicy.appId("a." + "x".repeat(260)))
    }

    @Test fun explicitRulesOverrideAutoCategories() {
        val app = AppGroupPolicy.App("desktop:android.com.example.game", "Game", listOf("Game"))
        assertEquals("school", AppGroupPolicy.assign(config(), app)?.id)
    }

    @Test fun excludedRulesDoNotMatch() {
        val app = AppGroupPolicy.App("desktop:android.com.example.other", "Other", emptyList())
        assertEquals("games", AppGroupPolicy.assign(config(), app)?.id)
    }

    @Test fun remoteGroupUsageCountsAgainstSharedLimit() {
        val app = AppGroupPolicy.App("desktop:android.com.example.other", "Other", emptyList())
        val result = AppGroupPolicy.decide(config(), app,
            mapOf("games" to 600L), mapOf("games" to 1200L),
            LocalDate.of(2026, 11, 9)) // Monday
        assertTrue(result.blocked)
        assertEquals(0L, result.remainingSeconds)
    }

    @Test fun newAppsAreBlockedOnlyWhenApprovalEnabled() {
        val app = AppGroupPolicy.App("desktop:android.org.example.reader", "Reader", emptyList())
        assertTrue(AppGroupPolicy.decide(config(), app, emptyMap(), emptyMap()).needsApproval)
        val unrestricted = AppGroupPolicy.Config(emptyList(), null, emptySet())
        assertFalse(AppGroupPolicy.decide(unrestricted, app, emptyMap(), emptyMap()).blocked)
    }

    @Test fun builtInBlockedGroupForbidsAppsWithoutWeekdayLimits() {
        val cfg = AppGroupPolicy.fromUserConfig(JSONObject("""
        {"app_groups":[{
          "id":"blocked-123","name":"Blocked","builtin":"blocked",
          "rules":[{"kind":"app_id","value":"desktop:android.org.example.game"}],
          "daily_limits":[],"adjustment_today":60
        }]}
        """.trimIndent()))
        val app = AppGroupPolicy.App("desktop:android.org.example.game",
            "Game", listOf("Game"))
        val result = AppGroupPolicy.decide(cfg, app, emptyMap(), emptyMap())
        assertTrue(result.blocked)
        assertEquals("parent_denied", result.reason)
    }

    @Test fun blockedDayOverridesPositiveAdjustment() {
        val config = AppGroupPolicy.Config(listOf(AppGroupPolicy.Group(
            "blocked", "Blocked", listOf(AppGroupPolicy.Rule("category", "Game", false)),
            mapOf(0 to 0), 60)), null, emptySet())
        val app = AppGroupPolicy.App("desktop:android.com.example.game", "Game", listOf("Game"))
        assertTrue(AppGroupPolicy.decide(config, app, emptyMap(), emptyMap(),
            LocalDate.of(2026, 11, 9)).blocked)
    }
}
