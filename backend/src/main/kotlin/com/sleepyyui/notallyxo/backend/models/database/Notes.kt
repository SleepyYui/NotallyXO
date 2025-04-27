package com.sleepyyui.notallyxo.backend.models.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Database table definition for Notes.
 * 
 * This table stores encrypted note content. The actual note data is encrypted on the client side
 * before being sent to the server, so the server only stores encrypted content.
 */
object Notes : UUIDTable("notes") {
    // The UUID is used as syncId in the client
    
    // Basic metadata (not encrypted)
    val title = varchar("title", 255)
    val type = varchar("type", 50) // TEXT or LIST
    val isPinned = bool("is_pinned").default(false)
    val isArchived = bool("is_archived").default(false)
    val isShared = bool("is_shared").default(false)
    
    // Encrypted content - this contains the actual note content (body, items, spans, etc.)
    val encryptedContent = text("encrypted_content")
    val encryptionIv = varchar("encryption_iv", 255).nullable()
    
    // Timestamps
    val createdAt = timestamp("created_at").default(Instant.now())
    val lastModifiedTimestamp = long("last_modified_timestamp")
    val lastSyncedTimestamp = long("last_synced_timestamp")
    
    // Owner information
    val ownerUserId = uuid("owner_user_id").references(Users.id)
    
    // Additional metadata
    val labels = text("labels").nullable() // Stored as JSON array of strings
}
