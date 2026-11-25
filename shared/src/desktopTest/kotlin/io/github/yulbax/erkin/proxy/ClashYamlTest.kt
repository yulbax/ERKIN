package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashYamlTest {

    @Test
    fun readsOnlyTheSupportedProxiesOfARealisticSubscription() {
        val profiles = SubscriptionParser.parseBody(SUBSCRIPTION)

        assertEquals(SubscriptionFormat.CLASH_YAML, SubscriptionFormat.detect(SUBSCRIPTION))
        assertEquals(
            listOf("Amsterdam WS", "Helsinki", "Frankfurt Trojan", "Chained", "Zurich", "🇩🇪 Berlin Fast"),
            profiles.map { it.name }
        )
        assertEquals(
            listOf(ProxyProtocol.VLESS, ProxyProtocol.HYSTERIA2, ProxyProtocol.TROJAN, ProxyProtocol.TROJAN, ProxyProtocol.SNELL, ProxyProtocol.TROJAN),
            profiles.map { it.protocol }
        )
    }

    @Test
    fun aFlagRightOnTheReadersBufferEdgeStillLoads() {
        val head = "proxies:\n  - name: \""
        val flag = "\uD83C\uDDF3\uD83C\uDDF1"
        val padding = "x".repeat(BUFFER_EDGE - head.length)
        val text = head + padding + flag + " Amsterdam\"\n    type: ss\n    server: 192.0.2.1\n    port: 8388\n" +
            "    cipher: aes-128-gcm\n    password: secret\n"

        assertEquals(BUFFER_EDGE, text.indexOf(flag))
        assertEquals(SubscriptionFormat.CLASH_YAML, SubscriptionFormat.detect(text))
        assertEquals(listOf(padding + flag + " Amsterdam"), SubscriptionParser.parseBody(text).map { it.name })
    }

    @Test
    fun stripsKeysThatBelongToTheLocalConfig() {
        val chained = SubscriptionParser.parseBody(SUBSCRIPTION).first { it.name == "Chained" }.proxy

        assertNull(chained["dialer-proxy"])
        assertNull(chained["interface-name"])
        assertNull(chained["name"])
    }

    @Test
    fun keepsNumbersAndBooleansTyped() {
        val vless = SubscriptionParser.parseBody(SUBSCRIPTION).first { it.protocol == ProxyProtocol.VLESS }.proxy

        assertEquals(JsonPrimitive(443), vless.getValue("port"))
        assertFalse(vless.getValue("port").jsonPrimitive.isString)
        assertTrue(vless.getValue("udp").jsonPrimitive.boolean)
        assertTrue(vless.getValue("tls").jsonPrimitive.boolean)
        assertEquals("chrome", vless.getValue("client-fingerprint").jsonPrimitive.content)
    }

    @Test
    fun expandsAnchorsAndMergeKeysWithoutOverridingOwnKeys() {
        val trojan = SubscriptionParser.parseBody(SUBSCRIPTION).first { it.name == "Frankfurt Trojan" }.proxy

        assertEquals("tr.example.org", trojan.getValue("server").jsonPrimitive.content)
        assertEquals("secret", trojan.getValue("password").jsonPrimitive.content)
        assertEquals("shared.example.com", trojan.getValue("sni").jsonPrimitive.content)
        assertEquals(8443, trojan.getValue("port").jsonPrimitive.content.toInt())
    }

    @Test
    fun everyLinkReparsesIntoTheSameDescriptor() {
        SubscriptionParser.parseBody(SUBSCRIPTION).forEach { profile ->
            val again = requireNotNull(LinkParser.parse(profile.link)) { profile.name }
            assertEquals(profile.proxy, again.proxy)
            assertEquals(profile.name, again.name)
        }
    }

    @Test
    fun readsAPastedListOfProxies() {
        val text = """
            - {name: One, type: trojan, server: a.example.com, port: 443, password: x}
            - {name: Two, type: trojan, server: b.example.com, port: 443, password: y}
        """.trimIndent()

        assertEquals(SubscriptionFormat.CLASH_YAML, SubscriptionFormat.detect(text))
        assertEquals(listOf("One", "Two"), SubscriptionParser.parseBody(text).map { it.name })
    }

    @Test
    fun fallsBackToTheServerWhenAProxyHasNoName() {
        val text = "proxies:\n  - {type: trojan, server: a.example.com, port: 443, password: x}"

        assertEquals(listOf("a.example.com"), SubscriptionParser.parseBody(text).map { it.name })
    }

    @Test
    fun aBarePasswordOnStaysAString() {
        val text = "proxies:\n  - {name: Bool, type: trojan, server: a.example.com, port: 443, password: on}"

        val proxy = SubscriptionParser.parseBody(text).single().proxy

        assertEquals(JsonPrimitive("on"), proxy.getValue("password"))
    }

    @Test
    fun otherFormatsAreNotDetectedAsYaml() {
        val others = listOf(
            "vless://0b3f1a2c-1111-4222-8333-444455556666@vl.example.com:443?security=tls#A\ntrojan://secret@tr.example.org:443#B",
            "dHJvamFuOi8vc2VjcmV0QHRyLmV4YW1wbGUub3JnOjQ0MyNC",
            ProtocolContractTest.OPENVPN,
            ProtocolContractTest.AMNEZIAWG,
            ProtocolContractTest.PLAIN_WIREGUARD_CONF,
            "key: value\nother: 1",
            "- one\n- two"
        )

        others.forEach { text ->
            assertFalse(text, ClashYamlReader.detects(text))
            assertTrue(text, SubscriptionFormat.detect(text) != SubscriptionFormat.CLASH_YAML)
        }
    }

    private companion object {
        const val BUFFER_EDGE = 1024
        val SUBSCRIPTION = """
            mixed-port: 7890
            allow-lan: false
            mode: rule
            dns:
              enable: true
              nameserver: [1.1.1.1]
            proxies:
              - name: Amsterdam WS
                type: vless
                server: vl.example.com
                port: 443
                uuid: 0b3f1a2c-1111-4222-8333-444455556666
                udp: true
                tls: true
                network: ws
                servername: vl.example.com
                client-fingerprint: chrome
                ws-opts:
                  path: /ws
                  headers:
                    Host: vl.example.com
              - name: Helsinki
                type: hysteria2
                server: hy.example.net
                port: 8443
                password: secret
                sni: hy.example.net
              - &base
                name: Frankfurt Trojan
                type: trojan
                server: tr.example.org
                port: 8443
                password: secret
                sni: shared.example.com
                skip-cert-verify: false
              - name: Chained
                type: trojan
                server: ch.example.org
                port: 443
                password: secret
                dialer-proxy: Helsinki
                interface-name: eth0
                routing-mark: 6666
              - name: Zurich
                type: snell
                server: sn.example.org
                port: 443
                psk: secret
              - name: Skipped
                type: tailscale
                server: ts.example.org
                port: 443
              - <<: *base
                name: "🇩🇪 Berlin Fast"
                server: be.example.org
            proxy-groups:
              - name: Auto
                type: url-test
                proxies: [Amsterdam WS, Helsinki]
            rules:
              - MATCH,Auto
        """.trimIndent()
    }
}
