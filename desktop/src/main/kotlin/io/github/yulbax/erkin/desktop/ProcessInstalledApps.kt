package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.hasOwnChannel
import io.github.yulbax.erkin.ui.platform.DesktopAppIcons
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class ProcessInstalledApps(
    private val appDao: AppDao,
    private val ownExecutable: String?,
    private val scope: CoroutineScope,
    private val programs: DesktopPrograms = DesktopPrograms.current,
    private val newAppsType: suspend () -> ConnectionType
) : InstalledAppsSource {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val refreshMutex = Mutex()
    private var catalog: List<CatalogProgram> = emptyList()
    private var catalogReadAt: TimeMark? = null

    init {
        scope.launch {
            while (true) {
                discoverApps()
                delay(REFRESH_INTERVAL)
            }
        }
    }

    override fun retry() {
        _isLoading.value = true
        scope.launch { discoverApps() }
    }

    override fun refresh() {
        catalogReadAt = null
        scope.launch { discoverApps() }
    }

    private suspend fun discoverApps() = refreshMutex.withLock {
        try {
            val saved = appDao.getAllAppsSnapshot()
            val newApps = newAppsType()
            val catalog = currentCatalog()
            val running = discover(programs.running(), saved, ownExecutable)
            val savedById = saved.associateBy { it.packageName }
            val started = running.filter { !it.isSystemApp && it.packageName !in savedById }
            if (started.isNotEmpty()) {
                appDao.addApps(started.map { App(it.packageName, it.name, isSystemApp = false, connectionType = newApps, path = it.path) })
            }
            _installedApps.value = listed(running, saved, catalog, newApps, ownExecutable).sortedBy { it.name.lowercase() }
            _error.value = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _error.value = error.message ?: "Failed to list running programs"
        } finally {
            _isLoading.value = false
        }
    }

    private fun currentCatalog(): List<CatalogProgram> {
        if (catalogReadAt?.let { it.elapsedNow() < CATALOG_INTERVAL } == true) return catalog
        catalog = runCatching { programs.catalog() }.getOrDefault(catalog)
        catalogReadAt = TimeSource.Monotonic.markNow()
        catalog.forEach { program -> program.icon?.let { DesktopAppIcons.remember(program.path ?: program.id, it) } }
        return catalog
    }

    companion object {
        private val REFRESH_INTERVAL = 15_000.milliseconds
        private val CATALOG_INTERVAL = 5.minutes
        private const val DELETED_SUFFIX = " (deleted)"

        fun executableOf(command: String): File = File(command.removeSuffix(DELETED_SUFFIX))

        fun listed(
            running: List<InstalledApp>,
            saved: List<App>,
            catalog: List<CatalogProgram>,
            newApps: ConnectionType,
            ownExecutable: String?
        ): List<InstalledApp> {
            fun keyOf(path: String) = WindowsAppIds.stable(path).lowercase()
            val programs = catalog.groupBy { it.id.lowercase() }.values.map { same -> same.minBy { it.name.length } }
            val catalogByKey = programs.associateBy { it.id.lowercase() }
            val ownChannel = saved.filter { it.hasOwnChannel(newApps) }.mapTo(HashSet()) { it.packageName }
            val runningIds = running.mapTo(HashSet()) { it.packageName }
            val removed = removedApps(saved, runningIds).toSet()
            fun keyOf(app: App) = keyOf(app.path ?: app.packageName)
            val shown = running.filter { !it.isSystemApp || it.packageName in ownChannel } +
                saved.filter { app ->
                    app.packageName !in runningIds && app.packageName !in removed && File(app.packageName).name != ownExecutable &&
                        (app.packageName in ownChannel || keyOf(app) in catalogByKey)
                }.map { InstalledApp(it.packageName, it.name, isSystemApp = false, isLaunchable = true, path = it.path) }
            val shownKeys = shown.mapTo(HashSet()) { keyOf(it.path ?: it.packageName) }
            val unlisted = programs
                .filter { it.id.lowercase() !in shownKeys && it.path?.let(::File)?.name != ownExecutable }
                .map { InstalledApp(it.id, it.name, isSystemApp = false, isLaunchable = true, path = it.path) }
            return shown.map { app ->
                val entry = catalogByKey[keyOf(app.path ?: app.packageName)]
                app.copy(name = entry?.name ?: app.name, path = entry?.path ?: app.path, isSystemApp = false, isLaunchable = true)
            } + unlisted
        }

        fun removedApps(saved: List<App>, runningIds: Set<String>): List<String> =
            saved.filter { app ->
                app.packageName !in runningIds && !WindowsAppIds.isStable(app.packageName) && when (val path = app.path) {
                    null -> app.packageName.endsWith(DELETED_SUFFIX)
                    else -> isRemoved(File(path))
                }
            }.map { it.packageName }

        private fun isRemoved(file: File): Boolean {
            if (file.exists()) return false
            val existing = generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.exists() } ?: return false
            return existing.parentFile == null || !existing.list().isNullOrEmpty()
        }

        fun discover(processes: List<RunningProcess>, saved: List<App>, ownExecutable: String?): List<InstalledApp> {
            val savedByPath = saved.associateBy { (it.path ?: it.packageName).lowercase() }
            val savedById = saved.associateBy { it.packageName.lowercase() }
            return processes
                .filter { it.executable.name.isNotBlank() && it.executable.name != ownExecutable }
                .groupBy { it.flatpak ?: it.executable.path.lowercase() }
                .values
                .map { copies ->
                    copies.first().flatpak?.let { app ->
                        return@map InstalledApp(app, app, isSystemApp = false, isLaunchable = true, path = null)
                    }
                    val file = copies.first().executable
                    val isProcess = copies.none { it.isProgram }
                    val ancestors = copies.firstNotNullOfOrNull { copy -> copy.ancestors.takeIf { it.isNotEmpty() } }.orEmpty()
                    InstalledApp(
                        packageName = savedByPath[file.path.lowercase()]?.packageName
                            ?: savedById[WindowsAppIds.stable(file.path).lowercase()]?.packageName
                            ?: WindowsAppIds.stable(file.path),
                        name = file.nameWithoutExtension,
                        isSystemApp = isProcess,
                        isLaunchable = !isProcess,
                        path = file.path,
                        ancestors = ancestors.map { it.path }.distinct()
                    )
                }
        }
    }
}
