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
            privateKey = interfaceSection.first("privatekey") ?: error("missing PrivateKey"),
            addresses = interfaceSection.all("address").flatMap { it.splitList() }.map(::withPrefix)
                .ifEmpty { error("missing Address") },
            mtu = interfaceSection.first("mtu")?.toIntOrNull() ?: fallbackMtu,
            peers = peerSections.map(::peer),
            interfaceValues = interfaceSection.firstValues()
        )
    }

    private fun peer(section: Section): WgPeer {
        val endpoint = section.first("endpoint") ?: error("missing Endpoint")
        return WgPeer(
            host = endpoint.substringBeforeLast(':').trim('[', ']'),
            port = endpoint.substringAfterLast(':').toIntOrNull() ?: DEFAULT_PORT,
            publicKey = section.first("publickey") ?: error("missing PublicKey"),
            presharedKey = section.first("presharedkey"),
            allowedIps = section.all("allowedips").flatMap { it.splitList() }.ifEmpty { listOf("0.0.0.0/0") },
            keepalive = section.first("persistentkeepalive")?.lowerBound(),
            reserved = section.first("reserved")?.let(::reservedBytes)
        )
    }

    fun reservedBytes(value: String): List<Int>? =
        value.split(',').map { it.trim().toIntOrNull() }
            .takeIf { bytes -> bytes.size == RESERVED_BYTES && bytes.all { it != null && it in 0..255 } }
            ?.filterNotNull()

    private fun sections(text: String): List<Pair<String, Section>> {
        val sections = mutableListOf<Pair<String, Section>>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("[") && line.endsWith("]") ->
                    sections += line.trim('[', ']').lowercase() to Section()
                line.contains('=') -> sections.lastOrNull()?.second?.put(
                    line.substringBefore('=').trim().lowercase(),
                    line.substringAfter('=').trim()
                )
            }
        }
        return sections
    }

    private class Section {
        private val values = mutableMapOf<String, MutableList<String>>()

        fun put(key: String, value: String) {
            values.getOrPut(key) { mutableListOf() } += value
        }

        fun first(key: String): String? = values[key]?.firstOrNull()

        fun all(key: String): List<String> = values[key].orEmpty()

        fun firstValues(): Map<String, String> = values.mapValues { it.value.first() }
    }

    private fun String.lowerBound(): Int? = substringBefore('-').trim().toIntOrNull()

    private fun String.splitList(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
