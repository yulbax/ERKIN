import java.net.URI
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val appVersion = providers.gradleProperty("erkin.versionName").get()
val byeDpiVersion = "17.3"
val hostOs = System.getProperty("os.name")
val macArch = if (System.getProperty("os.arch") == "aarch64") "arm64" else "amd64"
val buildHash = runCatching {
    providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
}.getOrDefault("")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jna)
    implementation(libs.compose.native.tray)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jetbrains.compose.material.icons.extended)
    testImplementation(libs.junit)
}

val bundledResourcesDir = layout.buildDirectory.dir("bundled-resources")

data class ByeDpiArchive(val name: String, val sha256: String, val entry: String)

fun registerBundledBinaries(
    os: String,
    executableSuffix: String,
    byeDpiArchive: ByeDpiArchive?
) = tasks.register("prepare${os.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }}Binaries") {
    group = "distribution"
    description = "Bundles erkin-service built by scripts/build-service.sh and byedpi for $os."
    val ciadpiName = "ciadpi$executableSuffix"
    val coreNames = listOf("erkin-service$executableSuffix", "mihomo-LICENSE.txt") +
        if (byeDpiArchive == null) listOf(ciadpiName) else emptyList()
    val coreFiles = coreNames.map { layout.projectDirectory.dir("libs/$os").file(it).asFile }
    val outputDir = bundledResourcesDir.map { it.dir(os) }
    val archiveName = byeDpiArchive?.name
    val archiveSha256 = byeDpiArchive?.sha256
    val archiveEntry = byeDpiArchive?.entry
    val archiveUrl = archiveName?.let { "https://github.com/hufrea/byedpi/releases/download/v0.$byeDpiVersion/$it" }
    inputs.property("byeDpiArchive", listOfNotNull(archiveUrl, archiveSha256, archiveEntry).joinToString(" "))
    inputs.files(coreFiles).withPropertyName("core").optional()
    outputs.dir(outputDir)
    doLast {
        fun untarEntry(archive: ByteArray, name: String): ByteArray? {
            val tar = GZIPInputStream(archive.inputStream()).use { it.readBytes() }
            var offset = 0
            while (offset + 512 <= tar.size) {
                val header = tar.copyOfRange(offset, offset + 512)
                val entryName = header.copyOfRange(0, 100).decodeToString().trimEnd('\u0000')
                if (entryName.isEmpty()) return null
                val size = header.copyOfRange(124, 136).decodeToString().trim('\u0000', ' ').toLong(8).toInt()
                val data = offset + 512
                if (entryName.removePrefix("./") == name) return tar.copyOfRange(data, data + size)
                offset = data + (size + 511) / 512 * 512
            }
            return null
        }

        val missing = coreFiles.filterNot { it.isFile }
        check(missing.isEmpty()) {
            "Missing ${missing.joinToString { it.name }} in desktop/libs/$os. " +
                "Run scripts/build-service.sh" +
                (if (archiveName == null) " and scripts/build-ciadpi.sh" else "") + " for $os first."
        }
        val target = outputDir.get().asFile.apply { mkdirs() }
        coreFiles.forEach { it.copyTo(File(target, it.name), overwrite = true).setExecutable(true) }
        if (archiveUrl == null || archiveSha256 == null || archiveEntry == null) return@doLast
        val bytes = URI(archiveUrl).toURL().openStream().use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(actual == archiveSha256) { "Checksum mismatch for $archiveUrl: $actual" }
        File(target, ciadpiName).apply {
            writeBytes(checkNotNull(untarEntry(bytes, archiveEntry)) { "Missing $archiveEntry in $archiveUrl" })
            setExecutable(true)
        }
    }
}

val prepareWindowsBinaries = registerBundledBinaries(
    os = "windows",
    executableSuffix = ".exe",
    byeDpiArchive = null
)

val prepareLinuxBinaries = registerBundledBinaries(
    os = "linux",
    executableSuffix = "",
    byeDpiArchive = ByeDpiArchive(
        name = "byedpi-$byeDpiVersion-x86_64.tar.gz",
        sha256 = "98f73c32eacb571ebd88d790f6376ed9e70f02d44c1fe862472ecea75cd7117d",
        entry = "ciadpi-x86_64"
    )
)

