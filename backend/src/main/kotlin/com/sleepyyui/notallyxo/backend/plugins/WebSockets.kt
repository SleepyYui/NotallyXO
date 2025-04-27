package com.sleepyyui.notallyxo.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

// Store active WebSocket connections per user ID
private val userConnections = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

// Shared flow for broadcasting messages
private val _broadcastFlow = MutableSharedFlow<Pair<UUID, String>>(replay = 0)
val broadcastFlow = _broadcastFlow.asSharedFlow()

/**
 * Configures WebSocket support for the application.
 */
fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        webSocket("/ws/updates") {
            val userId = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.getClaim("userId", String::class)?.let { UUID.fromString(it) }
            
            if (userId == null) {
                logger.warn { "WebSocket connection attempt without valid authentication." }
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }
            
            logger.info { "WebSocket connection established for user $userId" }
            
            // Add the session to the user's connection set
            val connections = userConnections.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }
            connections.add(this)
            
            try {
                // Listen for incoming messages (optional, can be used for client pings or specific commands)
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        logger.debug { "Received WebSocket message from user $userId: $text" }
                        // Handle incoming messages if needed (e.g., client confirming connection)
                        if (text.equals("ping", ignoreCase = true)) {
                            outgoing.send(Frame.Text("pong"))
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                logger.info { "WebSocket connection closed for user $userId: ${closeReason.await()}" }
            } catch (e: Throwable) {
                logger.error(e) { "Error in WebSocket session for user $userId: ${closeReason.await()}" }
            } finally {
                // Remove the session on disconnect
                connections.remove(this)
                if (connections.isEmpty()) {
                    userConnections.remove(userId)
                }
                logger.info { "WebSocket connection removed for user $userId" }
            }
        }
    }
    
    // Coroutine to listen to the broadcast flow and send messages to relevant users
    launch {
        broadcastFlow.collect { (targetUserId, message) ->
            userConnections[targetUserId]?.forEach { session ->
                try {
                    logger.debug { "Sending WebSocket update to user $targetUserId" }
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    logger.error(e) { "Failed to send WebSocket message to user $targetUserId" }
                    // Optionally handle session closure if sending fails
                    try {
                        session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Failed to send message"))
                    } catch (ignore: Exception) {}
                }
            }
        }
    }
}

/**
 * Broadcasts a message to a specific user via WebSocket.
 *
 * @param userId The ID of the user to send the message to.
 * @param message The message content (typically JSON).
 */
suspend fun broadcastUpdateToUser(userId: UUID, message: String) {
    if (userConnections.containsKey(userId)) {
        logger.info { "Broadcasting update to user $userId" }
        _broadcastFlow.emit(Pair(userId, message))
    } else {
        logger.debug { "No active WebSocket connections for user $userId, skipping broadcast." }
    }
}
