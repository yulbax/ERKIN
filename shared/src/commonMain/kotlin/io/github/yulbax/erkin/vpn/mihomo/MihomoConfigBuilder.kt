package io.github.yulbax.erkin.vpn.mihomo

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.isExecutablePath
import io.github.yulbax.erkin.data.profile.SubscriptionRule
import io.github.yulbax.erkin.data.profile.SubscriptionRules
import io.github.yulbax.erkin.data.profile.SubscriptionTarget
import io.github.yulbax.erkin.vpn.ProbeUrls
import io.github.yulbax.erkin.vpn.ServerProbePolicy
import io.github.yulbax.erkin.vpn.core.BYEDPI_PROXY_TAG
import io.github.yulbax.erkin.vpn.core.BuiltInOutbound
import io.github.yulbax.erkin.vpn.core.EngineConfig
import io.github.yulbax.erkin.vpn.core.EngineProxy
import io.github.yulbax.erkin.vpn.core.EngineRequirement
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.PrivateNetworks
import io.github.yulbax.erkin.vpn.core.RemoteDns
import io.github.yulbax.erkin.vpn.core.RoutingRule
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
import io.github.yulbax.erkin.vpn.core.RuleTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

sealed interface SystemDns {
    data object Local : SystemDns
    data class Server(val address: String) : SystemDns
    data class Tls(val host: String) : SystemDns
    data object Unknown : SystemDns
}

data class ProbeInbound(val port: Int, val username: String, val password: String)

enum class DesktopOs(private val replacedExecutableSuffix: String?, val childrenInheritChannels: Boolean) {
    WINDOWS(null, childrenInheritChannels = false),
    LINUX(" (deleted)", childrenInheritChannels = true);

    fun processIds(app: String): List<String> = listOfNotNull(app, replacedExecutableSuffix?.let { app + it })
}

sealed interface MihomoPlatform {
    fun processIds(app: String): List<String>

    val childrenInheritChannels: Boolean get() = false

    data class Android(val systemDns: SystemDns = SystemDns.Local) : MihomoPlatform {
        override fun processIds(app: String): List<String> = listOf(app)
    }

    data class Desktop(
        val os: DesktopOs,
        val directProcesses: List<String>,
        val systemDns: SystemDns = SystemDns.Local,
        val byeDpiProbePort: Int? = null
    ) : MihomoPlatform {
        override fun processIds(app: String): List<String> = os.processIds(app)

        override val childrenInheritChannels: Boolean get() = os.childrenInheritChannels
    }
}

object MihomoConfigBuilder {

    const val BYEDPI_PROXY = BYEDPI_PROXY_TAG
    const val DESKTOP_INTERFACE_NAME = "ERKIN"
    private const val TUN_STACK = "gvisor"
    private val DIRECT = BuiltInOutbound.DIRECT.tag
    private val REJECT = BuiltInOutbound.REJECT.tag
    const val PROBE_LISTENER = "probe-in"
    const val BYEDPI_PROBE_LISTENER = "byedpi-probe-in"
    private const val PROCESS_NAME = "PROCESS-NAME"
    private const val PROCESS_PATH = "PROCESS-PATH"
    private const val FAKE_IP_RANGE = "198.18.0.1/16"
    private const val DESKTOP_TUN_INET4 = "172.19.0.1/30"
    private const val DESKTOP_TUN_INET6 = "fdfe:dcba:9876::1/126"
    private const val SYSTEM_NAMESERVER = "system://"
    private const val RULE_LIST_FORMAT = "mrs"
    private const val BALANCER_CHECK_INTERVAL_S = 60
    private val BLOCK_QUIC_RULE = "AND,((NETWORK,UDP),(DST-PORT,443)),$REJECT"
    private val LOCAL_DOMAINS = listOf("+.lan", "+.local", "+.localdomain", "+.home.arpa", "+.internal", "localhost")

    private val json = Json { prettyPrint = false }

