package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import io.github.yulbax.erkin.vpn.core.EnginePlacement
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WireGuardTest {

    @Test
    fun parsesV2rayNgStyleLinkIntoWireguardEndpoint() {
        val parsed = requireNotNull(LinkParser.parse(FULL))
        val endpoint = parsed.outbound
        val peer = endpoint.peer()

        assertEquals(ProxyProtocol.WIREGUARD, parsed.protocol)
        assertEquals("WARP Frankfurt", parsed.name)
        assertEquals("wireguard", endpoint.text("type"))
        assertEquals(PRIVATE, endpoint.text("private_key"))
        assertEquals(listOf("172.16.0.2/32", "2606:4700:110:8a36::2/128"), endpoint.list("address"))
        assertEquals(1280, endpoint.text("mtu").toInt())
        assertEquals("engage.cloudflareclient.com", peer.text("address"))
        assertEquals(2408, peer.text("port").toInt())
        assertEquals(PUBLIC, peer.text("public_key"))
        assertEquals(PRESHARED, peer.text("pre_shared_key"))
        assertEquals(listOf("0.0.0.0/0", "::/0"), peer.list("allowed_ips"))
        assertEquals(listOf("12", "34", "56"), peer.list("reserved"))
    }

    @Test
    fun appliesTheCommonDefaults() {
        val parsed = requireNotNull(LinkParser.parse("wireguard://$PRIVATE_ENCODED@203.0.113.7?publickey=$PUBLIC_ENCODED"))
        val endpoint = parsed.outbound

        assertEquals("203.0.113.7", parsed.name)
        assertEquals(listOf("172.16.0.2/32"), endpoint.list("address"))
        assertEquals(1420, endpoint.text("mtu").toInt())
        assertEquals(51820, endpoint.peer().text("port").toInt())
        assertFalse(endpoint.peer().containsKey("reserved"))
        assertFalse(endpoint.peer().containsKey("pre_shared_key"))
    }

    @Test
    fun placesAsEndpointAndAsksForIpv4OnlyWithoutAnIpv6Address() {
        val v4Only = requireNotNull(LinkParser.parse("wireguard://$PRIVATE_ENCODED@h.example?publickey=$PUBLIC_ENCODED&address=10.0.0.2"))
        val dualStack = requireNotNull(LinkParser.parse(FULL))

        assertEquals(EnginePlacement.ENDPOINT, WireGuard.placement)
        assertEquals(setOf(EngineRequirement.IPV4_ONLY), WireGuard.requirements(v4Only.outbound))
        assertEquals(emptySet<EngineRequirement>(), WireGuard.requirements(dualStack.outbound))
        assertEquals(listOf("10.0.0.2/32"), v4Only.outbound.list("address"))
    }

    @Test
    fun parsesAPlainConfIntoTheUpstreamEndpoint() {
        val parsed = requireNotNull(LinkParser.parse(PLAIN_CONF))
        val endpoint = parsed.outbound
        val peer = endpoint.peer()

        assertEquals(ProxyProtocol.WIREGUARD, parsed.protocol)
        assertEquals("wg.example.com", parsed.name)
        assertEquals("wireguard", endpoint.text("type"))
        assertEquals(listOf("10.9.0.2/32", "fd00::2/128"), endpoint.list("address"))
        assertEquals(1380, endpoint.text("mtu").toInt())
        assertEquals("wg.example.com", peer.text("address"))
        assertEquals(51820, peer.text("port").toInt())
        assertEquals(PRESHARED, peer.text("pre_shared_key"))
        assertFalse(peer.containsKey("preshared_key"))
        assertEquals(listOf("0.0.0.0/0"), peer.list("allowed_ips"))
        assertEquals(25, peer.text("persistent_keepalive_interval").toInt())
        assertEquals(listOf("1", "2", "3"), peer.list("reserved"))
        assertEquals(emptySet<EngineRequirement>(), WireGuard.requirements(endpoint))
    }

    @Test
    fun rejectsLinksWithoutUsableKeysOrHost() {
        listOf(
            "wireguard://$PRIVATE_ENCODED@h.example",
            "wireguard://@h.example?publickey=$PUBLIC_ENCODED",
            "wireguard://not-a-key@h.example?publickey=$PUBLIC_ENCODED",
            "wireguard://$PRIVATE_ENCODED@h.example?publickey=short",
            "wireguard://$PRIVATE_ENCODED@h.example?publickey=$PUBLIC_ENCODED&presharedkey=bad"
        ).forEach { link -> assertNull(link, LinkParser.parse(link)) }
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.list(key: String): List<String> = getValue(key).jsonArray.map { it.jsonPrimitive.content }
    private fun JsonObject.peer(): JsonObject = getValue("peers").jsonArray.single().jsonObject

    private companion object {
        const val PRIVATE = "qJ3Dt9VHrsHZmRxRZ1rH0hqTJ1zBBpK1ZnpT6YBr4Ec="
        const val PUBLIC = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
        const val PRESHARED = "FpCyhws9cxwWoV4xELtfJvjJN+zQVRPISllRWgeopVE="
        const val PRIVATE_ENCODED = "qJ3Dt9VHrsHZmRxRZ1rH0hqTJ1zBBpK1ZnpT6YBr4Ec%3D"
        const val PUBLIC_ENCODED = "bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzH0JuVo51h2wPfgyo%3D"
        val PLAIN_CONF = """
            [Interface]
            PrivateKey = $PRIVATE
            Address = 10.9.0.2/32, fd00::2
            MTU = 1380

            [Peer]
            PublicKey = $PUBLIC
            PresharedKey = $PRESHARED
            AllowedIPs = 0.0.0.0/0
            Endpoint = wg.example.com:51820
            PersistentKeepalive = 25
            Reserved = 1, 2, 3
        """.trimIndent()
        const val FULL = "wireguard://$PRIVATE_ENCODED@engage.cloudflareclient.com:2408" +
            "?publickey=$PUBLIC_ENCODED&presharedkey=FpCyhws9cxwWoV4xELtfJvjJN%2BzQVRPISllRWgeopVE%3D" +
            "&address=172.16.0.2%2F32%2C2606%3A4700%3A110%3A8a36%3A%3A2%2F128&mtu=1280&reserved=12%2C34%2C56" +
            "#WARP%20Frankfurt"
    }
}
