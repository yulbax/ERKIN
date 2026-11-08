package io.github.yulbax.erkin.data.profile

import io.github.yulbax.erkin.data.AppDatabase
import io.github.yulbax.erkin.data.BackupCodec
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.SettingsEntity
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
    val groups: Flow<List<ServerGroupEntity>> = database.serverGroupDao().observeAll()

    suspend fun add(raw: String, fileName: String?, target: AddTarget): ProfileOperationResult {
        val input = raw.trim()
        val parsed = LinkParser.parse(input)
        val fileTitle = fileName?.let(::nameOfFile)
        return when {
            parsed != null && !input.listsSeveralLinks() ->
                insert(listOf(fileTitle?.let { parsed.copy(name = it) } ?: parsed), target)
            input.isHttpUrl() -> refreshSubscription(input)
            else -> insert(SubscriptionParser.parseBody(input), fileTitle?.let { target.named(it) } ?: target)
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
        val subscription = database.serverGroupDao().get(id)?.kind as? GroupKind.Subscription
            ?: return ProfileOperationResult.Success(affected = 0)
        return refreshSubscription(subscription.url)
    }

    suspend fun renameGroup(id: Long, name: String) {
        val groups = database.serverGroupDao()
        val group = groups.get(id) ?: return
        val trimmed = name.trim().takeIf { it.isNotEmpty() }
        if (trimmed == null && group.kind == GroupKind.Local) return
        groups.rename(id, trimmed)
    }

    suspend fun moveToGroup(ids: Collection<Long>, target: GroupTarget) {
        database.transaction {
            val groups = database.serverGroupDao()
            val stored = groups.getAll().associateBy { it.id }
            val all = profileDao.getAll()
            val moving = all.filter { it.id in ids && it.canJoinGroup(it.groupId?.let(stored::get)) }
            if (moving.isEmpty()) return@transaction
            val groupId = when (target) {
                is GroupTarget.New -> groups.insert(ServerGroupEntity(name = target.name.trim()))
                is GroupTarget.Existing -> target.id.takeIf { stored[it]?.kind?.acceptsServers == true } ?: return@transaction
            }
            val movingIds = moving.mapTo(HashSet()) { it.id }
            profileDao.moveToGroup(movingIds.toList(), groupId)
            val rest = all.filterNot { it.id in movingIds }
            val anchor = rest.indexOfLast { it.groupId == groupId }.takeIf { it >= 0 }?.plus(1)
                ?: all.indexOfFirst { it.id in movingIds }
            profileDao.reorder(rest.take(anchor).map { it.id } + moving.map { it.id } + rest.drop(anchor).map { it.id })
            groups.deleteEmpty()
        }
    }

    suspend fun removeFromGroup(ids: Collection<Long>) {
        database.transaction {
            val stored = database.serverGroupDao().getAll().associateBy { it.id }
            val all = profileDao.getAll()
            val leaving = all.filter { profile ->
                profile.id in ids && profile.groupId?.let(stored::get)?.kind?.acceptsServers == true
            }
            if (leaving.isEmpty()) return@transaction
            val leavingIds = leaving.mapTo(HashSet()) { it.id }
            profileDao.moveToGroup(leavingIds.toList(), null)
            val lastStaying = all.filterNot { it.id in leavingIds }.filter { it.groupId != null }
                .associate { it.groupId to it.id }
            val leftBehind = leaving.groupBy { it.groupId }
            val order = all.flatMap { profile ->
                when {
                    profile.id in leavingIds -> if (lastStaying[profile.groupId] == null) listOf(profile.id) else emptyList()
                    lastStaying[profile.groupId] == profile.id -> listOf(profile.id) + leftBehind[profile.groupId].orEmpty().map { it.id }
                    else -> listOf(profile.id)
                }
            }
            profileDao.reorder(order)
            database.serverGroupDao().deleteEmpty()
        }
    }

    suspend fun dissolveGroup(id: Long) {
        database.transaction {
            val groups = database.serverGroupDao()
            if (groups.get(id)?.kind != GroupKind.Local) return@transaction
            profileDao.moveToGroup(profileDao.getAll().filter { it.groupId == id }.map { it.id }, null)
            groups.delete(id)
        }
    }

    private suspend fun sync(url: String, subscription: FetchedSubscription): ProfileOperationResult.SubscriptionSynced {
        val subscriptions = database.serverGroupDao()
        val stored = subscriptions.obtain(url)
        val all = profileDao.getAll()
        val members = all.filter { it.isLink && it.groupId == stored.id }
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
        clearServers(stale)

        val listed = orderedIds + kept.map { it.id }
        val groupIds = if (keepsOwnOrder()) {
            val memberIds = members.mapTo(LinkedHashSet()) { it.id }
            memberIds.filter { it in listed } + listed.filterNot { it in memberIds }
        } else {
            listed
        }
        val others = profileDao.getAll().map { it.id }.filterNot { it in groupIds }
        val anchor = all.indexOfFirst { it.groupId == stored.id }
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

    private suspend fun keepsOwnOrder(): Boolean =
        (database.settingsDao().getSettings() ?: SettingsEntity()).sortServersByPing

    suspend fun select(profile: ProfileEntity) {
        database.transaction {
            profileDao.selectExclusive(profile.id)
            profile.groupId?.let { database.serverGroupDao().updateCurrent(it, profile.id) }
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
            clearServers(profiles + orphanedChains)
            database.serverGroupDao().deleteEmpty()
            ensureSelection()
            if (profileDao.getAll().isEmpty()) {
                database.appDao().reassignConnectionType(ConnectionType.VPN, ConnectionType.DIRECT)
            }
        }
    }

    suspend fun unbindServers(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        database.transaction { clearServerIds(ids.toList()) }
    }

    private suspend fun clearServers(profiles: List<ProfileEntity>) = clearServerIds(profiles.map { it.id })

    private suspend fun clearServerIds(ids: List<Long>) {
        database.appDao().clearServers(ids)
        database.routingRuleDao().clearServers(ids)
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
            append(" group=").append(profile.groupId != null)
        }
    }

    suspend fun ensureSelection(preferredId: Long? = null) {
        if (profileDao.getSelected() != null) return
        val id = preferredId ?: profileDao.getAll().firstOrNull()?.id ?: return
        profileDao.selectExclusive(id)
    }

    private fun AddTarget.named(name: String): AddTarget = when (this) {
        is AddTarget.Main -> AddTarget.Main(newGroupName = name)
        is AddTarget.Group -> this
    }

    private suspend fun insert(profiles: List<ParsedProfile>, target: AddTarget): ProfileOperationResult {
        if (profiles.isEmpty()) return ProfileOperationResult.InvalidLink
        database.transaction {
            val groups = database.serverGroupDao()
            val groupId = when (target) {
                is AddTarget.Group -> target.id.takeIf { groups.get(it)?.kind?.acceptsServers == true }
                is AddTarget.Main -> if (profiles.size > 1) groups.insert(ServerGroupEntity(name = target.newGroupName)) else null
            }
            val ids = profileDao.appendAll(profiles.map { it.toEntity(groupId) })
            ensureSelection(preferredId = ids.firstOrNull())
        }
        return ProfileOperationResult.Success(affected = profiles.size)
    }

    private suspend fun fetchSubscription(url: String): FetchResult = try {
        FetchResult.Success(subscriptionSource.fetch(url))
    } catch (error: CancellationException) {
        throw error
    } catch (error: SubscriptionRefusedException) {
        FetchResult.Refused(error.refusal)
    } catch (error: Exception) {
        FetchResult.Failed(error)
    }

    private fun ParsedProfile.toEntity(groupId: Long? = null) = ProfileEntity(
        name = name,
        type = protocol.wire,
        link = link,
        outboundJson = proxyJson(),
        groupId = groupId
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

    private fun String.listsSeveralLinks(): Boolean {
        val lines = lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return lines.size > 1 && LinkParser.parse(lines.first()) != null
    }

    private fun String.serverKey(): String = trim().let { if ('\n' in it) it else it.substringBefore('#') }

    private sealed interface FetchResult {
        data class Success(val subscription: FetchedSubscription) : FetchResult
        data class Failed(val cause: Throwable) : FetchResult
        data class Refused(val refusal: SubscriptionRefusal) : FetchResult
    }

    private fun FetchResult.toOperationResult(): ProfileOperationResult = when (this) {
        is FetchResult.Success -> ProfileOperationResult.Success(affected = subscription.profiles.size)
        is FetchResult.Failed -> ProfileOperationResult.FetchFailed(cause)
        is FetchResult.Refused -> ProfileOperationResult.Refused(refusal)
    }
}
