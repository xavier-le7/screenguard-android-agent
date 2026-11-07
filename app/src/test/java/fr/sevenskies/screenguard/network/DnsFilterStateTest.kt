package fr.sevenskies.screenguard.network

import org.junit.Assert.*
import org.junit.Test

class DnsFilterStateTest {
    @Test fun stoppedVpnIsNeverAdvertisedAsFiltering() {
        assertFalse(DnsFilterState.isActive(parentEnabled = false, serviceRunning = false))
        assertFalse(DnsFilterState.isActive(parentEnabled = true, serviceRunning = false))
        assertFalse(DnsFilterState.isActive(parentEnabled = false, serviceRunning = true))
        assertTrue(DnsFilterState.isActive(parentEnabled = true, serviceRunning = true))
    }

    @Test fun parentCanRequestStopEvenWhileVpnStillShuttingDown() {
        assertEquals(DnsFilterState.Mode.STOPPING,
            DnsFilterState.mode(parentEnabled = false, serviceRunning = true))
        assertEquals("Arrêt du VPN en cours",
            DnsFilterState.label(DnsFilterState.Mode.STOPPING))
    }

    @Test fun parentCanSeePendingAndDisabledStates() {
        assertEquals(DnsFilterState.Mode.PENDING,
            DnsFilterState.mode(parentEnabled = true, serviceRunning = false))
        assertEquals("INACTIF",
            DnsFilterState.label(DnsFilterState.Mode.DISABLED))
    }
}
