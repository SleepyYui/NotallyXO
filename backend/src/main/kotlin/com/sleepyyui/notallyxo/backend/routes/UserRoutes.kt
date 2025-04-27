package com.sleepyyui.notallyxo.backend.routes

import com.sleepyyui.notallyxo.backend.models.api.UserProfileResponse
import com.sleepyyui.notallyxo.backend.plugins.dbQuery
import com.sleepyyui.notallyxo.backend.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import mu.KotlinLogging
import java.util.*

private val logger = KotlinLogging.logger {}
private val userRepository = UserRepository()

/**
 * Handles requests for user profile information.
 */
suspend fun getUserProfile(call: ApplicationCall) {
    try {
        // Get the current authenticated user from the JWT principal
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
            
        val userUUID = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format")
        }

        // Fetch the user from the database
        val user = dbQuery { userRepository.findUserById(userUUID) }
            ?: throw NoSuchElementException("User not found")

        // Return user profile information
        call.respond(
            HttpStatusCode.OK,
            UserProfileResponse(
                userId = user.id.toString(),
                username = user.username,
                displayName = user.username, // Using username as display name for now
                publicKey = user.publicKey
            )
        )
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in getUserProfile: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "User not found in getUserProfile: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "User not found"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in getUserProfile" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}
