plugins { id("kmp-web-app") }

kotlin {
    sourceSets {
        jsMain.dependencies {
            implementation(project(":presentation"))
            implementation(project(":data:parser"))
            implementation(project(":data:gamedata"))
            implementation(project(":analytics"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.browser)
        }
    }
}
