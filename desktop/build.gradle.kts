import java.net.URI
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

val appVersion = providers.gradleProperty("erkin.versionName").get()
val byeDpiVersion = "17.3"

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

fun registerBundledBinaries(
    os: String,
    executableSuffix: String,
    archiveName: String,
    archiveSha256: String,
    archiveEntry: String
) = tasks.register("prepare${os.replaceFirstChar { it.uppercase() }}Binaries") {
    group = "distribution"
    description = "Bundles erkin-service built by scripts/build-libbox.sh and the official byedpi release for $os."
    val archiveUrl = "https://github.com/hufrea/byedpi/releases/download/v0.$byeDpiVersion/$archiveName"
    val coreFiles = listOf("erkin-service$executableSuffix", "sing-box-LICENSE.txt")
        .map { layout.projectDirectory.dir("libs/$os").file(it).asFile }
    val outputDir = bundledResourcesDir.map { it.dir(os) }
    val ciadpiName = "ciadpi$executableSuffix"
    inputs.property("byeDpiArchive", "$archiveUrl $archiveSha256 $archiveEntry")
    inputs.files(coreFiles).withPropertyName("core").optional()
    outputs.dir(outputDir)
    doLast {
        fun unzipEntry(archive: ByteArray, name: String): ByteArray? = ZipInputStream(archive.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == name }?.let { zip.readBytes() }
        }

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
                "Run CORE_TARGETS=$os scripts/build-libbox.sh first."
        }
        val target = outputDir.get().asFile.apply { mkdirs() }
        coreFiles.forEach { it.copyTo(File(target, it.name), overwrite = true).setExecutable(true) }
        val bytes = URI(archiveUrl).toURL().openStream().use { it.readBytes() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(actual == archiveSha256) { "Checksum mismatch for $archiveUrl: $actual" }
        val ciadpi = if (archiveName.endsWith(".zip")) unzipEntry(bytes, archiveEntry) else untarEntry(bytes, archiveEntry)
        File(target, ciadpiName).apply {
            writeBytes(checkNotNull(ciadpi) { "Missing $archiveEntry in $archiveUrl" })
            setExecutable(true)
        }
    }
}

val prepareWindowsBinaries = registerBundledBinaries(
    os = "windows",
    executableSuffix = ".exe",
    archiveName = "byedpi-$byeDpiVersion-x86_64-w64.zip",
    archiveSha256 = "70d2c94147193cb915f9c6eb5144b8d404dacbcfa90bda2383b6b211afafa456",
    archiveEntry = "ciadpi.exe"
)

val prepareLinuxBinaries = registerBundledBinaries(
    os = "linux",
    executableSuffix = "",
    archiveName = "byedpi-$byeDpiVersion-x86_64.tar.gz",
    archiveSha256 = "98f73c32eacb571ebd88d790f6376ed9e70f02d44c1fe862472ecea75cd7117d",
    archiveEntry = "ciadpi-x86_64"
)

compose.desktop {
    application {
        mainClass = "io.github.yulbax.erkin.desktop.MainKt"
        jvmArgs += listOf("-Derkin.version=$appVersion", "-Derkin.byedpi.version=$byeDpiVersion")

        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
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
        }
    }
}

if (System.getProperty("os.name").startsWith("Linux")) {
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareLinuxBinaries) }
}

if (System.getProperty("os.name").startsWith("Windows")) {
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
