plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0" apply false
}

allprojects {
    group = "com.solartweaks"
    version = "2.0.8"

    repositories {
        mavenCentral()
    }
}

tasks {
    register("release") {
        dependsOn(":agent:updaterConfig", ":agent:generateConfigurations", ":agent:generateFeatures")
    }
}

kotlin { jvmToolchain(16) }