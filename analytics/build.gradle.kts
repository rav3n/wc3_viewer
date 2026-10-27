plugins { id("kmp-library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
        }
        commonTest.dependencies {
            implementation(project(":data:gamedata"))
        }
    }
}
