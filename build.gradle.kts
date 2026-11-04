plugins {
    kotlin("jvm") version "2.4.21"
}

group = "faith.mimikyumochi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("de.fabmax.kool:kool-core:0.19.0")

    implementation(project(":core"))
    implementation(project(":end"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("kumikoJar") {
    group = "build"
    archiveFileName.set("kumiko.jar")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    manifest {
        attributes(
            "Main-Class" to "faith.mimikyumochi.MainKt",
            "Multi-Release" to "true",
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from("licenses") { into("licenses") }
    from("LICENSE") { into("META-INF") }
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("module-info.class", "META-INF/versions/*/module-info.class")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
        exclude("META-INF/INDEX.LIST", "META-INF/MANIFEST.MF")
        exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*license*", "META-INF/*LICENSE*", "LICENSE*", "NOTICE*")
    }
}
