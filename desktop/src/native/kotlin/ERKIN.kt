import java.io.File

object ERKIN {
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(ProcessHandle.current().info().command().orElseThrow()).parentFile
        System.setProperty("java.library.path", dir.path + File.pathSeparator + System.getProperty("java.library.path", ""))
        if (File(dir, NativeBuild.PORTABLE_MARKER).isFile) System.setProperty("erkin.portable", "true")
        val launch = mapOf(
            "java.home" to dir.path,
            "skiko.library.path" to dir.path,
            "compose.application.resources.dir" to File(dir, "app${File.separator}resources").path,
            "compose.application.configure.swing.globals" to "true",
            "jpackage.app-version" to NativeBuild.properties.getValue("erkin.version"),
        ) + NativeBuild.properties
        launch.forEach { (key, value) -> if (System.getProperty(key) == null) System.setProperty(key, value) }
        io.github.yulbax.erkin.desktop.main()
    }
}
