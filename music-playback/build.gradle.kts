plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":music-core"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
