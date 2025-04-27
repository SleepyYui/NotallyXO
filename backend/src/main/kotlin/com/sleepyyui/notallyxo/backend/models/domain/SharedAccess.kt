package com.sleepyyui.notallyxo.backend.models.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain model representing shared access to a note.
 */
data class SharedAccess(
    val id: UUID,
    val noteId: UUID,
    val userId: UUID,
    val accessLevel: String,
    val grantedTimestamp: Instant,
    val lastAccessedTimestamp: Instant?,
    val usedToken: UUID?
)
