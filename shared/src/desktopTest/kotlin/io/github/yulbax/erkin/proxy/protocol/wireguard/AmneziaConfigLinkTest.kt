package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class AmneziaConfigLinkTest {

    @Test
    fun parsesAmneziaWgLinkIntoEndpoint() {
        val parsed = requireNotNull(LinkParser.parse(link(CONFIG, "02")))

        assertEquals(ProxyProtocol.AMNEZIAWG, parsed.protocol)
        assertEquals("02", parsed.name)
        assertEquals("wireguard", parsed.proxy.getValue("type").jsonPrimitive.content)
        assertEquals("172.16.0.2/32", parsed.proxy.getValue("ip").jsonPrimitive.content)
        assertEquals("2606:4700:110:86a9:a897:c12d:2551:83d4/128", parsed.proxy.getValue("ipv6").jsonPrimitive.content)
        assertEquals("162.159.195.4", parsed.proxy.getValue("server").jsonPrimitive.content)
        assertEquals(942, parsed.proxy.getValue("port").jsonPrimitive.content.toInt())
        assertEquals(1280, parsed.proxy.getValue("mtu").jsonPrimitive.content.toInt())
        assertEquals(25, parsed.proxy.getValue("persistent-keepalive").jsonPrimitive.content.toInt())

        val options = parsed.proxy.getValue("amnezia-wg-option").jsonObject
        assertEquals(4, options.getValue("jc").jsonPrimitive.content.toInt())
        assertEquals(40, options.getValue("jmin").jsonPrimitive.content.toInt())
        assertEquals(70, options.getValue("jmax").jsonPrimitive.content.toInt())
        assertEquals("1", options.getValue("h1").jsonPrimitive.content)
        assertTrue(options.getValue("i1").jsonPrimitive.content.startsWith("<b 0x"))
    }

    @Test
    fun decodesTheFragmentAsTheName() {
        assertEquals("Home server", requireNotNull(LinkParser.parse(link(CONFIG, "Home%20server"))).name)
        assertEquals("162.159.195.4", requireNotNull(LinkParser.parse(link(CONFIG, null))).name)
    }

    @Test
    fun rejectsPayloadThatIsNotAConfig() {
        assertNull(LinkParser.parse("amneziawg://${encoded("hello")}"))
    }

    private fun link(config: String, name: String?): String =
        "amneziawg://${encoded(config)}" + (name?.let { "#$it" } ?: "")

    private fun encoded(value: String): String =
        Base64.UrlSafe.encode(value.encodeToByteArray()).trimEnd('=')

    private companion object {
        val CONFIG = """
            [Interface]
            Address = 172.16.0.2/32
            Address = 2606:4700:110:86a9:a897:c12d:2551:83d4/128
            PrivateKey = 4CQZ0B2iOfhPOTJl/P7+s4/kWNL12xY4tIJpV0ZNtkk=
            MTU = 1280
            Jc = 4
            Jmin = 40
            Jmax = 70
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0xcf0000000114db586441e3861982434d3eeb4509>

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 162.159.195.4:942
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
