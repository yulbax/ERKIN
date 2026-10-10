package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.proxy.LinkParser
import io.github.yulbax.erkin.vpn.core.Ipv6Mode
import io.github.yulbax.erkin.vpn.core.NetworkOptions
import io.github.yulbax.erkin.vpn.core.TlsFingerprint
import io.github.yulbax.erkin.vpn.core.TunStack
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object BackupCodec {
    private const val MAX_PACKAGE_NAME_LENGTH = 255
    private const val MAX_PROFILE_NAME_LENGTH = 512
    private const val MAX_LINK_LENGTH = 16 * 1024
    private const val MAX_OUTBOUND_LENGTH = 256 * 1024
    private const val MAX_BYEDPI_ARGS_LENGTH = 16 * 1024
    private const val MAX_DNS_LENGTH = 255

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(backup: AppConfigBackup): String =
        json.encodeToString(AppConfigBackup.serializer(), backup)

    fun decode(text: String): AppConfigBackup? =
        runCatching { json.decodeFromString(AppConfigBackup.serializer(), text) }
            .getOrNull()
            ?.takeIf(::isValid)

    fun read(input: InputStream): AppConfigBackup? =
        runCatching { input.readBounded(AppConfigBackup.MAX_IMPORT_BYTES).decodeToString() }
            .getOrNull()
            ?.let(::decode)

    fun isValid(backup: AppConfigBackup): Boolean {
        if (backup.version !in AppConfigBackup.MIN_SUPPORTED_VERSION..AppConfigBackup.CURRENT_VERSION) {
            return false
        }
        if (backup.apps == null && backup.settings == null && backup.profiles == null) return false
        if ((backup.apps?.size ?: 0) > AppConfigBackup.MAX_ENTRIES_PER_SECTION) return false
        if ((backup.profiles?.size ?: 0) > AppConfigBackup.MAX_ENTRIES_PER_SECTION) return false
        if ((backup.profiles?.count { it.selected } ?: 0) > 1) return false
        if (backup.apps?.any(::isInvalidAppBackup) == true) return false
        if (backup.settings?.let(::isValidSettingsBackup) == false) return false
        if (backup.profiles?.any { !isValidProfileBackup(it) } == true) return false
        return true
    }

    internal fun InputStream.readBounded(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) { "Backup exceeds ${maxBytes / 1024 / 1024} MB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun isInvalidAppBackup(entry: Map.Entry<String, String>): Boolean {
        val packageName = entry.key
        return packageName.isBlank() ||
            packageName.length > MAX_PACKAGE_NAME_LENGTH ||
            packageName.any(Char::isWhitespace) ||
            ConnectionType.fromWire(entry.value) == null
    }

    private fun isValidSettingsBackup(settings: BackupSettings): Boolean =
        settings.mtu in NetworkOptions.MTU_RANGE &&
            settings.byeDpiArgs.length <= MAX_BYEDPI_ARGS_LENGTH &&
            TunStack.entries.any { it.wire == settings.tunStack } &&
            Ipv6Mode.entries.any { it.wire == settings.ipv6Mode } &&
            isValidDns(settings.dnsRemote) &&
            isValidDns(settings.dnsDirect) &&
            (settings.newAppsConnectionType.isBlank() || ConnectionType.fromWire(settings.newAppsConnectionType) != null) &&
            (settings.preferredFingerprint.isBlank() ||
                TlsFingerprint.fromWire(settings.preferredFingerprint) != null)

    private fun isValidDns(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_DNS_LENGTH && value.none(Char::isWhitespace)

    private fun isValidProfileBackup(profile: BackupProfile): Boolean {
        if (profile.name.isBlank() || profile.name.length > MAX_PROFILE_NAME_LENGTH) return false
        if (profile.link.isBlank() || profile.link.length > MAX_LINK_LENGTH) return false
        if (profile.outboundJson.isBlank() || profile.outboundJson.length > MAX_OUTBOUND_LENGTH) return false
        if (!isValidSubscriptionUrl(profile.subscriptionUrl)) return false

        val parsed = LinkParser.parse(profile.link) ?: return false
        if (parsed.protocol.wire != profile.type) return false
        val outbound = runCatching { json.parseToJsonElement(profile.outboundJson) as? JsonObject }
            .getOrNull() ?: return false
        val type = runCatching { outbound["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        val handler = parsed.protocol.handler
        val server = handler.server(outbound) ?: return false
        return type == parsed.outbound["type"]?.jsonPrimitive?.contentOrNull &&
            server == handler.server(parsed.outbound) &&
            server.host.isNotBlank() &&
            server.port in 1..65535
    }

    private fun isValidSubscriptionUrl(value: String): Boolean {
        if (value.isBlank()) return true
        if (value.length > MAX_LINK_LENGTH) return false
        return runCatching {
            val uri = URI(value)
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.rawAuthority.isNullOrBlank()
        }.getOrDefault(false)
    }
}
