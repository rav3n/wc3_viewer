import wc3viewer.build.GenerateGameDataTask

plugins { id("kmp-library") }

// Game data: gamedata/<patch>/*.json -> Kotlin at build time (ARCHITECTURE §6.3, ADR 10).
// The generated sources live in build/ and are not committed.
val generateGameData = tasks.register<GenerateGameDataTask>("generateGameData") {
    dataDir.set(layout.settingsDirectory.dir("gamedata/3.0"))
    w3gjsMappings.set(layout.settingsDirectory.file("gamedata/sources/w3gjs-mappings.ts"))
    packageName.set("wc3viewer.gamedata.generated")
    objectName.set("GameData_3_0")
    outputDir.set(layout.buildDirectory.dir("generated/gamedata/commonMain/kotlin"))
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateGameData)
            dependencies {
                api(project(":domain"))
            }
        }
    }
}
