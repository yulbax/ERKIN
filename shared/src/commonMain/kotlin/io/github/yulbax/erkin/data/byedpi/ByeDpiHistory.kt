package io.github.yulbax.erkin.data.byedpi

import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.SettingsRepository
import io.github.yulbax.erkin.vpn.SiteResult
import io.github.yulbax.erkin.vpn.core.ByeDpiArgs
import io.github.yulbax.erkin.vpn.core.TestNetwork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ByeDpiHistory(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val dao: ByeDpiHistoryDao get() = database.byeDpiHistoryDao()

    val entries: Flow<List<ByeDpiArgsEntity>> get() = dao.observeArgs()
    val results: Flow<List<ByeDpiSiteResultEntity>> get() = dao.observeResults()

    suspend fun apply(line: String) {
        val stored = line.trim()
        settingsRepository.update { it.copy(byeDpiArgs = stored) }
        record(ByeDpiArgs.effective(stored), markUsed = true)
    }

    suspend fun remember(line: String) = record(ByeDpiArgs.effective(line), markUsed = false)

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun saveResults(line: String, network: TestNetwork, results: List<SiteResult>) {
        val testedAt = now()
        database.transaction {
            val id = idOf(ByeDpiArgs.effective(line))
            dao.clearResults(id, network.wire)
            dao.insertResults(
                results.map { ByeDpiSiteResultEntity(id, network.wire, it.host, it.group, it.reachable, testedAt) }
            )
        }
    }

    private suspend fun record(args: String, markUsed: Boolean) {
        val active = ByeDpiArgs.effective(settingsRepository.settings.first().byeDpiArgs)
        database.transaction {
            val existing = dao.find(args)
            if (existing == null) {
                dao.insert(ByeDpiArgsEntity(args = args, lastUsedAt = now()))
            } else if (markUsed) {
                dao.markUsed(existing.id, now())
            }
            dao.trimUnpinned(MAX_UNPINNED, active)
        }
    }

    private suspend fun idOf(args: String): Long =
        dao.find(args)?.id ?: dao.insert(ByeDpiArgsEntity(args = args, lastUsedAt = now()))

    companion object {
        const val MAX_UNPINNED = 30
    }
}
