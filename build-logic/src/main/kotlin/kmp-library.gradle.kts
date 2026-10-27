// Platform-independent logic: jvm (fast tests) + js (IR is the only JS compiler).
// No wasmJs: user decision 2026-10-27.
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(libs.findVersion("jvm-toolchain").get().requiredVersion.toInt())
    jvm()
    js {
        browser()
        nodejs()
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Integration tests read replays and golden files from the project root.
val projectRoot = rootDir.absolutePath
tasks.withType<Test>().configureEach {
    systemProperty("wc3.root", projectRoot)
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
