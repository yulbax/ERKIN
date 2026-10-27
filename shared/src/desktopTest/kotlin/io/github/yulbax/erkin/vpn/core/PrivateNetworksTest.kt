package io.github.yulbax.erkin.vpn.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateNetworksTest {
    @Test
    fun addressesInsidePrivateRangesMatch() {
        listOf("10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.1.1", "100.64.0.1", "fd00::1", "fe80::1", "[fd00::1]")
            .forEach { assertTrue(it, PrivateNetworks.contains(it)) }
    }

    @Test
    fun addressesJustOutsideOrPublicDoNotMatch() {
        listOf("172.15.255.255", "172.32.0.0", "100.128.0.0", "1.1.1.1", "2606:4700:4700::1111", "fec0::1")
            .forEach { assertFalse(it, PrivateNetworks.contains(it)) }
    }

    @Test
    fun hostNamesAreNeverResolved() {
        listOf("dns.google", "localhost", "router.lan", "")
            .forEach { assertFalse(it, PrivateNetworks.contains(it)) }
    }
}
