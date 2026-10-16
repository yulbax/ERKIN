package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.proxy.LinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class BackupSelection(
    val settings: Boolean,
    val apps: Boolean,
    val profiles: Boolean,
    val rules: Boolean
)

data class ImportResult(
    val applied: Int = 0,
    val skipped: Int = 0,
    val settingsApplied: Boolean = false,
    val profilesAdded: Int = 0,
    val profilesSkipped: Int = 0,
    val rulesAdded: Int = 0,
    val rulesSkipped: Int = 0,
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
                    database.settingsDao().getSettings() ?: SettingsEntity()
                } else {
                    null
                },
                profiles = if (selection.profiles) {
                    database.profileDao().getAll()
                } else {
                    null
                },
                rules = if (selection.rules) {
                    database.routingRuleDao().getAll()
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
        settings?.let { database.settingsDao().upsertSettings(it) }
        val profiles = backup.profiles?.takeIf { selection.profiles }?.let { applyProfiles(it) }
        val rules = backup.rules?.takeIf { selection.rules }?.let { applyRules(it) }
        return ImportResult(
            applied = apps?.applied ?: 0,
            skipped = apps?.skipped ?: 0,
            settingsApplied = settings != null,
            profilesAdded = profiles?.applied ?: 0,
            profilesSkipped = profiles?.skipped ?: 0,
            rulesAdded = rules?.applied ?: 0,
            rulesSkipped = rules?.skipped ?: 0
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

    private suspend fun applyRules(rules: List<RoutingRuleEntity>): SectionResult {
        val dao = database.routingRuleDao()
        val existing = dao.getAll().mapTo(HashSet()) { it.identity() }
        var added = 0
        var skipped = 0
        for (rule in rules) {
            if (!existing.add(rule.identity())) {
                skipped++
                continue
            }
            dao.append(rule.copy(id = 0))
            added++
        }
        return SectionResult(added, skipped)
    }

    private suspend fun applyProfiles(profiles: List<ProfileEntity>): SectionResult {
        val profileDao = database.profileDao()
        val stored = profileDao.getAll()
        val existingByLink = stored.filter { it.isLink }.associateTo(HashMap()) { it.link to it.id }
        val existingChains = stored.mapNotNullTo(HashSet()) { (it.kind as? ServerKind.Chain)?.chain }
        val newIds = HashMap<Long, Long>()
        var importedSelectedId: Long? = null
        var added = 0
        var skipped = 0
        for (profile in profiles.filter { it.isLink }) {
            val existingId = existingByLink[profile.link]
            val id = if (existingId != null) {
                skipped++
                existingId
            } else {
                val parsed = checkNotNull(LinkParser.parse(profile.link))
                added++
                profileDao.append(
                    profile.copy(id = 0, type = parsed.protocol.wire, outboundJson = parsed.proxyJson(), selected = false)
                ).also { existingByLink[profile.link] = it }
            }
            newIds[profile.id] = id
            if (profile.selected) importedSelectedId = id
        }
        for (profile in profiles) {
            val chain = (profile.kind as? ServerKind.Chain)?.chain ?: continue
            val remapped = chain.remap(newIds)
            if (remapped == null || !existingChains.add(remapped)) {
                skipped++
                continue
            }
            val id = profileDao.append(profile.copy(id = 0, outboundJson = remapped.encode(), selected = false))
            if (profile.selected) importedSelectedId = id
            added++
        }
        profileRepository.ensureSelection(preferredId = importedSelectedId)
        return SectionResult(added, skipped)
    }

    private fun RoutingRuleEntity.identity() = Triple(type, value, target)
}
