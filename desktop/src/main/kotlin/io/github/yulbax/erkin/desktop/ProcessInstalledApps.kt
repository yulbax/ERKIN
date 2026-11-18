package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.App
import io.github.yulbax.erkin.data.AppDao
import io.github.yulbax.erkin.data.ConnectionType
import io.github.yulbax.erkin.data.InstalledApp
import io.github.yulbax.erkin.data.InstalledAppsSource
import io.github.yulbax.erkin.data.isExecutablePath
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
        scope.launch { discoverApps() }
    }

    private suspend fun discoverApps() = refreshMutex.withLock {
        try {
            val saved = appDao.getAllAppsSnapshot()
            val running = discover(runningExecutables(), saved, ownExecutable, systemRoots)
            val savedById = saved.associateBy { it.packageName }
            val discovered = running.filter { it.packageName !in savedById }
            if (discovered.isNotEmpty()) {
                val type = newAppsType()
                appDao.addApps(discovered.map { App(it.packageName, it.name, it.isSystemApp, type, it.path) })
            }
            val runningIds = running.mapTo(HashSet()) { it.packageName }
            val removed = removedApps(saved, runningIds)
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
        private val REFRESH_INTERVAL = 15_000.milliseconds
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

        fun discover(executables: List<File>, saved: List<App>, ownExecutable: String?, systemRoots: List<String>): List<InstalledApp> {
            val legacyByPath = saved.filter { !isExecutablePath(it.packageName) && it.path != null }.associateBy { it.path?.lowercase() }
            return executables
                .filter { it.name.isNotBlank() && it.name != ownExecutable }
                .distinctBy { it.path.lowercase() }
                .map { file ->
                    InstalledApp(
                        packageName = legacyByPath[file.path.lowercase()]?.packageName ?: file.path,
                        name = file.nameWithoutExtension,
                        isSystemApp = systemRoots.any { file.path.startsWith(it, ignoreCase = true) },
                        isLaunchable = true,
                        path = file.path
                    )
                }
        }
    }
}
