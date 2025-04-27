package com.sleepyyui.notallyxo.backend.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

/**
 * Configuration class for JWT authentication.
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val realm: String,
    val expirationTimeMs: Long = 2592000000 // 30 days in milliseconds by default
) {
    /**
     * Generate a JWT token for a user.
     *
     * @param userId The ID of the user to create a token for
     * @return The generated JWT token string
     */
    fun generateToken(userId: String): String {
        return JWT.create()
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationTimeMs))
            .sign(Algorithm.HMAC256(secret))
    }
    
    companion object {
        /**
         * Create a JwtConfig from environment variables or use defaults.
         */
        fun fromEnvOrDefault(): JwtConfig {
            return JwtConfig(
                secret = System.getenv("JWT_SECRET") ?: "default-very-long-and-secure-jwt-secret-key-for-notallyxo-backend",
                issuer = System.getenv("JWT_ISSUER") ?: "notallyxo-backend",
                realm = System.getenv("JWT_REALM") ?: "NotallyXO Cloud Sync"
            )
        }
    }
}
