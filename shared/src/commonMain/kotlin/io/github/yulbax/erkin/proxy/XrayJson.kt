package io.github.yulbax.erkin.proxy

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private enum class XrayProtocol(val wire: String) {
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    SHADOWSOCKS("shadowsocks"),
    SOCKS("socks"),
    HTTP("http"),
    WIREGUARD("wireguard");

    companion object {
        fun fromWire(value: String): XrayProtocol? = entries.firstOrNull { it.wire == value }
    }
}

private enum class XrayNetwork(val wire: String, val link: LinkTransport, val settingsKey: String?) {
    TCP("tcp", LinkTransport.TCP, null),
    RAW("raw", LinkTransport.TCP, null),
    WS("ws", LinkTransport.WS, "wsSettings"),
    GRPC("grpc", LinkTransport.GRPC, "grpcSettings"),
    HTTPUPGRADE("httpupgrade", LinkTransport.HTTPUPGRADE, "httpupgradeSettings"),
    XHTTP("xhttp", LinkTransport.XHTTP, "xhttpSettings"),
    SPLITHTTP("splithttp", LinkTransport.XHTTP, "splithttpSettings"),
    H2("h2", LinkTransport.HTTP, "httpSettings"),
    HTTP("http", LinkTransport.HTTP, "httpSettings");

    companion object {
        fun fromWire(value: String): XrayNetwork? = entries.firstOrNull { it.wire == value }
    }
}

private data class XrayConfig(val remarks: String?, val outbounds: List<JsonObject>)

internal object XrayJsonReader : FormatReader {
    override fun detects(text: String): Boolean = configs(parseJson(text)) != null

    override fun read(text: String): List<ParsedProfile> =
        configs(parseJson(text)).orEmpty().flatMap { config ->
            config.outbounds.mapNotNull { outbound ->
                link(outbound, config.remarks ?: outbound.text("tag"))?.let(LinkParser::parse)
            }
        }

    private fun configs(root: Any?): List<XrayConfig>? = when (root) {
        is JsonObject -> outbounds(root["outbounds"])?.let { listOf(XrayConfig(null, it)) }
        is JsonArray -> {
            val items = root.filterIsInstance<JsonObject>().takeIf { it.isNotEmpty() && it.size == root.size }
            val nested = items?.map { item -> outbounds(item["outbounds"])?.let { XrayConfig(item.text("remarks"), it) } }
            when {
                items == null -> null
                nested.orEmpty().all { it != null } -> nested.orEmpty().filterNotNull()
                items.all { it.hasString("protocol") } -> listOf(XrayConfig(null, items))
                else -> null
            }
        }
        else -> null
    }

    private fun outbounds(element: Any?): List<JsonObject>? {
        val array = element as? JsonArray ?: return null
        return array.filterIsInstance<JsonObject>()
            .takeIf { it.isNotEmpty() && it.size == array.size && it.all { item -> item.hasString("protocol") } }
    }

    private fun link(outbound: JsonObject, name: String?): String? {
        val protocol = outbound.text("protocol")?.let(XrayProtocol::fromWire) ?: return null
        val settings = outbound.obj("settings") ?: return null
        if (protocol == XrayProtocol.WIREGUARD) return wireguard(settings, name)
        val server = (settings.objects("vnext").firstOrNull() ?: settings.objects("servers").firstOrNull()) ?: return null
        val user = server.objects("users").firstOrNull()
        val host = server.text("address") ?: return null
        val port = server.int("port") ?: return null
        val stream = stream(outbound) ?: return null
        return when (protocol) {
            XrayProtocol.VLESS -> shareLinkText(
                "vless", host, port, user = user?.text("id"), query = stream.query() + ("flow" to user?.text("flow")), name = name
            )
            XrayProtocol.VMESS -> vmessLink(
                name, host, port, user?.text("id") ?: return null, user.int("alterId"), user.text("security"), stream
            )
            XrayProtocol.TROJAN -> shareLinkText("trojan", host, port, user = server.text("password"), query = stream.query(), name = name)
            XrayProtocol.SHADOWSOCKS -> shadowsocksLink(
                host, port, server.text("method") ?: return null, server.text("password") ?: return null, null, null, name
            )
            XrayProtocol.SOCKS -> shareLinkText("socks5", host, port, user?.text("user"), user?.text("pass"), name = name)
            XrayProtocol.HTTP -> shareLinkText(
                if (stream.security == LinkSecurity.TLS) "https" else "http", host, port, user?.text("user"), user?.text("pass"), name = name
            )
            XrayProtocol.WIREGUARD -> null
        }
    }

    private fun wireguard(settings: JsonObject, name: String?): String? {
        val peer = settings.objects("peers").firstOrNull() ?: return null
        val endpoint = peer.text("endpoint") ?: return null
        return wireguardLink(
            privateKey = settings.text("secretKey") ?: return null,
            host = endpoint.substringBeforeLast(':').removeSurrounding("[", "]"),
            port = endpoint.substringAfterLast(':').toIntOrNull() ?: return null,
            publicKey = peer.text("publicKey") ?: return null,
            addresses = settings.texts("address"),
            presharedKey = peer.text("preSharedKey"),
            reserved = settings.texts("reserved"),
            mtu = settings.int("mtu"),
            name = name
        )
    }

    private fun stream(outbound: JsonObject): StreamParams? {
        val settings = outbound.obj("streamSettings") ?: return StreamParams()
        val network = settings.text("network")?.let { XrayNetwork.fromWire(it) ?: return null } ?: XrayNetwork.TCP
        val security = settings.text("security")?.let { LinkSecurity.fromWire(it) ?: return null } ?: LinkSecurity.NONE
        val tls = when (security) {
            LinkSecurity.NONE -> null
            LinkSecurity.TLS -> settings.obj("tlsSettings")
            LinkSecurity.REALITY -> settings.obj("realitySettings")
        }
        val transport = network.settingsKey?.let { settings.obj(it) }
        return StreamParams(
            transport = network.link,
            security = security,
            tls = TlsParams(
                sni = tls?.text("serverName"),
                insecure = tls?.bool("allowInsecure") == true,
                alpn = tls?.texts("alpn").orEmpty(),
                fingerprint = tls?.text("fingerprint"),
                publicKey = tls?.text("publicKey"),
                shortId = tls?.text("shortId")
            ),
            host = transport?.text("host") ?: transport?.obj("headers")?.text("Host"),
            path = transport?.text("path"),
            serviceName = transport?.text("serviceName"),
            mode = transport?.text("mode"),
            extra = transport?.obj("extra")?.toString()
        )
    }
}
