package com.sleepyyui.notallyxo.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configure Cross-Origin Resource Sharing (CORS) for the application.
 */
fun Application.configureCORS() {
    logger.info { "Configuring CORS" }
    
    install(CORS) {
        // Allow requests from any origin in development
        // In production, this should be restricted to specific domains
        anyHost()
        
        // HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        
        // Headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        
        // Allow sending auth credentials
        allowCredentials = true
        
        // How long the browser should cache CORS information
        maxAgeInSeconds = 3600 // 1 hour
    }
}
