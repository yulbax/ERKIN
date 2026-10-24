package io.github.yulbax.erkin.desktop

import io.github.yulbax.erkin.data.profile.DeviceIdentity
import java.io.File
import java.util.UUID

object DesktopDeviceIdentity {
    private const val FILE_NAME = "device-id"

    fun load(): DeviceIdentity = DeviceIdentity(
        hwid = hwid(File(DesktopPaths.dataDir, FILE_NAME)),
        os = DesktopPaths.os.displayName,
        osVersion = System.getProperty("os.version").orEmpty(),
        model = System.getProperty("os.name").orEmpty()
    )

    private fun hwid(file: File): String =
        runCatching { file.readText().trim() }.getOrNull()?.takeIf(DeviceIdentity::isValidHwid)
            ?: UUID.randomUUID().toString().also { id -> runCatching { file.writeText(id) } }
}
