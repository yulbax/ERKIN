package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileEntity
import io.github.yulbax.erkin.data.profile.GroupKind
import io.github.yulbax.erkin.data.profile.ServerGroupEntity
import kotlinx.serialization.Serializable

@Serializable
data class ProfileBackup(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val link: String = "",
    val outboundJson: String = "",
    val selected: Boolean = false,
    val subscriptionUrl: String = "",
    val subscriptionName: String? = null,
    val groupId: Long? = null,
    val remembered: Boolean = false
) {
    fun toEntity(groupId: Long? = null) = ProfileEntity(
        id = id,
        name = name,
        type = type,
        link = link,
        outboundJson = outboundJson,
        selected = selected,
        groupId = groupId
    )

    companion object {
        fun of(profile: ProfileEntity, group: ServerGroupEntity?) = ProfileBackup(
            id = profile.id,
            name = profile.name,
            type = profile.type,
            link = profile.link,
            outboundJson = profile.outboundJson,
            selected = profile.selected,
            subscriptionUrl = group?.url.orEmpty(),
            subscriptionName = group?.name,
            groupId = group?.takeIf { it.kind == GroupKind.Local }?.id,
            remembered = group?.currentId == profile.id
        )
    }
}
