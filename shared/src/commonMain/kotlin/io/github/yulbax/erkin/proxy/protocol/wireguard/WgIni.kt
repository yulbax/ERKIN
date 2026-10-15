package io.github.yulbax.erkin.proxy.protocol.wireguard

internal data class WgPeer(
    val host: String,
    val port: Int,
    val publicKey: String,
    val presharedKey: String?,
    val allowedIps: List<String>,
    val keepalive: Int?,
    val reserved: List<Int>?
)

internal data class WgConfig(
    val privateKey: String,
    val addresses: List<String>,
    val mtu: Int?,
    val peers: List<WgPeer>,
    val interfaceValues: Map<String, String>
) {
    val host: String get() = peers.first().host
}

internal object WgIni {
    private const val INTERFACE_SECTION = "interface"
    private const val PEER_SECTION = "peer"
    private const val DEFAULT_PORT = 51820
    private const val RESERVED_BYTES = 3

    fun looksLikeConfig(text: String): Boolean =
        text.lineSequence().any { it.trim().equals("[$INTERFACE_SECTION]", ignoreCase = true) }

    fun parseOrNull(text: String): WgConfig? =
        if (looksLikeConfig(text)) runCatching { parse(text) }.getOrNull() else null

    fun parse(text: String, fallbackMtu: Int? = null): WgConfig {
        val sections = sections(text)
        val interfaceSection = sections.firstOrNull { it.first == INTERFACE_SECTION }?.second
            ?: error("missing [Interface] section")
        val peerSections = sections.filter { it.first == PEER_SECTION }.map { it.second }
        require(peerSections.isNotEmpty()) { "missing [Peer] section" }

        return WgConfig(
            privateKey = interfaceSection["privatekey"] ?: error("missing PrivateKey"),
            addresses = interfaceSection["address"]?.splitList()?.map(::withPrefix) ?: error("missing Address"),
            mtu = interfaceSection["mtu"]?.toIntOrNull() ?: fallbackMtu,
            peers = peerSections.map(::peer),
            interfaceValues = interfaceSection
        )
    }

    private fun peer(values: Map<String, String>): WgPeer {
        val endpoint = values["endpoint"] ?: error("missing Endpoint")
        return WgPeer(
            host = endpoint.substringBeforeLast(':').trim('[', ']'),
            port = endpoint.substringAfterLast(':').toIntOrNull() ?: DEFAULT_PORT,
            publicKey = values["publickey"] ?: error("missing PublicKey"),
            presharedKey = values["presharedkey"],
            allowedIps = values["allowedips"]?.splitList() ?: listOf("0.0.0.0/0"),
            keepalive = values["persistentkeepalive"]?.lowerBound(),
            reserved = values["reserved"]?.let(::reservedBytes)
        )
    }

    fun reservedBytes(value: String): List<Int>? =
        value.split(',').map { it.trim().toIntOrNull() }
            .takeIf { bytes -> bytes.size == RESERVED_BYTES && bytes.all { it != null && it in 0..255 } }
            ?.filterNotNull()

    private fun sections(text: String): List<Pair<String, Map<String, String>>> {
        val sections = mutableListOf<Pair<String, MutableMap<String, String>>>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[") && line.endsWith("]") ->
                    sections += line.trim('[', ']').lowercase() to mutableMapOf()
                line.contains('=') -> sections.lastOrNull()?.second?.put(
                    line.substringBefore('=').trim().lowercase(),
                    line.substringAfter('=').trim()
                )
            }
        }
        return sections
    }

    private fun String.lowerBound(): Int? = substringBefore('-').trim().toIntOrNull()

    private fun String.splitList(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
