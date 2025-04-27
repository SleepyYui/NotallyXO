package com.sleepyyui.notallyxo.backend.routes

import com.google.gson.Gson
import com.sleepyyui.notallyxo.backend.models.api.*
import com.sleepyyui.notallyxo.backend.models.domain.Note
import com.sleepyyui.notallyxo.backend.plugins.broadcastUpdateToUser
import com.sleepyyui.notallyxo.backend.plugins.dbQuery
import com.sleepyyui.notallyxo.backend.repositories.NoteRepository
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
private val gson = Gson() // For creating JSON messages

/**
 * Get the current sync status from the server.
 */
suspend fun getSyncStatus(call: ApplicationCall) {
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

        // Get the user from the database
        val user = dbQuery { userRepository.findUserById(userUUID) }
            ?: throw NoSuchElementException("User not found")
        
        // Count the number of notes owned by this user
        val noteCount = dbQuery { noteRepository.countNotesByOwner(userUUID) }
        
        // Use current timestamp as last sync timestamp if there are no notes
        val currentTimestamp = System.currentTimeMillis()
        // For simplicity, we're using the current timestamp. In a real implementation, 
        // we would track the last sync timestamp per user.
        
        // Create and return the response
        val response = SyncStatusResponse(
            lastSyncTimestamp = currentTimestamp,
            noteCount = noteCount,
            pendingChanges = false // Always false for this simple implementation
        )
        
        call.respond(HttpStatusCode.OK, response)
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in getSyncStatus: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (e.message ?: "Bad request"))
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "User not found in getSyncStatus: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            mapOf("error" to (e.message ?: "User not found"))
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in getSyncStatus: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to "An internal server error occurred")
        )
    }
}

/**
 * Sync notes with the server.
 */
