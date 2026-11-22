package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.proxy.protocol.OpenVpn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnLoginTest {
    private val asking = """
        client
        dev tun
        remote vpn.example 1194 udp
        auth-user-pass
        <ca>
        CERT
        </ca>
    """.trimIndent()

    @Test
    fun aConfigThatAsksForALoginIsNotAddedUntilItGetsOne() {
        assertTrue(OpenVpn.asksForLogin(asking))
        assertNull(LinkParser.parse(asking))
    }

    @Test
    fun aLoginTurnsItIntoAServerWithThoseCredentials() {
        val filled = OpenVpn.withLogin(asking, "user@example.org", "secret")
        val proxy = LinkParser.parse(filled)?.proxyJson().orEmpty()

        assertFalse(OpenVpn.asksForLogin(filled))
        assertTrue("\"username\":\"user@example.org\"" in proxy)
        assertTrue("\"password\":\"secret\"" in proxy)
    }

    @Test
    fun aNewLoginReplacesTheStoredOne() {
        val first = OpenVpn.withLogin(asking, "old@example.org", "old")
        val second = OpenVpn.withLogin(first, "new@example.org", "new")
        val proxy = LinkParser.parse(second)?.proxyJson().orEmpty()

        assertEquals("new@example.org", OpenVpn.username(second))
        assertEquals(1, Regex("<auth-user-pass>").findAll(second).count())
        assertTrue("\"password\":\"new\"" in proxy)
        assertNull(OpenVpn.username(asking))
    }

    @Test
    fun aConfigWithoutALoginLineOrWithItInlineDoesNotAsk() {
        assertFalse(OpenVpn.asksForLogin(asking.replace("auth-user-pass\n", "")))
        assertFalse(OpenVpn.asksForLogin("vless://id@host:443#Name"))
        assertEquals(false, OpenVpn.asksForLogin(asking.replace("auth-user-pass", "auth-user-pass creds.txt")))
    }

    @Test
    fun commentedOutCertificateBlocksAreIgnored() {
        val softEther = """
            client
            dev tun
            proto udp
            remote vpn.example 1194
            auth-user-pass
            <ca>
            -----BEGIN CERTIFICATE-----
            CA
            -----END CERTIFICATE-----
            </ca>
            # Paste your certificate in the <cert> block and the key in the <key> one.
            ;<cert>
            ;-----BEGIN CERTIFICATE-----
            ;-----END CERTIFICATE-----
            ;</cert>
            #<key>
            #-----BEGIN PRIVATE KEY-----
            #-----END PRIVATE KEY-----
            #</key>
        """.trimIndent()
        val proxy = LinkParser.parse(OpenVpn.withLogin(softEther, "user", "secret"))?.proxyJson().orEmpty()

        assertTrue("\"ca\":\"-----BEGIN CERTIFICATE-----\\nCA\\n-----END CERTIFICATE-----\"" in proxy)
        assertFalse("\"cert\"" in proxy)
        assertFalse("\"key\"" in proxy)
    }
}
