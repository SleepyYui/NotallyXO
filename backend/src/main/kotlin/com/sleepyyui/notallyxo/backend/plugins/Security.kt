package com.sleepyyui.notallyxo.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sleepyyui.notallyxo.backend.config.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configure JWT authentication for the application.
 */
fun Application.configureSecurity(jwtConfig: JwtConfig) {
    logger.info { "Configuring security" }
    
    authentication {
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtConfig.secret))
                    .withIssuer(jwtConfig.issuer)
                    .build()
            )
            
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Authentication token is invalid or has expired")
            }
        }
    }
}
