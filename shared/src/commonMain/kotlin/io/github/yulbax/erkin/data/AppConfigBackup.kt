package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import kotlinx.serialization.Serializable

@Serializable
data class AppConfigBackup(
    val version: Int = MIN_SUPPORTED_VERSION,
    val apps: Map<String, String>? = null,
    val settings: SettingsEntity? = null,
    val profiles: List<ProfileBackup>? = null,
    val rules: List<RoutingRuleEntity>? = null
) {
    companion object {
        const val MIN_SUPPORTED_VERSION = 1
        const val CURRENT_VERSION = 6
        const val MAX_IMPORT_BYTES = 5L * 1024 * 1024
        const val MAX_ENTRIES_PER_SECTION = 10_000
    }
}
