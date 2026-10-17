package io.github.yulbax.erkin.proxy.protocol

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NewProtocolsTest {

    @Test
    fun tuicCarriesItsQuicOptions() {
        val proxy = parse(
            "tuic://uuid-1:p%40ss@tu.example.net:8443?congestion_control=bbr&udp_relay_mode=quic&alpn=h3&allow_insecure=1#T",
            ProxyProtocol.TUIC
        )
        assertEquals("uuid-1", proxy.text("uuid"))
        assertEquals("p@ss", proxy.text("password"))
        assertEquals("bbr", proxy.text("congestion-controller"))
        assertEquals("quic", proxy.text("udp-relay-mode"))
        assertEquals("tu.example.net", proxy.text("sni"))
        assertEquals("true", proxy.text("skip-cert-verify"))
        assertEquals(listOf("h3"), proxy.getValue("alpn").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun tuicWithoutAPasswordIsRejected() {
        assertNull(LinkParser.parse("tuic://uuid-only@tu.example.net:443#T"))
    }

    @Test
    fun anyTlsKeepsTheFingerprint() {
        val proxy = parse("anytls://secret@at.example.org:443/?sni=cdn.example.org&fp=chrome#A", ProxyProtocol.ANYTLS)
        assertEquals("secret", proxy.text("password"))
        assertEquals("cdn.example.org", proxy.text("sni"))
        assertEquals("chrome", proxy.text("client-fingerprint"))
    }

    @Test
    fun socksAcceptsPlainAndBase64Credentials() {
        val plain = parse("socks5://alice:s%3Acret@203.0.113.7#S", ProxyProtocol.SOCKS5)
        assertEquals("alice", plain.text("username"))
        assertEquals("s:cret", plain.text("password"))
        assertEquals("1080", plain.text("port"))
        val anonymous = parse("socks5://203.0.113.7:1081#S", ProxyProtocol.SOCKS5)
        assertNull(anonymous["username"])
    }

    @Test
    fun httpsProxiesNeverSwallowSubscriptionLinks() {
        val proxy = parse("https://alice:secret@proxy.example.com:8443#P", ProxyProtocol.HTTPS)
        assertEquals("true", proxy.text("tls"))
        assertEquals("false", proxy.text("udp"))
        listOf(
            "https://sub.example.com/api/v1/client/subscribe?token=abc",
            "https://sub.example.com:8443/s/abc",
            "https://example.com",
            "https://example.com:443"
        ).forEach { subscription -> assertFalse(subscription, Https.recognizes(subscription)) }
    }

    @Test
    fun openVpnNeedsInlineKeysAndCredentials() {
        val config = """
            client
            remote vpn.example.com 443 tcp
            <ca>
            CA
            </ca>
        """.trimIndent()
        val proxy = parse(config, ProxyProtocol.OPENVPN)
        assertEquals("vpn.example.com", proxy.text("server"))
        assertEquals("443", proxy.text("port"))
        assertEquals("tcp", proxy.text("proto"))
        assertEquals("CA", proxy.text("ca"))

        assertNull(LinkParser.parse("client\nremote vpn.example.com 1194\nca ca.crt\n"))
        assertNull(LinkParser.parse("$config\nauth-user-pass\n"))
    }

    private fun parse(input: String, protocol: ProxyProtocol): JsonObject {
        val parsed = requireNotNull(LinkParser.parse(input)) { input }
        assertEquals(protocol, parsed.protocol)
        return parsed.proxy
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content
}
