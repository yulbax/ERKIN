package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.vpn.mihomo.SystemDns
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemDnsDetectorTest {

    @Test
    fun macUsesTheRouterResolverForDirectDns() {
        val output = """
            DNS configuration

            resolver #1
              nameserver[0] : fe80::1%en0
              nameserver[1] : 192.168.64.1
              if_index : 7 (en0)

            resolver #2
              domain   : local
              options  : mdns

            DNS configuration (for scoped queries)

            resolver #1
              nameserver[0] : 10.0.0.1
        """.trimIndent()
        assertEquals(SystemDns.Server("192.168.64.1"), SystemDnsDetector.fromScutil(output))
    }

    @Test
    fun macIgnoresTheTunnelResolverAndPublicServers() {
        val output = """
            DNS configuration

            resolver #1
              nameserver[0] : 198.18.0.2
              nameserver[1] : 1.1.1.1

            DNS configuration (for scoped queries)

            resolver #1
              nameserver[0] : 192.168.64.1
        """.trimIndent()
        assertEquals(SystemDns.Unknown, SystemDnsDetector.fromScutil(output))
    }
}
