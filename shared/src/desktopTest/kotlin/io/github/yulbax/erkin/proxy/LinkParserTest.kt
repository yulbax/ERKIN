package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class LinkParserTest {

    @Test
    fun parsesVlessTlsProfile() {
        val parsed = LinkParser.parse(
            "vless://user-id@example.com:443?security=tls&sni=edge.example.com#Primary"
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("Primary", parsed.name)
        assertEquals(ProxyProtocol.VLESS, parsed.protocol)
        assertEquals("example.com", parsed.proxy["server"]?.jsonPrimitive?.content)
        assertEquals("user-id", parsed.proxy["uuid"]?.jsonPrimitive?.content)
        assertEquals(
            "edge.example.com",
            parsed.proxy["servername"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun namesWithSpacesAndHashesAreKeptAsWritten() {
        val links = listOf(
            "vless://id@example.com:443?type=ws&path=/al/2&security=tls&fp=firefox&alpn=http/1.1",
            "trojan://secret@example.com:443?security=tls",
            "tuic://uuid:pass@example.com:443?congestion_control=bbr",
            "hy2://pass@example.com:443",
            "anytls://pass@example.com:443",
            "socks5://example.com:1080"
        )
        for (link in links) {
            val parsed = requireNotNull(LinkParser.parse("$link#\uD83C\uDDE6\uD83C\uDDF1 Albania - Tirana #2")) { link }
            assertEquals(link, "\uD83C\uDDE6\uD83C\uDDF1 Albania - Tirana #2", parsed.name)
            assertEquals(link, "example.com", parsed.proxy["server"]?.jsonPrimitive?.content)
        }
        assertEquals("Tirana 2", LinkParser.parse("trojan://secret@example.com:443#Tirana%202")?.name)
    }

    @Test
    fun shadowsocksCarriesUdpOverTcpAndItsVersion() {
        val base = "ss://YWVzLTEyOC1nY206c2VjcmV0@203.0.113.5:8388/?"
        val versioned = requireNotNull(LinkParser.parse("${base}uot=1&udp-over-tcp-version=2#A")).proxy
        assertEquals("true", versioned["udp-over-tcp"]?.jsonPrimitive?.content)
        assertEquals("2", versioned["udp-over-tcp-version"]?.jsonPrimitive?.content)
        val plain = requireNotNull(LinkParser.parse("${base}udp-over-tcp=true#B")).proxy
        assertEquals("true", plain["udp-over-tcp"]?.jsonPrimitive?.content)
        assertNull(plain["udp-over-tcp-version"])
        assertNull(requireNotNull(LinkParser.parse("${base}udp-over-tcp-version=2#C")).proxy["udp-over-tcp"])
    }

    @Test
    fun transportPathIsDecodedOnce() {
        for (scheme in listOf("vless://id", "trojan://secret")) {
            val parsed = requireNotNull(LinkParser.parse("$scheme@example.com:443?security=tls&type=ws&path=%2Fa%252Fb%2Bc#P"))
            val wsOpts = Json.parseToJsonElement(parsed.proxyJson()).jsonObject.getValue("ws-opts").jsonObject
            assertEquals("/a%2Fb+c", wsOpts.getValue("path").jsonPrimitive.content)
        }
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertNull(LinkParser.parse("https://example.com/profile"))
    }

    @Test
    fun parsesPlainAndBase64Subscriptions() {
        val link = "vless://first@example.com:443?security=tls#One"
        val encoded = Base64.getEncoder().encodeToString(link.toByteArray())

        assertEquals(1, SubscriptionParser.parseBody(link).size)
        assertEquals(1, SubscriptionParser.parseBody(encoded).size)
        assertEquals("One", SubscriptionParser.parseBody(encoded).single().name)
    }
}
