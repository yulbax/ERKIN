package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JsonSubscriptionsTest {

    @Test
    fun readsASingBoxConfigAndSkipsGroupsAndBuiltins() {
        val profiles = SubscriptionParser.parseBody(SING_BOX)

        assertEquals(SubscriptionFormat.SING_BOX_JSON, SubscriptionFormat.detect(SING_BOX))
        assertEquals(
            listOf("Reality", "Vmess WS", "Trojan gRPC", "SS Obfs", "Hy2", "Tuic", "WG"),
            profiles.map { it.name }
        )
        assertEquals(
            listOf(
                ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS,
                ProxyProtocol.HYSTERIA2, ProxyProtocol.TUIC, ProxyProtocol.WIREGUARD
            ),
            profiles.map { it.protocol }
        )
    }

    @Test
    fun readsSingBoxOutboundArrays() {
        val body = """[{"type":"socks","tag":"S","server":"s.example.com","server_port":1080,"username":"u","password":"p"},
            {"type":"http","tag":"H","server":"h.example.com","server_port":443,"tls":{"enabled":true}}]"""

        assertEquals(SubscriptionFormat.SING_BOX_JSON, SubscriptionFormat.detect(body))
        assertEquals(listOf(ProxyProtocol.SOCKS5, ProxyProtocol.HTTPS), SubscriptionParser.parseBody(body).map { it.protocol })
    }

    @Test
    fun singBoxUdpOverTcpReachesTheShadowsocksDescriptor() {
        val body = """[{"type":"shadowsocks","tag":"A","server":"s.example.com","server_port":8388,"method":"aes-128-gcm","password":"p","udp_over_tcp":true},
            {"type":"shadowsocks","tag":"B","server":"s.example.com","server_port":8389,"method":"aes-128-gcm","password":"p","udp_over_tcp":{"enabled":false,"version":2}}]"""
        val (enabled, disabled) = SubscriptionParser.parseBody(body).map { it.proxy }

        assertEquals("true", enabled["udp-over-tcp"]?.jsonPrimitive?.content)
        assertEquals(null, enabled["udp-over-tcp-version"])
        assertEquals(null, disabled["udp-over-tcp"])
    }

    @Test
    fun readsAnXrayArrayOfFullConfigsNamedByRemarks() {
        val profiles = SubscriptionParser.parseBody(XRAY_ARRAY)

        assertEquals(SubscriptionFormat.XRAY_JSON, SubscriptionFormat.detect(XRAY_ARRAY))
        assertEquals(listOf("Berlin xhttp", "Oslo trojan"), profiles.map { it.name })
        assertEquals(listOf(ProxyProtocol.VLESS, ProxyProtocol.TROJAN), profiles.map { it.protocol })
    }

    @Test
    fun readsASingleXrayConfigNamedByTag() {
        val profiles = SubscriptionParser.parseBody(XRAY_SINGLE)

        assertEquals(SubscriptionFormat.XRAY_JSON, SubscriptionFormat.detect(XRAY_SINGLE))
        assertEquals(listOf("proxy"), profiles.map { it.name })
        assertEquals(listOf(ProxyProtocol.VMESS), profiles.map { it.protocol })
    }

    @Test
    fun readsASip008Document() {
        val profiles = SubscriptionParser.parseBody(SIP008)

        assertEquals(SubscriptionFormat.SIP008_JSON, SubscriptionFormat.detect(SIP008))
        assertEquals(listOf("Plain", "Plugin"), profiles.map { it.name })
        assertEquals(listOf(ProxyProtocol.SHADOWSOCKS, ProxyProtocol.SHADOWSOCKS), profiles.map { it.protocol })
    }

    @Test
    fun jsonProfilesMatchTheEquivalentShareLinks() {
        val singBox = SubscriptionParser.parseBody(SING_BOX).associateBy { it.name }
        val xray = SubscriptionParser.parseBody(XRAY_ARRAY).associateBy { it.name }
        val sip = SubscriptionParser.parseBody(SIP008).associateBy { it.name }

        val expected = listOf(
            singBox["Reality"] to "vless://11111111-1111-1111-1111-111111111111@r.example.com:443?security=reality&sni=www.example.com&fp=chrome&pbk=PUB&sid=ab12&flow=xtls-rprx-vision#Reality",
            singBox["Vmess WS"] to "vmess://" + vmessBody("Vmess WS", "v.example.com", "22222222-2222-2222-2222-222222222222", "ws", "cdn.example.com", "/ws", "tls", "cdn.example.com"),
            singBox["Trojan gRPC"] to "trojan://secret@t.example.com:443?security=tls&type=grpc&serviceName=svc&sni=t.example.com&alpn=h2#Trojan%20gRPC",
            singBox["SS Obfs"] to "ss://aes-256-gcm:pw@s.example.com:8388?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dcdn.example.com&uot=1&udp-over-tcp-version=2#SS%20Obfs",
            singBox["Hy2"] to "hysteria2://hpass@h.example.com:443?sni=h.example.com&insecure=1&obfs=salamander&obfs-password=ob#Hy2",
            singBox["Tuic"] to "tuic://33333333-3333-3333-3333-333333333333:tp@u.example.com:443?sni=u.example.com&alpn=h3&congestion_control=bbr&udp_relay_mode=native#Tuic",
            singBox["WG"] to "wireguard://$WG_PRIVATE@w.example.com:51820?publickey=$WG_PUBLIC&address=10.9.0.2%2F32&mtu=1280&reserved=1%2C2%2C3#WG",
            xray["Berlin xhttp"] to "vless://44444444-4444-4444-4444-444444444444@x.example.com:443?security=reality&type=xhttp&sni=www.example.com&fp=chrome&pbk=PUB2&sid=cd34&path=%2Fx&host=x.example.com&mode=auto#Berlin%20xhttp",
            xray["Oslo trojan"] to "trojan://pw2@o.example.com:443?security=tls&type=ws&path=%2Fws&host=o.example.com&sni=o.example.com#Oslo%20trojan",
            sip["Plugin"] to "ss://aes-128-gcm:p2@p.example.com:8443?plugin=v2ray-plugin%3Btls%3Bhost%3Dp.example.com#Plugin"
        )

        expected.forEach { (profile, link) ->
            val reference = LinkParser.parse(link)
            assertNotNull(link, reference)
            assertNotNull(profile)
            assertEquals(link, reference!!.protocol, profile!!.protocol)
            assertEquals(link, reference.proxy, profile.proxy)
        }
    }

    @Test
    fun everyJsonReaderRejectsTheOtherFormats() {
        val others = listOf(CLASH, LINK_LIST, BASE64_BODY, OVPN, WG_INI)
        val jsons = mapOf(
            SubscriptionFormat.SING_BOX_JSON to SING_BOX,
            SubscriptionFormat.XRAY_JSON to XRAY_ARRAY,
            SubscriptionFormat.SIP008_JSON to SIP008
        )

        jsons.forEach { (format, own) ->
            assertEquals(true, format.reader.detects(own))
            others.forEach { assertEquals(it, false, format.reader.detects(it)) }
            jsons.filterKeys { it != format }.values.forEach { assertEquals(false, format.reader.detects(it)) }
        }
        listOf(XRAY_SINGLE).forEach { assertEquals(false, SubscriptionFormat.SING_BOX_JSON.reader.detects(it)) }
        assertEquals(false, SubscriptionFormat.XRAY_JSON.reader.detects(SING_BOX_ARRAY))
        assertEquals(false, SubscriptionFormat.SING_BOX_JSON.reader.detects(XRAY_ARRAY))
    }

    private fun vmessBody(name: String, host: String, id: String, net: String, hostHeader: String, path: String, tls: String, sni: String): String {
        val json = """{"v":"2","ps":"$name","add":"$host","port":"443","id":"$id","aid":"0","scy":"auto","net":"$net","host":"$hostHeader","path":"$path","tls":"$tls","sni":"$sni","alpn":""}"""
        return java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private companion object {
        const val WG_PRIVATE = "qJ3Dt9VHrsHZmRxRZ1rH0hqTJ1zBBpK1ZnpT6YBr4Ec%3D"
        const val WG_PUBLIC = "7PjU0wTXBPX0CUHKCCgPLZ2fDLxTzBBpK1ZnpT6YBrQ%3D"
        const val WG_PRIVATE_RAW = "qJ3Dt9VHrsHZmRxRZ1rH0hqTJ1zBBpK1ZnpT6YBr4Ec="
        const val WG_PUBLIC_RAW = "7PjU0wTXBPX0CUHKCCgPLZ2fDLxTzBBpK1ZnpT6YBrQ="

        val SING_BOX_ARRAY = """[{"type":"socks","server":"s.example.com","server_port":1080}]"""

        val SING_BOX = """
            {
              "log": {"level": "info"},
              "outbounds": [
                {"type": "selector", "tag": "select", "outbounds": ["Reality", "Vmess WS"]},
                {"type": "urltest", "tag": "auto", "outbounds": ["Reality"]},
                {"type": "direct", "tag": "direct"},
                {"type": "vless", "tag": "Reality", "server": "r.example.com", "server_port": 443,
                 "uuid": "11111111-1111-1111-1111-111111111111", "flow": "xtls-rprx-vision",
                 "tls": {"enabled": true, "server_name": "www.example.com",
                         "utls": {"enabled": true, "fingerprint": "chrome"},
                         "reality": {"enabled": true, "public_key": "PUB", "short_id": "ab12"}}},
                {"type": "vmess", "tag": "Vmess WS", "server": "v.example.com", "server_port": 443,
                 "uuid": "22222222-2222-2222-2222-222222222222", "security": "auto", "alter_id": 0,
                 "tls": {"enabled": true, "server_name": "cdn.example.com"},
                 "transport": {"type": "ws", "path": "/ws", "headers": {"Host": "cdn.example.com"}}},
                {"type": "trojan", "tag": "Trojan gRPC", "server": "t.example.com", "server_port": 443,
                 "password": "secret",
                 "tls": {"enabled": true, "server_name": "t.example.com", "alpn": ["h2"]},
                 "transport": {"type": "grpc", "service_name": "svc"}},
                {"type": "shadowsocks", "tag": "SS Obfs", "server": "s.example.com", "server_port": 8388,
                 "method": "aes-256-gcm", "password": "pw",
                 "plugin": "obfs-local", "plugin_opts": "obfs=http;obfs-host=cdn.example.com",
                 "udp_over_tcp": {"enabled": true, "version": 2}},
                {"type": "hysteria2", "tag": "Hy2", "server": "h.example.com", "server_port": 443,
                 "password": "hpass", "obfs": {"type": "salamander", "password": "ob"},
                 "tls": {"enabled": true, "server_name": "h.example.com", "insecure": true}},
                {"type": "tuic", "tag": "Tuic", "server": "u.example.com", "server_port": 443,
                 "uuid": "33333333-3333-3333-3333-333333333333", "password": "tp",
                 "congestion_control": "bbr", "udp_relay_mode": "native",
                 "tls": {"enabled": true, "server_name": "u.example.com", "alpn": ["h3"]}},
                {"type": "block", "tag": "block"},
                {"type": "dns", "tag": "dns-out"}
              ],
              "endpoints": [
                {"type": "wireguard", "tag": "WG", "address": ["10.9.0.2/32"], "private_key": "$WG_PRIVATE_RAW",
                 "mtu": 1280,
                 "peers": [{"address": "w.example.com", "port": 51820, "public_key": "$WG_PUBLIC_RAW",
                            "allowed_ips": ["0.0.0.0/0"], "reserved": [1, 2, 3]}]}
              ]
            }
        """.trimIndent()

        val XRAY_ARRAY = """
            [
              {"remarks": "Berlin xhttp", "outbounds": [
                {"tag": "proxy", "protocol": "vless", "settings": {"vnext": [{"address": "x.example.com", "port": 443,
                  "users": [{"id": "44444444-4444-4444-4444-444444444444", "encryption": "none"}]}]},
                 "streamSettings": {"network": "xhttp", "security": "reality",
                   "realitySettings": {"serverName": "www.example.com", "publicKey": "PUB2", "shortId": "cd34", "fingerprint": "chrome"},
                   "xhttpSettings": {"path": "/x", "host": "x.example.com", "mode": "auto"}}},
                {"tag": "direct", "protocol": "freedom"},
                {"tag": "block", "protocol": "blackhole"},
                {"tag": "dns-out", "protocol": "dns"}
              ]},
              {"remarks": "Oslo trojan", "outbounds": [
                {"tag": "proxy", "protocol": "trojan", "settings": {"servers": [{"address": "o.example.com", "port": 443, "password": "pw2"}]},
                 "streamSettings": {"network": "ws", "security": "tls",
                   "tlsSettings": {"serverName": "o.example.com"},
                   "wsSettings": {"path": "/ws", "headers": {"Host": "o.example.com"}}}},
                {"tag": "direct", "protocol": "freedom"}
              ]}
            ]
        """.trimIndent()

        val XRAY_SINGLE = """
            {"log": {}, "outbounds": [
              {"tag": "proxy", "protocol": "vmess", "settings": {"vnext": [{"address": "m.example.com", "port": 443,
                "users": [{"id": "55555555-5555-5555-5555-555555555555", "alterId": 0, "security": "auto"}]}]},
               "streamSettings": {"network": "ws", "security": "tls", "tlsSettings": {"serverName": "m.example.com"},
                 "wsSettings": {"path": "/v", "headers": {"Host": "m.example.com"}}}},
              {"tag": "direct", "protocol": "freedom"}
            ]}
        """.trimIndent()

        val SIP008 = """
            {"version": 1, "servers": [
              {"id": "a", "remarks": "Plain", "server": "q.example.com", "server_port": 8388, "password": "p1", "method": "chacha20-ietf-poly1305"},
              {"id": "b", "remarks": "Plugin", "server": "p.example.com", "server_port": 8443, "password": "p2", "method": "aes-128-gcm",
               "plugin": "v2ray-plugin", "plugin_opts": "tls;host=p.example.com"}
            ]}
        """.trimIndent()

        val CLASH = """
            proxies:
              - {name: A, type: trojan, server: a.example.com, port: 443, password: x}
        """.trimIndent()

        const val LINK_LIST = "trojan://pw@a.example.com:443#A\nvless://11111111-1111-1111-1111-111111111111@b.example.com:443#B"

        val BASE64_BODY = java.util.Base64.getEncoder().encodeToString(LINK_LIST.toByteArray())

        val OVPN = "client\ndev tun\nproto udp\nremote ovpn.example.com 1194\n"

        val WG_INI = "[Interface]\nPrivateKey = $WG_PRIVATE_RAW\nAddress = 10.0.0.2/32\n\n[Peer]\nPublicKey = $WG_PUBLIC_RAW\nEndpoint = w.example.com:51820\n"
    }
}
