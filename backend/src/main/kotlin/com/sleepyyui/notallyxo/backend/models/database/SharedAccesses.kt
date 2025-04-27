package com.sleepyyui.notallyxo.backend.models.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Database table definition for SharedAccesses.
 * 
 * This table tracks which users have access to which notes, along with their
 * access levels (READ_ONLY or READ_WRITE).
 */
object SharedAccesses : UUIDTable("shared_accesses") {
    // References to the note and user
    val noteId = reference("note_id", Notes, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    
    // Access level (READ_ONLY or READ_WRITE)
    val accessLevel = varchar("access_level", 20)
    
    // Timestamps and metadata
    val grantedTimestamp = timestamp("granted_timestamp").default(Instant.now())
    val lastAccessedTimestamp = timestamp("last_accessed_timestamp").nullable()
    
    // If this access was granted via a token, store the token for reference
    val usedToken = uuid("used_token").nullable()
    
    // Unique constraint to prevent duplicate accesses
    init {
        uniqueIndex("idx_note_user_unique", noteId, userId)
    }
}
