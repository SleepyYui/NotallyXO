package com.sleepyyui.notallyxo.backend.models.domain

import java.time.Instant
import java.util.UUID

/**
 * Domain model representing a user in the system.
 *
 * This class is used for business logic and service operations.
 * It's converted from and to database entities as needed.
 */
data class User(
    val id: UUID,
    val username: String,
    val email: String?,
    val authToken: String,
    val publicKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastLoginAt: Instant?
)
