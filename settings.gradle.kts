rootProject.name = "artikelstar-ktor-client"

pluginManagement {
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    plugins {
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("jvm") version kotlinVersion
        id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
