package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.proxy.LinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class BackupSelection(
    val settings: Boolean,
    val apps: Boolean,
    val profiles: Boolean
)

data class ImportResult(
    val applied: Int = 0,
    val skipped: Int = 0,
    val settingsApplied: Boolean = false,
    val profilesAdded: Int = 0,
    val profilesSkipped: Int = 0,
    val error: String? = null
)

private data class SectionResult(val applied: Int, val skipped: Int)

class ConfigBackupRepository(
    private val database: AppDatabase,
    private val profileRepository: ProfileRepository
) {

    suspend fun export(selection: BackupSelection): String = withContext(Dispatchers.IO) {
        val backup = database.transaction {
            AppConfigBackup(
                version = AppConfigBackup.CURRENT_VERSION,
                apps = if (selection.apps) {
                    database.appDao().getAllAppsSnapshot()
                        .sortedBy { it.packageName }
                        .associate { it.packageName to it.connectionType.wire }
                } else {
                    null
                },
                settings = if (selection.settings) {
                    (database.settingsDao().getSettings() ?: SettingsEntity()).toBackupSettings()
                } else {
                    null
                },
                profiles = if (selection.profiles) {
                    database.profileDao().getAll().map { it.toBackupProfile() }
                } else {
                    null
                }
            )
        }
        BackupCodec.encode(backup)
    }

    suspend fun inspect(open: () -> InputStream?): AppConfigBackup? = withContext(Dispatchers.IO) {
        try {
            open()?.use(BackupCodec::read)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun apply(backup: AppConfigBackup, selection: BackupSelection): ImportResult =
        withContext(Dispatchers.IO) {
            if (!BackupCodec.isValid(backup)) return@withContext ImportResult(error = "Invalid backup")
            try {
                database.transaction { applyInTransaction(backup, selection) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ImportResult(error = error.message ?: "Import failed")
            }
        }

    private suspend fun applyInTransaction(
        backup: AppConfigBackup,
        selection: BackupSelection
    ): ImportResult {
        val apps = backup.apps?.takeIf { selection.apps }?.let { applyApps(it) }
        val settings = backup.settings?.takeIf { selection.settings }
        settings?.let { database.settingsDao().upsertSettings(it.toEntity()) }
        val profiles = backup.profiles?.takeIf { selection.profiles }?.let { applyProfiles(it) }
        return ImportResult(
            applied = apps?.applied ?: 0,
            skipped = apps?.skipped ?: 0,
            settingsApplied = settings != null,
            profilesAdded = profiles?.applied ?: 0,
            profilesSkipped = profiles?.skipped ?: 0
        )
    }

    private suspend fun applyApps(apps: Map<String, String>): SectionResult {
        val appDao = database.appDao()
        val existing = appDao.getAllAppsSnapshot().associateBy { it.packageName }
        val updates = mutableListOf<App>()
        var applied = 0
        var skipped = 0
        for ((packageName, typeWire) in apps) {
            val app = existing[packageName]
            val type = ConnectionType.fromWire(typeWire)
            if (app == null || type == null) {
                skipped++
                continue
            }
            if (app.connectionType != type) updates.add(app.copy(connectionType = type))
            applied++
        }
        if (updates.isNotEmpty()) appDao.upsertApps(updates)
        return SectionResult(applied, skipped)
    }

    private suspend fun applyProfiles(profiles: List<BackupProfile>): SectionResult {
        val profileDao = database.profileDao()
        val existingByLink = profileDao.getAll().associateTo(HashMap()) { it.link to it.id }
        var importedSelectedId: Long? = null
        var added = 0
        var skipped = 0
        for (profile in profiles) {
            val existingId = existingByLink[profile.link]
            if (existingId != null) {
                if (profile.selected) importedSelectedId = existingId
                skipped++
                continue
            }
            val parsed = checkNotNull(LinkParser.parse(profile.link))
            val id = profileDao.insert(profile.toEntity(parsed))
            existingByLink[profile.link] = id
            if (profile.selected) importedSelectedId = id
            added++
        }
        profileRepository.ensureSelection(preferredId = importedSelectedId)
        return SectionResult(added, skipped)
    }
}
