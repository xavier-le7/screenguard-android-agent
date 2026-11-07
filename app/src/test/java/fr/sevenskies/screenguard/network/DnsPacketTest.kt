package fr.sevenskies.screenguard.network

import org.junit.Assert.*
import org.junit.Test

class DnsPacketTest {
    private fun packet(): ByteArray {
        val dns = byteArrayOf(
            0x12,0x34, 0x01,0x00, 0x00,0x01, 0x00,0x00, 0x00,0x00, 0x00,0x00,
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0x00,
            0x00,0x01, 0x00,0x01
        )
        val p = ByteArray(28 + dns.size)
        p[0] = 0x45
        p[2] = (p.size shr 8).toByte(); p[3] = p.size.toByte()
        p[8] = 64; p[9] = 17
        byteArrayOf(10,44,0,2).copyInto(p, 12)
        byteArrayOf(10,44,0,1).copyInto(p, 16)
        p[20] = 0x17; p[21] = 0x70
        p[22] = 0; p[23] = 53
        p[24] = ((dns.size+8) shr 8).toByte(); p[25] = (dns.size+8).toByte()
        dns.copyInto(p, 28)
        return p
    }

    @Test fun parseAndRespondNxDomain() {
        val p = packet()
        val q = DnsPacket.parse(p, p.size)
        assertNotNull(q)
        assertEquals("example.com", q!!.domain)
        val dns = DnsPacket.errorDns(q.dns, 3)
        assertEquals(3, dns[3].toInt() and 15)
        val response = DnsPacket.response(q, dns)
        assertNotNull(response)
        assertEquals(53, response!![21].toInt() and 255)
        assertEquals(0x17, response[22].toInt() and 255)
        assertEquals(10, response[12].toInt() and 255)
    }

    @Test fun rejectsMalformedUdpAndDns() {
        val p = packet()
        p[23] = 54
        assertNull(DnsPacket.parse(p, p.size))
        assertNull(DnsPacket.parse(byteArrayOf(0), 1))
    }
}
