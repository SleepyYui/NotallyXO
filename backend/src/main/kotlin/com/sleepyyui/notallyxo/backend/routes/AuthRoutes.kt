package com.sleepyyui.notallyxo.backend.routes

import com.sleepyyui.notallyxo.backend.models.api.AuthResponse
import com.sleepyyui.notallyxo.backend.plugins.dbQuery
import com.sleepyyui.notallyxo.backend.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val userRepository = UserRepository()

/**
 * Handles user authentication via token.
 * 
 * This endpoint expects a token in the Authorization header (format: "Bearer TOKEN")
 * and returns user information if the token is valid.
 */
suspend fun authenticateUser(call: ApplicationCall) {
    try {
        // Extract token from Authorization header
        val authHeader = call.request.header(HttpHeaders.Authorization)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respond(
                HttpStatusCode.Unauthorized,
                AuthResponse(success = false, message = "Authorization header missing or invalid")
            )
            return
        }

        val token = authHeader.substring("Bearer ".length)
        if (token.isBlank()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                AuthResponse(success = false, message = "Token cannot be blank")
            )
            return
        }

        // Validate token and get user
        val user = dbQuery { userRepository.findUserByToken(token) }
        
        if (user == null) {
            logger.info { "Authentication failed: Invalid token" }
            call.respond(
                HttpStatusCode.Unauthorized,
                AuthResponse(success = false, message = "Invalid authentication token")
            )
            return
        }
        
        // If we reach here, authentication was successful
        logger.info { "User authenticated: ${user.id}" }
        
        // Update last login timestamp
        dbQuery { userRepository.updateLastLogin(user.id) }
        
        call.respond(
            HttpStatusCode.OK,
            AuthResponse(
                success = true,
                userId = user.id.toString()
            )
        )
    } catch (e: Exception) {
        logger.error(e) { "Error during authentication" }
        call.respond(
            HttpStatusCode.InternalServerError,
            AuthResponse(success = false, message = "Authentication failed: ${e.message}")
        )
    }
}
