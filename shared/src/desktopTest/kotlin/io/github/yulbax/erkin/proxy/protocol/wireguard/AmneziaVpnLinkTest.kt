package io.github.yulbax.erkin.proxy.protocol.wireguard

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ProxyProtocol
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class AmneziaVpnLinkTest {

    @Test
    fun parsesAmneziaLinkIntoAmneziaWgEndpoint() {
        val parsed = requireNotNull(LinkParser.parse(link(AMNEZIA_JSON)))

        assertEquals(ProxyProtocol.AMNEZIAWG, parsed.protocol)
        assertEquals("Home server", parsed.name)

        val endpoint = parsed.outbound
        assertEquals("awg", endpoint.getValue("type").jsonPrimitive.content)
        assertEquals(listOf("10.8.1.10/32"), endpoint.getValue("address").jsonArray.map { it.jsonPrimitive.content })

        val peer = endpoint.getValue("peers").jsonArray.single().jsonObject
        assertEquals("129.101.124.211", peer.getValue("address").jsonPrimitive.content)
        assertEquals(42874, peer.getValue("port").jsonPrimitive.content.toInt())
        assertEquals(25, peer.getValue("persistent_keepalive_interval").jsonPrimitive.content.toInt())
        assertEquals(1280, endpoint.getValue("mtu").jsonPrimitive.content.toInt())
    }

    @Test
    fun mapsAmneziaWg3ParametersToForkFieldNames() {
        val endpoint = requireNotNull(LinkParser.parse(link(AMNEZIA_JSON))).outbound

        assertEquals("aHeaderKey=", endpoint.getValue("header_protection_key").jsonPrimitive.content)
        assertEquals("100-120", endpoint.getValue("rekey_after_time").jsonPrimitive.content)
        assertEquals("3-7", endpoint.getValue("rekey_timeout").jsonPrimitive.content)
        assertEquals("150-180", endpoint.getValue("reject_after_time").jsonPrimitive.content)
        assertEquals("5-15", endpoint.getValue("keepalive_timeout").jsonPrimitive.content)
        assertEquals("15-20", endpoint.getValue("max_handshake_attempts").jsonPrimitive.content)
        assertEquals(true, endpoint.getValue("random_trailers").jsonPrimitive.content.toBoolean())
        assertEquals(true, endpoint.getValue("disable_cookies").jsonPrimitive.content.toBoolean())
        assertEquals("<b 0xf3>", endpoint.getValue("i1").jsonPrimitive.content)
        assertFalse(endpoint.getValue("jc").jsonPrimitive.isString)
        assertTrue(endpoint.keys.none { it == "DisableCookies" || it == "RandomTrailers" })
    }

    @Test
    fun rejectsLinksWithoutAWireguardContainer() {
        val other = AMNEZIA_JSON
            .replace("\"awg\"", "\"xray\"")
            .replace("amnezia-awg2", "amnezia-xray")
        assertNull(LinkParser.parse(link(other)))
    }

    @Test
    fun rejectsGarbagePayload() {
        assertNull(LinkParser.parse("vpn://not-a-valid-payload"))
    }

    private fun link(payload: String): String {
        val bytes = payload.encodeToByteArray()
        val deflater = Deflater()
        deflater.setInput(bytes)
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        val size = bytes.size
        val framed = byteArrayOf(
            (size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()
        ) + compressed.toByteArray()
        return "vpn://" + Base64.UrlSafe.encode(framed).trimEnd('=')
    }

    private companion object {
        val CONFIG = """
            [Interface]
            Address = 10.8.1.10/32
            DNS = ${'$'}PRIMARY_DNS, ${'$'}SECONDARY_DNS
            PrivateKey = aPrivateKey=
            Jc = 6
            Jmin = 10
            Jmax = 50
            S1 = 12
            S2 = 12
            S3 = 12
            S4 = 12
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0xf3>
            HeaderProtectionKey = aHeaderKey=
            RekeyAfterTime = 100-120
            RekeyTimeout = 3-7
            RejectAfterTime = 150-180
            KeepaliveTimeout = 5-15
            MaxHandshakeAttempts = 15-20
            RandomTrailers = on
            DisableCookies = on

            [Peer]
            PublicKey = aPublicKey=
            PresharedKey = aPresharedKey=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 129.101.124.211:42874
            PersistentKeepalive = 25-35
        """.trimIndent()

        val AMNEZIA_JSON = """
            {
              "containers": [
                {
                  "awg": {
                    "last_config": ${quoted(lastConfig())},
                    "port": "42874",
                    "protocol_version": "3.1",
                    "transport_proto": "udp"
                  },
                  "container": "amnezia-awg2"
                }
              ],
              "defaultContainer": "amnezia-awg2",
              "description": "Home server",
              "dns1": "1.1.1.1",
              "hostName": "129.101.124.211",
              "format_version": 1
            }
        """.trimIndent()

        private fun lastConfig(): String =
            """{"config": ${quoted(CONFIG)}, "mtu": "1280", "hostName": "129.101.124.211"}"""

        private fun quoted(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }
}
