package com.sleepyyui.notallyxo.backend.repositories

import com.sleepyyui.notallyxo.backend.models.database.Users
import com.sleepyyui.notallyxo.backend.models.domain.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import java.time.Instant
import java.util.*

/**
 * Repository class for user-related database operations.
 */
class UserRepository {
    
    /**
     * Find a user by their authentication token.
     *
     * @param token The authentication token to search for
     * @return User object if found, null otherwise
     */
    fun findUserByToken(token: String): User? {
        return Users.select { Users.authToken eq token }
            .singleOrNull()
            ?.let { resultRow ->
                User(
                    id = resultRow[Users.id].value,
                    username = resultRow[Users.username],
                    email = resultRow[Users.email],
                    authToken = resultRow[Users.authToken],
                    publicKey = resultRow[Users.publicKey],
                    createdAt = resultRow[Users.createdAt],
                    updatedAt = resultRow[Users.updatedAt],
                    lastLoginAt = resultRow[Users.lastLoginAt]
                )
            }
    }

    /**
     * Find a user by their ID.
     *
     * @param userId The user ID to search for
     * @return User object if found, null otherwise
     */
    fun findUserById(userId: UUID): User? {
        return Users.select { Users.id eq userId }
            .singleOrNull()
            ?.let { resultRow ->
                User(
                    id = resultRow[Users.id].value,
                    username = resultRow[Users.username],
                    email = resultRow[Users.email],
                    authToken = resultRow[Users.authToken],
                    publicKey = resultRow[Users.publicKey],
                    createdAt = resultRow[Users.createdAt],
                    updatedAt = resultRow[Users.updatedAt],
                    lastLoginAt = resultRow[Users.lastLoginAt]
                )
            }
    }

    /**
     * Update the last login timestamp for a user.
     *
     * @param userId The ID of the user to update
     * @return The number of rows updated (should be 1 if successful)
     */
    fun updateLastLogin(userId: UUID): Int {
        return Users.update({ Users.id eq userId }) {
            it[lastLoginAt] = Instant.now()
        }
    }

    /**
     * Create a new user.
     *
     * @param username The username for the new user
     * @param email Optional email for the new user
     * @param authToken The authentication token for the new user
     * @param publicKey Optional public key for encryption
     * @return The created User object
     */
    fun createUser(
        username: String,
        email: String? = null,
        authToken: String,
        publicKey: String? = null
    ): User {
        val userId = UUID.randomUUID()
        val now = Instant.now()
        
        Users.insert {
            it[id] = userId
            it[Users.username] = username
            it[Users.email] = email
            it[Users.authToken] = authToken
            it[Users.publicKey] = publicKey
            it[createdAt] = now
            it[updatedAt] = now
        }
        
        return User(
            id = userId,
            username = username,
            email = email,
            authToken = authToken,
            publicKey = publicKey,
            createdAt = now,
            updatedAt = now,
            lastLoginAt = null
        )
    }

    /**
     * Update a user's public key.
     *
     * @param userId The ID of the user to update
     * @param publicKey The new public key
     * @return The number of rows updated (should be 1 if successful)
     */
    fun updatePublicKey(userId: UUID, publicKey: String): Int {
        return Users.update({ Users.id eq userId }) {
            it[Users.publicKey] = publicKey
            it[updatedAt] = Instant.now()
        }
    }
}
