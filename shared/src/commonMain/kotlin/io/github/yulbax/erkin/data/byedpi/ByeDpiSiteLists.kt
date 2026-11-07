package io.github.yulbax.erkin.data.byedpi

import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.vpn.SiteGroup
import io.github.yulbax.erkin.vpn.SiteListPreset
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ByeDpiSiteLists(
    private val database: AppDatabase,
    private val language: () -> String = { Locale.getDefault().language }
) {
    private val dao: SiteListDao get() = database.siteListDao()
    private val seeding = Mutex()

    @Volatile
    private var seeded = false

    val lists: Flow<List<SiteList>> = flow {
        ensureSeeded()
        emitAll(dao.observe())
    }.map { it.toSiteLists() }

    suspend fun active(): List<SiteGroup> {
        ensureSeeded()
        return dao.getAll().toSiteLists().filter { it.active }.map { it.group }
    }

    suspend fun setActive(id: Long, active: Boolean) = change { lists ->
        val list = lists.firstOrNull { it.id == id } ?: return@change
        if (active || lists.canDisable(list)) dao.setActive(id, active)
    }

    suspend fun add(draft: SiteListDraft) = change {
        if (draft.valid) dao.insert(SiteListEntity(name = draft.name.trim(), sites = SiteHosts.format(draft.sites), active = true))
    }

    suspend fun edit(id: Long, draft: SiteListDraft) = change { lists ->
        if (!draft.valid) return@change
        val list = lists.firstOrNull { it.id == id } ?: return@change
        val name = draft.name.trim()
        val kind = list.kind
        if (kind is SiteListKind.BuiltIn && name == kind.preset.title && draft.sites == kind.preset.sites) {
            dao.edit(id, null, null)
        } else {
            dao.edit(id, name, SiteHosts.format(draft.sites))
        }
    }

    suspend fun reset(id: Long) = change { lists ->
        if (lists.firstOrNull { it.id == id }?.resettable == true) dao.edit(id, null, null)
    }

    suspend fun delete(id: Long) = change { lists ->
        val list = lists.firstOrNull { it.id == id } ?: return@change
        if (list.deletable && lists.canDisable(list)) dao.deleteCustom(id)
    }

    suspend fun backup(): List<SiteListBackup> {
        ensureSeeded()
        return dao.getAll().map { SiteListBackup(it.preset, it.name, SiteHosts.stored(it.sites), it.active) }
    }

    private suspend fun change(block: suspend (List<SiteList>) -> Unit) {
        ensureSeeded()
        database.transaction { block(dao.getAll().toSiteLists()) }
    }

    suspend fun restoreWithinTransaction(backups: List<SiteListBackup>) {
        val stored = dao.getAll()
        val presets = stored.filter { it.preset != null }.associateBy { it.preset }
        val custom = stored.filter { it.preset == null }.associateBy { it.name }
        for (backup in backups) {
            val sites = backup.sites?.mapNotNull(SiteHosts::normalize)?.distinct()?.takeIf { it.isNotEmpty() }?.let(SiteHosts::format)
            if (backup.preset != null) {
                if (SiteListPreset.fromWire(backup.preset) == null) continue
                val existing = presets[backup.preset]
                if (existing == null) {
                    dao.insert(SiteListEntity(preset = backup.preset, name = backup.name, sites = sites, active = backup.active))
                } else {
                    dao.replace(existing.id, backup.name, sites, backup.active)
                }
            } else {
                val name = backup.name?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                if (sites == null) continue
                val existing = custom[name]
                if (existing == null) {
                    dao.insert(SiteListEntity(name = name, sites = sites, active = backup.active))
                } else {
                    dao.replace(existing.id, name, sites, backup.active)
                }
            }
        }
        val restored = dao.getAll()
        if (restored.none { it.active }) {
            restored.filter { SiteListPreset.fromWire(it.preset) in SiteListPreset.UPGRADE_DEFAULTS }.forEach { dao.setActive(it.id, true) }
        }
    }

    private suspend fun ensureSeeded() {
        if (seeded) return
        seeding.withLock {
            if (seeded) return
            database.transaction {
                val stored = dao.getAll()
                val defaults = if (stored.isEmpty()) SiteListPreset.defaultsFor(language()) else emptySet()
                val known = stored.mapNotNullTo(HashSet()) { it.preset }
                dao.insertMissing(
                    SiteListPreset.entries.filter { it.wire !in known }.map { SiteListEntity(preset = it.wire, active = it in defaults) }
                )
            }
            seeded = true
        }
    }
}
