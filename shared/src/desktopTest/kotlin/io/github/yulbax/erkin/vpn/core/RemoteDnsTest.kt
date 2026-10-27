package io.github.yulbax.erkin.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteDnsTest {
    @Test
    fun plainAddressesStayDnsOverTls() {
        assertEquals("tls://1.1.1.1", RemoteDns.nameserver("1.1.1.1"))
        assertEquals("tls://dns.google", RemoteDns.nameserver(" dns.google "))
        assertEquals("tls://[2606:4700:4700::1111]", RemoteDns.nameserver("2606:4700:4700::1111"))
    }

    @Test
    fun plainPrivateAddressesUsePlainDns() {
        assertEquals("udp://192.168.1.1", RemoteDns.nameserver("192.168.1.1"))
        assertEquals("udp://10.2.0.1", RemoteDns.nameserver("10.2.0.1"))
        assertEquals("udp://[fd00::1]", RemoteDns.nameserver("fd00::1"))
        assertEquals("tls://192.168.1.1", RemoteDns.nameserver("tls://192.168.1.1"))
    }

    @Test
    fun fullAddressesKeepTheirProtocol() {
        assertEquals("https://dns.google/dns-query", RemoteDns.nameserver("https://dns.google/dns-query"))
        assertEquals("quic://dns.adguard-dns.com", RemoteDns.nameserver("quic://dns.adguard-dns.com"))
        assertEquals("udp://9.9.9.9:53", RemoteDns.nameserver("udp://9.9.9.9:53"))
    }

    @Test
    fun addressesThatWouldLeakOrBreakAreRejected() {
        assertNull(RemoteDns.nameserver("system://"))
        assertNull(RemoteDns.nameserver("dhcp://wlan0"))
        assertNull(RemoteDns.nameserver("https://dns.google/dns-query#DIRECT"))
        assertNull(RemoteDns.nameserver("1.1.1.1 8.8.8.8"))
        assertNull(RemoteDns.nameserver(""))
    }
}
