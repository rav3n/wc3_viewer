plugins { id("kmp-library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(project(":core:binary"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(project(":analytics"))
            implementation(project(":data:gamedata"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

