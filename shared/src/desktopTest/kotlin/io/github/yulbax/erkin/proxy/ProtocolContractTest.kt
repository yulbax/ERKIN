package io.github.yulbax.erkin.proxy

import io.github.yulbax.erkin.data.AppConfigBackup
import io.github.yulbax.erkin.data.BackupCodec
import io.github.yulbax.erkin.data.BackupProfile
import io.github.yulbax.erkin.vpn.core.EnginePlacement
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.singbox.ConfigBuilder
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
            val server = requireNotNull(protocol.handler.server(parsed.outbound)) { "$protocol server" }
            assertTrue("$protocol host", server.host.isNotBlank())
            assertTrue("$protocol port ${server.port}", server.port in 1..65535)
        }
    }

    @Test
    fun theEngineConfigPlacesEveryProtocolWhereItsCodecSays() {
        val proxies = parsedSamples().map { (protocol, parsed) ->
            protocol.engineProxy(protocol.wire, parsed.outboundJson())
        }
        val config = build(proxies, activeTag = proxies.first().tag)
        val outbounds = config.tags("outbounds")
        val endpoints = config.tags("endpoints")

        parsedSamples().forEach { (protocol, _) ->
            val expected = if (protocol.handler.placement == EnginePlacement.ENDPOINT) endpoints else outbounds
            assertTrue("$protocol placed as ${protocol.handler.placement}", protocol.wire in expected)
        }
        val latencyTest = Json.parseToJsonElement(ConfigBuilder.buildLatencyTest(proxies)).jsonObject
        parsedSamples().forEach { (protocol, _) ->
            val section = if (protocol.handler.placement == EnginePlacement.ENDPOINT) "endpoints" else "outbounds"
            assertTrue("$protocol in the latency test $section", protocol.wire in latencyTest.tags(section))
        }
    }

    @Test
    fun sessionRequirementsOfTheActiveProxyShapeTheEngineConfig() {
        val proxies = parsedSamples().map { (protocol, parsed) ->
            protocol.engineProxy(protocol.wire, parsed.outboundJson())
        }
        proxies.forEach { active ->
            val config = build(proxies, activeTag = active.tag, NetworkOptions(ipv6Mode = Ipv6Mode.ENABLE))
            val rules = config.getValue("route").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
            val resolves = rules.any { it["action"]?.jsonPrimitive?.content == "resolve" }
            val strategy = config.getValue("dns").jsonObject.getValue("strategy").jsonPrimitive.content
            assertEquals("${active.tag} resolve rule", EngineRequirement.RESOLVE_BEFORE_DIAL in active.requirements, resolves)
            assertEquals("${active.tag} ipv4_only", EngineRequirement.IPV4_ONLY in active.requirements, strategy == "ipv4_only")
        }
    }

    @Test
    fun eachParsedSampleSurvivesABackupRoundTrip() {
        val profiles = parsedSamples().map { (protocol, parsed) ->
            BackupProfile(name = parsed.name, type = protocol.wire, link = parsed.link, outboundJson = parsed.outboundJson())
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
            ConfigBuilder.build(
                proxies = proxies,
                activeProxyTag = activeTag,
                byeDpiPackages = emptyList(),
                vpnPackages = listOf("org.telegram"),
                tunneledPackages = listOf("org.telegram"),
                byeDpiPort = 1081,
                probePort = 2080,
                probeUser = "user",
                probePass = "pass",
                options = options
            )
        ).jsonObject

    private fun JsonObject.tags(section: String): List<String> =
        this[section]?.jsonArray?.mapNotNull { it.jsonObject["tag"]?.jsonPrimitive?.content }.orEmpty()

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
            )
        )

        val SAMPLES: Map<ProxyProtocol, String> = FORMS.mapValues { it.value.first() }
    }
}
