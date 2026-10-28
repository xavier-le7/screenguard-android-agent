package fr.sevenskies.screenguard.protocol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ScreenGuardProtocolTest {
    @Test fun pairingHasCorrectEnvelopeAndPayload() {
        val message = JSONObject(
            ScreenGuardProtocol.pairingRequest("machine-1", "Android-Alex", "ABC234")
        )
        assertEquals("pairing_request", message.getString("type"))
        assertNotNull(Instant.parse(message.getString("timestamp")))
        val payload = message.getJSONObject("payload")
        assertEquals("machine-1", payload.getString("machine_id"))
        assertEquals("ABC234", payload.getString("pairing_code"))
        assertFalse(payload.has("cloud_account"))
    }

    @Test fun helloMatchesUpstreamKeys() {
        val payload = JSONObject(
            ScreenGuardProtocol.agentHello("machine-2", "Android-Sam", "Europe/Paris")
        ).getJSONObject("payload")
        assertEquals(0, payload.getInt("last_config_version"))
        assertEquals("Europe/Paris", payload.getString("timezone"))
        assertEquals(0, payload.getJSONArray("capabilities").length())
    }

    @Test fun userListKeepsStableLocalUid() {
        val obj = JSONObject(ScreenGuardProtocol.userListUpdate("Alex"))
        val user = obj.getJSONObject("payload")
            .getJSONArray("users").getJSONObject(0)
        assertEquals(1000, user.getInt("local_uid"))
        assertEquals("alex", user.getString("username"))
        assertEquals("Alex", user.getString("display_name"))
    }

    @Test fun quotaHeartbeatIncludesLinkedUidButNeverConsumesTime() {
        val heartbeat = JSONObject(ScreenGuardProtocol.quotaHeartbeat())
        assertEquals("heartbeat", heartbeat.getString("type"))
        val users = heartbeat.getJSONObject("payload").getJSONArray("users")
        assertEquals(1, users.length())
        val entry = users.getJSONObject(0)
        assertEquals(1000, entry.getInt("local_uid"))
        assertEquals(0, entry.getInt("active_seconds_since_last"))
        assertTrue(entry.getBoolean("idle"))
        assertEquals(0, entry.getInt("session_count"))
    }

    @Test fun emptyRemainingListIsNotEvidenceOfUnpairing() {
        val incoming = ScreenGuardProtocol.parse(
            ScreenGuardProtocol.envelope(
                "remaining_update", JSONObject().put("users", org.json.JSONArray())
            )
        )
        assertNull(ScreenGuardProtocol.remainingForLocalUser(incoming))
    }

    @Test fun remainingSelectsStableLocalUidEvenWhenNotFirst() {
        val payload = JSONObject().put("users", org.json.JSONArray()
            .put(JSONObject().put("local_uid", 999)
                .put("remaining_minutes", 1440).put("enforce", "allow"))
            .put(JSONObject().put("local_uid", 1000)
                .put("remaining_minutes", 75).put("enforce", "warn")))
        val incoming = ScreenGuardProtocol.parse(
            ScreenGuardProtocol.envelope("remaining_update", payload)
        )
        val entry = ScreenGuardProtocol.remainingForLocalUser(incoming)
        assertNotNull(entry)
        assertEquals(75, entry!!.minutes)
        assertEquals("warn", entry.enforce)
    }

    @Test fun snapshotsAreCumulativeAndKeepDate() {
        val json = JSONObject(ScreenGuardProtocol.usageSync(mapOf(
            "2026-10-24" to 540L, "2026-10-23" to 80L
        )))
        assertEquals("usage_sync", json.getString("type"))
        val records = json.getJSONObject("payload").getJSONArray("usage")
        assertEquals(2, records.length())
        assertEquals(1000, records.getJSONObject(0).getInt("local_uid"))
        assertEquals("2026-10-23", records.getJSONObject(0).getString("date"))
        assertEquals(80L, records.getJSONObject(0).getLong("used_seconds"))
        assertEquals(540L, records.getJSONObject(1).getLong("used_seconds"))
    }

    @Test fun heartbeatRemainsZeroDespiteCumulativeSnapshots() {
        val before = ScreenGuardProtocol.usageSync(mapOf("2026-10-24" to 120L))
        val after = ScreenGuardProtocol.usageSync(mapOf("2026-10-24" to 190L))
        assertEquals(120L, JSONObject(before).getJSONObject("payload")
            .getJSONArray("usage").getJSONObject(0).getLong("used_seconds"))
        assertEquals(190L, JSONObject(after).getJSONObject("payload")
            .getJSONArray("usage").getJSONObject(0).getLong("used_seconds"))
        assertEquals(0, JSONObject(ScreenGuardProtocol.quotaHeartbeat())
            .getJSONObject("payload").getJSONArray("users")
            .getJSONObject(0).getInt("active_seconds_since_last"))
    }

    @Test fun pairingCodeHasExpectedAlphabet() {
        repeat(100) {
            val code = ScreenGuardProtocol.newPairingCode()
            assertEquals(6, code.length)
            assertTrue(code.matches(Regex("[A-HJ-NP-Z2-9]{6}")))
        }
    }

    @Test fun parsesNullPongWithoutException() {
        val json = """{"type":"pong","timestamp":"2026-10-24T12:25:00Z","payload":null}"""
        val parsed = ScreenGuardProtocol.parse(json)
        assertEquals("pong", parsed.type)
        assertNull(parsed.payload)
    }

    @Test fun parentNotificationParsesRealUpstreamSummaryAndBody() {
        val json = ScreenGuardProtocol.envelope("notify_user",
            JSONObject()
                .put("local_uid", 1000)
                .put("summary", "Message des parents")
                .put("body", "Viens manger, s'il te plaît ! 🍽️"))
        val msg = ScreenGuardProtocol.parentMessageForLocalUser(
            ScreenGuardProtocol.parse(json))
        assertNotNull(msg)
        assertEquals("Message des parents", msg!!.summary)
        assertEquals("Viens manger, s'il te plaît ! 🍽️", msg.body)
    }

    @Test fun parentMessageIgnoresOtherUsersAndEmptyBodies() {
        val otherUser = ScreenGuardProtocol.parse(ScreenGuardProtocol.envelope(
            "notify_user", JSONObject()
                .put("local_uid", 1001)
                .put("summary", "Sibling")
                .put("body", "Bonjour")))
        assertNull(ScreenGuardProtocol.parentMessageForLocalUser(otherUser))
        val empty = ScreenGuardProtocol.parse(ScreenGuardProtocol.envelope(
            "notify_user", JSONObject().put("local_uid", 1000)
                .put("summary", "Salut").put("body", "   ")))
        assertNull(ScreenGuardProtocol.parentMessageForLocalUser(empty))
    }

    @Test fun parentMessageHasSafeFallbackSummary() {
        val raw = ScreenGuardProtocol.parse(ScreenGuardProtocol.envelope(
            "notify_user", JSONObject()
                .put("local_uid", 1000)
                .put("body", "Test")
                .put("summary", "")))
        val msg = ScreenGuardProtocol.parentMessageForLocalUser(raw)
        assertEquals("Message du parent", msg?.summary)
    }

    @Test fun malformedRequiredPayloadIsRejectedOnAccess() {
        val json = """{"type":"remaining_update","timestamp":"2026-10-24T12:25:00Z","payload":null}"""
        val parsed = ScreenGuardProtocol.parse(json)
        assertThrows(IllegalArgumentException::class.java) {
            parsed.requirePayload()
        }
    }

    @Test fun parsesRemainingUpdatePayload() {
        val json = ScreenGuardProtocol.envelope(
            "remaining_update",
            JSONObject().put("users", org.json.JSONArray()
                .put(JSONObject().put("local_uid", 1000)
                    .put("remaining_minutes", 23).put("enforce", "warn")))
        )
        val result = ScreenGuardProtocol.parse(json)
        assertEquals("remaining_update", result.type)
        assertEquals(23, ScreenGuardProtocol.remainingForLocalUser(result)?.minutes)
    }
}
