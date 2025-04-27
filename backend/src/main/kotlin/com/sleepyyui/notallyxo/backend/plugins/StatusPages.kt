package com.sleepyyui.notallyxo.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configure status pages and error handling for the application.
 */
fun Application.configureStatusPages() {
    logger.info { "Configuring status pages" }
    
    install(StatusPages) {
        // Handle general exceptions
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal Server Error", "message" to "An unexpected error occurred")
            )
        }
        
        // Handle specific exceptions
        exception<SecurityException> { call, cause ->
            logger.warn { "Security exception: ${cause.message}" }
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "Access Denied", "message" to (cause.message ?: "You don't have permission to access this resource"))
            )
        }
        
        exception<IllegalArgumentException> { call, cause ->
            logger.warn { "Bad request: ${cause.message}" }
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Bad Request", "message" to (cause.message ?: "Invalid request parameters"))
            )
        }
        
        exception<NoSuchElementException> { call, cause ->
            logger.warn { "Resource not found: ${cause.message}" }
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Not Found", "message" to (cause.message ?: "The requested resource was not found"))
            )
        }
        
        // Handle 404 errors for undefined routes
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Not Found", "message" to "The requested endpoint does not exist")
            )
        }
        
        // Handle unauthorized requests
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized, 
                mapOf("error" to "Unauthorized", "message" to "Authentication is required to access this resource")
            )
        }
    }
}
