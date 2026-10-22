package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.BackupCodec
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.proxy.ParsedProfile
import io.github.yulbax.erkin.proxy.SubscriptionParser
import io.github.yulbax.erkin.util.AppLog
import io.github.yulbax.erkin.util.readBounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface SubscriptionProfileSource {
    suspend fun fetch(url: String): FetchedSubscription
}

class ProfileRepository(
    private val database: AppDatabase,
    private val profileDao: ProfileDao,
    private val subscriptionSource: SubscriptionProfileSource,
    private val log: AppLog
) {
    val profiles: Flow<List<ProfileEntity>> = profileDao.observeAll()
    val selected: Flow<ProfileEntity?> = profileDao.observeSelected()
    val subscriptions: Flow<List<SubscriptionEntity>> = database.subscriptionDao().observeAll()

    suspend fun add(raw: String, fileName: String? = null): ProfileOperationResult {
        val input = raw.trim()
        val parsed = LinkParser.parse(input)
        return when {
            parsed != null -> insertSingle(fileName?.let(::nameOfFile)?.let { parsed.copy(name = it) } ?: parsed)
            input.isHttpUrl() -> refreshSubscription(input)
            else -> insertAll(SubscriptionParser.parseBody(input))
        }
    }

    fun readConfigFile(open: () -> InputStream?): String? = runCatching {
        open()?.use { it.readBounded(MAX_CONFIG_FILE_BYTES) }?.decodeToString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun update(
        profile: ProfileEntity,
        name: String,
        link: String
    ): ProfileOperationResult {
        val trimmedLink = link.trim()
        if (trimmedLink.isNotEmpty() && trimmedLink != profile.link) {
            val parsed = LinkParser.parse(trimmedLink) ?: return ProfileOperationResult.InvalidLink
            profileDao.updateConfig(
                id = profile.id,
                name = name.trim().ifBlank { parsed.name },
                type = parsed.protocol.wire,
                link = trimmedLink,
                outboundJson = parsed.proxyJson()
            )
        } else {
            profileDao.updateName(profile.id, name.trim().ifBlank { profile.name })
        }
        return ProfileOperationResult.Success()
    }

    suspend fun refreshSubscription(url: String): ProfileOperationResult {
        val source = url.trim()
        if (source.isEmpty()) return ProfileOperationResult.Success(affected = 0)
        val fetched = fetchSubscription(source)
        if (fetched !is FetchResult.Success) return fetched.toOperationResult()
        if (fetched.subscription.profiles.isEmpty()) return ProfileOperationResult.EmptySubscription
        return database.transaction { sync(source, fetched.subscription) }
    }

    suspend fun refreshSubscription(id: Long): ProfileOperationResult {
        val stored = database.subscriptionDao().get(id) ?: return ProfileOperationResult.Success(affected = 0)
        return refreshSubscription(stored.url)
    }

    suspend fun renameSubscription(id: Long, name: String) {
        database.subscriptionDao().rename(id, name.trim().takeIf { it.isNotEmpty() })
    }

    private suspend fun sync(url: String, subscription: FetchedSubscription): ProfileOperationResult.SubscriptionSynced {
        val subscriptions = database.subscriptionDao()
        val stored = subscriptions.obtain(url)
        val all = profileDao.getAll()
        val members = all.filter { it.isLink && it.subscriptionId == stored.id }
        val unmatched = members.toMutableList()
        val matches = arrayOfNulls<ProfileEntity>(subscription.profiles.size)
        subscription.profiles.forEachIndexed { index, fresh ->
            val key = fresh.link.serverKey()
            unmatched.firstOrNull { it.link.serverKey() == key }?.let {
                matches[index] = it
                unmatched -= it
            }
        }
        subscription.profiles.forEachIndexed { index, fresh ->
            if (matches[index] != null) return@forEachIndexed
            val sameName = subscription.profiles.indices.count { matches[it] == null && subscription.profiles[it].name == fresh.name }
            val candidate = unmatched.singleOrNull { it.name == fresh.name }
            if (sameName == 1 && candidate != null) {
                matches[index] = candidate
                unmatched -= candidate
            }
        }

        var updated = 0
        val orderedIds = subscription.profiles.mapIndexed { index, fresh ->
            val match = matches[index]
            if (match == null) {
                profileDao.append(fresh.toEntity(stored.id))
            } else {
                val descriptor = fresh.proxyJson()
                if (match.link != fresh.link || match.outboundJson != descriptor || match.type != fresh.protocol.wire) {
                    profileDao.updateConfig(match.id, match.name, fresh.protocol.wire, fresh.link, descriptor)
                    updated++
                }
                match.id
            }
        }
        val chained = all.mapNotNull { (it.kind as? ServerKind.Chain)?.chain }
        val (kept, stale) = unmatched.partition { profile -> profile.selected || chained.any { it.uses(profile.id) } }
        profileDao.deleteMany(stale)

        val groupIds = orderedIds + kept.map { it.id }
        val others = profileDao.getAll().map { it.id }.filterNot { it in groupIds }
        val anchor = all.indexOfFirst { it.subscriptionId == stored.id }
            .let { first -> if (first < 0) others.size else all.take(first).count { it.id in others } }
        profileDao.reorder(others.take(anchor) + groupIds + others.drop(anchor))
        ensureSelection(preferredId = orderedIds.firstOrNull())

        val usage = subscription.info.usage
        subscriptions.update(
            stored.copy(
                title = subscription.info.title,
                upload = usage?.upload,
                download = usage?.download,
                total = usage?.total,
                expire = usage?.expire,
                updatedAt = System.currentTimeMillis()
            )
        )
        return ProfileOperationResult.SubscriptionSynced(
            added = matches.count { it == null },
            updated = updated,
            removed = stale.size,
            selectedDropped = kept.any { it.selected }
        )
    }

    suspend fun reorder(ids: List<Long>) {
        profileDao.reorder(ids)
    }

    suspend fun select(profile: ProfileEntity) {
        database.transaction {
            profileDao.selectExclusive(profile.id)
            profile.subscriptionId?.let { database.subscriptionDao().updateCurrent(it, profile.id) }
        }
    }

    suspend fun saveChain(existing: ProfileEntity?, name: String, chain: ProxyChain): Boolean {
        val servers = profileDao.getAll().filter { it.isLink }.associateBy { it.id }
        if (!chain.isValid || chain.serverIds.any { it !in servers }) return false
        val hopNames = chain.hops.map { hop ->
            when (hop) {
                ChainEntry.ByeDpi -> CHAIN_BYEDPI_NAME
                is ChainEntry.Server -> servers.getValue(hop.profileId).name
            }
        }
        val title = name.trim().ifBlank { (hopNames + servers.getValue(chain.exitId).name).joinToString(" → ") }
        database.transaction {
            if (existing == null) {
                val id = profileDao.append(
                    ProfileEntity(name = title, type = ProxyChain.TYPE, link = "", outboundJson = chain.encode())
                )
                ensureSelection(preferredId = id)
            } else {
                profileDao.updateConfig(existing.id, title, ProxyChain.TYPE, "", chain.encode())
            }
        }
        return true
    }

    suspend fun delete(profiles: List<ProfileEntity>) {
        database.transaction {
            val ids = profiles.mapTo(HashSet()) { it.id }
            val orphanedChains = profileDao.getAll().filter { profile ->
                profile.id !in ids && (profile.kind as? ServerKind.Chain)?.chain?.let { chain -> ids.any(chain::uses) } == true
            }
            profileDao.deleteMany(profiles + orphanedChains)
            database.subscriptionDao().deleteEmpty()
            ensureSelection()
            if (profileDao.getAll().isEmpty()) {
                database.appDao().reassignConnectionType(ConnectionType.VPN, ConnectionType.DIRECT)
            }
        }
    }

    suspend fun refreshDescriptors() {
        val rebuilt = mutableListOf<Long>()
        val unparseable = mutableListOf<Long>()
        database.transaction {
            profileDao.getAll().filter { it.isLink }.forEach { profile ->
                val parsed = LinkParser.parse(profile.link)
                if (parsed == null) {
                    unparseable += profile.id
                    return@forEach
                }
                val descriptor = parsed.proxyJson()
                if (descriptor == profile.outboundJson && parsed.protocol.wire == profile.type) return@forEach
                profileDao.updateConfig(
                    id = profile.id,
                    name = profile.name,
                    type = parsed.protocol.wire,
                    link = profile.link,
                    outboundJson = descriptor
                )
                rebuilt += profile.id
            }
        }
        if (rebuilt.isNotEmpty()) log.i(TAG, "descriptors rebuilt for profiles $rebuilt")
        if (unparseable.isNotEmpty()) {
            log.w(TAG, "profiles $unparseable keep a stale descriptor: their link no longer parses")
        }
    }

    suspend fun profileDiagnostics(): List<String> = profileDao.getAll().map { profile ->
        when (profile.kind) {
            is ServerKind.Chain -> return@map "id=${profile.id} type=chain chain=${profile.outboundJson} selected=${profile.selected}"
            is ServerKind.Link -> Unit
        }
        val parsed = LinkParser.parse(profile.link)
        buildString {
            append("id=").append(profile.id)
            append(" type=").append(profile.type)
            if (profile.protocol == null) append(" (unknown to this build)")
            append(" parses=").append(parsed != null)
            if (parsed != null) append(" fresh=").append(parsed.proxyJson() == profile.outboundJson)
            append(" selected=").append(profile.selected)
            append(" subscription=").append(profile.subscriptionId != null)
        }
    }

    suspend fun ensureSelection(preferredId: Long? = null) {
        if (profileDao.getSelected() != null) return
        val id = preferredId ?: profileDao.getAll().firstOrNull()?.id ?: return
        profileDao.selectExclusive(id)
    }

    private suspend fun insertSingle(profile: ParsedProfile): ProfileOperationResult {
        database.transaction {
            val id = profileDao.append(profile.toEntity())
            ensureSelection(preferredId = id)
        }
        return ProfileOperationResult.Success()
    }

    private suspend fun insertAll(profiles: List<ParsedProfile>): ProfileOperationResult {
        if (profiles.isEmpty()) return ProfileOperationResult.InvalidLink
        database.transaction {
            val ids = profileDao.appendAll(profiles.map { it.toEntity() })
            ensureSelection(preferredId = ids.firstOrNull())
        }
        return ProfileOperationResult.Success(affected = profiles.size)
    }

    private suspend fun fetchSubscription(url: String): FetchResult = try {
        FetchResult.Success(subscriptionSource.fetch(url))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        FetchResult.Failed(error)
    }

    private fun ParsedProfile.toEntity(subscriptionId: Long? = null) = ProfileEntity(
        name = name,
        type = protocol.wire,
        link = link,
        outboundJson = proxyJson(),
        subscriptionId = subscriptionId
    )

    private companion object {
        const val TAG = "ProfileRepository"
        const val CHAIN_BYEDPI_NAME = "ByeDPI"
        const val MAX_CONFIG_FILE_BYTES = 1L shl 20
    }

    private fun nameOfFile(fileName: String): String? {
        val name = fileName.trim()
        val dot = name.lastIndexOf('.')
        return (if (dot > 0) name.substring(0, dot) else name).trim().takeIf { it.isNotEmpty() }
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun String.serverKey(): String = trim().let { if ('\n' in it) it else it.substringBefore('#') }

    private sealed interface FetchResult {
        data class Success(val subscription: FetchedSubscription) : FetchResult
        data class Failed(val cause: Throwable) : FetchResult
    }

    private fun FetchResult.toOperationResult(): ProfileOperationResult = when (this) {
        is FetchResult.Success -> ProfileOperationResult.Success(affected = subscription.profiles.size)
        is FetchResult.Failed -> ProfileOperationResult.FetchFailed(cause)
    }
}
