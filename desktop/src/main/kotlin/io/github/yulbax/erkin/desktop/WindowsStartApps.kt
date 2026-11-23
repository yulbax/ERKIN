package io.github.yulbax.erkin.desktop

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

data class StartApp(val name: String, val id: String, val path: String = "", val logo: String = "")

object WindowsStartApps {
    private const val QUERY_TIMEOUT_SECONDS = 60L
    private const val PROGRAM_EXTENSION = ".exe"
    private const val EXPLORER_ID = "Microsoft.Windows.Explorer"
    private val UNINSTALLER = Regex("(?i)uninstall|^unins\\d*$")
    private val KNOWN_FOLDER_ID = Regex("^\\{([0-9A-Fa-f-]{36})}\\\\(.+)$")
    private val ABSOLUTE_PATH = Regex("^[A-Za-z]:\\\\.+")

    private val KNOWN_FOLDERS: Map<String, (Environment) -> String?> = mapOf(
        "1AC14E77-02E7-4E5D-B744-2EB1AE5198B7" to { env -> env("SystemRoot")?.let { "$it\\System32" } },
        "D65231B0-B2F1-4857-A4CE-A8E7C6EA7D27" to { env -> env("SystemRoot")?.let { "$it\\SysWOW64" } },
        "F38BF404-1D43-42F2-9305-67DE0B28FC23" to { env -> env("SystemRoot") },
        "6D809377-6AF0-444B-8957-A3773F02200E" to { env -> env("ProgramW6432") ?: env("ProgramFiles") },
        "905E63B6-C1BF-494E-B29C-65B732D3D21A" to { env -> env("ProgramFiles") },
        "7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E" to { env -> env("ProgramFiles(x86)") },
        "F7F1ED05-9F6D-47A2-AAAE-29D317C6F066" to { env -> env("CommonProgramFiles") },
        "62AB5D82-FDC1-4DC3-A9DD-070D1D495D97" to { env -> env("ProgramData") },
        "F1B32785-6FBA-4FCF-9D55-7B8E7F157091" to { env -> env("LOCALAPPDATA") },
        "3EB685DB-65F9-4CF6-A03A-E3EF65729F3D" to { env -> env("APPDATA") },
        "5E6C858F-0E22-4760-9AFE-EA3317B67173" to { env -> env("USERPROFILE") },
        "5CD7AEE2-2219-4A67-B85D-6C9CE15660CB" to { env -> env("LOCALAPPDATA")?.let { "$it\\Programs" } }
    )

    private val QUERY = """
        ${'$'}ErrorActionPreference = 'SilentlyContinue'
        [Console]::OutputEncoding = [Text.Encoding]::UTF8
        ${'$'}packages = @{}
        Get-AppxPackage | ForEach-Object { ${'$'}packages[${'$'}_.PackageFamilyName] = ${'$'}_ }
        @(Get-StartApps | ForEach-Object {
          ${'$'}entry = [ordered]@{ name = ${'$'}_.Name; id = ${'$'}_.AppID; path = ''; logo = '' }
          ${'$'}parts = ${'$'}_.AppID.Split('!', 2)
          if (${'$'}parts.Count -eq 2 -and ${'$'}packages.ContainsKey(${'$'}parts[0])) {
            ${'$'}package = ${'$'}packages[${'$'}parts[0]]
            ${'$'}manifest = [xml](Get-Content -LiteralPath (Join-Path ${'$'}package.InstallLocation 'AppxManifest.xml') -Raw)
            ${'$'}app = ${'$'}manifest.Package.Applications.Application | Where-Object { ${'$'}_.Id -eq ${'$'}parts[1] } | Select-Object -First 1
            if (${'$'}app.Executable -and ${'$'}app.Executable -notlike '*${'$'}*') { ${'$'}entry.path = Join-Path ${'$'}package.InstallLocation ${'$'}app.Executable }
            ${'$'}logo = ${'$'}app.VisualElements.Square44x44Logo
            if (${'$'}logo) { ${'$'}entry.logo = Join-Path ${'$'}package.InstallLocation ${'$'}logo }
          }
          [pscustomobject]${'$'}entry
        }) | ConvertTo-Json -Compress
    """.trimIndent()

    fun list(): List<CatalogProgram> = catalog(parse(query()), StartMenuPrograms.shortcutTargets(), System::getenv) { File(it).isFile }

    fun catalog(
        apps: List<StartApp>,
        shortcuts: Map<String, String>,
        environment: Environment,
        exists: (String) -> Boolean
    ): List<CatalogProgram> =
        apps.mapNotNull { app ->
            val executable = executableOf(app, shortcuts, environment) ?: return@mapNotNull null
            CatalogProgram.executable(app.name, File(executable), app.logo.ifEmpty { null }?.let(::logoFile))
        }
            .filter { program -> program.path.orEmpty().let { it.endsWith(PROGRAM_EXTENSION, ignoreCase = true) && exists(it) } }
            .filterNot { program -> UNINSTALLER.containsMatchIn(program.name) || UNINSTALLER.containsMatchIn(File(program.path.orEmpty()).nameWithoutExtension) }

    private fun executableOf(app: StartApp, shortcuts: Map<String, String>, environment: Environment): String? {
        app.path.ifEmpty { null }?.let { return it }
        KNOWN_FOLDER_ID.find(app.id)?.let { match ->
            val folder = KNOWN_FOLDERS[match.groupValues[1].uppercase()]?.invoke(environment) ?: return null
            return "$folder\\${match.groupValues[2]}"
        }
        if (ABSOLUTE_PATH.matches(app.id)) return app.id
        if (app.id == EXPLORER_ID) return environment("SystemRoot")?.let { "$it\\explorer.exe" }
        return shortcuts[app.name.lowercase()]
    }

    fun logoFile(logo: String): File? {
        val declared = File(logo)
        val dir = declared.parentFile ?: return null
        val base = declared.nameWithoutExtension
        val variants = dir.listFiles { file -> file.name.startsWith("$base.") && file.extension.equals(declared.extension, ignoreCase = true) }
            .orEmpty()
            .filterNot { "contrast-" in it.name || "lightunplated" in it.name || "theme-light" in it.name }
        return variants.maxWithOrNull(compareBy<File> { "altform-unplated" in it.name }.thenBy { sizeOf(it.name) })
            ?: declared.takeIf { it.isFile }
    }

    private fun sizeOf(name: String): Int {
        Regex("targetsize-(\\d+)").find(name)?.let { return it.groupValues[1].toInt().coerceAtMost(256) }
        Regex("scale-(\\d+)").find(name)?.let { return it.groupValues[1].toInt() * 44 / 100 }
        return 0
    }

    fun parse(output: String): List<StartApp> =
        Json.parseToJsonElement(output.trim().removePrefix("\uFEFF")).jsonArray.mapNotNull { element ->
            val fields = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = (fields[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            StartApp(text("name"), text("id"), text("path"), text("logo")).takeIf { it.name.isNotEmpty() && it.id.isNotEmpty() }
        }

    private fun query(): String {
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", QUERY)
            .redirectErrorStream(false)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!process.waitFor(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        return output
    }
}

typealias Environment = (String) -> String?

object WindowsAppIds {
    private val PACKAGE_FOLDER = Regex("(?i)(\\\\WindowsApps\\\\)([^\\\\_]+)_[^\\\\]*_([^\\\\_]+)(\\\\)")

    fun isStable(id: String): Boolean = '*' in id

    fun stable(path: String): String = PACKAGE_FOLDER.replace(path) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}_*_${match.groupValues[3]}${match.groupValues[4]}"
    }
}
