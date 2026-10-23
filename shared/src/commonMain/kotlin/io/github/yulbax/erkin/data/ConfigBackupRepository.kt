package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileRepository
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.data.rules.RoutingRuleEntity
import io.github.yulbax.erkin.data.rules.RuleListEntity
import io.github.yulbax.erkin.vpn.core.RuleLists
import io.github.yulbax.erkin.vpn.core.RuleSetKind
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
    val deferred: Int = 0,
    val settingsApplied: Boolean = false,
    val profilesAdded: Int = 0,
    val profilesSkipped: Int = 0,
    val rulesAdded: Int = 0,
    val rulesSkipped: Int = 0,
    val error: String? = null
)

private data class SectionResult(val applied: Int, val skipped: Int)

private data class ProfilesImport(val result: SectionResult, val ids: Map<Long, Long>)

class ConfigBackupRepository(
    private val database: AppDatabase,
    private val profileRepository: ProfileRepository
) {

    suspend fun export(selection: BackupSelection): String = withContext(Dispatchers.IO) {
        val backup = database.transaction {
            val apps = database.appDao().getAllAppsSnapshot().sortedBy { it.packageName }
            AppConfigBackup(
                version = AppConfigBackup.CURRENT_VERSION,
                apps = if (selection.apps) {
                    apps.associate { it.packageName to it.connectionType.wire }
                } else {
                    null
                },
                appServers = if (selection.apps && selection.profiles) {
                    apps.mapNotNull { app -> app.serverId?.let { app.packageName to it } }.toMap().ifEmpty { null }
                } else {
                    null
                },
                settings = if (selection.settings) {
                    database.settingsDao().getSettings() ?: SettingsEntity()
                } else {
                    null
                },
                profiles = if (selection.profiles) {
                    val subscriptions = database.subscriptionDao().getAll().associateBy { it.id }
                    database.profileDao().getAll().map { ProfileBackup.of(it, it.subscriptionId?.let(subscriptions::get)) }
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
        val profiles = backup.profiles?.let { applyProfiles(it, insert = selection.profiles) }
        val apps = backup.apps?.takeIf { selection.apps }?.let { applyApps(it, backup.appServers.orEmpty(), profiles?.ids.orEmpty()) }
        val settings = backup.settings?.takeIf { selection.settings }
        settings?.let { database.settingsDao().upsertSettings(it) }
        val added = profiles?.result?.takeIf { selection.profiles }
        val rules = backup.rules?.takeIf { selection.rules }?.let { applyRules(it) }
        return ImportResult(
            applied = apps?.applied ?: 0,
            deferred = apps?.skipped ?: 0,
            settingsApplied = settings != null,
            profilesAdded = added?.applied ?: 0,
            profilesSkipped = added?.skipped ?: 0,
            rulesAdded = rules?.applied ?: 0,
            rulesSkipped = rules?.skipped ?: 0
        )
    }

    private suspend fun applyApps(apps: Map<String, String>, servers: Map<String, Long>, ids: Map<Long, Long>): SectionResult {
        val appDao = database.appDao()
        val existing = appDao.getAllAppsSnapshot().associateBy { it.packageName }
        val updates = mutableListOf<App>()
        val pending = mutableListOf<PendingApp>()
        var applied = 0
        for ((packageName, typeWire) in apps) {
            val type = ConnectionType.fromWire(typeWire) ?: continue
            val serverId = servers[packageName]?.let(ids::get)?.takeIf { type == ConnectionType.VPN }
            val app = existing[packageName]
            if (app == null) {
                pending.add(PendingApp(packageName, type, serverId))
                continue
            }
            if (app.connectionType != type || app.serverId != serverId) {
                updates.add(app.copy(connectionType = type, serverId = serverId))
            }
            applied++
        }
        if (updates.isNotEmpty()) appDao.upsertApps(updates)
        if (pending.isNotEmpty()) appDao.upsertPending(pending)
        return SectionResult(applied, pending.size)
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
            rule.toRule()?.let(RuleLists::url)?.let { url ->
                val kind = RuleLists.countryOf(url)?.let { RuleSetKind.IPCIDR.behavior }
                database.ruleListDao().insertIfAbsent(RuleListEntity(source = url, kind = kind))
            }
            added++
        }
        return SectionResult(added, skipped)
    }

    private suspend fun applyProfiles(backups: List<ProfileBackup>, insert: Boolean): ProfilesImport {
        val profileDao = database.profileDao()
        val subscriptionDao = database.subscriptionDao()
        val profiles = backups.map { it to it.toEntity() }
        val stored = profileDao.getAll()
        val existingByLink = stored.filter { it.isLink }.associateTo(HashMap()) { it.link to it.id }
        val existingChains = stored.mapNotNull { profile ->
            (profile.kind as? ServerKind.Chain)?.chain?.let { it to profile.id }
        }.toMap(HashMap())
        val newIds = HashMap<Long, Long>()
        var importedSelectedId: Long? = null
        var added = 0
        var skipped = 0
        for ((backup, profile) in profiles.filter { (_, it) -> it.isLink }) {
            val existingId = existingByLink[profile.link]
            val id = if (existingId != null) {
                skipped++
                existingId
            } else if (!insert) {
                continue
            } else {
                val parsed = checkNotNull(LinkParser.parse(profile.link))
                added++
                val subscriptionId = backup.subscriptionUrl.takeIf { it.isNotBlank() }?.let { subscriptionDao.obtain(it).id }
                profileDao.append(
                    profile.copy(
                        id = 0,
                        type = parsed.protocol.wire,
                        outboundJson = parsed.proxyJson(),
                        selected = false,
                        subscriptionId = subscriptionId
                    )
                ).also { existingByLink[profile.link] = it }
            }
            newIds[profile.id] = id
            if (profile.selected) importedSelectedId = id
        }
        for ((_, profile) in profiles) {
            val chain = (profile.kind as? ServerKind.Chain)?.chain ?: continue
            val remapped = chain.remap(newIds)
            val existingId = remapped?.let(existingChains::get)
            if (remapped == null || existingId != null || !insert) {
                existingId?.let { newIds[profile.id] = it }
                skipped++
                continue
            }
            val id = profileDao.append(profile.copy(id = 0, outboundJson = remapped.encode(), selected = false))
            existingChains[remapped] = id
            newIds[profile.id] = id
            if (profile.selected) importedSelectedId = id
            added++
        }
        if (insert) {
            restoreSubscriptionNames(backups)
            profileRepository.ensureSelection(preferredId = importedSelectedId)
        }
        return ProfilesImport(SectionResult(added, skipped), newIds)
    }

    private suspend fun restoreSubscriptionNames(backups: List<ProfileBackup>) {
        val subscriptionDao = database.subscriptionDao()
        backups.mapNotNull { backup -> backup.subscriptionName?.let { backup.subscriptionUrl to it } }
            .filter { (url, _) -> url.isNotBlank() }
            .distinctBy { (url, _) -> url }
            .forEach { (url, name) ->
                subscriptionDao.find(url)?.takeIf { it.name == null }?.let { subscriptionDao.rename(it.id, name) }
            }
    }

    private fun RoutingRuleEntity.identity() = Triple(type, value, target)
}
