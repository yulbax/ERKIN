package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaWgTest {

    @Test
    fun parsesAmneziaWgConfigIntoWireguardEndpoint() {
        val parsed = requireNotNull(LinkParser.parse(CONFIG))

        assertEquals(ProxyProtocol.AMNEZIAWG, parsed.protocol)
        assertEquals("vpn.example.com", parsed.name)

        val proxy = parsed.proxy
        assertEquals("wireguard", proxy.getValue("type").jsonPrimitive.content)
        assertEquals("aPrivateKey=", proxy.getValue("private-key").jsonPrimitive.content)
        assertEquals("10.8.1.2/32", proxy.getValue("ip").jsonPrimitive.content)
        assertEquals("fd42::2/128", proxy.getValue("ipv6").jsonPrimitive.content)
        assertEquals(1280, proxy.getValue("mtu").jsonPrimitive.content.toInt())
        assertEquals("vpn.example.com", proxy.getValue("server").jsonPrimitive.content)
        assertEquals(51820, proxy.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("aPublicKey=", proxy.getValue("public-key").jsonPrimitive.content)
        assertEquals("aPresharedKey=", proxy.getValue("pre-shared-key").jsonPrimitive.content)
        assertEquals(listOf("0.0.0.0/0", "::/0"), proxy.getValue("allowed-ips").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(25, proxy.getValue("persistent-keepalive").jsonPrimitive.content.toInt())

        val options = proxy.getValue("amnezia-wg-option").jsonObject
        assertFalse("obfuscation numbers must stay JSON numbers", options.getValue("jc").jsonPrimitive.isString)
        assertTrue("magic headers are strings in the Amnezia schema", options.getValue("h1").jsonPrimitive.isString)
        assertFalse(proxy.getValue("mtu").jsonPrimitive.isString)
        assertEquals(4, options.getValue("jc").jsonPrimitive.content.toInt())
        assertEquals(50, options.getValue("jmin").jsonPrimitive.content.toInt())
        assertEquals(1000, options.getValue("jmax").jsonPrimitive.content.toInt())
        assertEquals(15, options.getValue("s1").jsonPrimitive.content.toInt())
        assertEquals("1148364954", options.getValue("h1").jsonPrimitive.content)
        assertNull(options["version"])
        assertNull(proxy["dns"])
        assertEquals(CONFIG, parsed.link)
    }

    @Test
    fun leavesAConfigWithoutObfuscationToPlainWireguard() {
        val plain = CONFIG.lines().filterNot { it.startsWith("J") || it.startsWith("S") || it.startsWith("H") }.joinToString("\n")
        val parsed = requireNotNull(LinkParser.parse(plain))

        assertEquals(ProxyProtocol.WIREGUARD, parsed.protocol)
        assertEquals("wireguard", parsed.proxy.getValue("type").jsonPrimitive.content)
        assertNull(parsed.proxy["amnezia-wg-option"])
        assertFalse(AmneziaWg.recognizes(plain))
    }

    @Test
    fun rejectsConfigWithoutPeer() {
        assertNull(LinkParser.parse("[Interface]\nPrivateKey = aPrivateKey=\nAddress = 10.8.1.2/32\n"))
    }

    @Test
    fun stillParsesRegularLinks() {
        assertEquals(
            ProxyProtocol.VLESS,
            requireNotNull(LinkParser.parse("vless://id@example.com:443?security=tls#Amsterdam")).protocol
        )
    }

    private companion object {
        val CONFIG = """
            [Interface]
            Address = 10.8.1.2/32, fd42::2/128
            DNS = 1.1.1.1
            PrivateKey = aPrivateKey=
            MTU = 1280
            Jc = 4
            Jmin = 50
            Jmax = 1000
            S1 = 15
            S2 = 36
            H1 = 1148364954
            H2 = 1813799810
            H3 = 1082378570
            H4 = 1151590459

            [Peer]
            PublicKey = aPublicKey=
            PresharedKey = aPresharedKey=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = vpn.example.com:51820
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
