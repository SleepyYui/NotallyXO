package com.sleepyyui.notallyxo.backend.routes

import com.google.gson.Gson
import com.sleepyyui.notallyxo.backend.models.api.ShareRequestDto
import com.sleepyyui.notallyxo.backend.models.api.SharedAccessDto
import com.sleepyyui.notallyxo.backend.models.api.UpdateShareRequestDto
import com.sleepyyui.notallyxo.backend.models.domain.SharedAccess
import com.sleepyyui.notallyxo.backend.plugins.broadcastUpdateToUser
import com.sleepyyui.notallyxo.backend.plugins.dbQuery
import com.sleepyyui.notallyxo.backend.repositories.NoteRepository
import com.sleepyyui.notallyxo.backend.repositories.SharedAccessRepository
import com.sleepyyui.notallyxo.backend.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging
import java.util.*

private val logger = KotlinLogging.logger {}
private val noteRepository = NoteRepository()
private val userRepository = UserRepository()
private val sharedAccessRepository = SharedAccessRepository()
private val gson = Gson()

/**
 * Share a note with another user.
 */
suspend fun shareNote(call: ApplicationCall) {
    try {
        // Extract note ID from path parameter
        val syncIdParam = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        val noteId = try {
            UUID.fromString(syncIdParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid note ID format")
        }

        // Get the current authenticated user (owner)
        val principal = call.principal<JWTPrincipal>()
        val ownerUserIdStr = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        val ownerUserId = try {
            UUID.fromString(ownerUserIdStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid owner user ID format")
        }

        // Parse the share request from the body
        val shareRequest = call.receive<ShareRequestDto>()
        val targetUserId = try {
            UUID.fromString(shareRequest.userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid target user ID format")
        }

        // Validate access level
        if (shareRequest.accessLevel !in listOf("READ_ONLY", "READ_WRITE")) {
            throw IllegalArgumentException("Invalid access level. Must be READ_ONLY or READ_WRITE.")
        }

        // Fetch the note
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")

        // Security check: Only the owner can share the note
        if (note.ownerUserId != ownerUserId) {
            throw SecurityException("Only the owner can share this note")
        }

        // Check if target user exists
        val targetUser = dbQuery { userRepository.findUserById(targetUserId) }
            ?: throw NoSuchElementException("Target user not found")

        // Prevent sharing with self
        if (ownerUserId == targetUserId) {
            throw IllegalArgumentException("Cannot share a note with yourself")
        }

        // Check if already shared with this user
        val existingAccess = dbQuery { sharedAccessRepository.findSharedAccess(noteId, targetUserId) }
        if (existingAccess != null) {
            throw IllegalArgumentException("Note is already shared with this user. Use PUT to update access.")
        }

        // Create the shared access record
        val newSharedAccess = SharedAccess(
            noteId = noteId,
            userId = targetUserId,
            accessLevel = shareRequest.accessLevel
        )
        val savedAccess = dbQuery { sharedAccessRepository.addSharedAccess(newSharedAccess) }

        // Update the note's isShared flag if it's not already true
        if (!note.isShared) {
            val updatedNote = note.copy(isShared = true)
            dbQuery { noteRepository.saveNote(updatedNote) }
        }

        // Broadcast update to the user the note was shared with
        val shareMessage = mapOf("type" to "NOTE_SHARED", "syncId" to noteId.toString())
        broadcastUpdateToUser(targetUserId, gson.toJson(shareMessage))

        // Broadcast update to the owner (and potentially other shared users in future)
        val ownerUpdateMessage = mapOf("type" to "NOTE_ACCESS_CHANGED", "syncId" to noteId.toString())
        broadcastUpdateToUser(ownerUserId, gson.toJson(ownerUpdateMessage))

        // Respond with the created shared access details
        val sharedAccessDto = SharedAccessDto(
            userId = savedAccess.userId.toString(),
            accessLevel = savedAccess.accessLevel
        )
        call.respond(HttpStatusCode.Created, sharedAccessDto)

    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in shareNote: ${e.message}" }
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
    } catch (e: NoSuchElementException) {
        logger.warn { "Not found in shareNote: ${e.message}" }
        call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Not found")))
    } catch (e: SecurityException) {
        logger.warn { "Access denied in shareNote: ${e.message}" }
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Access denied")))
    } catch (e: Exception) {
        logger.error(e) { "Error in shareNote: ${e.message}" }
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An internal server error occurred"))
    }
}

/**
 * Get all shared accesses for a specific note.
 */
suspend fun getSharedAccessesForNote(call: ApplicationCall) {
    try {
        // Extract note ID from path parameter
        val syncIdParam = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        val noteId = try {
            UUID.fromString(syncIdParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid note ID format")
        }

        // Get the current authenticated user
        val principal = call.principal<JWTPrincipal>()
        val currentUserIdStr = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        val currentUserId = try {
            UUID.fromString(currentUserIdStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid current user ID format")
        }

        // Fetch the note
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")

        // Security check: Only the owner can see all shares
        if (note.ownerUserId != currentUserId) {
            throw SecurityException("Only the owner can view sharing details for this note")
        }

        // Fetch shared accesses
        val sharedAccesses = dbQuery { sharedAccessRepository.getSharedAccessesForNote(noteId) }

        // Convert to DTOs
        val sharedAccessDtos = sharedAccesses.map {
            SharedAccessDto(
                userId = it.userId.toString(),
                accessLevel = it.accessLevel
            )
        }

        call.respond(HttpStatusCode.OK, sharedAccessDtos)

    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in getSharedAccessesForNote: ${e.message}" }
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
    } catch (e: NoSuchElementException) {
        logger.warn { "Not found in getSharedAccessesForNote: ${e.message}" }
        call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Not found")))
    } catch (e: SecurityException) {
        logger.warn { "Access denied in getSharedAccessesForNote: ${e.message}" }
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Access denied")))
    } catch (e: Exception) {
        logger.error(e) { "Error in getSharedAccessesForNote: ${e.message}" }
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An internal server error occurred"))
    }
}

/**
 * Update the access level for a user a note is shared with.
 */
suspend fun updateSharedAccess(call: ApplicationCall) {
    try {
        // Extract note ID and target user ID from path parameters
        val syncIdParam = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        val noteId = try {
            UUID.fromString(syncIdParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid note ID format")
        }

        val targetUserIdParam = call.parameters["userId"]
            ?: throw IllegalArgumentException("Missing userId parameter")
        val targetUserId = try {
            UUID.fromString(targetUserIdParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid target user ID format")
        }

        // Get the current authenticated user (owner)
        val principal = call.principal<JWTPrincipal>()
        val ownerUserIdStr = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        val ownerUserId = try {
            UUID.fromString(ownerUserIdStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid owner user ID format")
        }

        // Parse the update request from the body
        val updateRequest = call.receive<UpdateShareRequestDto>()

        // Validate access level
        if (updateRequest.accessLevel !in listOf("READ_ONLY", "READ_WRITE")) {
            throw IllegalArgumentException("Invalid access level. Must be READ_ONLY or READ_WRITE.")
        }

        // Fetch the note
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")

        // Security check: Only the owner can modify sharing permissions
        if (note.ownerUserId != ownerUserId) {
            throw SecurityException("Only the owner can modify sharing permissions for this note")
        }

        // Fetch the existing shared access record
        val existingAccess = dbQuery { sharedAccessRepository.findSharedAccess(noteId, targetUserId) }
            ?: throw NoSuchElementException("No existing share found for this user and note")

        // Update the access level
        val updatedAccess = existingAccess.copy(accessLevel = updateRequest.accessLevel)
        val savedAccess = dbQuery { sharedAccessRepository.updateSharedAccess(updatedAccess) }

        // Broadcast update to the affected user
        val updateMessage = mapOf("type" to "NOTE_ACCESS_CHANGED", "syncId" to noteId.toString())
        broadcastUpdateToUser(targetUserId, gson.toJson(updateMessage))
        // Also notify owner
        broadcastUpdateToUser(ownerUserId, gson.toJson(updateMessage))

        // Respond with the updated shared access details
        val sharedAccessDto = SharedAccessDto(
            userId = savedAccess.userId.toString(),
            accessLevel = savedAccess.accessLevel
        )
        call.respond(HttpStatusCode.OK, sharedAccessDto)

    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in updateSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
    } catch (e: NoSuchElementException) {
        logger.warn { "Not found in updateSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Not found")))
    } catch (e: SecurityException) {
        logger.warn { "Access denied in updateSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Access denied")))
    } catch (e: Exception) {
        logger.error(e) { "Error in updateSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An internal server error occurred"))
    }
}

/**
 * Revoke a user's access to a shared note.
 */
suspend fun revokeSharedAccess(call: ApplicationCall) {
    try {
        // Extract note ID and target user ID from path parameters
        val syncIdParam = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        val noteId = try {
            UUID.fromString(syncIdParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid note ID format")
        }

        val targetUserIdParam = call.parameters["userId"]
            ?: throw IllegalArgumentException("Missing userId parameter")
        val targetUserId = try {
            UUID.fromString(targetUserIdParam)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid target user ID format")
        }

        // Get the current authenticated user (owner)
        val principal = call.principal<JWTPrincipal>()
        val ownerUserIdStr = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        val ownerUserId = try {
            UUID.fromString(ownerUserIdStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid owner user ID format")
        }

        // Fetch the note
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")

        // Security check: Only the owner can revoke access
        if (note.ownerUserId != ownerUserId) {
            throw SecurityException("Only the owner can revoke access to this note")
        }

        // Check if the share exists before attempting deletion
        val existingAccess = dbQuery { sharedAccessRepository.findSharedAccess(noteId, targetUserId) }
            ?: throw NoSuchElementException("No existing share found for this user and note to revoke")

        // Delete the shared access record
        val deleted = dbQuery { sharedAccessRepository.deleteSharedAccess(noteId, targetUserId) }

        if (deleted) {
            // Check if this was the last share and update note's isShared flag if necessary
            val remainingShares = dbQuery { sharedAccessRepository.getSharedAccessesForNote(noteId) }
            if (remainingShares.isEmpty() && note.isShared) {
                val updatedNote = note.copy(isShared = false)
                dbQuery { noteRepository.saveNote(updatedNote) }
            }

            // Broadcast update to the user whose access was revoked
            val revokeMessage = mapOf("type" to "NOTE_ACCESS_REVOKED", "syncId" to noteId.toString())
            broadcastUpdateToUser(targetUserId, gson.toJson(revokeMessage))
            // Also notify owner
            val ownerUpdateMessage = mapOf("type" to "NOTE_ACCESS_CHANGED", "syncId" to noteId.toString())
            broadcastUpdateToUser(ownerUserId, gson.toJson(ownerUpdateMessage))

            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        } else {
            throw Exception("Failed to revoke shared access")
        }

    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in revokeSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Bad request")))
    } catch (e: NoSuchElementException) {
        logger.warn { "Not found in revokeSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Not found")))
    } catch (e: SecurityException) {
        logger.warn { "Access denied in revokeSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Access denied")))
    } catch (e: Exception) {
        logger.error(e) { "Error in revokeSharedAccess: ${e.message}" }
        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An internal server error occurred"))
    }
}
