package io.github.yulbax.erkin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_apps")
data class PendingApp(
    @PrimaryKey val packageName: String,
    val connectionType: ConnectionType,
    val serverId: Long? = null
)
