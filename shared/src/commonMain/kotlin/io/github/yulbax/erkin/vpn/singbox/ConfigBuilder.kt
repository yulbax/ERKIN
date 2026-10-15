package io.github.yulbax.erkin.vpn.singbox

import io.github.yulbax.erkin.data.isExecutablePath
import io.github.yulbax.erkin.vpn.core.EnginePlacement
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

sealed interface AppRouting {
    val appRuleKey: String

    data object AndroidPackages : AppRouting {
        override val appRuleKey: String = "package_name"
    }

    data class DesktopProcesses(val directProcesses: List<String>) : AppRouting {
        override val appRuleKey: String = "process_name"
    }
}

object ConfigBuilder {

    private val json = Json { prettyPrint = false }
    private const val TUN_INET6 = "fdfe:dcba:9876::1/126"
    private const val TUN_INET4 = "172.18.0.1/30"

    const val HEALTH_GROUP_TAG = "health"
    private const val LIBBOX_MIN_REPORTED_GROUP_SIZE = 2
    const val DESKTOP_INTERFACE_NAME = "ERKIN"
    private const val PROBE_INBOUND_TAG = "probe-in"
    private const val REMOTE_DNS_TAG = "remote"
    private const val IPV4_ONLY = "ipv4_only"
    private const val PROCESS_PATH_KEY = "process_path"

    private fun appRule(key: String, apps: List<String>, outbound: String): JsonObject = buildJsonObject {
        putJsonArray(key) { apps.forEach { add(it) } }
        put("action", "route")
        put("outbound", outbound)
    }

