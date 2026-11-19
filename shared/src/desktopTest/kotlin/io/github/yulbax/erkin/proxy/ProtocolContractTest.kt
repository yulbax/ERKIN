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
import kotlinx.serialization.json.JsonPrimitive
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
                assertEquals("recognizers of a $protocol form", listOf(protocol), recognizersOf(sample))
            }
        }
    }

    @Test
    fun everyProtocolHasACoreProxyForm() {
        ProxyProtocol.entries.forEach { protocol ->
            val descriptor = requireNotNull(LinkParser.parse(SAMPLES.getValue(protocol))).proxy
            val link = CoreProxySource.encode("Core ${protocol.wire}", descriptor)
            assertEquals("scheme of $protocol", CoreProxySource.SCHEME, ShareLink.parse(link)?.scheme)
            val parsed = requireNotNull(LinkParser.parse(link)) { "$protocol core form did not parse" }
            assertEquals(protocol, parsed.protocol)
            assertEquals("Core ${protocol.wire}", parsed.name)
            assertEquals(descriptor, parsed.proxy)
            assertEquals(link, parsed.link)
        }
    }

    @Test
    fun everyProtocolSurvivesAClashYamlDocument() {
        ProxyProtocol.entries.forEach { protocol ->
            val descriptor = requireNotNull(LinkParser.parse(SAMPLES.getValue(protocol))).proxy
            val named = JsonObject(descriptor + ("name" to JsonPrimitive("Sample")))
            val yaml = "proxies:\n  - " + Json.encodeToString(JsonObject.serializer(), named)
            val parsed = SubscriptionParser.parseBody(yaml).single()
            assertEquals(protocol, parsed.protocol)
            assertEquals("descriptor of $protocol", descriptor, parsed.proxy)
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

    private fun recognizersOf(sample: String): List<ProxyProtocol> {
        val documents = ProxyProtocol.entries.filter { (it.handler as? DocumentHandler)?.recognizes(sample) == true }
        val link = ShareLink.parse(sample) ?: return documents
        val links = ProxyProtocol.entries.filter { (it.handler as? LinkHandler)?.recognizes(link) == true }
        assertTrue("a link form is also read as a document: $sample", documents.isEmpty() || links.isEmpty())
        return documents + links
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

    internal companion object {
        val VMESS = "vmess://" + Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"Vienna","add":"vm.example.com","port":"443","id":"0b3f1a2c-1111-4222-8333-444455556666","aid":"0","net":"ws","host":"vm.example.com","path":"/ws","tls":"tls"}"""
                .toByteArray()
        )
        val SHADOWSOCKS = "ss://" + Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray()) +
            "@203.0.113.5:8388#Oslo"
        val AMNEZIAWG = """
            [Interface]
            Address = 10.8.1.2/32
            PrivateKey = lfifzx2jzHHCjtXnW+j5Bc7XeY0CIVO7nz5oaI8Z2Bg=
            Jc = 4

            [Peer]
            PublicKey = OXX78VOGhF/LETadWXqWz7lKHDWRrYOTbuXgO6P8I0M=
            AllowedIPs = 0.0.0.0/0
            Endpoint = awg.example.com:51820
        """.trimIndent()

        val AMNEZIAWG_LINK = "amneziawg://" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(AMNEZIAWG.encodeToByteArray()) + "#Riga"

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
            MIIBXjCCAQSgAwIBAgIUa+7Khhu02W70eFypjHNC1mxxpBwwCgYIKoZIzj0EAwIw
            FTETMBEGA1UEAwwKZXJraW4tdGVzdDAeFw0yNjExMTkxMDA3MTVaFw0zNjExMTYx
            MDA3MTVaMBUxEzARBgNVBAMMCmVya2luLXRlc3QwWTATBgcqhkjOPQIBBggqhkjO
            PQMBBwNCAAQf1Tr1UhLr3mmHeXfpSAamAVSlj3/S6T/czNPKPm++SEHYocMNsNWY
            d806tm3Et8M7N5OSPPm1dcGtcsG+D3wGozIwMDAdBgNVHQ4EFgQUhyPqNNLm0fd+
            oR9LpDdnwFtHNJwwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiAi
            a9nQB/6oqxHdLBhrkoPEWETaPvJto0cDpdbStHDp3gIhAJw3ID+aLdZANhCAFEV/
            afR0gUHd6jfgiARlG0kNK9LZ
            -----END CERTIFICATE-----
            </ca>
            <tls-auth>
            -----BEGIN OpenVPN Static key V1-----
            8e4f376c09c866d9e8a6ec3fd0dc2656
            d8e131d2cce6effdcacab831692a816d
            60efc1db93368f895596a1e8fc799979
            6ec789a5a25dc5c28761f59772e1a7e4
            d7ef0964e27ddee4731cc55bbbea6467
            d104fb5c60405bba576f77a8f0f89b5b
            3998858ae3d7bbb6ea1ed05c6965f9ca
            ad66c217871b58762656a73009a855f5
            e3a3c217fe7206b5afd4cbbe8e73dacc
            fb7a111b76cc08b31932c7f79ba13c59
            3f7532e92c7cf89190131229315c7a61
            30feca3c72bd12b5396d170d162bb8ea
            30659cd13c90a62e4bfa90444e931238
            dd62bdde96c5d024a2bc8a0557990203
            d91c711d01325a7e3de94116837c0c04
            337600ceec6e2d9ab25efc3e5435dfc8
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
            ProxyProtocol.AMNEZIAWG to listOf(AMNEZIAWG, AMNEZIAWG_LINK),
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
