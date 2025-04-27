package com.sleepyyui.notallyxo.backend.repositories

import com.sleepyyui.notallyxo.backend.models.database.SharedAccesses
import com.sleepyyui.notallyxo.backend.models.domain.SharedAccess
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

/**
 * Repository class for shared access related database operations.
 */
class SharedAccessRepository {

    /**
     * Create a new shared access entry.
     *
     * @param noteId The ID of the note being shared
     * @param userId The ID of the user to share with
     * @param accessLevel The level of access (READ_ONLY or READ_WRITE)
     * @return The created SharedAccess object
     */
    fun createSharedAccess(
        noteId: UUID,
        userId: UUID,
        accessLevel: String
    ): SharedAccess {
        val id = UUID.randomUUID()
        val now = Instant.now()
        
        // First check if this access already exists
        val existingAccess = SharedAccesses.select {
            (SharedAccesses.noteId eq noteId) and (SharedAccesses.userId eq userId)
        }.singleOrNull()
        
        if (existingAccess != null) {
            // Update the existing entry instead of creating a new one
            SharedAccesses.update({
                (SharedAccesses.noteId eq noteId) and (SharedAccesses.userId eq userId)
            }) {
                it[SharedAccesses.accessLevel] = accessLevel
                it[grantedTimestamp] = now
            }
            
            return SharedAccess(
                id = existingAccess[SharedAccesses.id].value,
                noteId = noteId,
                userId = userId,
                accessLevel = accessLevel,
                grantedTimestamp = now,
                lastAccessedTimestamp = null,
                usedToken = existingAccess[SharedAccesses.usedToken]
            )
        }
        
        // Create a new shared access entry
        SharedAccesses.insert {
            it[SharedAccesses.id] = id
            it[SharedAccesses.noteId] = noteId
            it[SharedAccesses.userId] = userId
            it[SharedAccesses.accessLevel] = accessLevel
            it[grantedTimestamp] = now
            it[lastAccessedTimestamp] = null
            it[usedToken] = null
        }
        
        return SharedAccess(
            id = id,
            noteId = noteId,
            userId = userId,
            accessLevel = accessLevel,
            grantedTimestamp = now,
            lastAccessedTimestamp = null,
            usedToken = null
        )
    }
    
    /**
     * Get all shared accesses for a specific note.
     *
     * @param noteId The ID of the note
     * @return List of SharedAccess objects
     */
    fun getSharedAccessesForNote(noteId: UUID): List<SharedAccess> {
        return SharedAccesses.select { SharedAccesses.noteId eq noteId }
            .map { row ->
                SharedAccess(
                    id = row[SharedAccesses.id].value,
                    noteId = noteId,
                    userId = row[SharedAccesses.userId],
                    accessLevel = row[SharedAccesses.accessLevel],
                    grantedTimestamp = row[SharedAccesses.grantedTimestamp],
                    lastAccessedTimestamp = row[SharedAccesses.lastAccessedTimestamp],
                    usedToken = row[SharedAccesses.usedToken]
                )
            }
    }
    
    /**
     * Get all notes shared with a specific user.
     *
     * @param userId The ID of the user
     * @return List of note IDs
     */
    fun getNotesSharedWithUser(userId: UUID): List<UUID> {
        return SharedAccesses.select { SharedAccesses.userId eq userId }
            .map { it[SharedAccesses.noteId] }
    }
    
    /**
     * Update the last accessed timestamp for a shared note.
     *
     * @param noteId The ID of the note
     * @param userId The ID of the user
     * @return Whether the update was successful
     */
    fun updateLastAccessedTimestamp(noteId: UUID, userId: UUID): Boolean {
        val updated = SharedAccesses.update({
            (SharedAccesses.noteId eq noteId) and (SharedAccesses.userId eq userId)
        }) {
            it[lastAccessedTimestamp] = Instant.now()
        }
        
        return updated > 0
    }
    
    /**
     * Remove shared access for a user to a note.
     *
     * @param noteId The ID of the note
     * @param userId The ID of the user
     * @return Whether the deletion was successful
     */
    fun removeSharedAccess(noteId: UUID, userId: UUID): Boolean {
        val deleted = SharedAccesses.deleteWhere { 
            (SharedAccesses.noteId eq noteId) and (SharedAccesses.userId eq userId)
        }
        
        return deleted > 0
    }
}
