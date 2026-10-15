package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.data.isExecutablePath
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

sealed interface SystemDns {
    data object Local : SystemDns
    data class Server(val address: String) : SystemDns
    data object Unknown : SystemDns
}

sealed interface MihomoPlatform {
    data object Android : MihomoPlatform

    data class Desktop(
        val directProcesses: List<String>,
        val systemDns: SystemDns = SystemDns.Local,
        val byeDpiProbePort: Int? = null
    ) : MihomoPlatform
}

object MihomoConfigBuilder {

    const val BYEDPI_PROXY = "byedpi"
    const val DESKTOP_INTERFACE_NAME = "ERKIN"
    private const val TUN_STACK = "gvisor"
    private const val DIRECT = "DIRECT"
    private const val REJECT = "REJECT"
    private const val PROBE_LISTENER = "probe-in"
    private const val BYEDPI_PROBE_LISTENER = "byedpi-probe-in"
    private const val PROCESS_NAME = "PROCESS-NAME"
    private const val PROCESS_PATH = "PROCESS-PATH"
    private const val FAKE_IP_RANGE = "198.18.0.1/16"
    private const val DESKTOP_TUN_INET4 = "172.19.0.1/30"
    private const val DESKTOP_TUN_INET6 = "fdfe:dcba:9876::1/126"
    private const val SYSTEM_NAMESERVER = "system://"
    private val LOCAL_DOMAINS = listOf("+.lan", "+.local", "+.localdomain", "+.home.arpa", "+.internal", "localhost")
    private val PRIVATE_NETWORKS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "100.64.0.0/10",
        "fc00::/7", "fe80::/10"
    )

    private val json = Json { prettyPrint = false }

    fun build(
        proxies: List<EngineProxy>,
        activeProxyTag: String,
        byeDpiPackages: List<String>,
        vpnPackages: List<String>,
        byeDpiPort: Int,
        probePort: Int,
        probeUser: String,
        probePass: String,
        options: NetworkOptions = NetworkOptions(),
        platform: MihomoPlatform = MihomoPlatform.Android,
        directPaths: List<String> = emptyList()
    ): String {
        val active = proxies.firstOrNull { it.tag == activeProxyTag } ?: proxies.firstOrNull()
        val activeProxy = active?.let { MihomoProxy.of(it, options.preferredFingerprint) }
        val ipv6 = options.ipv6Mode.ipv6Enabled && EngineRequirement.IPV4_ONLY !in active?.sessionRequirements.orEmpty()
        val vpnTarget = active?.tag ?: DIRECT
        val desktop = platform as? MihomoPlatform.Desktop
        val byeDpiProbePort = desktop?.byeDpiProbePort?.takeIf { byeDpiPackages.isNotEmpty() }
        val defaultTarget = if (desktop != null) DIRECT else REJECT
        val directNameserver = when (val systemDns = desktop?.systemDns) {
            SystemDns.Local -> SYSTEM_NAMESERVER
            is SystemDns.Server -> systemDns.address
            SystemDns.Unknown, null -> options.dnsDirect
        }

        val config = buildJsonObject {
            put("mode", "rule")
            put("log-level", "warning")
            put("ipv6", ipv6)
            put("find-process-mode", "strict")
            put("unified-delay", true)
            if (desktop != null) {
                putJsonObject("tun") {
                    put("enable", true)
                    put("stack", TUN_STACK)
                    put("device", DESKTOP_INTERFACE_NAME)
                    put("auto-route", true)
                    put("strict-route", true)
                    put("auto-detect-interface", true)
                    put("mtu", options.mtu)
                    putJsonArray("dns-hijack") { add("any:53") }
                    putJsonArray("inet4-address") { add(DESKTOP_TUN_INET4) }
                    if (ipv6) putJsonArray("inet6-address") { add(DESKTOP_TUN_INET6) }
                }
            }
            putJsonObject("dns") {
                put("enable", true)
                put("ipv6", ipv6)
                put("enhanced-mode", "fake-ip")
                put("fake-ip-range", FAKE_IP_RANGE)
                putJsonArray("fake-ip-filter") { LOCAL_DOMAINS.forEach { add(it) } }
                putJsonArray("default-nameserver") { add(options.dnsDirect) }
                putJsonArray("proxy-server-nameserver") { add(options.dnsDirect) }
                putJsonArray("nameserver") {
                    add(if (active != null) "tls://${options.dnsRemote}#${active.tag}" else "tls://${options.dnsRemote}")
                }
                if (desktop != null) {
                    putJsonArray("direct-nameserver") { add(directNameserver) }
                    putJsonObject("nameserver-policy") { put(LOCAL_DOMAINS.joinToString(","), directNameserver) }
                }
            }
            putJsonArray("listeners") {
                add(buildJsonObject {
                    put("name", PROBE_LISTENER)
                    put("type", "socks")
                    put("listen", "127.0.0.1")
                    put("port", probePort)
                    put("udp", true)
                    putJsonArray("users") {
                        add(buildJsonObject {
                            put("username", probeUser)
                            put("password", probePass)
                        })
                    }
                })
                if (byeDpiProbePort != null) {
                    add(buildJsonObject {
                        put("name", BYEDPI_PROBE_LISTENER)
                        put("type", "socks")
                        put("listen", "127.0.0.1")
                        put("port", byeDpiProbePort)
                        put("udp", true)
                    })
                }
            }
            putJsonArray("proxies") {
                activeProxy?.let { add(it) }
                add(buildJsonObject {
                    put("name", BYEDPI_PROXY)
                    put("type", "socks5")
                    put("server", "127.0.0.1")
                    put("port", byeDpiPort)
                    put("udp", true)
                })
            }
            putJsonArray("rules") {
                add("IN-NAME,$PROBE_LISTENER,$vpnTarget")
                if (byeDpiProbePort != null) add("IN-NAME,$BYEDPI_PROBE_LISTENER,$BYEDPI_PROXY")
                desktop?.directProcesses?.forEach { add("$PROCESS_NAME,$it,$DIRECT") }
                if (options.bypassLan) PRIVATE_NETWORKS.forEach { add("IP-CIDR,$it,$DIRECT,no-resolve") }
                if (desktop != null) {
                    val channels = listOf(byeDpiPackages to BYEDPI_PROXY, vpnPackages to vpnTarget, directPaths to DIRECT)
                    channels.forEach { (apps, target) -> processRules(PROCESS_PATH, apps.filter(::isExecutablePath), target) }
                    channels.forEach { (apps, target) -> processRules(PROCESS_NAME, apps.filterNot(::isExecutablePath), target) }
                } else {
                    processRules(PROCESS_NAME, byeDpiPackages, BYEDPI_PROXY)
                    processRules(PROCESS_NAME, vpnPackages, vpnTarget)
                }
                add("MATCH,$defaultTarget")
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    fun proxiesForTest(proxies: List<EngineProxy>, options: NetworkOptions = NetworkOptions()): String =
        json.encodeToString(JsonArray.serializer(), JsonArray(proxies.mapNotNull { MihomoProxy.of(it, options.preferredFingerprint) }))

    private fun JsonArrayBuilder.processRules(type: String, apps: List<String>, target: String) {
        apps.forEach { add("$type,$it,$target") }
    }
}
