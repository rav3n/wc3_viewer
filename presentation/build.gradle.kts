plugins { id("kmp-library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            // Only metric result types (data classes), not implementations.
            api(project(":analytics"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":data:gamedata"))
        }
    }
}
