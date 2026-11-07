package fr.sevenskies.screenguard.network

import org.junit.Assert.*
import org.junit.Test

class DomainFilterTest {
    @Test fun exactAndSubdomainsBlocked() {
        val blocked = setOf("example.com", "Bad.Example.ORG.")
        assertTrue(DomainFilter.blocked("example.com", blocked))
        assertTrue(DomainFilter.blocked("WWW.EXAMPLE.COM.", blocked))
        assertTrue(DomainFilter.blocked("x.bad.example.org", blocked))
        assertFalse(DomainFilter.blocked("notexample.com", blocked))
        assertFalse(DomainFilter.blocked("example.com.attacker.net", blocked))
    }

    @Test fun malformedHostsCannotMatch() {
        assertNull(DomainFilter.normalise("https://example.com"))
        assertNull(DomainFilter.normalise("x..example.com"))
        assertNull(DomainFilter.normalise("-bad.example.com"))
        assertFalse(DomainFilter.blocked("good.example.com", setOf("exa/mple.com")))
    }
}
