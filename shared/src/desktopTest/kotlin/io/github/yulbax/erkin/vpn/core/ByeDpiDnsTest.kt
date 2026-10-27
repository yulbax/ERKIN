package io.github.yulbax.erkin.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ByeDpiDnsTest {
    @Test
    fun nameserverCarriesTheTransport() {
        assertEquals("tls://1.1.1.1", ByeDpiDns.DEFAULT.nameserver())
        assertEquals("udp://9.9.9.9", ByeDpiDns("9.9.9.9", DnsTransport.UDP).nameserver())
        assertEquals("tls://[2606:4700:4700::1111]", ByeDpiDns("2606:4700:4700::1111", DnsTransport.TLS).nameserver())
    }

    @Test
    fun onlyPlainIpAddressesAreAcceptedAndKeptAsTyped() {
        assertEquals("1.1.1.1", ByeDpiDns.normalizeAddress(" 1.1.1.1 "))
        assertEquals("2606:4700:4700::1111", ByeDpiDns.normalizeAddress("2606:4700:4700::1111"))
        assertEquals("2606:4700:4700::1111", ByeDpiDns.normalizeAddress("2606:4700:4700::1111".uppercase()))
        listOf("dns.google", "tls://1.1.1.1", "1.1.1.0/24", "1.1.1.256", "")
            .forEach { assertNull(it, ByeDpiDns.normalizeAddress(it)) }
    }

    @Test
    fun unknownStoredTransportFallsBackToEncrypted() {
        assertEquals(DnsTransport.UDP, DnsTransport.fromWire("udp"))
        assertEquals(DnsTransport.TLS, DnsTransport.fromWire("quic"))
        assertEquals(DnsTransport.TLS, DnsTransport.of(encrypted = true))
        assertEquals(DnsTransport.UDP, DnsTransport.of(encrypted = false))
    }
}
