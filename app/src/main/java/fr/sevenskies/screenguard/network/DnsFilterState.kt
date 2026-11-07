package fr.sevenskies.screenguard.network

/**
 * Distinguishes parent opt-in (persisted) from an actually running VPN.
 * A stopped VPN must never be advertised as filtering at the server.
 */
object DnsFilterState {
    enum class Mode { ACTIVE, STOPPING, PENDING, DISABLED }

    fun mode(parentEnabled: Boolean, serviceRunning: Boolean): Mode = when {
        serviceRunning && parentEnabled -> Mode.ACTIVE
        serviceRunning -> Mode.STOPPING
        parentEnabled -> Mode.PENDING
        else -> Mode.DISABLED
    }

    fun isActive(parentEnabled: Boolean, serviceRunning: Boolean): Boolean =
        mode(parentEnabled, serviceRunning) == Mode.ACTIVE

    fun label(mode: Mode): String = when (mode) {
        Mode.ACTIVE -> "ACTIF — VPN DNS connecté"
        Mode.STOPPING -> "Arrêt du VPN en cours"
        Mode.PENDING -> "Activation demandée — VPN inactif"
        Mode.DISABLED -> "INACTIF"
    }
}
