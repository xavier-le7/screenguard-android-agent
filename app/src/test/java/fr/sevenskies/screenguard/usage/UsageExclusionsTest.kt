package fr.sevenskies.screenguard.usage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UsageExclusionsTest {
    private fun allowed(from: Long, to: Long, vararg blocked: BlockedInterval): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        UsageExclusions.forEachAllowed(from, to, blocked.toList()) { a, b -> result.add(a to b) }
        return result
    }

    @Test fun noOverlayKeepsNormalUsage() {
        assertEquals(listOf(0L to 10L), allowed(0, 10))
    }

    @Test fun lockDoesNotCountAcrossMultipleSamplesAndResumeStartsNow() {
        val blocked = BlockedInterval(10, 40)
        assertEquals(listOf(0L to 10L), allowed(0, 20, blocked))
        assertEquals(emptyList<Pair<Long, Long>>(), allowed(20, 30, blocked))
        assertEquals(listOf(40L to 50L), allowed(30, 50, blocked))
    }

    @Test fun replayMergesOverlapsAndClipsToSample() {
        assertEquals(listOf(4L to 6L, 9L to 10L), allowed(1, 10,
            BlockedInterval(7, 9), BlockedInterval(-2, 4),
            BlockedInterval(6, 8), BlockedInterval(10, 12), BlockedInterval(5, 5)))
    }

    @Test fun replayAcrossMidnightDoesNotBillBlockedTimeOnEitherDay() {
        val zone = ZoneOffset.UTC
        val midnight = LocalDate.of(2026, 11, 21).atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = mutableMapOf<String, Long>()
        UsageExclusions.forEachAllowed(midnight - 5000, midnight + 7000,
            listOf(BlockedInterval(midnight - 2000, midnight + 3000))) { a, b ->
            UsageAccounting.addActiveInterval(totals, a, b, zone)
        }
        assertEquals(mapOf("2026-11-20" to 3000L, "2026-11-21" to 4000L), totals)
    }
}
