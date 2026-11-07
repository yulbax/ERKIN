package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.byedpi.SiteListBackup
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import kotlinx.serialization.Serializable

@Serializable
data class AppConfigBackup(
    val version: Int = MIN_SUPPORTED_VERSION,
    val apps: Map<String, String>? = null,
    val settings: SettingsEntity? = null,
    val profiles: List<ProfileBackup>? = null,
    val rules: List<RoutingRuleEntity>? = null,
    val appServers: Map<String, Long>? = null,
    val siteLists: List<SiteListBackup>? = null
) {
    fun adaptedTo(appsCanBypass: Boolean): AppConfigBackup {
        val ruleApps = rules.orEmpty().mapNotNullTo(HashSet()) { it.app }
        val bypass = ConnectionType.BYPASS.wire
        val direct = ConnectionType.DIRECT.wire
        fun channel(app: String?, wire: String): String = when {
            !appsCanBypass && wire == bypass -> direct
            appsCanBypass && version < BYPASS_VERSION && wire == direct && app !in ruleApps -> bypass
            else -> wire
        }
        return copy(
            apps = apps?.mapValues { (app, wire) -> channel(app, wire) },
            settings = settings?.let { it.copy(newAppsConnectionType = channel(null, it.newAppsConnectionType)) }
        )
    }

    companion object {
        const val MIN_SUPPORTED_VERSION = 1
        const val CURRENT_VERSION = 9
        private const val BYPASS_VERSION = 9
        const val MAX_IMPORT_BYTES = 5L * 1024 * 1024
        const val MAX_ENTRIES_PER_SECTION = 10_000
    }
}
