package io.github.yulbax.erkin.data.profile

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.yulbax.erkin.proxy.ProxyProtocol

@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subscriptionId")]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val link: String = "",
    val outboundJson: String = "",
    val selected: Boolean = false,
    val subscriptionId: Long? = null,
    val position: Int = 0
) {
    val protocol: ProxyProtocol? get() = ProxyProtocol.fromWire(type)
}
