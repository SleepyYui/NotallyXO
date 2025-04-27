package com.sleepyyui.notallyxo.backend.models.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain model representing a one-time sharing token.
 */
data class SharingToken(
    val id: UUID,
    val noteId: UUID,
    val accessLevel: String,
    val createdTimestamp: Instant,
    val expirationTimestamp: Instant?,
    val isUsed: Boolean,
    val usedTimestamp: Instant? = null,
    val usedByUserId: UUID? = null
)
