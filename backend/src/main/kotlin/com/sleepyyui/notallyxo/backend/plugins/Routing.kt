package com.sleepyyui.notallyxo.backend.plugins

import com.sleepyyui.notallyxo.backend.routes.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configure application routing.
 */
fun Application.configureRouting() {
    logger.info { "Configuring routing" }
    
    routing {
        // Public routes (no authentication required)
        route("api/v1") {
            // Authentication route
            post("auth/token") {
                authenticateUser(call)
            }
            
            // Protected routes (require authentication)
            authenticate("auth-jwt") {
                // User profile routes
                route("users") {
                    get("profile") {
                        getUserProfile(call)
                    }
                }
                
                // Sync routes
                route("sync") {
                    get("status") {
                        getSyncStatus(call)
                    }
                    post {
                        syncNotes(call)
                    }
                }
                
                // Note routes
                route("notes/{syncId}") {
                    get {
                        getNote(call)
                    }
                    put {
                        putNote(call)
                    }
                    delete {
                        deleteNote(call)
                    }
                    
                    // Sharing routes
                    post("share") {
                        shareNote(call)
                    }
                    post("sharing-token") {
                        createSharingToken(call)
                    }
                }
                
                // Shared note routes
                route("shared") {
                    post("accept") {
                        acceptSharedNote(call)
                    }
                }
            }
        }
    }
}
