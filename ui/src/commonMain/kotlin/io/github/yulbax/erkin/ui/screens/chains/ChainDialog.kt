package io.github.yulbax.erkin.ui.screens.chains

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.yulbax.erkin.data.profile.ChainEntry
import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.ProxyChain
import io.github.yulbax.erkin.data.profile.ServerKind
import io.github.yulbax.erkin.data.profile.isLink
import io.github.yulbax.erkin.data.profile.kind
import io.github.yulbax.erkin.ui.components.AppDialog
import io.github.yulbax.erkin.ui.components.DropdownSetting
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChainDialog(
    initial: ProfileEntity?,
    profiles: List<ProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, chain: ProxyChain) -> Unit,
    onDelete: (() -> Unit)?
) {
    val servers = profiles.filter { it.isLink }
    val start = (initial?.kind as? ServerKind.Chain)?.chain
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var hops by remember { mutableStateOf(start?.hops ?: listOf(ChainEntry.ByeDpi)) }
    var exitId by remember { mutableStateOf(start?.exitId ?: servers.firstOrNull()?.id) }
    val byeDpiLabel = stringResource(Res.string.chain_byedpi)
    val unnamed = stringResource(Res.string.unnamed_profile)
    fun serverName(id: Long?): String = servers.firstOrNull { it.id == id }?.name?.ifEmpty { unnamed } ?: "—"
    fun hopLabel(hop: ChainEntry): String = when (hop) {
        ChainEntry.ByeDpi -> byeDpiLabel
        is ChainEntry.Server -> serverName(hop.profileId)
    }
    val chain = exitId?.let { ProxyChain(hops, it) }
    val valid = chain != null && chain.isValid && chain.serverIds.all { id -> servers.any { it.id == id } }
    val unused = servers.firstOrNull { server -> chain?.uses(server.id) != true }
    fun hopServers(except: Int? = null): Set<Long> =
        hops.filterIndexed { i, _ -> i != except }.mapNotNullTo(HashSet()) { (it as? ChainEntry.Server)?.profileId }
    fun hopOptions(index: Int): List<ChainEntry> {
        val taken = hopServers(except = index) + listOfNotNull(exitId)
        return servers.filter { it.id !in taken }.map { ChainEntry.Server(it.id) }
    }

    AppDialog(
        title = stringResource(if (initial == null) Res.string.new_chain else Res.string.edit_chain),
        onDismiss = onDismiss,
        buttons = {
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.delete)) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(onClick = { chain?.let { onSave(name, it) } }, enabled = valid) {
                Text(stringResource(Res.string.save))
            }
        }
    ) {
        GroupCard(
            itemColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            items = buildList {
                add {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.name_label)) },
                        placeholder = { Text(stringResource(Res.string.chain_name_hint)) },
                        singleLine = true,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                hops.forEachIndexed { index, hop ->
                    add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                DropdownSetting(
                                    label = stringResource(if (index == 0) Res.string.chain_entry else Res.string.chain_via),
                                    options = if (index == 0) listOf<ChainEntry>(ChainEntry.ByeDpi) + hopOptions(index) else hopOptions(index),
                                    selected = hop,
                                    optionLabel = ::hopLabel,
                                    onSelect = { selected -> hops = hops.toMutableList().also { it[index] = selected } }
                                )
                            }
                            if (index > 0) {
                                IconButton(onClick = { hops = hops.filterIndexed { i, _ -> i != index } }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.chain_remove_hop))
                                }
                            }
                        }
                    }
                }
                add {
                    DropdownSetting(
                        label = stringResource(Res.string.chain_exit),
                        options = servers.map { it.id }.filterNot { it in hopServers() },
                        selected = exitId ?: -1L,
                        optionLabel = ::serverName,
                        onSelect = { exitId = it }
                    )
                }
            }
        )
        if (unused != null) {
            TextButton(onClick = { hops = hops + ChainEntry.Server(unused.id) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.chain_add_hop))
            }
        }
    }
}

@Composable
internal fun chainLabel(profile: ProfileEntity, profiles: List<ProfileEntity>): String? {
    val chain = (profile.kind as? ServerKind.Chain)?.chain ?: return null
    val unnamed = stringResource(Res.string.unnamed_profile)
    fun nameOf(id: Long) = profiles.firstOrNull { it.id == id }?.name?.ifEmpty { unnamed } ?: "—"
    val byeDpi = stringResource(Res.string.chain_byedpi)
    val hops = chain.hops.map { hop ->
        when (hop) {
            ChainEntry.ByeDpi -> byeDpi
            is ChainEntry.Server -> nameOf(hop.profileId)
        }
    }
    return (hops + nameOf(chain.exitId)).joinToString(" → ")
}
