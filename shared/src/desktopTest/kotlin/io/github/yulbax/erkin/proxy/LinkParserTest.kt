package io.github.yulbax.erkin.proxy

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
