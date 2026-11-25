package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity

object NewAppsChannelPin {
    private val EARLIER_DEFAULT = ConnectionType.DIRECT

    fun pinned(settings: SettingsEntity, upgrading: Boolean, platformDefault: ConnectionType): SettingsEntity =
        if (settings.newAppsConnectionType.isNotEmpty()) settings
        else settings.copy(newAppsConnectionType = (if (upgrading) EARLIER_DEFAULT else platformDefault).wire)
}
