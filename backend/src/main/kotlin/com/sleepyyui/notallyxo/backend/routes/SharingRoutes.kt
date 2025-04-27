package com.sleepyyui.notallyxo.backend.routes

import com.google.gson.Gson
import com.sleepyyui.notallyxo.backend.models.api.NoteDto
import com.sleepyyui.notallyxo.backend.models.api.ShareNoteRequest
import com.sleepyyui.notallyxo.backend.models.api.SharingTokenResponse
import com.sleepyyui.notallyxo.backend.models.database.SharedAccesses
import com.sleepyyui.notallyxo.backend.models.database.SharingTokens
import com.sleepyyui.notallyxo.backend.plugins.broadcastUpdateToUser
import com.sleepyyui.notallyxo.backend.plugins.dbQuery
import com.sleepyyui.notallyxo.backend.repositories.NoteRepository
import com.sleepyyui.notallyxo.backend.repositories.SharedAccessRepository
import com.sleepyyui.notallyxo.backend.repositories.SharingTokenRepository
import com.sleepyyui.notallyxo.backend.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}
private val noteRepository = NoteRepository()
private val userRepository = UserRepository()
private val sharingTokenRepository = SharingTokenRepository()
private val sharedAccessRepository = SharedAccessRepository()
private val gson = Gson()

/**
 * Share a note with another user.
 */
