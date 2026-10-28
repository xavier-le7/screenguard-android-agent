package fr.sevenskies.screenguard.usage

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageAccountingTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun timestamp(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, s, 0, paris).toInstant().toEpochMilli()

    @Test fun partialSamplesDoNotLoseSeconds() {
        val totals = mutableMapOf<String, Long>()
        val start = timestamp(2026, 10, 24, 10, 0)
        repeat(7) { i ->
            UsageAccounting.addActiveInterval(totals, start + i * 750L, start + (i + 1) * 750L, paris)
        }
        assertEquals(5250L, totals["2026-10-24"])
        assertEquals(5L, totals["2026-10-24"]!! / 1000)
    }

    @Test fun splitsAtMidnightInsteadOfChargingNextDay() {
        val totals = mutableMapOf<String, Long>()
        UsageAccounting.addActiveInterval(totals,
            timestamp(2026, 10, 24, 23, 59, 30),
            timestamp(2026, 10, 25, 0, 0, 45), paris)
        assertEquals(30000L, totals["2026-10-24"])
        assertEquals(45000L, totals["2026-10-25"])
    }

    @Test fun daylightSavingFallBackIsNotLost() {
        val totals = mutableMapOf<String, Long>()
        UsageAccounting.addActiveInterval(totals,
            timestamp(2026, 10, 25, 1, 30),
            timestamp(2026, 10, 25, 4, 30), paris)
        assertEquals(4L * 3600 * 1000, totals["2026-10-25"])
    }

    @Test fun noTimeWhenIntervalIsZero() {
        val totals = mutableMapOf<String, Long>()
        val at = timestamp(2026, 10, 24, 10, 0)
        UsageAccounting.addActiveInterval(totals, at, at, paris)
        assertTrue(totals.isEmpty())
    }
}
