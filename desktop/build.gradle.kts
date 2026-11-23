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
val testing = providers.gradleProperty("erkin.testing").map(String::toBoolean).getOrElse(false)
val buildHash = runCatching {
    providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
}.getOrDefault("")

val hostOs = if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux"
val desktopOs = providers.gradleProperty("erkin.desktopOs").getOrElse(hostOs)

val appProperties = mapOf(
    "erkin.version" to appVersion,
    "erkin.build" to buildHash,
    "erkin.byedpi.version" to byeDpiVersion,
    "erkin.testing" to testing.toString()
)

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(true)
    }
    sourceSets {
        main { kotlin.srcDir("src/$desktopOs/kotlin") }
        test { kotlin.srcDir("src/${desktopOs}Test/kotlin") }
    }
}

sourceSets.test { resources.srcDir("src/${desktopOs}Test/resources") }

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compose.native.tray)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jetbrains.compose.material.icons.extended)
    testImplementation(libs.junit)
    if (desktopOs == "windows") implementation(libs.jna)
}

val bundledResourcesDir = layout.buildDirectory.dir("bundled-resources")

data class ByeDpiArchive(val name: String, val sha256: String, val entry: String)

fun registerBundledBinaries(
    os: String,
    executableSuffix: String,
    byeDpiArchive: ByeDpiArchive?
) = tasks.register("prepare${os.replaceFirstChar { it.uppercase() }}Binaries") {
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
                "Run TARGETS=$os scripts/build-service.sh" +
                (if (archiveName == null) " and scripts/build-ciadpi.sh" else "") + " first."
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

val prepareBundledFonts = tasks.register<Sync>("prepareBundledFonts") {
    from(project.file("fonts"))
    into(bundledResourcesDir.map { it.dir("common/fonts") })
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareBundledFonts) }

