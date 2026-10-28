package fr.sevenskies.screenguard.enforcement

import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure offline policy projection. Server is authoritative while online.
 * The cached daily decision is NEVER carried into another local calendar day:
 * we prefer an explicit UNKNOWN state over mistakenly locking a new day.
 */
object EnforcementPolicy {
    enum class Mode { UNKNOWN, ALLOW, WARN, LOCK }

    data class Snapshot(
        val date: LocalDate?,
        val remainingMinutes: Int,
        val enforce: String,
        val localUsedSecondsAtReceipt: Long,
        val currentWindowEndsAt: String?,
        val thresholds: List<Int>
    )
    data class Result(val mode: Mode, val secondsRemaining: Long?, val reason: String)

    fun evaluate(
        snapshot: Snapshot,
        today: LocalDate,
        currentTime: LocalTime,
        localUsedSeconds: Long
    ): Result {
        if (snapshot.date != today || snapshot.remainingMinutes < 0) {
            return Result(Mode.UNKNOWN, null, "Aucune décision récente pour cette journée")
        }
        if (snapshot.enforce == "lock") {
            return Result(Mode.LOCK, 0, "Quota ou plage horaire épuisé")
        }
        if (snapshot.enforce != "allow" && snapshot.enforce != "warn") {
            return Result(Mode.UNKNOWN, null, "Décision serveur non reconnue")
        }
        val windowEnd = snapshot.currentWindowEndsAt?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
        if (windowEnd != null && !currentTime.isBefore(windowEnd)) {
            return Result(Mode.LOCK, 0, "Fin de la plage horaire autorisée")
        }
        val newlyUsed = (localUsedSeconds - snapshot.localUsedSecondsAtReceipt).coerceAtLeast(0)
        val remaining = (snapshot.remainingMinutes.toLong() * 60 - newlyUsed).coerceAtLeast(0)
        if (remaining == 0L) return Result(Mode.LOCK, 0, "Quota journalier épuisé")
        val warning = snapshot.enforce == "warn" ||
            snapshot.thresholds.any { threshold ->
                threshold > 0 && remaining <= threshold.toLong() * 60
            }
        return Result(
            if (warning) Mode.WARN else Mode.ALLOW,
            remaining,
            if (warning) "Le temps autorisé touche à sa fin" else "Autorisé"
        )
    }
}
