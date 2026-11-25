package io.github.yulbax.erkin.desktop

import java.io.File

object FlatpakSandbox {
    private const val INFO_FILE = "/.flatpak-info"
    private const val APP_ROOT = "/app"
    private val info = File(INFO_FILE)

    val appId: String? = System.getenv("FLATPAK_ID")?.takeIf { info.isFile }

    val isActive: Boolean = appId != null

    private val hostAppRoot: String by lazy {
        checkNotNull(IniFiles.value(info.readText(), "[Instance]", "app-path")) { "$INFO_FILE has no app-path" }
    }

    fun onHost(file: File): List<String> =
        listOf("flatpak-spawn", "--host", "--watch-bus", hostAppRoot + file.absolutePath.removePrefix(APP_ROOT))

    fun sharedRuntimeDir(): File = File(checkNotNull(System.getenv("XDG_RUNTIME_DIR")), "app/${checkNotNull(appId)}")
}