compose.desktop {
    application {
        mainClass = "io.github.yulbax.erkin.desktop.MainKt"
        jvmArgs += appProperties.map { (key, value) -> "-D$key=$value" }
        if (System.getProperty("os.name").startsWith("Windows")) jvmArgs += "-Derkin.updateAsset=windows-x86_64.msi"

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

    val portableImage = layout.buildDirectory.dir("portable/ERKIN")
    val preparePortableImage = tasks.register<Sync>("preparePortableImage") {
        group = "distribution"
        description = "Turns the release app image into the portable ERKIN that runs as administrator."
        dependsOn("createReleaseDistributable")
        from(layout.buildDirectory.dir("compose/binaries/main-release/app/ERKIN"))
        into(portableImage)
        val root = portableImage.map { it.asFile }
        doLast {
            val dir = root.get()
            fun run(vararg command: String) {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                check(process.waitFor() == 0) { "${command.first()} failed: $output" }
            }

            val cfg = File(dir, "app/ERKIN.cfg")
            val text = cfg.readText()
            val newline = if ("\r\n" in text) "\r\n" else "\n"
            val header = "[JavaOptions]$newline"
            check(header in text) { "No [JavaOptions] section in $cfg" }
            val options = listOf("-Derkin.portable=true").joinToString("") { "java-options=$it$newline" }
            cfg.writeText(text.replaceFirst(header, header + options))

            val kits = File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Windows Kits/10/bin")
            val mt = kits.listFiles().orEmpty()
                .map { File(it, "x64/mt.exe") }
                .filter { it.isFile }
                .maxByOrNull { it.parentFile.parentFile.name }
                ?: error("mt.exe not found in $kits; install the Windows SDK")
            val launcher = File(dir, "ERKIN.exe")
            val manifest = File(temporaryDir, "ERKIN.manifest")
            run(mt.absolutePath, "-nologo", "-inputresource:${launcher.absolutePath};#1", "-out:${manifest.absolutePath}")
            val asInvoker = "level=\"asInvoker\""
            val manifestText = manifest.readText()
            check(asInvoker in manifestText) { "The ERKIN.exe manifest does not request asInvoker: $manifestText" }
            manifest.writeText(manifestText.replace(asInvoker, "level=\"requireAdministrator\""))
            run(mt.absolutePath, "-nologo", "-manifest", manifest.absolutePath, "-outputresource:${launcher.absolutePath};#1")
        }
    }

    tasks.register<Zip>("packagePortableZip") {
        group = "distribution"
        description = "Zips the portable ERKIN folder."
        from(preparePortableImage) { into("ERKIN") }
        destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main-release/portable"))
        archiveFileName.set("ERKIN-$appVersion-windows-x86_64-portable.zip")
    }
}

enum class NativeTarget(
    val id: String,
    val executableSuffix: String,
    val distributionAppPath: String,
    val nativeImageLauncher: String,
    val sqliteEntry: String,
    val graalvmFiles: List<Pair<String, String>>,
    val extraArguments: List<String>
) {
    LINUX(
        id = "linux",
        executableSuffix = "",
        distributionAppPath = "lib/app",
        nativeImageLauncher = "native-image",
        sqliteEntry = "natives/linux_x64/libsqliteJni.so",
        graalvmFiles = listOf("lib/libjawt.so" to "", "lib/libjawt.so" to "lib"),
        extraArguments = emptyList()
    ),
    WINDOWS(
        id = "windows",
        executableSuffix = ".exe",
        distributionAppPath = "app",
        nativeImageLauncher = "native-image.cmd",
        sqliteEntry = "natives/windows_x64/sqliteJni.dll",
        graalvmFiles = listOf("bin/jawt.dll" to "bin", "lib/fontconfig.bfc" to "lib"),
        extraArguments = listOf(
            "-H:+AddAllCharsets",
            "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
            "-H:NativeLinkerOption=/ENTRY:mainCRTStartup"
        )
    );

    val javaExecutable get() = "bin/java$executableSuffix"
}

val nativeTarget = NativeTarget.entries.first { it.id == desktopOs }
val graalvmHome = providers.gradleProperty("erkin.graalvm").orElse(providers.environmentVariable("GRAALVM_HOME"))
val distributionLibDir = layout.buildDirectory.dir("compose/binaries/main/app/ERKIN/${nativeTarget.distributionAppPath}")
val distributionJars = fileTree(distributionLibDir) { include("*.jar") }
val nativeConfigDir = layout.projectDirectory.dir("native/$desktopOs")
val nativeImageDir = layout.buildDirectory.dir("native/image")
val generatedNativeDir = layout.buildDirectory.dir("generated/native/kotlin")
val composeLocales = layout.projectDirectory.dir("../ui/src/commonMain/composeResources").asFile.listFiles().orEmpty()
    .map { it.name }
    .filter { it.startsWith("values-") }
    .map { it.removePrefix("values-") }
    .sorted()

val nativeSourceSet = sourceSets.create("native") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

val generateNativeBuild = tasks.register("generateNativeBuild") {
    val properties = appProperties
    val buildFile = generatedNativeDir.map { it.file("NativeBuild.kt") }
    inputs.property("appProperties", properties)
    outputs.dir(generatedNativeDir)
    doLast {
        fun escaped(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        val entries = properties.entries.joinToString(",\n") { (key, value) -> "        \"${escaped(key)}\" to \"${escaped(value)}\"" }
        buildFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("internal object NativeBuild {\n    val properties: Map<String, String> = mapOf(\n$entries\n    )\n}\n")
        }
    }
}

kotlin.sourceSets.named("native") { kotlin.srcDir(generateNativeBuild) }

val buildNativeImage = tasks.register<Exec>("buildNativeImage") {
    group = "distribution"
    description = "Compiles the desktop app into a GraalVM native image."
    dependsOn("createDistributable", "nativeClasses")
    inputs.files(distributionJars).withPropertyName("jars")
    inputs.files(nativeSourceSet.output.classesDirs).withPropertyName("nativeClasses")
    inputs.files(nativeConfigDir).withPropertyName("nativeConfig")
    outputs.dir(nativeImageDir)
    val home = graalvmHome
    val targetOs = desktopOs
    val buildOs = hostOs
    val target = nativeTarget
    val imageDirectory = nativeImageDir
    val classesDirs = nativeSourceSet.output.classesDirs
    val jars = distributionJars
    val configDir = nativeConfigDir.asFile.invariantSeparatorsPath
    val locales = (listOf("en") + composeLocales).joinToString(",")
    doFirst {
        val graalvm = home.orNull ?: error("Set the erkin.graalvm Gradle property or GRAALVM_HOME to a GraalVM JDK.")
        check(targetOs == buildOs) { "native-image builds only for the host OS ($buildOs), not $targetOs." }
        val imageDir = imageDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        val classpath = (classesDirs.files + jars.files)
            .joinToString(File.pathSeparator) { it.invariantSeparatorsPath }
        val arguments = listOf(
            "-J-Xmx4g",
            "-Os",
            "--enable-native-access=ALL-UNNAMED",
            "-H:ConfigurationFileDirectories=$configDir",
            "-Djava.awt.headless=false",
            "-H:+UnlockExperimentalVMOptions",
            "-H:IncludeLocales=$locales",
            "-H:IncludeResources=composeResources/.*",
            "-H:ExcludeResources=.*sqliteJni.*|composeResources/.*/font/.*"
        ) + target.extraArguments + listOf(
            "-cp",
            "\"$classpath\"",
            "-o",
            File(imageDir, "ERKIN").invariantSeparatorsPath,
            "ERKIN"
        )
        val argumentsFile = File(temporaryDir, "native-image.args")
        argumentsFile.writeText(arguments.joinToString("\n"))
        executable = File(graalvm, "bin/${target.nativeImageLauncher}").path
        args("@${argumentsFile.invariantSeparatorsPath}")
    }
}

tasks.register<Sync>("packageNative") {
    group = "distribution"
    description = "Assembles the native app folder with the executable, Skiko, sqlite and AWT libraries."
    dependsOn(buildNativeImage)
    into(layout.buildDirectory.dir("native/ERKIN"))
    from(nativeImageDir)
    from(distributionLibDir) { exclude("*.jar", "*.cfg", ".jpackage.xml", "resources/**") }
    from(distributionLibDir.map { it.dir("resources") }) { into("app/resources") }
    val sqliteLibrary = nativeTarget.sqliteEntry
    from(configurations.runtimeClasspath.map { classpath -> classpath.filter { it.name.startsWith("sqlite-bundled-jvm") }.map(::zipTree) }) {
        include(sqliteLibrary)
        eachFile { path = name }
        includeEmptyDirs = false
    }
    nativeTarget.graalvmFiles.forEach { (source, destination) ->
        from(graalvmHome.map { File(it, source) }) { into(destination) }
    }
}

tasks.register<JavaExec>("recordNativeConfig") {
    group = "distribution"
    description = "Runs the app on GraalVM with the tracing agent and merges its findings into native/$desktopOs."
    dependsOn("createDistributable")
    classpath = distributionJars
    mainClass.set("io.github.yulbax.erkin.desktop.MainKt")
    jvmArgs(
        "-agentlib:native-image-agent=config-merge-dir=${nativeConfigDir.asFile.invariantSeparatorsPath}",
        "-Dcompose.application.resources.dir=${distributionLibDir.get().dir("resources").asFile.invariantSeparatorsPath}",
        "-Dcompose.application.configure.swing.globals=true"
    )
    jvmArgs(appProperties.map { (key, value) -> "-D$key=$value" })
    val home = graalvmHome
    val java = nativeTarget.javaExecutable
    doFirst {
        val graalvm = home.orNull ?: error("Set the erkin.graalvm Gradle property or GRAALVM_HOME to a GraalVM JDK.")
        setExecutable(File(graalvm, java).path)
    }
}
