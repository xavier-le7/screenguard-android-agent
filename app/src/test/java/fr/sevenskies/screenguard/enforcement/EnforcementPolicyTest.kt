package fr.sevenskies.screenguard.enforcement

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EnforcementPolicyTest {
    private val day = LocalDate.of(2026, 10, 24)
    private fun input(
        minutes: Int = 10,
        enforce: String = "allow",
        used: Long = 100,
        endsAt: String? = null,
        thresholds: List<Int> = listOf(5, 1)
    ) = EnforcementPolicy.Snapshot(day, minutes, enforce, used, endsAt, thresholds)

    private fun decide(snapshot: EnforcementPolicy.Snapshot, used: Long = 100,
                       time: LocalTime = LocalTime.of(17, 0), date: LocalDate = day) =
        EnforcementPolicy.evaluate(snapshot, date, time, used)

    @Test fun serverLockIsAlwaysLock() {
        assertEquals(EnforcementPolicy.Mode.LOCK,
            decide(input(enforce = "lock", minutes = 100)).mode)
    }

    @Test fun offlineScreenUseReducesRemaining() {
        val decision = decide(input(minutes = 6), used = 100 + 140)
        assertEquals(EnforcementPolicy.Mode.WARN, decision.mode)
        assertEquals(220L, decision.secondsRemaining)
    }

    @Test fun reachingBudgetLocksEvenWithoutServer() {
        assertEquals(EnforcementPolicy.Mode.LOCK,
            decide(input(minutes = 2), used = 100 + 121).mode)
    }

    @Test fun wallClockEndLocksEvenWithTimeRemaining() {
        assertEquals(EnforcementPolicy.Mode.LOCK,
            decide(input(minutes = 60, endsAt = "17:30:00"),
                time = LocalTime.of(17, 31)).mode)
    }

    @Test fun oldDayDoesNotLockNewDay() {
        assertEquals(EnforcementPolicy.Mode.UNKNOWN,
            decide(input(enforce = "lock"), date = day.plusDays(1)).mode)
    }

    @Test fun unknownDecisionsDoNotLock() {
        assertEquals(EnforcementPolicy.Mode.UNKNOWN,
            decide(input(enforce = "disabled")).mode)
    }

    @Test fun previousDayUsageDoesNotCauseNegativeDelta() {
        assertEquals(600L, decide(input(minutes = 10), used = 50).secondsRemaining)
    }
}