    fun build(
        proxies: List<EngineProxy>,
        activeProxyTag: String,
        byeDpiPackages: List<String>,
        vpnPackages: List<String>,
        tunneledPackages: List<String>,
        byeDpiPort: Int,
        probePort: Int,
        probeUser: String,
        probePass: String,
        options: NetworkOptions = NetworkOptions(),
        routing: AppRouting = AppRouting.AndroidPackages,
        directPaths: List<String> = emptyList()
    ): String {
        val ipv6Enabled = options.ipv6Mode.ipv6Enabled
        val (proxyEndpoints, proxyOutbounds) = proxyObjects(proxies, options)
        val active = proxies.firstOrNull { it.tag == activeProxyTag } ?: proxies.firstOrNull()
        val proxyTarget = active?.tag ?: "direct"
        val sessionRequirements = active?.sessionRequirements.orEmpty()
        val dnsStrategy =
            if (EngineRequirement.IPV4_ONLY in sessionRequirements) IPV4_ONLY else options.ipv6Mode.dnsStrategy

        val config = buildJsonObject {
            putJsonObject("log") {
                put("level", "warn")
                put("timestamp", true)
                put("output", "box.log")
            }

            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("tag", REMOTE_DNS_TAG)
                        put("type", "tls")
                        put("server", options.dnsRemote)
                        if (active != null) put("detour", active.tag)
                    })
                    add(buildJsonObject {
                        put("tag", "local")
                        put("type", "udp")
                        put("server", options.dnsDirect)
                    })
                    if (byeDpiPackages.isNotEmpty()) {
                        add(buildJsonObject {
                            put("tag", "byedpi-dns")
                            put("type", "tls")
                            put("server", options.dnsRemote)
                            put("detour", "byedpi")
                        })
                    }
                }
                if (byeDpiPackages.isNotEmpty()) {
                    putJsonArray("rules") {
                        add(buildJsonObject {
                            putJsonArray(routing.appRuleKey) {
                                byeDpiPackages.forEach { add(it) }
                            }
                            put("action", "route")
                            put("server", "byedpi-dns")
                        })
                    }
                }
                put("final", REMOTE_DNS_TAG)
                put("strategy", dnsStrategy)
            }

            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    putJsonArray("address") {
                        add(TUN_INET4)
                        if (ipv6Enabled) add(TUN_INET6)
                    }
                    put("mtu", options.mtu)
                    put("auto_route", true)
                    put("stack", options.tunStack.wire)
                    if (routing is AppRouting.DesktopProcesses) {
                        put("interface_name", DESKTOP_INTERFACE_NAME)
                        put("strict_route", true)
                    }
                    if (routing is AppRouting.AndroidPackages && tunneledPackages.isNotEmpty()) {
                        putJsonArray("include_package") {
                            tunneledPackages.forEach { add(it) }
                        }
                    }
                })
                add(buildJsonObject {
                    put("type", "socks")
                    put("tag", PROBE_INBOUND_TAG)
                    put("listen", "127.0.0.1")
                    put("listen_port", probePort)
                    putJsonArray("users") {
                        add(buildJsonObject {
                            put("username", probeUser)
                            put("password", probePass)
                        })
                    }
                })
            }

            putJsonArray("outbounds") {
                proxyOutbounds.forEach { add(it) }
                if (active != null) {
                    add(buildJsonObject {
                        put("type", "selector")
                        put("tag", HEALTH_GROUP_TAG)
                        putJsonArray("outbounds") { repeat(LIBBOX_MIN_REPORTED_GROUP_SIZE) { add(active.tag) } }
                    })
                }
                add(buildJsonObject {
                    put("type", "socks")
                    put("tag", "byedpi")
                    put("server", "127.0.0.1")
                    put("server_port", byeDpiPort)
                    put("version", "5")
                })
                add(buildJsonObject {
                    put("type", "direct")
                    put("tag", "direct")
                })
            }

            if (proxyEndpoints.isNotEmpty()) {
                putJsonArray("endpoints") { proxyEndpoints.forEach { add(it) } }
            }

            putJsonObject("route") {
                putJsonArray("rules") {
                    if (options.sniff) add(buildJsonObject { put("action", "sniff") })
                    add(buildJsonObject {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    })
                    if (EngineRequirement.RESOLVE_BEFORE_DIAL in sessionRequirements) {
                        add(buildJsonObject {
                            put("action", "resolve")
                            put("server", REMOTE_DNS_TAG)
                            put("strategy", dnsStrategy)
                        })
                    }
                    add(buildJsonObject {
                        putJsonArray("inbound") { add(PROBE_INBOUND_TAG) }
                        put("action", "route")
                        put("outbound", proxyTarget)
                    })
                    if (routing is AppRouting.DesktopProcesses && routing.directProcesses.isNotEmpty()) {
                        add(buildJsonObject {
                            putJsonArray(routing.appRuleKey) { routing.directProcesses.forEach { add(it) } }
                            put("action", "route")
                            put("outbound", "direct")
                        })
                    }
                    if (options.bypassLan) {
                        add(buildJsonObject {
                            put("ip_is_private", true)
                            put("action", "route")
                            put("outbound", "direct")
                        })
                    }
                    val appRules = listOf(
                        byeDpiPackages to "byedpi",
                        vpnPackages to proxyTarget,
                        directPaths to "direct"
                    )
                    appRules.forEach { (apps, outbound) ->
                        val paths = apps.filter(::isExecutablePath)
                        if (paths.isNotEmpty()) add(appRule(PROCESS_PATH_KEY, paths, outbound))
                    }
                    appRules.forEach { (apps, outbound) ->
                        val names = apps.filterNot(::isExecutablePath)
                        if (names.isNotEmpty()) add(appRule(routing.appRuleKey, names, outbound))
                    }
                    if (routing is AppRouting.AndroidPackages) add(buildJsonObject { put("action", "reject") })
                }
                put("final", if (routing is AppRouting.AndroidPackages) proxyTarget else "direct")
                put("auto_detect_interface", true)
                put("default_domain_resolver", "local")
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    fun buildLatencyTest(proxies: List<EngineProxy>, options: NetworkOptions = NetworkOptions()): String {
        val (proxyEndpoints, proxyOutbounds) = proxyObjects(proxies, options)
        val config = buildJsonObject {
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("tag", "local")
                        put("type", "udp")
                        put("server", options.dnsDirect)
                    })
                }
                put("strategy", options.ipv6Mode.dnsStrategy)
            }
            putJsonArray("outbounds") { proxyOutbounds.forEach { add(it) } }
            if (proxyEndpoints.isNotEmpty()) {
                putJsonArray("endpoints") { proxyEndpoints.forEach { add(it) } }
            }
            putJsonObject("route") {
                put("default_domain_resolver", "local")
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun proxyObjects(proxies: List<EngineProxy>, options: NetworkOptions): Pair<List<JsonObject>, List<JsonObject>> {
        val (endpoints, outbounds) = proxies.partition { it.placement == EnginePlacement.ENDPOINT }
        return endpoints.map { proxyObject(it, options) } to outbounds.map { proxyObject(it, options) }
    }

    private fun proxyObject(proxy: EngineProxy, options: NetworkOptions): JsonObject {
        val outbound = JsonObject((Json.parseToJsonElement(proxy.outboundDescriptor) as JsonObject).toMutableMap().apply {
            put("tag", JsonPrimitive(proxy.tag))
        })
        return if (EngineRequirement.TLS_FINGERPRINT in proxy.requirements) {
            applyPreferredFingerprint(outbound, options.preferredFingerprint)
        } else {
            outbound
        }
    }

    private fun applyPreferredFingerprint(outbound: JsonObject, preferred: TlsFingerprint?): JsonObject {
        if (preferred == null) return outbound
        val tls = outbound["tls"] as? JsonObject ?: return outbound
        val utls = tls["utls"] as? JsonObject ?: buildJsonObject { put("enabled", true) }
        val newUtls = JsonObject(utls.toMutableMap().apply {
            put("enabled", JsonPrimitive(true))
            put("fingerprint", JsonPrimitive(preferred.wire))
        })
        val newTls = JsonObject(tls.toMutableMap().apply { put("utls", newUtls) })
        return JsonObject(outbound.toMutableMap().apply { put("tls", newTls) })
    }
}
