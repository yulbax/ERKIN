package io.github.yulbax.erkin.data.profile

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.yulbax.erkin.proxy.ProxyProtocol

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val link: String,
    val outboundJson: String,
    val selected: Boolean = false,
    val subscriptionUrl: String = ""
) {
    val protocol: ProxyProtocol? get() = ProxyProtocol.fromWire(type)
}
