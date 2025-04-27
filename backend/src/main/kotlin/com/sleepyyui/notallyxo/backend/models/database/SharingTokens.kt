package com.sleepyyui.notallyxo.backend.models.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Database table definition for SharingTokens.
 * 
 * This table stores one-time tokens that can be used to share notes with other users.
 */
object SharingTokens : UUIDTable("sharing_tokens") {
    // The note this token is for
    val noteId = reference("note_id", Notes, onDelete = ReferenceOption.CASCADE)
    
    // Access level to grant when the token is used
    val accessLevel = varchar("access_level", 20)
    
    // Timestamps
    val createdTimestamp = timestamp("created_timestamp").default(Instant.now())
    val expirationTimestamp = timestamp("expiration_timestamp").nullable()
    
    // Whether this token has been used already
    val isUsed = bool("is_used").default(false)
    
    // If token was used, store when and by whom
    val usedTimestamp = timestamp("used_timestamp").nullable()
    val usedByUserId = reference("used_by_user_id", Users, onDelete = ReferenceOption.SET_NULL).nullable()
}
