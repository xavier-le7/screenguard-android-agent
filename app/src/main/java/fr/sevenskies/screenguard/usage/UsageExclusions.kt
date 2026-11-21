package fr.sevenskies.screenguard.usage

/** Half-open wall-clock intervals during which an actual blocking overlay was shown. */
data class BlockedInterval(val start: Long, val end: Long)

/** Pure interval subtraction; overlapping, adjacent and out-of-range blocks are safe. */
object UsageExclusions {
    fun forEachAllowed(
        from: Long,
        to: Long,
        blocked: List<BlockedInterval>,
        credit: (Long, Long) -> Unit
    ) {
        if (to <= from) return
        var cursor = from
        for (block in blocked.sortedBy { it.start }) {
            if (block.end <= cursor || block.end <= block.start) continue
            if (block.start >= to) break
            if (block.start > cursor) credit(cursor, minOf(block.start, to))
            cursor = maxOf(cursor, minOf(block.end, to))
            if (cursor >= to) return
        }
        if (cursor < to) credit(cursor, to)
    }
}
