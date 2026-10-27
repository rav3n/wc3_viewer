// Compose Multiplatform web on plain js(IR) (canvas rendering, no wasmJs).
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(libs.findVersion("jvm-toolchain").get().requiredVersion.toInt())
    js {
        outputModuleName.set("app")
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }
}
