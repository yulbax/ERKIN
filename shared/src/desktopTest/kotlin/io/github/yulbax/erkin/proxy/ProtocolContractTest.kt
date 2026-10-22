package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.data.AppConfigBackup
import io.github.yulbax.erkin.data.BackupCodec
import io.github.yulbax.erkin.data.ProfileBackup
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.mihomo.MihomoConfigBuilder
import io.github.yulbax.erkin.vpn.mihomo.ProbeInbound
import io.github.yulbax.erkin.vpn.mihomo.MihomoProxy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProtocolContractTest {

    @Test
    fun everyProtocolHasAContractSample() {
        assertEquals(ProxyProtocol.entries.toSet(), FORMS.keys)
        assertTrue(FORMS.values.all { it.isNotEmpty() })
    }

    @Test
    fun everyInputFormIsRecognizedByItsOwnProtocolOnly() {
        FORMS.forEach { (protocol, forms) ->
            forms.forEach { sample ->
                val recognizers = ProxyProtocol.entries.filter { it.handler.recognizes(sample) }
                assertEquals("recognizers of a $protocol form", listOf(protocol), recognizers)
            }
        }
    }

    @Test
    fun everyInputFormParsesIntoADescriptorWithAServer() {
        FORMS.flatMap { (protocol, forms) -> forms.map { protocol to it } }.forEach { (protocol, sample) ->
            val parsed = requireNotNull(LinkParser.parse(sample)) { "$protocol sample did not parse" }
            assertEquals(protocol, parsed.protocol)
            assertTrue("$protocol name", parsed.name.isNotBlank())
            val server = requireNotNull(protocol.handler.server(parsed.proxy)) { "$protocol server" }
            assertTrue("$protocol host", server.host.isNotBlank())
            assertTrue("$protocol port ${server.port}", server.port in 1..65535)
        }
    }

    @Test
    fun everyProtocolBecomesAMihomoProxy() {
        val proxies = parsedSamples().map { (protocol, parsed) ->
            protocol.engineProxy(protocol.wire, parsed.proxyJson())
        }
        proxies.forEach { proxy ->
            val converted = MihomoProxy.of(proxy, null)
            assertTrue("${proxy.tag} converts", converted != null)
            assertEquals(proxy.tag, converted?.getValue("name")?.jsonPrimitive?.content)
            assertEquals(proxy.tag, build(listOf(proxy), activeTag = proxy.tag).names("proxies").first())
        }
        val tested = Json.parseToJsonElement(MihomoConfigBuilder.proxiesForTest(proxies)) as JsonArray
        assertEquals(proxies.map { it.tag }, tested.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun sessionRequirementsOfTheActiveProxyShapeTheEngineConfig() {
        val proxies = parsedSamples().map { (protocol, parsed) ->
            protocol.engineProxy(protocol.wire, parsed.proxyJson())
        }
        proxies.forEach { active ->
            val config = build(proxies, activeTag = active.tag, NetworkOptions(ipv6Mode = Ipv6Mode.ENABLE))
            val ipv6 = config.getValue("dns").jsonObject.getValue("ipv6").jsonPrimitive.boolean
            assertEquals("${active.tag} ipv4 only", EngineRequirement.IPV4_ONLY in active.requirements, !ipv6)
        }
    }

    @Test
    fun anIpv6LiteralReachesTheCoreWithoutBrackets() {
        listOf(
            "hysteria2://secret@[2001:db8::1]:10444?sni=bingo&insecure=1#V6",
            "vless://00000000-0000-0000-0000-000000000000@[2001:db8::1]:443?security=tls#V6",
            "trojan://secret@[2001:db8::1]:443#V6",
            "tuic://00000000-0000-0000-0000-000000000000:secret@[2001:db8::1]:443#V6",
            "anytls://secret@[2001:db8::1]:443#V6",
            "socks5://[2001:db8::1]:1080#V6",
            "ss://YWVzLTEyOC1nY206c2VjcmV0@[2001:db8::1]:8388#V6"
        ).forEach { link ->
            val parsed = requireNotNull(LinkParser.parse(link)) { link }
            assertEquals(link, "2001:db8::1", parsed.protocol.handler.server(parsed.proxy)?.host)
        }
    }

    @Test
    fun eachParsedSampleSurvivesABackupRoundTrip() {
        val profiles = parsedSamples().map { (protocol, parsed) ->
            ProfileBackup(name = parsed.name, type = protocol.wire, link = parsed.link, outboundJson = parsed.proxyJson())
        }
        val backup = AppConfigBackup(version = AppConfigBackup.CURRENT_VERSION, profiles = profiles)

        assertTrue(BackupCodec.isValid(backup))
        profiles.forEach { profile ->
            assertTrue(profile.type, BackupCodec.isValid(backup.copy(profiles = listOf(profile))))
        }
    }

    private fun parsedSamples(): List<Pair<ProxyProtocol, ParsedProfile>> =
        ProxyProtocol.entries.map { it to requireNotNull(LinkParser.parse(SAMPLES.getValue(it))) }

    private fun build(proxies: List<EngineProxy>, activeTag: String, options: NetworkOptions = NetworkOptions()): JsonObject =
        Json.parseToJsonElement(
            MihomoConfigBuilder.build(
                EngineConfig(proxies, activeTag, emptyList(), listOf("org.telegram"), listOf("org.telegram"), 1081, options),
                ProbeInbound(2080, "user", "pass")
            )
        ).jsonObject

    private fun JsonObject.names(section: String): List<String> =
        this[section]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }.orEmpty()

    private companion object {
        val VMESS = "vmess://" + Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"Vienna","add":"vm.example.com","port":"443","id":"0b3f1a2c-1111-4222-8333-444455556666","aid":"0","net":"ws","host":"vm.example.com","path":"/ws","tls":"tls"}"""
                .toByteArray()
        )
        val SHADOWSOCKS = "ss://" + Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray()) +
            "@203.0.113.5:8388#Oslo"
        val AMNEZIAWG = """
            [Interface]
            Address = 10.8.1.2/32
            PrivateKey = aPrivateKey=
            Jc = 4

            [Peer]
            PublicKey = aPublicKey=
            AllowedIPs = 0.0.0.0/0
            Endpoint = awg.example.com:51820
        """.trimIndent()

        val PLAIN_WIREGUARD_CONF = """
            [Interface]
            Address = 10.9.0.2/32
            PrivateKey = qJ3Dt9VHrsHZmRxRZ1rH0hqTJ1zBBpK1ZnpT6YBr4Ec=

            [Peer]
            PublicKey = 7PjU0wTXBPX0CUHKCCgPLZ2fDLxTzBBpK1ZnpT6YBrQ=
            AllowedIPs = 0.0.0.0/0
            Endpoint = wg.example.com:51820
        """.trimIndent()

        val OPENVPN = """
            client
            dev tun
            proto udp
            remote ovpn.example.com 1194
            cipher AES-256-GCM
            data-ciphers AES-256-GCM:AES-128-GCM
            auth SHA256
            key-direction 1
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIB
            -----END CERTIFICATE-----
            </ca>
            <tls-auth>
            -----BEGIN OpenVPN Static key V1-----
            00
            -----END OpenVPN Static key V1-----
            </tls-auth>
            <auth-user-pass>
            alice
            secret
            </auth-user-pass>
        """.trimIndent()

        val FORMS: Map<ProxyProtocol, List<String>> = mapOf(
            ProxyProtocol.VMESS to listOf(VMESS),
            ProxyProtocol.VLESS to listOf(
                "vless://0b3f1a2c-1111-4222-8333-444455556666@vl.example.com:443?security=tls&sni=vl.example.com#Amsterdam"
            ),
            ProxyProtocol.TROJAN to listOf("trojan://secret@tr.example.org:443#Frankfurt"),
            ProxyProtocol.SHADOWSOCKS to listOf(SHADOWSOCKS),
            ProxyProtocol.HYSTERIA2 to listOf("hysteria2://secret@hy.example.net:8443?sni=hy.example.net#Helsinki"),
            ProxyProtocol.AMNEZIAWG to listOf(AMNEZIAWG),
            ProxyProtocol.WIREGUARD to listOf(
                "wireguard://qJ3Dt9VHrsHZmRxRZ1rH0hqTJ1zBBpK1ZnpT6YBr4Ec%3D@wg.example.com:51820" +
                    "?publickey=7PjU0wTXBPX0CUHKCCgPLZ2fDLxTzBBpK1ZnpT6YBrQ%3D&address=10.9.0.2%2F32&mtu=1280#Warsaw",
                PLAIN_WIREGUARD_CONF
            ),
            ProxyProtocol.TUIC to listOf(
                "tuic://0b3f1a2c-1111-4222-8333-444455556666:secret@tu.example.net:443" +
                    "?congestion_control=bbr&udp_relay_mode=native&alpn=h3&sni=tu.example.net#Tallinn"
            ),
            ProxyProtocol.ANYTLS to listOf("anytls://secret@at.example.org:443/?sni=at.example.org&insecure=1#Riga"),
            ProxyProtocol.SOCKS5 to listOf(
                "socks5://alice:secret@203.0.113.7:1080#Vilnius",
                "socks://" + Base64.getEncoder().encodeToString("alice:secret".toByteArray()) + "@203.0.113.8:1080#Minsk"
            ),
            ProxyProtocol.HTTPS to listOf("https://alice:secret@proxy.example.com:8443#Prague"),
            ProxyProtocol.OPENVPN to listOf(OPENVPN)
        )

        val SAMPLES: Map<ProxyProtocol, String> = FORMS.mapValues { it.value.first() }
    }
}
