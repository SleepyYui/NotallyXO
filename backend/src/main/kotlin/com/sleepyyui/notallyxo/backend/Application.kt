package com.sleepyyui.notallyxo.backend

import com.sleepyyui.notallyxo.backend.config.DatabaseConfig
import com.sleepyyui.notallyxo.backend.config.JwtConfig
import com.sleepyyui.notallyxo.backend.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Main entry point for the NotallyXO backend application.
 */
fun main() {
    // Load configurations from environment variables or defaults
    val jwtConfig = JwtConfig.fromEnvOrDefault()
    val dbConfig = DatabaseConfig.fromEnvOrDefault()
    
    // Initialize the database
    initializeDatabase(dbConfig)
    
    // Load port from environment variable or use default
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    
    // Start the server
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        configureApplication(jwtConfig)
    }.start(wait = true)
}

/**
 * Configure the Ktor application with all necessary plugins and routes.
 */
fun Application.configureApplication(jwtConfig: JwtConfig) {
    logger.info { "Configuring NotallyXO backend application" }
    
    // Configure core server features
    configureMonitoring()
    configureSerialization()
    configureCORS()
    configureSecurity(jwtConfig)
    configureStatusPages()
    configureWebSockets() // Add WebSocket configuration
    
    // Configure routes
    configureRouting()
    
    logger.info { "Application configuration completed" }
}