suspend fun syncNotes(call: ApplicationCall) {
    try {
        val syncRequest = call.receive<SyncRequest>()
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", String::class)
            ?: throw IllegalArgumentException("User ID not found in token")
        val userUUID = UUID.fromString(userId)
        
        val changedSince = syncRequest.lastSyncTimestamp
        val currentTimestamp = Instant.now()
        
        // Process changes from the client
        val newServerIds = mutableMapOf<String, String>() // Temporary ID to server ID mapping
        val conflicts = mutableListOf<NoteConflict>()
        val updatedNoteIds = mutableSetOf<UUID>()
        val deletedNoteIdsClient = mutableSetOf<UUID>()
        
        // Save changed notes from the client
        syncRequest.changedNotes.forEach { noteDto ->
            try {
                val noteId = UUID.fromString(noteDto.syncId)
                val existingNote = dbQuery { noteRepository.findNoteById(noteId) }
                
                // Check ownership or shared access (simplified for brevity)
                if (existingNote != null && existingNote.ownerUserId != userUUID) {
                    // Add proper shared access check here
                    throw SecurityException("User does not own this note")
                }
                
                val note = Note(
                    id = noteId,
                    ownerUserId = userUUID, // Assume ownership for now
                    title = noteDto.title,
                    encryptedContent = noteDto.content,
                    encryptionIv = noteDto.encryptionIv,
                    createdTimestamp = existingNote?.createdTimestamp ?: currentTimestamp, // Preserve original creation time
                    lastModifiedTimestamp = noteDto.lastModifiedTimestamp,
                    lastSyncedTimestamp = currentTimestamp,
                    type = noteDto.type,
                    isArchived = noteDto.isArchived,
                    isPinned = noteDto.isPinned,
                    isShared = noteDto.isShared,
                    labels = noteDto.labels
                )
                
                // Check for conflicts
                if (existingNote != null &&
                    existingNote.lastModifiedTimestamp > changedSince &&
                    existingNote.lastModifiedTimestamp != noteDto.lastModifiedTimestamp) {
                    
                    logger.warn { "Conflict detected for note $noteId" }
                    val serverNoteDto = NoteDto(
                        syncId = existingNote.id.toString(),
                        title = existingNote.title,
                        content = existingNote.encryptedContent,
                        encryptionIv = existingNote.encryptionIv,
                        lastModifiedTimestamp = existingNote.lastModifiedTimestamp,
                        lastSyncedTimestamp = existingNote.lastSyncedTimestamp,
                        type = existingNote.type,
                        isArchived = existingNote.isArchived,
                        isPinned = existingNote.isPinned,
                        isShared = existingNote.isShared,
                        labels = existingNote.labels,
                        ownerUserId = existingNote.ownerUserId.toString(),
                        sharedAccesses = emptyList() // Populate if needed
                    )
                    conflicts.add(NoteConflict(clientNote = noteDto, serverNote = serverNoteDto))
                } else {
                    // No conflict, save the note
                    dbQuery { noteRepository.saveNote(note) }
                    updatedNoteIds.add(noteId)
                    // Broadcast update to the user (excluding the current session if possible)
                    val updateMessage = mapOf("type" to "NOTE_UPDATED", "syncId" to noteId.toString())
                    broadcastUpdateToUser(userUUID, gson.toJson(updateMessage))
                }
            } catch (e: Exception) {
                logger.error(e) { "Error processing changed note ${noteDto.syncId}: ${e.message}" }
                // Optionally add to a list of failed updates to report back
            }
        }
        
        // Process deletions from the client
        syncRequest.deletedNoteIds.forEach { noteId ->
            try {
                val uuid = UUID.fromString(noteId)
                val deleted = dbQuery { 
                    val note = noteRepository.findNoteById(uuid)
                    // Only allow owner to delete
                    if (note != null && note.ownerUserId == userUUID) {
                        noteRepository.deleteNote(uuid)
                        true
                    } else {
                        logger.warn { "Attempt to delete note $uuid failed: Note not found or user $userUUID is not owner." }
                        false
                    }
                }
                if (deleted) {
                    deletedNoteIdsClient.add(uuid)
                    // Broadcast deletion to the user
                    val updateMessage = mapOf("type" to "NOTE_DELETED", "syncId" to noteId)
                    broadcastUpdateToUser(userUUID, gson.toJson(updateMessage))
                }
            } catch (e: Exception) {
                logger.error(e) { "Error processing deleted note $noteId: ${e.message}" }
                // Optionally add to a list of failed deletions
            }
        }
        
        // Get server changes since the client's last sync
        val serverChanges = dbQuery { 
            noteRepository.findNotesModifiedSince(userUUID, changedSince)
                .filter { note -> 
                    // Don't include notes that were just updated by this client or are in conflict
                    !updatedNoteIds.contains(note.id) && 
                    !conflicts.any { it.serverNote.syncId == note.id.toString() }
                }
                .map { note ->
                    NoteDto(
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
                        sharedAccesses = emptyList() // Populate if needed
                    )
                }
        }
        
        // Get server deletions (requires tracking deleted notes, simplified here)
        // In a real implementation, you'd query a 'deleted_notes' table or similar
        val serverDeletedIds = emptyList<String>() 

        // Create the sync response
        val syncResponse = SyncResponse(
            success = conflicts.isEmpty(), // Indicate success only if no conflicts
            lastSyncTimestamp = currentTimestamp,
            updatedNotes = serverChanges,
            deletedNoteIds = serverDeletedIds, // Send server-side deletions
            conflicts = conflicts,
            message = if (conflicts.isNotEmpty()) "Sync completed with conflicts." else "Sync successful."
        )
        
        call.respond(HttpStatusCode.OK, syncResponse)
    } catch (e: IllegalArgumentException) {
        logger.warn { "Bad request in syncNotes: ${e.message}" }
        call.respond(
            HttpStatusCode.BadRequest,
            SyncResponse(success = false, lastSyncTimestamp = Instant.EPOCH, message = e.message ?: "Bad request")
        )
    } catch (e: NoSuchElementException) {
        logger.warn { "Resource not found in syncNotes: ${e.message}" }
        call.respond(
            HttpStatusCode.NotFound,
            SyncResponse(success = false, lastSyncTimestamp = Instant.EPOCH, message = e.message ?: "Resource not found")
        )
    } catch (e: SecurityException) {
        logger.warn { "Access denied in syncNotes: ${e.message}" }
        call.respond(
            HttpStatusCode.Forbidden,
            SyncResponse(success = false, lastSyncTimestamp = Instant.EPOCH, message = e.message ?: "Access denied")
        )
    } catch (e: Exception) {
        logger.error(e) { "Error in syncNotes: ${e.message}" }
        call.respond(
            HttpStatusCode.InternalServerError,
            SyncResponse(success = false, lastSyncTimestamp = Instant.EPOCH, message = "An internal server error occurred")
        )
    }
}
