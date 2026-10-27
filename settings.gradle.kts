pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "wc3-viewer"

include(
    ":domain",
    ":core:binary",
    ":data:parser",
    ":data:gamedata",
    ":analytics",
    ":presentation",
    ":app:web",
)
