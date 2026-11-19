package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private enum class SingBoxType(val wire: String) {
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    SHADOWSOCKS("shadowsocks"),
    HYSTERIA2("hysteria2"),
    HYSTERIA("hysteria"),
    TUIC("tuic"),
    ANYTLS("anytls"),
    SOCKS("socks"),
    HTTP("http"),
    SSH("ssh"),
    WIREGUARD("wireguard");

    companion object {
        fun fromWire(value: String): SingBoxType? = entries.firstOrNull { it.wire == value }
    }
}

private enum class SingBoxTransport(val wire: String, val link: LinkTransport) {
    WS("ws", LinkTransport.WS),
    GRPC("grpc", LinkTransport.GRPC),
    HTTP("http", LinkTransport.HTTP),
    HTTPUPGRADE("httpupgrade", LinkTransport.HTTPUPGRADE);

    companion object {
        fun fromWire(value: String): SingBoxTransport? = entries.firstOrNull { it.wire == value }
    }
}

internal object SingBoxJsonReader : FormatReader {
    private val SECTIONS = listOf("outbounds", "endpoints")

    override fun detects(text: String): Boolean = outbounds(parseJson(text)) != null

    override fun read(text: String): List<ParsedProfile> =
        outbounds(parseJson(text)).orEmpty().mapNotNull { item -> link(item)?.let(LinkParser::parse) }

    private fun outbounds(root: Any?): List<JsonObject>? = when (root) {
        is JsonObject -> {
            val sections = SECTIONS.mapNotNull { root[it] as? JsonArray }
            val items = sections.flatten()
            items.filterIsInstance<JsonObject>()
                .takeIf { sections.isNotEmpty() && it.isNotEmpty() && it.size == items.size && it.all { item -> item.hasString("type") } }
        }
        is JsonArray -> root.filterIsInstance<JsonObject>()
            .takeIf { it.isNotEmpty() && it.size == root.size && it.all { item -> "type" in item && "server_port" in item } }
        else -> null
    }

    private fun link(item: JsonObject): String? {
        val type = item.text("type")?.let(SingBoxType::fromWire) ?: return null
        val name = item.text("tag")
        if (type == SingBoxType.WIREGUARD) return wireguard(item, name)
        val host = item.text("server") ?: return null
        val port = item.int("server_port") ?: return null
        val tls = item.obj("tls")
        val quicQuery = readTls(tls).query(insecureKey = "insecure")
        return when (type) {
            SingBoxType.VLESS -> shareLinkText(
                "vless", host, port, user = item.text("uuid"),
                query = (stream(item) ?: return null).query() + ("flow" to item.text("flow")), name = name
            )
            SingBoxType.VMESS -> vmessLink(
                name, host, port, item.text("uuid") ?: return null, item.int("alter_id"), item.text("security"), stream(item) ?: return null
            )
            SingBoxType.TROJAN -> shareLinkText(
                "trojan", host, port, user = item.text("password"), query = (stream(item) ?: return null).query(), name = name
            )
            SingBoxType.SHADOWSOCKS -> shadowsocksLink(
                host, port, item.text("method") ?: return null, item.text("password") ?: return null,
                item.text("plugin"), item.text("plugin_opts"), name, udpOverTcp(item)
            )
            SingBoxType.HYSTERIA2 -> shareLinkText(
                "hysteria2", host, port, user = item.text("password"),
                query = quicQuery + mapOf("obfs" to item.obj("obfs")?.text("type"), "obfs-password" to item.obj("obfs")?.text("password")),
                name = name
            )
            SingBoxType.HYSTERIA -> shareLinkText(
                "hysteria", host, port,
                query = readTls(tls).query(sniKey = "peer", insecureKey = "insecure") + mapOf(
                    "auth" to item.text("auth_str"),
                    "upmbps" to item.int("up_mbps")?.toString(),
                    "downmbps" to item.int("down_mbps")?.toString(),
                    "obfs" to item.text("obfs")
                ),
                name = name
            )
            SingBoxType.TUIC -> shareLinkText(
                "tuic", host, port, user = item.text("uuid"), password = item.text("password"),
                query = quicQuery + mapOf(
                    "congestion_control" to item.text("congestion_control"),
                    "udp_relay_mode" to item.text("udp_relay_mode")
                ),
                name = name
            )
            SingBoxType.ANYTLS -> shareLinkText("anytls", host, port, user = item.text("password"), query = quicQuery, name = name)
            SingBoxType.SOCKS -> shareLinkText("socks5", host, port, item.text("username"), item.text("password"), name = name)
            SingBoxType.HTTP -> shareLinkText(
                if (tls.isEnabled()) "https" else "http", host, port, item.text("username"), item.text("password"), name = name
            )
            SingBoxType.SSH -> shareLinkText(
                "ssh", host, port, item.text("user"), item.text("password"),
                query = mapOf(
                    "pk" to item.text("private_key"),
                    "pkp" to item.text("private_key_passphrase"),
                    "hk" to item.texts("host_key").joinToString(",")
                ),
                name = name
            )
            SingBoxType.WIREGUARD -> null
        }
    }

    private fun wireguard(item: JsonObject, name: String?): String? {
        val peer = item.objects("peers").firstOrNull() ?: item
        return wireguardLink(
            privateKey = item.text("private_key") ?: return null,
            host = peer.text("address") ?: peer.text("server") ?: return null,
            port = peer.int("port") ?: peer.int("server_port") ?: return null,
            publicKey = peer.text("public_key") ?: item.text("peer_public_key") ?: return null,
            addresses = item.texts("local_address").ifEmpty { item.texts("address") },
            presharedKey = peer.text("pre_shared_key") ?: item.text("pre_shared_key"),
            reserved = peer.texts("reserved").ifEmpty { item.texts("reserved") },
            mtu = item.int("mtu"),
            name = name
        )
    }

    private fun JsonObject?.isEnabled(): Boolean = this?.bool("enabled") == true

    private fun udpOverTcp(item: JsonObject): UdpOverTcp? = when (val value = item["udp_over_tcp"]) {
        is JsonObject -> value.takeIf { it.isEnabled() }?.let { UdpOverTcp(it.int("version")) }
        is JsonPrimitive -> UdpOverTcp(null).takeIf { item.bool("udp_over_tcp") }
        else -> null
    }

    private fun readTls(tls: JsonObject?): TlsParams = TlsParams(
        sni = tls?.text("server_name"),
        insecure = tls?.bool("insecure") == true,
        alpn = tls?.texts("alpn").orEmpty(),
        fingerprint = tls?.obj("utls")?.text("fingerprint"),
        publicKey = tls?.obj("reality")?.text("public_key"),
        shortId = tls?.obj("reality")?.text("short_id")
    )

    private fun stream(item: JsonObject): StreamParams? {
        val tls = item.obj("tls").takeIf { it.isEnabled() }
        val transport = item.obj("transport")
        val kind = transport?.text("type")?.let { SingBoxTransport.fromWire(it)?.link ?: return null } ?: LinkTransport.TCP
        return StreamParams(
            transport = kind,
            security = when {
                tls == null -> LinkSecurity.NONE
                tls.obj("reality").isEnabled() -> LinkSecurity.REALITY
                else -> LinkSecurity.TLS
            },
            tls = readTls(tls),
            host = transport?.text("host") ?: transport?.obj("headers")?.text("Host"),
            path = transport?.text("path"),
            serviceName = transport?.text("service_name")
        )
    }
}
