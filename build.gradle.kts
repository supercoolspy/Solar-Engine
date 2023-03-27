plugins {
    kotlin("jvm") version "1.8.0" apply false
    kotlin("plugin.serialization") version "1.8.0" apply false
}

allprojects {
    group = "com.solartweaks"
    version = "2.1.0"

    repositories {
        mavenCentral()
    }
}

tasks {
    register("release") {
        dependsOn(":agent:updaterConfig", ":agent:generateConfigurations", ":agent:generateFeatures")
    }
}