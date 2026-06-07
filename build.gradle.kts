import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
}

group = "de.muellerml"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.ktor:ktor-client-cio:2.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:2.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.2")
    implementation("io.ktor:ktor-client-logging-jvm:2.1.2")

    implementation("org.jsoup:jsoup:1.15.3")
}


tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    compileTestKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
}
