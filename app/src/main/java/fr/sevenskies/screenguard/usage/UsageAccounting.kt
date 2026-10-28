package fr.sevenskies.screenguard.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure function: divide elapsed screen-unlocked time by local day, including
 * daylight-saving transitions. Store milliseconds to avoid losing subsecond
 * fragments on every sample. Only convert to seconds at wire boundary.
 */
object UsageAccounting {
    fun addActiveInterval(
        totals: MutableMap<String, Long>,
        startMillis: Long,
        endMillis: Long,
        zone: ZoneId
    ) {
        require(endMillis >= startMillis)
        var point = startMillis
        while (point < endMillis) {
            val day: LocalDate = Instant.ofEpochMilli(point).atZone(zone).toLocalDate()
            val boundary = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val until = minOf(boundary, endMillis)
            check(until > point)
            val key = day.toString()
            totals[key] = Math.addExact(totals[key] ?: 0L, until - point)
            point = until
        }
    }
}