val prepareMacosBinaries = registerBundledBinaries(
    os = "macos-$macArch",
    executableSuffix = "",
    byeDpiArchive = null
)

compose.desktop {
    application {
        mainClass = "io.github.yulbax.erkin.desktop.MainKt"
        jvmArgs += listOf("-Derkin.version=$appVersion", "-Derkin.build=$buildHash", "-Derkin.byedpi.version=$byeDpiVersion")
        if (hostOs.startsWith("Windows")) jvmArgs += "-Derkin.updateAsset=windows-x86_64.msi"

        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "ERKIN"
            packageVersion = appVersion
            vendor = "yulbax"
            description = "ERKIN VPN with per-app routing and ByeDPI"
            modules("java.sql", "java.naming", "jdk.unsupported")
            appResourcesRootDir.set(bundledResourcesDir)

            windows {
                iconFile.set(project.file("icons/erkin.ico"))
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = false
                upgradeUuid = "85e5f814-d069-44e8-9991-526d02154e62"
            }

            macOS {
                iconFile.set(project.file("icons/erkin.icns"))
                bundleID = "io.github.yulbax.erkin"
                dockName = "ERKIN"
                minimumSystemVersion = "12.0"
            }
        }
    }
}

if (hostOs.startsWith("Linux")) {
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareLinuxBinaries) }
}

if (hostOs.startsWith("Mac")) {
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareMacosBinaries) }
    mapOf("createDistributable" to "main", "createReleaseDistributable" to "main-release").forEach { (taskName, variant) ->
        val resources = layout.buildDirectory.dir("compose/binaries/$variant/app/ERKIN.app/Contents/app/resources")
        tasks.matching { it.name == taskName }.configureEach {
            doLast {
                listOf("erkin-service", "ciadpi").forEach { name ->
                    val binary = resources.get().file(name).asFile
                    check(binary.setExecutable(true, false)) { "Could not make $binary executable" }
                }
            }
        }
    }
}

if (hostOs.startsWith("Windows")) {
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareWindowsBinaries) }

    tasks.register<Exec>("packageServiceMsi") {
        group = "distribution"
        description = "Builds the MSI with a WiX template that installs and removes the ERKIN background service."
        dependsOn("createReleaseDistributable", rootProject.tasks.named("unzipWix"))
        val appImage = layout.buildDirectory.dir("compose/binaries/main-release/app/ERKIN")
        val output = layout.buildDirectory.dir("compose/binaries/main-release/msi")
        val wixDir = rootProject.layout.buildDirectory.dir("wix311")
        inputs.dir(appImage)
        inputs.dir("packaging/windows")
        outputs.dir(output)
        doFirst {
            output.get().asFile.apply { deleteRecursively(); mkdirs() }
            environment("PATH", wixDir.get().asFile.absolutePath + File.pathSeparator + System.getenv("PATH"))
        }
        executable = File(System.getProperty("java.home"), "bin/jpackage.exe").absolutePath
        args(
            "--type", "msi",
            "--app-image", appImage.get().asFile.absolutePath,
            "--dest", output.get().asFile.absolutePath,
            "--resource-dir", project.file("packaging/windows").absolutePath,
            "--name", "ERKIN",
            "--app-version", appVersion,
            "--vendor", "yulbax",
            "--description", "ERKIN VPN with per-app routing and ByeDPI",
            "--install-dir", "ERKIN",
            "--win-menu",
            "--win-shortcut",
            "--win-dir-chooser",
            "--win-upgrade-uuid", "85e5f814-d069-44e8-9991-526d02154e62"
        )
        val builtName = "ERKIN-$appVersion.msi"
        val msiName = "ERKIN-$appVersion-windows-x86_64.msi"
        val msiDir = output.map { it.asFile }
        doLast {
            val dir = msiDir.get()
            val built = File(dir, builtName)
            check(built.renameTo(File(dir, msiName))) { "Could not rename $built to $msiName" }
        }
    }
}
