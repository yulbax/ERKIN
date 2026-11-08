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
            entity = ServerGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val link: String = "",
    val outboundJson: String = "",
    val selected: Boolean = false,
    val groupId: Long? = null,
    val position: Int = 0
) {
    val protocol: ProxyProtocol? get() = ProxyProtocol.fromWire(type)
}
