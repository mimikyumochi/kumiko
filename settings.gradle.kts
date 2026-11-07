plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "Kumiko"

val simModules = listOf("core", "end", "nether")
for (name in simModules) {
    include(name)
    project(":$name").projectDir = file("sim/$name")
}
