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
import kotlin.time.Duration.Companion.milliseconds

class ProcessInstalledApps(
    private val appDao: AppDao,
    private val ownExecutable: String?,
    private val scope: CoroutineScope,
    private val classification: ProgramClassification = ProgramClassification.forCurrentOs(),
    private val catalog: () -> List<StartMenuProgram> = if (DesktopPaths.isWindows) StartMenuPrograms::list else ::emptyList,
    private val newAppsType: suspend () -> ConnectionType
) : InstalledAppsSource {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val refreshMutex = Mutex()

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
            val running = discover(runningProcesses(), saved, ownExecutable, classification)
            val savedById = saved.associateBy { it.packageName }
            val discovered = running.filter { it.packageName !in savedById }
            if (discovered.isNotEmpty()) {
                val type = newAppsType()
                appDao.addApps(discovered.map { App(it.packageName, it.name, it.isSystemApp, type, it.path) })
            }
            val reclassified = running.mapNotNull { app ->
                savedById[app.packageName]?.takeIf { it.isSystemApp != app.isSystemApp }?.copy(isSystemApp = app.isSystemApp)
            }
            if (reclassified.isNotEmpty()) appDao.upsertApps(reclassified)
            val runningIds = running.mapTo(HashSet()) { it.packageName }
            val removed = removedApps(saved, runningIds)
            val remembered = saved
                .filter { it.packageName !in runningIds && it.packageName !in removed && File(it.packageName).name != ownExecutable }
                .map { InstalledApp(it.packageName, it.name, it.isSystemApp, isLaunchable = classification.isLaunchable(it.isSystemApp), path = it.path) }
            _installedApps.value = (running + remembered + catalogued(catalog(), running + remembered, ownExecutable))
                .sortedBy { it.name.lowercase() }
            _error.value = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _error.value = error.message ?: "Failed to list running programs"
        } finally {
            _isLoading.value = false
        }
    }

    private fun runningProcesses(): List<RunningProcess> =
        ProcessHandle.allProcesses().toList().mapNotNull { process ->
            process.info().command().orElse(null)?.let { RunningProcess(executableOf(it), DesktopLaunchers.startedByDesktop(process)) }
        }

    companion object {
        private val REFRESH_INTERVAL = 15_000.milliseconds
        private const val DELETED_SUFFIX = " (deleted)"

        fun executableOf(command: String): File = File(command.removeSuffix(DELETED_SUFFIX))

        fun catalogued(programs: List<StartMenuProgram>, listed: List<InstalledApp>, ownExecutable: String?): List<InstalledApp> {
            val listedPaths = listed.mapNotNullTo(HashSet()) { (it.path ?: it.packageName).lowercase() }
            return programs
                .filter { it.executable.path.lowercase() !in listedPaths && it.executable.name != ownExecutable }
                .map { InstalledApp(it.executable.path, it.name, isSystemApp = false, isLaunchable = true, path = it.executable.path) }
        }

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

        fun discover(
            processes: List<RunningProcess>,
            saved: List<App>,
            ownExecutable: String?,
            classification: ProgramClassification
        ): List<InstalledApp> {
            val savedByPath = saved.associateBy { (it.path ?: it.packageName).lowercase() }
            return processes
                .filter { it.executable.name.isNotBlank() && it.executable.name != ownExecutable }
                .groupBy { it.executable.path.lowercase() }
                .values
                .map { copies ->
                    val file = copies.first().executable
                    val isProcess = copies.all(classification::isProcess)
                    InstalledApp(
                        packageName = savedByPath[file.path.lowercase()]?.packageName ?: file.path,
                        name = file.nameWithoutExtension,
                        isSystemApp = isProcess,
                        isLaunchable = classification.isLaunchable(isProcess),
                        path = file.path
                    )
                }
        }
    }
}
