package com.sleepyyui.notallyxo.backend.repositories

import com.sleepyyui.notallyxo.backend.models.database.SharingTokens
import com.sleepyyui.notallyxo.backend.models.domain.SharingToken
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

/**
 * Repository class for sharing token related database operations.
 */
class SharingTokenRepository {

    /**
     * Create a new sharing token.
     *
     * @param id The UUID to use for this token
     * @param noteId The ID of the note to share
     * @param accessLevel The level of access (READ_ONLY or READ_WRITE)
     * @param expirationTimestamp Optional expiration timestamp
     * @return The created SharingToken object
     */
    fun createSharingToken(
        id: UUID,
        noteId: UUID,
        accessLevel: String,
        expirationTimestamp: Instant? = null
    ): SharingToken {
        val now = Instant.now()
        
        SharingTokens.insert {
            it[SharingTokens.id] = id
            it[SharingTokens.noteId] = noteId
            it[SharingTokens.accessLevel] = accessLevel
            it[createdTimestamp] = now
            it[SharingTokens.expirationTimestamp] = expirationTimestamp
            it[isUsed] = false
            it[usedTimestamp] = null
            it[usedByUserId] = null
        }
        
        return SharingToken(
            id = id,
            noteId = noteId,
            accessLevel = accessLevel,
            createdTimestamp = now,
            expirationTimestamp = expirationTimestamp,
            isUsed = false
        )
    }
    
    /**
     * Find a sharing token by its ID.
     *
     * @param tokenId The UUID of the token
     * @return The SharingToken if found, null otherwise
     */
    fun findTokenById(tokenId: UUID): SharingToken? {
        return SharingTokens.select { SharingTokens.id eq tokenId }
            .singleOrNull()
            ?.let { row ->
                SharingToken(
                    id = row[SharingTokens.id].value,
                    noteId = row[SharingTokens.noteId],
                    accessLevel = row[SharingTokens.accessLevel],
                    createdTimestamp = row[SharingTokens.createdTimestamp],
                    expirationTimestamp = row[SharingTokens.expirationTimestamp],
                    isUsed = row[SharingTokens.isUsed],
                    usedTimestamp = row[SharingTokens.usedTimestamp],
                    usedByUserId = row[SharingTokens.usedByUserId]
                )
            }
    }
    
    /**
     * Mark a token as used.
     *
     * @param tokenId The ID of the token
     * @param usedByUserId The ID of the user who used the token
     * @return Whether the update was successful
     */
    fun markTokenAsUsed(tokenId: UUID, usedByUserId: UUID): Boolean {
        val updated = SharingTokens.update({ SharingTokens.id eq tokenId }) {
            it[isUsed] = true
            it[usedTimestamp] = Instant.now()
            it[SharingTokens.usedByUserId] = usedByUserId
        }
        
        return updated > 0
    }
    
    /**
     * Get all sharing tokens for a specific note.
     *
     * @param noteId The ID of the note
     * @return List of SharingToken objects
     */
    fun getTokensForNote(noteId: UUID): List<SharingToken> {
        return SharingTokens.select { SharingTokens.noteId eq noteId }
            .map { row ->
                SharingToken(
                    id = row[SharingTokens.id].value,
                    noteId = noteId,
                    accessLevel = row[SharingTokens.accessLevel],
                    createdTimestamp = row[SharingTokens.createdTimestamp],
                    expirationTimestamp = row[SharingTokens.expirationTimestamp],
                    isUsed = row[SharingTokens.isUsed],
                    usedTimestamp = row[SharingTokens.usedTimestamp],
                    usedByUserId = row[SharingTokens.usedByUserId]
                )
            }
    }
    
    /**
     * Delete a sharing token.
     *
     * @param tokenId The ID of the token to delete
     * @return Whether the deletion was successful
     */
    fun deleteToken(tokenId: UUID): Boolean {
        val deleted = SharingTokens.deleteWhere { SharingTokens.id eq tokenId }
        return deleted > 0
    }
    
    /**
     * Delete all expired tokens.
     *
     * @return The number of tokens deleted
     */
    fun cleanupExpiredTokens(): Int {
        val now = Instant.now()
        return SharingTokens.deleteWhere {
            (SharingTokens.expirationTimestamp.isNotNull()) and 
            (SharingTokens.expirationTimestamp less now)
        }
    }
}
