package fr.sevenskies.screenguard.network

/**
 * Tiny bounded IPv4/UDP DNS-only VPN packet codec. No general traffic handling.
 * Never stores domain names or packet payloads. Validates each offset before use.
 */
object DnsPacket {
    data class Query(
        val source: ByteArray, val destination: ByteArray,
        val sourcePort: Int, val dns: ByteArray, val domain: String
    )

    private fun u8(b: Byte) = b.toInt() and 255
    private fun word(b: ByteArray, p: Int) = (u8(b[p]) shl 8) or u8(b[p + 1])
    private fun write16(b: ByteArray, p: Int, n: Int) {
        b[p] = (n shr 8).toByte()
        b[p + 1] = n.toByte()
    }

    fun parse(packet: ByteArray, size: Int): Query? {
        if (size !in 40..1500 || u8(packet[0]) shr 4 != 4) return null
        val ihl = (u8(packet[0]) and 15) * 4
        if (ihl < 20 || size < ihl + 20 || word(packet, 2) > size ||
            word(packet, 6) and 0x3fff != 0 || u8(packet[9]) != 17) return null
        val total = word(packet, 2)
        if (total < ihl + 20 || word(packet, ihl + 2) != 53) return null
        val length = word(packet, ihl + 4)
        if (length < 20 || length > total - ihl) return null
        val start = ihl + 8
        val dns = packet.copyOfRange(start, start + length - 8)
        if (dns.size < 17 || word(dns, 4) != 1) return null
        val labels = mutableListOf<String>()
        var pos = 12
        while (pos < dns.size) {
            val n = u8(dns[pos++])
            if (n == 0) break
            if (n > 63 || labels.size >= 60 || pos + n > dns.size) return null
            val label = String(dns, pos, n, Charsets.US_ASCII)
            if (!label.matches(Regex("[A-Za-z0-9_-]+"))) return null
            labels.add(label)
            pos += n
        }
        if (pos + 4 > dns.size || labels.isEmpty()) return null
        return Query(packet.copyOfRange(12, 16), packet.copyOfRange(16, 20),
            word(packet, ihl), dns, labels.joinToString("."))
    }

    /** DNS question is preserved; all answer and additional records are removed. */
    fun errorDns(query: ByteArray, code: Int): ByteArray {
        require(query.size in 17..1472 && code in 0..15)
        var end = 12
        while (end < query.size) {
            val n = u8(query[end++])
            if (n == 0) break
            require(n in 1..63 && end + n <= query.size)
            end += n
        }
        require(end + 4 <= query.size)
        val result = query.copyOfRange(0, end + 4)
        // Reply with QR, copy RD from the query, advertise recursion; rcode.
        result[2] = (0x80 or (u8(query[2]) and 0x01)).toByte()
        result[3] = (0x80 or code).toByte()
        for (p in 6..11) result[p] = 0
        return result
    }

    fun response(request: Query, answer: ByteArray): ByteArray? {
        if (answer.size < 12 || answer.size > 1472) return null
        val total = 20 + 8 + answer.size
        if (total > 1500) return null
        val packet = ByteArray(total)
        packet[0] = 0x45
        write16(packet, 2, total)
        packet[8] = 64
        packet[9] = 17
        request.destination.copyInto(packet, 12)
        request.source.copyInto(packet, 16)
        val offset = 20
        write16(packet, offset, 53)
        write16(packet, offset + 2, request.sourcePort)
        write16(packet, offset + 4, answer.size + 8)
        answer.copyInto(packet, offset + 8)
        write16(packet, 10, checksum(packet, 0, 20))
        val pseudo = ByteArray(12 + 8 + answer.size)
        packet.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 17
        write16(pseudo, 10, 8 + answer.size)
        packet.copyInto(pseudo, 12, 20, total)
        val udpChecksum = checksum(pseudo, 0, pseudo.size)
        write16(packet, offset + 6, if (udpChecksum == 0) 0xffff else udpChecksum)
        return packet
    }

    private fun checksum(b: ByteArray, from: Int, n: Int): Int {
        var sum = 0L
        var i = from
        while (i + 1 < from + n) {
            sum += word(b, i)
            i += 2
        }
        if (i < from + n) sum += u8(b[i]) shl 8
        while (sum > 0xffff) sum = (sum and 0xffffL) + (sum shr 16)
        return (sum.toInt() xor 0xffff) and 0xffff
    }
}
