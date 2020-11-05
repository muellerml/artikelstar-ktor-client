plugins {
    kotlin("jvm") version "1.4.0"
}

group = "de.muellerml"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.ktor:ktor-client-cio:1.4.0")
    implementation("io.ktor:ktor-client-logging-jvm:1.4.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")

    implementation("org.jsoup:jsoup:1.12.1")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
