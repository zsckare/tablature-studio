plugins {
    kotlin("jvm") version "2.1.20" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
subprojects {
    group = "com.tablaturestudio"
    version = "0.1.0-dev"
}
