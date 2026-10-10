package io.github.yulbax.erkin.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val database: AppDatabase,
    private val settingsDao: SettingsDao
) {
    val settings: Flow<SettingsEntity> = settingsDao.observeSettings()
        .map { it ?: SettingsEntity() }

    suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
        database.transaction {
            val current = settingsDao.getSettings() ?: SettingsEntity()
            settingsDao.upsertSettings(transform(current).copy(id = SETTINGS_ID))
        }
    }

    private companion object {
        const val SETTINGS_ID = 1
    }
}
