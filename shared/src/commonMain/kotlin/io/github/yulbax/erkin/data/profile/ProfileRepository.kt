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
    suspend fun fetch(url: String): List<ParsedProfile>
}

class ProfileRepository(
    private val database: AppDatabase,
    private val profileDao: ProfileDao,
    private val subscriptionSource: SubscriptionProfileSource,
    private val log: AppLog
) {
    val profiles: Flow<List<ProfileEntity>> = profileDao.observeAll()
    val selected: Flow<ProfileEntity?> = profileDao.observeSelected()

    suspend fun add(raw: String): ProfileOperationResult {
        val input = raw.trim()
        val parsed = LinkParser.parse(input)
        return when {
            parsed != null -> insertSingle(parsed)
            input.isHttpUrl() -> importSubscription(input)
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

    suspend fun refreshSubscription(profile: ProfileEntity): ProfileOperationResult {
        val url = profile.subscriptionUrl.trim()
        if (url.isEmpty()) return ProfileOperationResult.Success(affected = 0)
        val parsed = fetchSubscription(url)
        if (parsed !is FetchResult.Success) return parsed.toOperationResult()
        if (parsed.profiles.isEmpty()) return ProfileOperationResult.EmptySubscription

        val serverKey = profile.link.serverKey()
        val fresh = parsed.profiles.firstOrNull { it.link.serverKey() == serverKey }
            ?: parsed.profiles.singleOrNull { it.name == profile.name }
            ?: return ProfileOperationResult.Success(affected = 0)
        profileDao.updateConfig(
            id = profile.id,
            name = profile.name,
            type = fresh.protocol.wire,
            link = fresh.link,
            outboundJson = fresh.proxyJson()
        )
        return ProfileOperationResult.Success()
    }

    suspend fun reorder(ids: List<Long>) {
        profileDao.reorder(ids)
    }

    suspend fun select(profile: ProfileEntity) {
        profileDao.selectExclusive(profile.id)
    }

    suspend fun delete(profiles: List<ProfileEntity>) {
        database.transaction {
            profileDao.deleteMany(profiles)
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
            profileDao.getAll().forEach { profile ->
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
        val parsed = LinkParser.parse(profile.link)
        buildString {
            append("id=").append(profile.id)
            append(" type=").append(profile.type)
            if (profile.protocol == null) append(" (unknown to this build)")
            append(" parses=").append(parsed != null)
            if (parsed != null) append(" fresh=").append(parsed.proxyJson() == profile.outboundJson)
            append(" selected=").append(profile.selected)
            append(" subscription=").append(profile.subscriptionUrl.isNotEmpty())
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

    private suspend fun importSubscription(url: String): ProfileOperationResult {
        val parsed = fetchSubscription(url)
        if (parsed !is FetchResult.Success) return parsed.toOperationResult()
        if (parsed.profiles.isEmpty()) return ProfileOperationResult.EmptySubscription
        return insertAll(parsed.profiles, subscriptionUrl = url)
    }

    private suspend fun insertAll(profiles: List<ParsedProfile>, subscriptionUrl: String = ""): ProfileOperationResult {
        if (profiles.isEmpty()) return ProfileOperationResult.InvalidLink
        database.transaction {
            val ids = profileDao.appendAll(profiles.map { it.toEntity(subscriptionUrl) })
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

    private fun ParsedProfile.toEntity(subscriptionUrl: String = "") = ProfileEntity(
        name = name,
        type = protocol.wire,
        link = link,
        outboundJson = proxyJson(),
        subscriptionUrl = subscriptionUrl
    )

    private companion object {
        const val TAG = "ProfileRepository"
        const val MAX_CONFIG_FILE_BYTES = 1L shl 20
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun String.serverKey(): String = trim().let { if ('\n' in it) it else it.substringBefore('#') }

    private sealed interface FetchResult {
        data class Success(val profiles: List<ParsedProfile>) : FetchResult
        data class Failed(val cause: Throwable) : FetchResult
    }

    private fun FetchResult.toOperationResult(): ProfileOperationResult = when (this) {
        is FetchResult.Success -> ProfileOperationResult.Success(affected = profiles.size)
        is FetchResult.Failed -> ProfileOperationResult.FetchFailed(cause)
    }
}
