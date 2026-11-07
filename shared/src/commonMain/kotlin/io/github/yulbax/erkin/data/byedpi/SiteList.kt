package io.github.yulbax.erkin.data.byedpi

import io.github.yulbax.erkin.vpn.SiteGroup
import io.github.yulbax.erkin.vpn.SiteListPreset
import io.github.yulbax.erkin.vpn.core.RuleValues
import kotlinx.serialization.Serializable

sealed interface SiteListKind {
    data class BuiltIn(val preset: SiteListPreset, val modified: Boolean) : SiteListKind
    data object Custom : SiteListKind
}

data class SiteList(val id: Long, val kind: SiteListKind, val name: String, val sites: List<String>, val active: Boolean) {
    val group: SiteGroup get() = SiteGroup(name, sites)
    val resettable: Boolean get() = (kind as? SiteListKind.BuiltIn)?.modified == true
    val deletable: Boolean get() = kind is SiteListKind.Custom
}

data class SiteListDraft(val name: String, val text: String) {
    val sites: List<String> = SiteHosts.parse(text)
    val valid: Boolean get() = name.isNotBlank() && name.length <= MAX_NAME_LENGTH && sites.isNotEmpty()

    companion object {
        const val MAX_NAME_LENGTH = 64

        fun of(list: SiteList) = SiteListDraft(list.name, SiteHosts.format(list.sites))
    }
}

@Serializable
data class SiteListBackup(
    val preset: String? = null,
    val name: String? = null,
    val sites: List<String>? = null,
    val active: Boolean = false
)

object SiteHosts {
    const val MAX_SITES = 1_000
    private val SEPARATORS = Regex("[\\s,;]+")

    fun parse(text: String): List<String> =
        text.split(SEPARATORS).mapNotNull(::normalize).distinct().take(MAX_SITES)

    fun normalize(raw: String): String? = RuleValues.Domain.normalize(raw)?.takeIf { '.' in it }

    fun format(sites: List<String>): String = sites.joinToString("\n")

    fun stored(text: String?): List<String>? = text?.lines()?.filter { it.isNotBlank() }
}

fun List<SiteList>.canDisable(list: SiteList): Boolean = !list.active || count { it.active } > 1

internal fun List<SiteListEntity>.toSiteLists(): List<SiteList> =
    mapNotNull { it.toSiteList() }.sortedWith(compareBy({ it.order }, { it.id }))

private val SiteList.order: Int get() = when (val kind = kind) {
    is SiteListKind.BuiltIn -> kind.preset.ordinal
    SiteListKind.Custom -> SiteListPreset.entries.size
}

private fun SiteListEntity.toSiteList(): SiteList? {
    if (preset == null) return SiteList(id, SiteListKind.Custom, name.orEmpty(), SiteHosts.stored(sites).orEmpty(), active)
    val builtIn = SiteListPreset.fromWire(preset) ?: return null
    return SiteList(
        id = id,
        kind = SiteListKind.BuiltIn(builtIn, modified = name != null || sites != null),
        name = name ?: builtIn.title,
        sites = SiteHosts.stored(sites) ?: builtIn.sites,
        active = active
    )
}
