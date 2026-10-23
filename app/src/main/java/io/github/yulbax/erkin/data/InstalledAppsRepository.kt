package io.github.yulbax.erkin.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(createdAtStart = true, binds = [InstalledAppsSource::class])
class InstalledAppsRepository(
    context: Context,
    private val appDao: AppDao,
    private val settingsDao: SettingsDao
) : InstalledAppsSource {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (packageName == appContext.packageName) return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

            scope.launch {
                try {
                    when (intent.action) {
                        Intent.ACTION_PACKAGE_ADDED -> addOrUpdatePackage(packageName)
                        Intent.ACTION_PACKAGE_FULLY_REMOVED -> if (!replacing) removePackage(packageName)
                    }
                    _error.value = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _error.value = error.message ?: "Failed to update apps"
                }
            }
        }
    }

    init {
        registerReceiver()
        sync()
    }

    override fun retry() {
        _isLoading.value = true
        _error.value = null
        sync()
    }

    private fun sync() {
        scope.launch {
            try {
                fullSync()
                _error.value = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _error.value = error.message ?: "Failed to load apps"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(packageReceiver, filter)
        }
    }

    private suspend fun fullSync() {
        val packageManager = appContext.packageManager
        val launchablePackages = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
        val installed = packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != appContext.packageName }
            .map { it.toInstalledApp(packageManager, it.packageName in launchablePackages) }
            .sortedBy { it.name.lowercase() }
            .toList()

        reconcile(installed)
        _installedApps.value = installed
    }

    private suspend fun addOrUpdatePackage(packageName: String) {
        val packageManager = appContext.packageManager
        val installed = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            val isLaunchable = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName),
                0
            ).any { it.activityInfo?.packageName == packageName }
            info.toInstalledApp(packageManager, isLaunchable)
        }.getOrNull() ?: return

        val existing = appDao.getApp(packageName)
        if (existing != null) {
            appDao.upsertApp(existing.copy(name = installed.name, isSystemApp = installed.isSystemApp))
        } else {
            appDao.addApps(listOf(installed.toStoredApp(newAppsType())))
        }
        _installedApps.value = (_installedApps.value.filterNot { it.packageName == packageName } + installed)
            .sortedBy { it.name.lowercase() }
    }

    private suspend fun removePackage(packageName: String) {
        appDao.deleteApp(packageName)
        _installedApps.value = _installedApps.value.filterNot { it.packageName == packageName }
    }

    private suspend fun reconcile(installed: List<InstalledApp>) {
        val existing = appDao.getAllAppsSnapshot().associateBy { it.packageName }
        val installedPackages = installed.mapTo(HashSet()) { it.packageName }

        val orphaned = existing.keys.filter { it !in installedPackages }
        if (orphaned.isNotEmpty()) appDao.deleteApps(orphaned)

        val newAppsType = newAppsType()
        val added = installed.filter { it.packageName !in existing }.map { it.toStoredApp(newAppsType) }
        val changed = installed.mapNotNull { app ->
            val saved = existing[app.packageName] ?: return@mapNotNull null
            saved.takeIf { it.name != app.name || it.isSystemApp != app.isSystemApp }
                ?.copy(name = app.name, isSystemApp = app.isSystemApp)
        }
        appDao.addApps(added)
        if (changed.isNotEmpty()) appDao.upsertApps(changed)
    }

    private fun ApplicationInfo.toInstalledApp(
        packageManager: PackageManager,
        isLaunchable: Boolean
    ) = InstalledApp(
        packageName = packageName,
        name = runCatching { packageManager.getApplicationLabel(this).toString() }
            .getOrDefault(packageName),
        isSystemApp = flags and ApplicationInfo.FLAG_SYSTEM != 0,
        isLaunchable = isLaunchable
    )

    private suspend fun newAppsType(): ConnectionType =
        (settingsDao.getSettings() ?: SettingsEntity()).newAppsConnectionType(ConnectionType.VPN)

    private fun InstalledApp.toStoredApp(newAppsType: ConnectionType) = App(
        packageName = packageName,
        name = name,
        isSystemApp = isSystemApp,
        connectionType = if (newAppsType == ConnectionType.VPN && usesDirectRouting(packageName)) {
            ConnectionType.DIRECT
        } else {
            newAppsType
        }
    )

    companion object {
        private val DIRECT_PACKAGE_SEGMENTS = setOf("ru", "yandex", "vkontakte", "vk")

        fun usesDirectRouting(packageName: String): Boolean = packageName.split('.').any { segment ->
            segment.lowercase() in DIRECT_PACKAGE_SEGMENTS
        }
    }
}
