plugins {
    kotlin("jvm")
}

group = "faith.mimikyumochi"
version = "0.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":core")))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
