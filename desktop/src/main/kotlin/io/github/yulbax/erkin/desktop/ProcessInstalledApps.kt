package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import io.github.yulbax.erkin.data.InstalledAppsSource
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
import kotlin.time.Duration.Companion.seconds

class ProcessInstalledApps(
    private val appDao: AppDao,
    private val ownExecutable: String?,
    private val scope: CoroutineScope,
    private val newAppsType: suspend () -> ConnectionType
) : InstalledAppsSource {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val refreshMutex = Mutex()
    private val systemRoots = listOfNotNull(System.getenv("SystemRoot"), "/usr/", "/lib", "/sbin", "/bin")

    init {
        scope.launch {
            while (true) {
                refresh()
                delay(REFRESH_INTERVAL)
            }
        }
    }

    override fun retry() {
        _isLoading.value = true
        scope.launch { refresh() }
    }

    private suspend fun refresh() = refreshMutex.withLock {
        try {
            val running = discover(runningExecutables(), ownExecutable, systemRoots)
            val saved = appDao.getAllAppsSnapshot()
            val savedById = saved.associateBy { it.packageName }
            val discovered = running.filter { it.packageName !in savedById }
            if (discovered.isNotEmpty()) {
                val type = newAppsType()
                appDao.addApps(discovered.map { App(it.packageName, it.name, it.isSystemApp, type, it.path) })
            }
            running.filter { it.path != null && savedById[it.packageName]?.let { app -> app.path != it.path } == true }
                .forEach { appDao.updatePath(it.packageName, it.path) }
            val runningIds = running.mapTo(HashSet()) { it.packageName }
            val removed = removedApps(saved, runningIds)
            if (removed.isNotEmpty()) appDao.deleteApps(removed)
            val remembered = saved
                .filter { it.packageName !in runningIds && it.packageName !in removed && File(it.packageName).name != ownExecutable }
                .map { InstalledApp(it.packageName, it.name, it.isSystemApp, isLaunchable = true, path = it.path) }
            _installedApps.value = (running + remembered).sortedBy { it.name.lowercase() }
            _error.value = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _error.value = error.message ?: "Failed to list running programs"
        } finally {
            _isLoading.value = false
        }
    }

    private fun runningExecutables(): List<File> =
        ProcessHandle.allProcesses()
            .map { it.info().command().orElse(null) }
            .toList()
            .filterNotNull()
            .map(::executableOf)

    companion object {
        private val REFRESH_INTERVAL = 15.seconds
        private const val DELETED_SUFFIX = " (deleted)"

        fun executableOf(command: String): File = File(command.removeSuffix(DELETED_SUFFIX))

        fun removedApps(saved: List<App>, runningIds: Set<String>): List<String> =
            saved.filter { app ->
                app.packageName !in runningIds && when (val path = app.path) {
                    null -> app.packageName.endsWith(DELETED_SUFFIX)
                    else -> isRemoved(File(path))
                }
            }.map { it.packageName }

        private fun isRemoved(file: File): Boolean {
            if (file.exists()) return false
            val existing = generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.exists() } ?: return false
            return existing.parentFile == null || !existing.list().isNullOrEmpty()
        }

        fun discover(executables: List<File>, ownExecutable: String?, systemRoots: List<String>): List<InstalledApp> =
            executables
                .filter { it.name.isNotBlank() && it.name != ownExecutable }
                .distinctBy { it.path.lowercase() }
                .groupBy { it.name }
                .flatMap { (name, copies) ->
                    fun app(id: String, path: String?, file: File) = InstalledApp(
                        packageName = id,
                        name = file.nameWithoutExtension,
                        isSystemApp = systemRoots.any { file.path.startsWith(it, ignoreCase = true) },
                        isLaunchable = true,
                        path = path
                    )
                    val first = copies.first()
                    if (copies.size == 1) {
                        listOf(app(name, first.path, first))
                    } else {
                        listOf(app(name, null, first)) + copies.map { app(it.path, it.path, it) }
                    }
                }
    }
}
