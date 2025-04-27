package com.sleepyyui.notallyxo.backend.models.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Database table definition for Users.
 * 
 * This table stores user information including authentication details and
 * encryption keys.
 */
object Users : UUIDTable("users") {
    val username = varchar("username", 100).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex().nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val authToken = varchar("auth_token", 255).uniqueIndex()
    val publicKey = text("public_key").nullable()
    val privateKey = text("private_key").nullable()
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())
    val lastLoginAt = timestamp("last_login_at").nullable()
}
