plugins {
    kotlin("jvm") // Version managed by root project
    id("io.ktor.plugin") version "2.3.10" // Use a recent Ktor version
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" // Define version explicitly
}

group = "com.sleepyyui.notallyxo"
version = "0.0.1"

application {
    mainClass.set("com.sleepyyui.notallyxo.backend.ApplicationKt")
}

kotlin {
    jvmToolchain(11) // Use Java 11 Toolchain
}

dependencies {
    // Ktor Core
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")

    // Content Negotiation (JSON)
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")

    // Status Pages for error handling
    implementation("io.ktor:ktor-server-status-pages-jvm")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Coroutines - Align with version potentially used in Android module if needed
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    // Let's try without explicit version first, might inherit

    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit") // Use version from root project
}

// Ktor plugin configuration
ktor {
    fatJar {
        archiveFileName.set("notallyxo-backend.jar")
    }
} 