    fun build(engine: EngineConfig, probe: ProbeInbound, platform: MihomoPlatform = MihomoPlatform.Android()): String {
        val proxies = engine.proxies
        val activeProxyTag = engine.activeProxyTag
        val byeDpiPackages = engine.byeDpiPackages
        val vpnPackages = engine.vpnPackages
        val byeDpiPort = engine.byeDpiSocksPort
        val options = engine.network
        val directPaths = engine.directPaths
        val rules = engine.rules
        val probePort = probe.port
        val probeUser = probe.username
        val probePass = probe.password
        val balancer = engine.balancer
        val mainTag = balancer?.tag ?: (proxies.firstOrNull { it.tag == activeProxyTag } ?: proxies.firstOrNull())?.tag
        val vpnTarget = mainTag ?: DIRECT
        val mainExits = balancer?.members?.toSet() ?: setOfNotNull(mainTag)
        val exits = proxies.filter { it.tag in mainExits || it.tag in engine.vpnTargets.values || it.tag in engine.serverTags.values }
        val ipv6 = options.ipv6Mode.ipv6Enabled && exits.none { EngineRequirement.IPV4_ONLY in it.sessionRequirements }
        val vpnTargetOf = { app: String -> engine.vpnTargets[app] ?: vpnTarget }
        val desktop = platform as? MihomoPlatform.Desktop
        val ruleTarget = { rule: RoutingRule ->
            when (rule.target) {
                RuleTarget.VPN -> rule.serverId?.let(engine.serverTags::get) ?: mainTag ?: REJECT
                RuleTarget.BYEDPI -> BYEDPI_PROXY
                RuleTarget.DIRECT -> DIRECT
                RuleTarget.BLOCK -> REJECT
            }
        }
        val byeDpiProbePort = desktop?.byeDpiProbePort?.takeIf { engine.usesByeDpi }
        val defaultTarget = when (engine.newApps) {
            ConnectionType.VPN -> vpnTarget
            ConnectionType.BYEDPI -> BYEDPI_PROXY
            ConnectionType.DIRECT -> DIRECT
            ConnectionType.BYPASS -> if (desktop != null) DIRECT else REJECT
            ConnectionType.BLOCK -> REJECT
        }
        val systemDns = when (platform) {
            is MihomoPlatform.Android -> platform.systemDns
            is MihomoPlatform.Desktop -> platform.systemDns
        }
        val directNameserver = when (systemDns) {
            SystemDns.Local -> SYSTEM_NAMESERVER
            is SystemDns.Server -> systemDns.address
            is SystemDns.Tls -> "tls://${systemDns.host}"
            SystemDns.Unknown -> options.dnsDirect
        }

        val config = buildJsonObject {
            put("mode", "rule")
            put("log-level", "warning")
            put("ipv6", ipv6)
            put("find-process-mode", "always")
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
                    val remote = RemoteDns.nameserver(options.dnsRemote)
                        ?: requireNotNull(RemoteDns.nameserver(NetworkOptions.DEFAULT.dnsRemote))
                    add(mainTag?.let { "$remote#$it" } ?: remote)
                }
                putJsonArray("direct-nameserver") { add(directNameserver) }
                putJsonObject("nameserver-policy") { put(LOCAL_DOMAINS.joinToString(","), directNameserver) }
            }
            if (options.sniff) {
                putJsonObject("sniffer") {
                    put("enable", true)
                    put("override-destination", false)
                    put("parse-pure-ip", true)
                    putJsonObject("sniff") {
                        putJsonObject("TLS") { putJsonArray("ports") { add("443"); add("8443") } }
                        putJsonObject("HTTP") { putJsonArray("ports") { add("80"); add("8080-8880") } }
                        putJsonObject("QUIC") { putJsonArray("ports") { add("443"); add("8443") } }
                    }
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
                proxies.mapNotNull { MihomoProxy.of(it, options.preferredFingerprint) }.forEach { add(it) }
                add(byeDpiProxy(byeDpiPort))
            }
            balancer?.let { group ->
                putJsonArray("proxy-groups") {
                    add(buildJsonObject {
                        put("name", group.tag)
                        put("type", "load-balance")
                        put("strategy", "consistent-hashing")
                        putJsonArray("proxies") { group.members.forEach { add(it) } }
                        put("url", ProbeUrls.VPN)
                        put("interval", BALANCER_CHECK_INTERVAL_S)
                        put("timeout", ServerProbePolicy.TIMEOUT.inWholeMilliseconds)
                        put("lazy", false)
                    })
                }
            }
            val lists = rules.mapNotNull(RuleLists::url).distinct()
                .flatMap { url -> engine.ruleLists[url].orEmpty().map { url to it } }
            val subscription = engine.subscriptionRules
            if (lists.isNotEmpty() || subscription?.rules?.providers?.isNotEmpty() == true) {
                putJsonObject("rule-providers") {
                    subscription?.let { applied ->
                        applied.rules.providers.forEach { provider ->
                            val name = SubscriptionRules.providerName(applied.groupId, provider.name)
                            when (val list = provider.managed) {
                                null -> put(name, provider.definition)
                                else -> putJsonObject(name) { putFileProvider(list.url, list.kind) }
                            }
                        }
                    }
                    lists.forEach { (url, kind) ->
                        putJsonObject(RuleLists.providerName(url, kind)) { putFileProvider(url, kind) }
                    }
                }
            }
            putJsonArray("rules") {
                add("IN-NAME,$PROBE_LISTENER,$vpnTarget")
                if (byeDpiProbePort != null) add("IN-NAME,$BYEDPI_PROBE_LISTENER,$BYEDPI_PROXY")
                desktop?.directProcesses?.forEach { name -> platform.processIds(name).forEach { add("$PROCESS_NAME,$it,$DIRECT") } }
                val blocked = engine.blockedPackages.toSet()
                val (blockedAppRules, listedRules) = rules.partition { it.app in blocked }
                engine.blockedPackages.forEach { app ->
                    blockedAppRules.filter { it.app == app }.forEach { rule -> addRule(rule, ruleTarget(rule), engine.ruleLists, platform) }
                    appMatches(app, platform).forEach { add("$it,$REJECT") }
                }
                if (options.bypassLan) PrivateNetworks.CIDRS.forEach { add("IP-CIDR,$it,$DIRECT,no-resolve") }
                if (options.blockQuic) add(BLOCK_QUIC_RULE)
                listedRules.forEach { rule -> addRule(rule, ruleTarget(rule), engine.ruleLists, platform) }
                subscription?.let { applied ->
                    applied.rules.rules.forEach { rule ->
                        val target = when (rule.target) {
                            SubscriptionTarget.VPN -> vpnTarget
                            SubscriptionTarget.DIRECT -> DIRECT
                            SubscriptionTarget.BLOCK -> REJECT
                        }
                        val condition = rule.conditionWith { SubscriptionRules.providerName(applied.groupId, it) }
                        add((listOf(condition, target) + rule.options).joinToString(SubscriptionRule.TOKEN_SEPARATOR))
                    }
                }
                if (desktop != null) {
                    val direct = (directPaths + engine.directPackages + engine.bypassPackages).distinct()
                    val channels = listOf<Pair<List<String>, (String) -> String>>(
                        byeDpiPackages to { BYEDPI_PROXY },
                        vpnPackages to vpnTargetOf,
                        direct to { DIRECT }
                    )
                    channels.forEach { (apps, target) -> processRules(PROCESS_PATH, apps.filter(::isExecutablePath), platform, target) }
                    channels.forEach { (apps, target) -> processRules(PROCESS_NAME, apps.filterNot(::isExecutablePath), platform, target) }
                    if (platform.childrenInheritChannels) {
                        channels.forEach { (apps, target) -> processRules(PROCESS_NAME, apps.filter(::isExecutablePath), platform, target) }
                    }
                } else {
                    processRules(PROCESS_NAME, byeDpiPackages, platform) { BYEDPI_PROXY }
                    processRules(PROCESS_NAME, vpnPackages, platform, vpnTargetOf)
                    processRules(PROCESS_NAME, engine.directPackages, platform) { DIRECT }
                }
                add("MATCH,$defaultTarget")
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun JsonObjectBuilder.putFileProvider(url: String, kind: RuleSetKind) {
        put("type", "file")
        put("behavior", kind.behavior)
        put("format", RULE_LIST_FORMAT)
        put("path", RuleLists.path(url, kind))
    }

    private fun JsonArrayBuilder.addRule(
        rule: RoutingRule,
        target: String,
        ruleLists: Map<String, Set<RuleSetKind>>,
        platform: MihomoPlatform
    ) {
        val conditions = when (val url = RuleLists.url(rule)) {
            null -> listOf("${rule.match.mihomo},${rule.value}")
            else -> ruleLists[url].orEmpty().sorted().map { kind -> "${rule.match.mihomo},${RuleLists.providerName(url, kind)}" }
        }
        val app = rule.app
        conditions.forEach { condition ->
            if (app == null) {
                add("$condition,$target")
            } else {
                add("AND,(${appCondition(app, platform)},($condition)),$target")
            }
        }
    }

    fun proxiesForTest(proxies: List<EngineProxy>, options: NetworkOptions = NetworkOptions(), byeDpiPort: Int? = null): String =
        json.encodeToString(
            JsonArray.serializer(),
            JsonArray(proxies.mapNotNull { MihomoProxy.of(it, options.preferredFingerprint) } + listOfNotNull(byeDpiPort?.let(::byeDpiProxy)))
        )

    private fun byeDpiProxy(port: Int) = buildJsonObject {
        put("name", BYEDPI_PROXY)
        put("type", "socks5")
        put("server", "127.0.0.1")
        put("port", port)
        put("udp", true)
    }

    private fun appMatches(app: String, platform: MihomoPlatform): List<String> {
        val types = when {
            !isExecutablePath(app) -> listOf(PROCESS_NAME)
            platform.childrenInheritChannels -> listOf(PROCESS_PATH, PROCESS_NAME)
            else -> listOf(PROCESS_PATH)
        }
        return types.flatMap { type -> platform.processIds(app).map { "$type,$it" } }
    }

    private fun appCondition(app: String, platform: MihomoPlatform): String {
        val matches = appMatches(app, platform).map { "($it)" }
        return matches.singleOrNull() ?: "(OR,(${matches.joinToString(",")}))"
    }

    private fun JsonArrayBuilder.processRules(
        type: String,
        apps: List<String>,
        platform: MihomoPlatform,
        target: (String) -> String
    ) {
        apps.forEach { app -> platform.processIds(app).forEach { add("$type,$it,${target(app)}") } }
    }
}
