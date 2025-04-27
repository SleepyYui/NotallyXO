package com.sleepyyui.notallyxo.backend.models.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain model representing a note in the system.
 *
 * This class is used for business logic and service operations.
 * The content of the note is encrypted with the user's key.
 */
data class Note(
    val id: UUID,
    val title: String,
    val type: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isShared: Boolean,
    val encryptedContent: String,
    val encryptionIv: String?,
    val createdAt: Instant,
    val lastModifiedTimestamp: Long,
    val lastSyncedTimestamp: Long,
    val ownerUserId: UUID,
    val labels: List<String>?
)