suspend fun shareNote(call: ApplicationCall) {
    try {
        // Extract note ID from path parameter
        val syncId = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        
        // Parse the UUID
        val noteId = try {
            UUID.fromString(syncId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid note ID format")
        }
        
        // Get the current authenticated user from the JWT principal
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        
        val userUUID = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format")
        }
        
        // Get user ID and access level from query parameters or request body
        val queryUserId = call.request.queryParameters["user_id"]
        val queryAccessLevel = call.request.queryParameters["access_level"]
        
        val shareRequest = if (queryUserId != null && queryAccessLevel != null) {
            // Use query parameters
            ShareNoteRequest(queryUserId, queryAccessLevel)
        } else {
            // Use request body
            call.receive<ShareNoteRequest>()
        }
        
        // Validate target user ID
        val targetUserUUID = try {
            UUID.fromString(shareRequest.userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid target user ID format")
        }
        
        // Validate access level
        if (shareRequest.accessLevel != "READ_ONLY" && shareRequest.accessLevel != "READ_WRITE") {
            throw IllegalArgumentException("Access level must be READ_ONLY or READ_WRITE")
        }
        
        // Check if note exists and belongs to the user
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")
        
        // Security check: only the owner can share
        if (note.ownerUserId != userUUID) {
            throw SecurityException("Not authorized to share this note")
        }
        
        // Check if target user exists
        val targetUser = dbQuery { userRepository.findUserById(targetUserUUID) }
            ?: throw NoSuchElementException("Target user not found")
        
        // Create shared access
        val wasAlreadyShared = note.isShared
        dbQuery {
            sharedAccessRepository.createSharedAccess(
                noteId = noteId,
                userId = targetUserUUID,
                accessLevel = shareRequest.accessLevel
            )
            
            // Update note sharing status if not already shared
            if (!wasAlreadyShared) {
                noteRepository.updateNoteSharing(noteId, true)
            }
        }

        // Broadcast update to the target user that the note is now shared with them
        val updateMessageTarget = mapOf("type" to "NOTE_SHARED", "syncId" to noteId.toString())
        broadcastUpdateToUser(targetUserUUID, gson.toJson(updateMessageTarget))

        // Broadcast update to the owner that the note's sharing status/access list changed
        val updateMessageOwner = mapOf("type" to "NOTE_UPDATED", "syncId" to noteId.toString())
        broadcastUpdateToUser(userUUID, gson.toJson(updateMessageOwner))
        
        // Return the updated note
        val updatedNote = dbQuery { noteRepository.findNoteById(noteId) }!!
        
        // Convert to DTO
        val noteDto = NoteDto(
            syncId = updatedNote.id.toString(),
            title = updatedNote.title,
            content = updatedNote.encryptedContent,
            encryptionIv = updatedNote.encryptionIv,
            lastModifiedTimestamp = updatedNote.lastModifiedTimestamp,
            lastSyncedTimestamp = updatedNote.lastSyncedTimestamp,
            type = updatedNote.type,
            isArchived = updatedNote.isArchived,
            isPinned = updatedNote.isPinned,
            isShared = true,
            labels = updatedNote.labels,
            ownerUserId = updatedNote.ownerUserId.toString(),
            sharedAccesses = dbQuery { sharedAccessRepository.getSharedAccessesForNote(noteId) }
                .map { sharedAccess ->
                    com.sleepyyui.notallyxo.backend.models.api.SharedAccessDto(
                        userId = sharedAccess.userId.toString(),
                        accessLevel = sharedAccess.accessLevel
                    )
                }
        )
        
        call.respond(HttpStatusCode.OK, noteDto)
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in shareNote: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "Resource not found in shareNote: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "Resource not found"))
        )
    } catch (e: SecurityException) {
        logger.warn { "Access denied in shareNote: ${e.message}" }
        call.respond(
            HttpStatusCode.Forbidden,
            mapOf("error" to (e.message ?: "Access denied"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in shareNote: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}

/**
 * Create a one-time sharing token for a note.
 */
suspend fun createSharingToken(call: ApplicationCall) {
    try {
        // Extract note ID from path parameter
        val syncId = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        
        // Parse the UUID
        val noteId = try {
            UUID.fromString(syncId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid note ID format")
        }
        
        // Get the current authenticated user from the JWT principal
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        
        val userUUID = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format")
        }
        
        // Get access level from query parameters
        val accessLevel = call.request.queryParameters["access_level"]
            ?: throw IllegalArgumentException("Missing access_level parameter")
        
        // Validate access level
        if (accessLevel != "READ_ONLY" && accessLevel != "READ_WRITE") {
            throw IllegalArgumentException("Access level must be READ_ONLY or READ_WRITE")
        }
        
        // Get expiry time if provided
        val expiryTime = call.request.queryParameters["expiry_time"]?.toLongOrNull()
        val expiryInstant = if (expiryTime != null && expiryTime > 0) {
            Instant.ofEpochMilli(expiryTime)
        } else {
            null
        }
        
        // Check if note exists and belongs to the user
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")
        
        // Security check: only the owner can create sharing tokens
        if (note.ownerUserId != userUUID) {
            throw SecurityException("Not authorized to share this note")
        }
        
        // Create sharing token
        val tokenId = UUID.randomUUID()
        dbQuery {
            sharingTokenRepository.createSharingToken(
                id = tokenId,
                noteId = noteId,
                accessLevel = accessLevel,
                expirationTimestamp = expiryInstant
            )
            
            // Update note sharing status
            noteRepository.updateNoteSharing(noteId, true)
        }
        
        call.respond(
            HttpStatusCode.OK,
            SharingTokenResponse(token = tokenId.toString())
        )
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in createSharingToken: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "Resource not found in createSharingToken: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "Resource not found"))
        )
    } catch (e: SecurityException) {
        logger.warn { "Access denied in createSharingToken: ${e.message}" }
        call.respond(
            HttpStatusCode.Forbidden,
            mapOf("error" to (e.message ?: "Access denied"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in createSharingToken: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}

/**
 * Accept a shared note using a token.
 */
suspend fun acceptSharedNote(call: ApplicationCall) {
    try {
        // Get token from query parameter
        val token = call.request.queryParameters["token"]
            ?: throw IllegalArgumentException("Missing token parameter")
        
        // Parse the UUID
        val tokenId = try {
            UUID.fromString(token)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid token format")
        }
        
        // Get the current authenticated user from the JWT principal
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        
        val userUUID = try {
            UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format")
        }
        
        // Get the sharing token
        val sharingToken = dbQuery { sharingTokenRepository.findTokenById(tokenId) }
            ?: throw NoSuchElementException("Token not found")
        
        // Check if token is valid
        if (sharingToken.isUsed) {
            throw IllegalArgumentException("Token has already been used")
        }
        
        if (sharingToken.expirationTimestamp != null && 
            sharingToken.expirationTimestamp.isBefore(Instant.now())) {
            throw IllegalArgumentException("Token has expired")
        }
        
        // Get the note
        val note = dbQuery { noteRepository.findNoteById(sharingToken.noteId) }
            ?: throw NoSuchElementException("Note not found")
        
        // Create shared access and mark token as used
        val wasAlreadyShared = note.isShared
        dbQuery {
            sharedAccessRepository.createSharedAccess(
                noteId = sharingToken.noteId,
                userId = userUUID,
                accessLevel = sharingToken.accessLevel
            )
            
            // Mark token as used
            sharingTokenRepository.markTokenAsUsed(tokenId, userUUID)

            // Update note sharing status if not already shared
            if (!wasAlreadyShared) {
                noteRepository.updateNoteSharing(sharingToken.noteId, true)
            }
        }

        // Broadcast update to the accepting user that they now have access
        val updateMessageAcceptingUser = mapOf("type" to "NOTE_SHARED", "syncId" to sharingToken.noteId.toString())
        broadcastUpdateToUser(userUUID, gson.toJson(updateMessageAcceptingUser))

        // Broadcast update to the owner that someone accepted the share
        val updateMessageOwner = mapOf("type" to "NOTE_UPDATED", "syncId" to sharingToken.noteId.toString())
        broadcastUpdateToUser(note.ownerUserId, gson.toJson(updateMessageOwner))
        
        // Return the note DTO
        val updatedNote = dbQuery { noteRepository.findNoteById(sharingToken.noteId) }!!
        val noteDto = NoteDto(
            syncId = updatedNote.id.toString(),
            title = updatedNote.title,
            content = updatedNote.encryptedContent,
            encryptionIv = updatedNote.encryptionIv,
            lastModifiedTimestamp = updatedNote.lastModifiedTimestamp,
            lastSyncedTimestamp = updatedNote.lastSyncedTimestamp,
            type = updatedNote.type,
            isArchived = updatedNote.isArchived,
            isPinned = updatedNote.isPinned,
            isShared = true,
            labels = updatedNote.labels,
            ownerUserId = updatedNote.ownerUserId.toString(),
            sharedAccesses = dbQuery { sharedAccessRepository.getSharedAccessesForNote(updatedNote.id) }
                .map { sharedAccess ->
                    com.sleepyyui.notallyxo.backend.models.api.SharedAccessDto(
                        userId = sharedAccess.userId.toString(),
                        accessLevel = sharedAccess.accessLevel
                    )
                }
        )
        
        call.respond(HttpStatusCode.OK, noteDto)
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in acceptSharedNote: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "Resource not found in acceptSharedNote: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "Resource not found"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in acceptSharedNote: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}
