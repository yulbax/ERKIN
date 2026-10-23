package io.github.yulbax.erkin.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM apps")
    fun getAllApps(): Flow<List<App>>

    @Query("SELECT * FROM apps")
    suspend fun getAllAppsSnapshot(): List<App>

    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    suspend fun getApp(packageName: String): App?

    @Upsert
    suspend fun upsertApp(app: App)

    @Upsert
    suspend fun upsertApps(apps: List<App>)

    @Query("UPDATE apps SET connectionType = :connectionType, serverId = :serverId WHERE packageName = :packageName")
    suspend fun updateConnectionType(
        packageName: String,
        connectionType: ConnectionType,
        serverId: Long?
    ): Int

    @Transaction
    suspend fun setConnectionTypes(apps: List<App>) {
        apps.forEach { app ->
            val updated = updateConnectionType(app.packageName, app.connectionType, app.serverId)
            if (updated == 0) upsertApp(app)
        }
    }

    @Query("UPDATE apps SET connectionType = :to, serverId = NULL WHERE connectionType = :from")
    suspend fun reassignConnectionType(from: ConnectionType, to: ConnectionType): Int

    @Query("UPDATE apps SET serverId = NULL WHERE serverId IN (:serverIds)")
    suspend fun clearAppServers(serverIds: List<Long>)

    @Query("UPDATE pending_apps SET serverId = NULL WHERE serverId IN (:serverIds)")
    suspend fun clearPendingServers(serverIds: List<Long>)

    @Transaction
    suspend fun clearServers(serverIds: List<Long>) {
        clearAppServers(serverIds)
        clearPendingServers(serverIds)
    }

    @Query("SELECT * FROM pending_apps WHERE packageName IN (:packageNames)")
    suspend fun getPending(packageNames: List<String>): List<PendingApp>

    @Upsert
    suspend fun upsertPending(apps: List<PendingApp>)

    @Query("DELETE FROM pending_apps WHERE packageName IN (:packageNames)")
    suspend fun deletePending(packageNames: List<String>)

    @Transaction
    suspend fun addApps(apps: List<App>) {
        if (apps.isEmpty()) return
        val pending = getPending(apps.map { it.packageName }).associateBy { it.packageName }
        upsertApps(apps.map { app ->
            pending[app.packageName]?.let { app.copy(connectionType = it.connectionType, serverId = it.serverId) } ?: app
        })
        if (pending.isNotEmpty()) deletePending(pending.keys.toList())
    }

    @Query("UPDATE apps SET path = :path WHERE packageName = :packageName")
    suspend fun updatePath(packageName: String, path: String?)

    @Query("DELETE FROM apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    @Query("DELETE FROM apps WHERE packageName IN (:packageNames)")
    suspend fun deleteApps(packageNames: List<String>)
}
