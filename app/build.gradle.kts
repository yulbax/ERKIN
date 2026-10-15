import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun healthProbeUrl(name: String, defaultValue: String): String =
    providers.gradleProperty(name).orElse(defaultValue).get().trim().also { value ->
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) {
            "$name must be an absolute HTTPS URL"
        }
}

val byeDpiHealthProbeUrl = healthProbeUrl(
    "erkin.byeDpiHealthProbeUrl",
    "https://www.youtube.com/generate_204"
)

val baseVersionCode = 10200
val updateAbis = listOf("universal", "armeabi-v7a", "arm64-v8a", "x86_64")

val releaseStoreFile = providers.environmentVariable("KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
val hasReleaseSigning = releaseStoreFile != null &&
    file(releaseStoreFile).isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "io.github.yulbax.erkin"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "io.github.yulbax.erkin"
        minSdk = 29
        targetSdk = 37
        versionCode = baseVersionCode
        versionName = providers.gradleProperty("erkin.versionName").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "BYEDPI_HEALTH_PROBE_URL",
            byeDpiHealthProbeUrl.asBuildConfigString()
        )
        buildConfigField(
            "String[]",
            "UPDATE_ABIS",
            updateAbis.joinToString(prefix = "{", postfix = "}") { it.asBuildConfigString() }
        )
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            val requested = (project.findProperty("abiFilter") as String?)?.split(",")
            include(*(requested ?: listOf("arm64-v8a", "x86_64", "armeabi-v7a")).toTypedArray())
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            excludes += "lib/x86/**"
            useLegacyPackaging = true
        }
    }
}

koinCompiler {
    logSeverity = "info"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

val verifyMihomo = tasks.register("verifyMihomo") {
    group = "verification"
    description = "Fails early when the generated mihomo AAR is missing."
    inputs.file(layout.projectDirectory.file("libs/mihomo.aar"))
        .withPropertyName("mihomoAar")

    doLast {
        check(inputs.files.singleFile.isFile) {
            "Missing app/libs/mihomo.aar. Run scripts/build-mihomo.sh before building the app."
        }
    }
}

tasks.named("preBuild").configure { dependsOn(verifyMihomo) }

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails early when release signing credentials are missing."
    inputs.property("configured", hasReleaseSigning)

    doLast {
        check(inputs.properties["configured"] == true) {
            "Release signing is not configured. Set KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier ?: "universal"
            val abiCode = updateAbis.indexOf(abi)
            check(abiCode >= 0) { "No update ABI code for $abi" }
            output.versionCode.set(baseVersionCode * 10 + abiCode)
            if (variant.buildType == "release") {
                (output as VariantOutputImpl).outputFileName.set(output.versionName.map { "ERKIN-$it-android-$abi.apk" })
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))
    implementation(files("libs/mihomo.aar"))

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3.v150alpha21)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}
