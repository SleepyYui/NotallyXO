package com.sleepyyui.notallyxo.backend.routes

import com.google.gson.Gson
import com.sleepyyui.notallyxo.backend.models.api.NoteDto
import com.sleepyyui.notallyxo.backend.models.api.SharedAccessDto
import com.sleepyyui.notallyxo.backend.models.domain.Note
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
import java.time.Instant
import java.util.*

private val logger = KotlinLogging.logger {}
private val noteRepository = NoteRepository()
private val userRepository = UserRepository()
private val sharedAccessRepository = SharedAccessRepository()
private val gson = Gson()

/**
 * Get a specific note by its sync ID.
 */
suspend fun getNote(call: ApplicationCall) {
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
        
        // Fetch the note from the database
        val note = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")

        // Fetch shared accesses for this note
        val sharedAccesses = dbQuery { sharedAccessRepository.getSharedAccessesForNote(noteId) }
        val userHasAccess = sharedAccesses.any { it.userId == userUUID }

        // Security check: allow access if owner or has shared access
        if (note.ownerUserId != userUUID && !userHasAccess) {
            throw SecurityException("Not authorized to access this note")
        }
        
        // Convert domain model to DTO
        val noteDto = NoteDto(
            syncId = note.id.toString(),
            title = note.title,
            content = note.encryptedContent,
            encryptionIv = note.encryptionIv,
            lastModifiedTimestamp = note.lastModifiedTimestamp,
            lastSyncedTimestamp = note.lastSyncedTimestamp,
            type = note.type,
            isArchived = note.isArchived,
            isPinned = note.isPinned,
            isShared = note.isShared,
            labels = note.labels,
            ownerUserId = note.ownerUserId.toString(),
            sharedAccesses = sharedAccesses.map {
                SharedAccessDto(
                    userId = it.userId.toString(),
                    accessLevel = it.accessLevel
                )
            }
        )
        
        call.respond(HttpStatusCode.OK, noteDto)
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in getNote: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "Note not found in getNote: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "Note not found"))
        )
    } catch (e: SecurityException) {
        logger.warn { "Access denied in getNote: ${e.message}" }
        call.respond(
            HttpStatusCode.Forbidden,
            mapOf("error" to (e.message ?: "Access denied"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in getNote" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}

/**
 * Create or update a note.
 */
suspend fun putNote(call: ApplicationCall) {
    try {
        // Extract note ID from path parameter
        val syncIdParam = call.parameters["syncId"]
            ?: throw IllegalArgumentException("Missing syncId parameter")
        
        // Parse the UUID
        val syncId = try {
            UUID.fromString(syncIdParam)
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
        
        // Parse the note DTO from the request body
        val noteDto = call.receive<NoteDto>()
        
        // Ensure the path parameter matches the request body
        if (syncIdParam != noteDto.syncId) {
            throw IllegalArgumentException("Path syncId does not match request body syncId")
        }
        
        // Check if note exists
        val existingNote = dbQuery { noteRepository.findNoteById(syncId) }
        val sharedAccesses = if (existingNote != null) {
            dbQuery { sharedAccessRepository.getSharedAccessesForNote(syncId) }
        } else {
            emptyList()
        }
        val userSharedAccess = sharedAccesses.find { it.userId == userUUID }

        // Security check: allow update if owner or has READ_WRITE access
        if (existingNote != null && existingNote.ownerUserId != userUUID && userSharedAccess?.accessLevel != "READ_WRITE") {
            throw SecurityException("Not authorized to modify this note")
        }
        
        // Convert DTO to domain model
        val note = Note(
            id = syncId,
            title = noteDto.title,
            type = noteDto.type,
            isPinned = noteDto.isPinned,
            isArchived = noteDto.isArchived,
            isShared = noteDto.isShared,
            encryptedContent = noteDto.content,
            encryptionIv = noteDto.encryptionIv,
            createdAt = existingNote?.createdAt ?: Instant.now(),
            lastModifiedTimestamp = noteDto.lastModifiedTimestamp,
            lastSyncedTimestamp = noteDto.lastSyncedTimestamp,
            ownerUserId = existingNote?.ownerUserId ?: userUUID,
            labels = noteDto.labels
        )
        
        // Save the note
        val savedNote = dbQuery { noteRepository.saveNote(note) }

        // Fetch updated shared accesses
        val updatedSharedAccesses = dbQuery { sharedAccessRepository.getSharedAccessesForNote(savedNote.id) }
        
        // Convert saved note back to DTO
        val savedNoteDto = NoteDto(
            syncId = savedNote.id.toString(),
            title = savedNote.title,
            content = savedNote.encryptedContent,
            encryptionIv = savedNote.encryptionIv,
            lastModifiedTimestamp = savedNote.lastModifiedTimestamp,
            lastSyncedTimestamp = savedNote.lastSyncedTimestamp,
            type = savedNote.type,
            isArchived = savedNote.isArchived,
            isPinned = savedNote.isPinned,
            isShared = savedNote.isShared,
            labels = savedNote.labels,
            ownerUserId = savedNote.ownerUserId.toString(),
            sharedAccesses = updatedSharedAccesses.map {
                SharedAccessDto(
                    userId = it.userId.toString(),
                    accessLevel = it.accessLevel
                )
            }
        )

        // Broadcast update to owner and shared users
        val updateMessage = mapOf("type" to "NOTE_UPDATED", "syncId" to savedNote.id.toString())
        val messageJson = gson.toJson(updateMessage)
        if (savedNote.ownerUserId != userUUID) {
            broadcastUpdateToUser(savedNote.ownerUserId, messageJson)
        }
        updatedSharedAccesses.filter { it.userId != userUUID }.forEach { access ->
            broadcastUpdateToUser(access.userId, messageJson)
        }
        
        call.respond(HttpStatusCode.OK, savedNoteDto)
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in putNote: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: SecurityException) {
        logger.warn { "Access denied in putNote: ${e.message}" }
        call.respond(
            HttpStatusCode.Forbidden,
            mapOf("error" to (e.message ?: "Access denied"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in putNote: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}

/**
 * Delete a note by its sync ID.
 */
suspend fun deleteNote(call: ApplicationCall) {
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
        
        // Check if note exists
        val existingNote = dbQuery { noteRepository.findNoteById(noteId) }
            ?: throw NoSuchElementException("Note not found")

        // Fetch shared accesses before deletion
        val sharedAccesses = dbQuery { sharedAccessRepository.getSharedAccessesForNote(noteId) }

        // Security check: only the owner can delete
        if (existingNote.ownerUserId != userUUID) {
            throw SecurityException("Not authorized to delete this note")
        }
        
        // Delete the note
        val deleted = dbQuery { noteRepository.deleteNote(noteId) }
        
        if (deleted) {
            // Broadcast deletion to shared users
            val deleteMessage = mapOf("type" to "NOTE_DELETED", "syncId" to noteId.toString())
            val messageJson = gson.toJson(deleteMessage)
            sharedAccesses.forEach { access ->
                broadcastUpdateToUser(access.userId, messageJson)
            }

            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        } else {
            throw Exception("Failed to delete note")
        }
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in deleteNote: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "Note not found in deleteNote: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "Note not found"))
        )
    } catch (e: SecurityException) {
        logger.warn { "Access denied in deleteNote: ${e.message}" }
        call.respond(
            HttpStatusCode.Forbidden,
            mapOf("error" to (e.message ?: "Access denied"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in deleteNote: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}
