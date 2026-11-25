package io.github.yulbax.erkin.desktop

import java.io.File

data class RunningProcess(
    val executable: File,
    val isProgram: Boolean,
    val ancestors: List<File> = emptyList(),
    val flatpak: String? = null
)

data class HostProcess(
    val pid: Long,
    val parent: Long,
    val exe: String?,
    val name: String,
    val unit: String? = null,
    val flatpak: String? = null
)

class OwnApp(private val executable: String?, private val flatpak: String?) {
    fun isOwn(id: String): Boolean = id == flatpak || File(id).name == executable
}

data class CatalogProgram(val name: String, val id: String, val path: String?, val icon: File? = null) {
    companion object {
        fun executable(name: String, executable: File, icon: File? = null, id: String = executable.path) =
            CatalogProgram(name, id, executable.path, icon)

        fun flatpak(name: String, app: String, icon: File? = null) = CatalogProgram(name, app, null, icon)
    }
}

interface DesktopPrograms {
    fun catalog(): List<CatalogProgram>

    fun running(hostProcesses: () -> List<HostProcess>): List<RunningProcess>

    fun stableId(path: String): String

    fun isStableId(id: String): Boolean
}
