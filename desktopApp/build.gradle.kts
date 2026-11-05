plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":music-core"))
    implementation(project(":music-import"))
    implementation(project(":music-renderer"))
    implementation(project(":music-playback"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}
compose.desktop {
    application {
        mainClass = "com.tablaturestudio.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "TablatureStudio"
            packageVersion = "1.0.0"
        }
    }
}
