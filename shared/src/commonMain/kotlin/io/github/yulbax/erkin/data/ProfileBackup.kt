package io.github.yulbax.erkin.data

import io.github.yulbax.erkin.data.profile.ProfileEntity
import kotlinx.serialization.Serializable

@Serializable
data class ProfileBackup(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val link: String = "",
    val outboundJson: String = "",
    val selected: Boolean = false,
    val subscriptionUrl: String = ""
) {
    fun toEntity(subscriptionId: Long? = null) = ProfileEntity(
        id = id,
        name = name,
        type = type,
        link = link,
        outboundJson = outboundJson,
        selected = selected,
        subscriptionId = subscriptionId
    )

    companion object {
        fun of(profile: ProfileEntity, subscriptionUrl: String?) = ProfileBackup(
            id = profile.id,
            name = profile.name,
            type = profile.type,
            link = profile.link,
            outboundJson = profile.outboundJson,
            selected = profile.selected,
            subscriptionUrl = subscriptionUrl.orEmpty()
        )
    }
}
