import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("io.ktor.plugin") version "2.3.5"
    application
}

group = "com.sleepyyui.notallyxo"
version = "0.0.1"

application {
    mainClass.set("com.sleepyyui.notallyxo.backend.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server dependencies
    implementation("io.ktor:ktor-server-core-jvm:2.3.5")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.5")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.5")
    implementation("io.ktor:ktor-server-auth-jvm:2.3.5")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:2.3.5")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.5")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.5")
    
    // Serialization
    implementation("io.ktor:ktor-serialization-gson-jvm:2.3.5")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.zaxxer:HikariCP:5.0.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // Security and encryption
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")
    
    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.5")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.0")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}
