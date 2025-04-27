package com.sleepyyui.notallyxo.backend.repositories

import com.google.gson.Gson
import com.sleepyyui.notallyxo.backend.models.database.Notes
import com.sleepyyui.notallyxo.backend.models.domain.Note
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

/**
 * Repository class for note-related database operations.
 */
class NoteRepository {
    
    private val gson = Gson()
    
    /**
     * Find a note by its UUID (sync ID).
     *
     * @param noteId The note ID to search for
     * @return Note object if found, null otherwise
     */
    fun findNoteById(noteId: UUID): Note? {
        return Notes.select { Notes.id eq noteId }
            .singleOrNull()
            ?.let { resultRow ->
                Note(
                    id = resultRow[Notes.id].value,
                    title = resultRow[Notes.title],
                    type = resultRow[Notes.type],
                    isPinned = resultRow[Notes.isPinned],
                    isArchived = resultRow[Notes.isArchived],
                    isShared = resultRow[Notes.isShared],
                    encryptedContent = resultRow[Notes.encryptedContent],
                    encryptionIv = resultRow[Notes.encryptionIv],
                    createdAt = resultRow[Notes.createdAt],
                    lastModifiedTimestamp = resultRow[Notes.lastModifiedTimestamp],
                    lastSyncedTimestamp = resultRow[Notes.lastSyncedTimestamp],
                    ownerUserId = resultRow[Notes.ownerUserId],
                    labels = resultRow[Notes.labels]?.let {
                        gson.fromJson(it, Array<String>::class.java).toList()
                    }
                )
            }
    }

    /**
     * Find all notes owned by a specific user.
     *
     * @param userId The user ID to search for
     * @return List of notes owned by the user
     */
    fun findNotesByOwner(userId: UUID): List<Note> {
        return Notes.select { Notes.ownerUserId eq userId }
            .map { resultRow ->
                Note(
                    id = resultRow[Notes.id].value,
                    title = resultRow[Notes.title],
                    type = resultRow[Notes.type],
                    isPinned = resultRow[Notes.isPinned],
                    isArchived = resultRow[Notes.isArchived],
                    isShared = resultRow[Notes.isShared],
                    encryptedContent = resultRow[Notes.encryptedContent],
                    encryptionIv = resultRow[Notes.encryptionIv],
                    createdAt = resultRow[Notes.createdAt],
                    lastModifiedTimestamp = resultRow[Notes.lastModifiedTimestamp],
                    lastSyncedTimestamp = resultRow[Notes.lastSyncedTimestamp],
                    ownerUserId = resultRow[Notes.ownerUserId],
                    labels = resultRow[Notes.labels]?.let {
                        gson.fromJson(it, Array<String>::class.java).toList()
                    }
                )
            }
    }

    /**
     * Find notes modified since a specific timestamp for a user.
     *
     * @param userId The user ID
     * @param timestamp The timestamp to check against
     * @return List of notes modified since the timestamp
     */
    fun findNotesModifiedSince(userId: UUID, timestamp: Long): List<Note> {
        return Notes.select { 
            (Notes.ownerUserId eq userId) and (Notes.lastModifiedTimestamp greater timestamp)
        }
        .map { resultRow ->
            Note(
                id = resultRow[Notes.id].value,
                title = resultRow[Notes.title],
                type = resultRow[Notes.type],
                isPinned = resultRow[Notes.isPinned],
                isArchived = resultRow[Notes.isArchived],
                isShared = resultRow[Notes.isShared],
                encryptedContent = resultRow[Notes.encryptedContent],
                encryptionIv = resultRow[Notes.encryptionIv],
                createdAt = resultRow[Notes.createdAt],
                lastModifiedTimestamp = resultRow[Notes.lastModifiedTimestamp],
                lastSyncedTimestamp = resultRow[Notes.lastSyncedTimestamp],
                ownerUserId = resultRow[Notes.ownerUserId],
                labels = resultRow[Notes.labels]?.let {
                    gson.fromJson(it, Array<String>::class.java).toList()
                }
            )
        }
    }

    /**
     * Create or update a note.
     *
     * @param note The note to save
     * @return The saved note
     */
    fun saveNote(note: Note): Note {
        // Check if the note already exists
        val existingNote = findNoteById(note.id)

        if (existingNote == null) {
            // Insert new note
            Notes.insert {
                it[id] = note.id
                it[title] = note.title
                it[type] = note.type
                it[isPinned] = note.isPinned
                it[isArchived] = note.isArchived
                it[isShared] = note.isShared
                it[encryptedContent] = note.encryptedContent
                it[encryptionIv] = note.encryptionIv
                it[createdAt] = note.createdAt
                it[lastModifiedTimestamp] = note.lastModifiedTimestamp
                it[lastSyncedTimestamp] = note.lastSyncedTimestamp
                it[ownerUserId] = note.ownerUserId
                it[labels] = note.labels?.let { gson.toJson(it) }
            }
        } else {
            // Update existing note
            Notes.update({ Notes.id eq note.id }) {
                it[title] = note.title
                it[type] = note.type
                it[isPinned] = note.isPinned
                it[isArchived] = note.isArchived
                it[isShared] = note.isShared
                it[encryptedContent] = note.encryptedContent
                it[encryptionIv] = note.encryptionIv
                it[lastModifiedTimestamp] = note.lastModifiedTimestamp
                it[lastSyncedTimestamp] = note.lastSyncedTimestamp
                it[labels] = note.labels?.let { gson.toJson(it) }
            }
        }

        return note
    }

    /**
     * Delete a note by its ID.
     *
     * @param noteId The ID of the note to delete
     * @return Whether the deletion was successful
     */
    fun deleteNote(noteId: UUID): Boolean {
        val deletedRows = Notes.deleteWhere { Notes.id eq noteId }
        return deletedRows > 0
    }

    /**
     * Count the number of notes owned by a specific user.
     *
     * @param userId The user ID
     * @return The note count
     */
    fun countNotesByOwner(userId: UUID): Int {
        return Notes.select { Notes.ownerUserId eq userId }.count().toInt()
    }

    /**
     * Update the sharing status of a note.
     *
     * @param noteId The ID of the note to update
     * @param isShared The new sharing status
     * @return Whether the update was successful
     */
    fun updateNoteSharing(noteId: UUID, isShared: Boolean): Boolean {
        val updatedRows = Notes.update({ Notes.id eq noteId }) {
            it[Notes.isShared] = isShared
        }
        return updatedRows > 0
    }
}
