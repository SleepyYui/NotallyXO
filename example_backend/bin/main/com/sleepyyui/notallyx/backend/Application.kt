package com.sleepyyui.notallyx.backend

import io.ktor.http.* // Import HttpStatusCode
import io.ktor.serialization.kotlinx.json.* // Import Kotlinx JSON support
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException // Import BadRequestException
import io.ktor.server.plugins.contentnegotiation.* // Import Content Negotiation
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable // Import Serializable
import kotlinx.serialization.json.Json // Import Json configuration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant

// --- Data Models --- //

// These match the client-side models in NotallyApiService.kt
@Serializable
data class EncryptedNotePayload(
    val serverId: String? = null, // Null for new notes from client
    val encryptedContent: ByteArray, // Combined Ciphertext + Nonce
    val lastModifiedClient: Long
) {
    // Need custom equals/hashCode for ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedNotePayload
        if (serverId != other.serverId) return false
        if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        if (lastModifiedClient != other.lastModifiedClient) return false
        return true
    }
    override fun hashCode(): Int {
        var result = serverId?.hashCode() ?: 0
        result = 31 * result + encryptedContent.contentHashCode()
        result = 31 * result + lastModifiedClient.hashCode()
        return result
    }
}

@Serializable
data class SyncRequest(
    val changes: List<EncryptedNotePayload>,
    val since: Long
)

@Serializable
data class SyncResponse(
    val serverChanges: List<EncryptedNotePayload>,
    val newServerIds: Map<String, String>,
    val currentServerTime: Long,
    val newlySharedNotes: List<SharedNoteInfo> = emptyList()
)

// Add model for public key upload
@Serializable
data class PublicKeyUpload(
    val publicKeyBytes: ByteArray
) {
     override fun equals(other: Any?): Boolean { /* ... ByteArray handling ... */ return false}
     override fun hashCode(): Int { /* ... ByteArray handling ... */ return 0 }
}

// --- In-Memory Storage (Replace with Database later) --- //

// --- Sharing Data Models --- //
@Serializable
data class ShareNoteRequest(
    val recipientToken: String, // Insecure, replace with user ID later
    val encryptedNoteKey: ByteArray // Symmetric key of the note, encrypted with recipient's public key
) {
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ShareNoteRequest
        if (recipientToken != other.recipientToken) return false
        if (!encryptedNoteKey.contentEquals(other.encryptedNoteKey)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = recipientToken.hashCode()
        result = 31 * result + encryptedNoteKey.contentHashCode()
        return result
    }
}

// Structure to hold sharing info: NoteServerId -> RecipientToken -> EncryptedNoteKey
// This allows multiple recipients per note
val noteSharingStore = ConcurrentHashMap<String, ConcurrentHashMap<String, ByteArray>>()

// UserToken -> ServerNoteId -> NoteData
data class StoredNote(
    val serverId: String,
    val encryptedContent: ByteArray,
    var lastModifiedServer: Long,
    val ownerToken: String
    // Add sharing info later
) {
     override fun equals(other: Any?): Boolean { /* ... ByteArray handling ... */ return false}
     override fun hashCode(): Int { /* ... ByteArray handling ... */ return 0 }
}

// Simple in-memory store for demonstration
// Key: User Token, Value: Map<ServerId, StoredNote>
val userNotesStore = ConcurrentHashMap<String, ConcurrentHashMap<String, StoredNote>>()

// Store public keys (UserToken -> PublicKeyBytes)
val userPublicKeys = ConcurrentHashMap<String, ByteArray>()

// Add model for sharing info delivery in SyncResponse
@Serializable
data class SharedNoteInfo(
    val noteServerId: String,
    val encryptedNoteKey: ByteArray, // Encrypted with recipient's (the syncing user's) public key
    val notePayload: EncryptedNotePayload // The actual note content payload
) {
     override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SharedNoteInfo
        if (noteServerId != other.noteServerId) return false
        if (!encryptedNoteKey.contentEquals(other.encryptedNoteKey)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = noteServerId.hashCode()
        result = 31 * result + encryptedNoteKey.contentHashCode()
        return result
    }
}

// --- Main Application --- //

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    // Add StatusPages for better error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Access log via call.application.log
            call.application.log.error("Unhandled error: ${cause.localizedMessage}", cause)
            call.respond(HttpStatusCode.InternalServerError, "An internal error occurred")
        }
        exception<BadRequestException> { call, cause: BadRequestException ->
             // Access log via call.application.log
             call.application.log.warn("Bad Request: ${cause.message}")
             call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad Request")
        }
        // Add more specific exception handlers as needed
    }
    configureRouting()
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello NotallyX Backend!")
        }

        // --- Sync Endpoint --- //
        post("/sync") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null) {
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Missing Authorization header")
                return@post
            }

            // TODO: Validate token properly (e.g., against a user database)
            if (token.isBlank()) {
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid token")
                return@post
            }

            val syncRequest = call.receive<SyncRequest>()
            val userStore = userNotesStore.computeIfAbsent(token) { ConcurrentHashMap() }
            val serverTime = Instant.now().toEpochMilli()
            val serverChangesResponse = mutableListOf<EncryptedNotePayload>()
            val newServerIdsResponse = mutableMapOf<String, String>()

            // 1. Process client changes
            syncRequest.changes.forEach { clientChange ->
                if (clientChange.serverId == null) {
                    // New note from client
                    val newServerId = UUID.randomUUID().toString()
                    val storedNote = StoredNote(
                        serverId = newServerId,
                        encryptedContent = clientChange.encryptedContent,
                        lastModifiedServer = serverTime, // Use server time for new notes
                        ownerToken = token
                    )
                    userStore[newServerId] = storedNote
                    // Use client timestamp as temporary ID key
                    newServerIdsResponse[clientChange.lastModifiedClient.toString()] = newServerId
                } else {
                    // Existing note update from client
                    val serverId = clientChange.serverId
                    val existingNote = userStore[serverId]

                    if (existingNote != null) {
                        // Basic conflict resolution: Last Write Wins (based on client timestamp)
                        // Server timestamp could also be used.
                        // Note: Timestamps must be reliable (sync clocks or use server time primarily)
                        if (clientChange.lastModifiedClient >= existingNote.lastModifiedServer) {
                            val updatedNote = existingNote.copy(
                                encryptedContent = clientChange.encryptedContent,
                                lastModifiedServer = serverTime // Update server timestamp
                            )
                            userStore[serverId] = updatedNote
                        } else {
                            // Conflict: Server version is newer, ignore client change for now
                            // Add server version to response so client can resolve
                            serverChangesResponse.add(
                                EncryptedNotePayload(
                                    serverId = existingNote.serverId,
                                    encryptedContent = existingNote.encryptedContent,
                                    lastModifiedClient = existingNote.lastModifiedServer // Send server time back
                                )
                            )
                        }
                    } else {
                        // Note exists on client but not server? (e.g., server reset)
                        // Treat as new note for now
                        val newServerId = serverId // Keep client's ID if possible, or generate new one
                        val storedNote = StoredNote(
                            serverId = newServerId,
                            encryptedContent = clientChange.encryptedContent,
                            lastModifiedServer = serverTime,
                            ownerToken = token
                        )
                        userStore[newServerId] = storedNote
                        // Don't add to newServerIds map as client already knows this ID
                    }
                }
            }

            // 2. Find server changes since client's last sync
            userStore.values.forEach { storedNote ->
                if (storedNote.lastModifiedServer > syncRequest.since) {
                    // Add if not already added due to conflict resolution
                    if (!serverChangesResponse.any { it.serverId == storedNote.serverId }) {
                         serverChangesResponse.add(
                            EncryptedNotePayload(
                                serverId = storedNote.serverId,
                                encryptedContent = storedNote.encryptedContent,
                                lastModifiedClient = storedNote.lastModifiedServer // Send server time
                            )
                        )
                    }
                }
            }

            // 3. Find newly shared notes for this user
            val newlySharedNotesResponse = mutableListOf<SharedNoteInfo>()
            noteSharingStore.forEach { (noteId, shares) ->
                shares[token]?.let { encryptedKey ->
                    // Find the actual note payload
                    // This requires searching through all users' notes again... inefficient.
                    // A better structure (e.g., a central note store) would be needed for scale.
                    var foundNotePayload: EncryptedNotePayload? = null
                    var ownerToken: String? = null

                    // Find the note and its owner
                    val noteOwnerPair = userNotesStore.entries.find { (_, notes) -> notes.containsKey(noteId) }
                    val storedNote = noteOwnerPair?.value?.get(noteId)

                    if (storedNote != null) {
                         foundNotePayload = EncryptedNotePayload(
                            serverId = storedNote.serverId,
                            encryptedContent = storedNote.encryptedContent,
                            lastModifiedClient = storedNote.lastModifiedServer // Use server time as last modified
                        )
                    } else {
                        // Log an error or handle cases where the shared note doesn't exist anymore
                        application.log.error("Shared note $noteId for recipient $token not found in userNotesStore.")
                    }

                    if (foundNotePayload != null) {
                        newlySharedNotesResponse.add(SharedNoteInfo(noteId, encryptedKey, foundNotePayload))
                        // Optional: Consider removing the share notification
                        // shares.remove(token)
                    } else {
                         application.log.warn("Could not find payload for shared note $noteId recipient $token")
                    }
                }
            }

            // 4. Respond
            call.respond(
                SyncResponse(
                    serverChanges = serverChangesResponse,
                    newServerIds = newServerIdsResponse,
                    currentServerTime = serverTime,
                    newlySharedNotes = newlySharedNotesResponse // Include the shared notes info
                )
            )
        }

        // --- Public Key Endpoints --- //

        // Endpoint for clients to upload their public key
        post("/users/public_key") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (token == null || token.isBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                return@post
            }

            val request = call.receive<PublicKeyUpload>()
            if (request.publicKeyBytes.isEmpty()) {
                 throw BadRequestException("Public key cannot be empty")
            }
            userPublicKeys[token] = request.publicKeyBytes
            // Logging inside routing handler is fine with implicit application.log
            application.log.info("Stored public key for token starting with: ${token.take(4)}")
            call.respond(HttpStatusCode.OK, "Public key stored")
        }

        // Endpoint for clients to fetch another user's public key
        // NOTE: Needs a way to identify users other than tokens for security.
        // Using token here is insecure as anyone knowing a token could get the key.
        // Replace {userToken} with {userId} when user IDs are implemented.
        get("/users/{userToken}/public_key") {
            val requestToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")
             if (requestToken == null || requestToken.isBlank()) {
                 call.respond(HttpStatusCode.Unauthorized, "Invalid or missing token")
                 return@get
             }
             // TODO: Add authentication check - does requestToken have permission?

            val targetToken = call.parameters["userToken"]
            if (targetToken == null || targetToken.isBlank()) {
                 call.respond(HttpStatusCode.BadRequest, "Missing user identifier")
                 return@get
            }

            val publicKeyBytes = userPublicKeys[targetToken]
            if (publicKeyBytes != null) {
                // Respond with the public key bytes directly
                // Client needs to know how to interpret these (e.g., Tink JSON format)
                call.respondBytes(publicKeyBytes, ContentType.Application.OctetStream)
            } else {
                call.respond(HttpStatusCode.NotFound, "Public key not found for user")
            }
        }

        // TODO: Implement /notes/{note_server_id}/share endpoint later
        // --- Share Endpoint --- //
        post("/notes/{note_server_id}/share") {
            val senderToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            if (senderToken == null || senderToken.isBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing sender token")
                return@post
            }

            val noteServerId = call.parameters["note_server_id"]
            if (noteServerId == null || noteServerId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing note_server_id")
                return@post
            }

            // Find the note across all users (inefficient for large scale, ok for now)
            var noteOwnerToken: String? = null
            var noteExists = false
            for ((ownerToken, notes) in userNotesStore) {
                if (notes.containsKey(noteServerId)) {
                    noteOwnerToken = ownerToken
                    noteExists = true
                    break
                }
            }

            if (!noteExists) {
                call.respond(HttpStatusCode.NotFound, "Note not found")
                return@post
            }

            // Check if the sender is the owner of the note
            if (noteOwnerToken != senderToken) {
                call.respond(HttpStatusCode.Forbidden, "Sender does not own this note")
                return@post
            }

            // Receive recipient details and the encrypted key
            val shareRequest = call.receive<ShareNoteRequest>()

            // TODO: Validate recipientToken exists (e.g., check userPublicKeys)
            if (shareRequest.recipientToken.isBlank()) {
                 call.respond(HttpStatusCode.BadRequest, "Recipient identifier cannot be blank")
                 return@post
            }
            if (shareRequest.encryptedNoteKey.isEmpty()) {
                 call.respond(HttpStatusCode.BadRequest, "Encrypted note key cannot be empty")
                 return@post
            }

            // Store the sharing information
            val noteShares = noteSharingStore.computeIfAbsent(noteServerId) { ConcurrentHashMap() }
            noteShares[shareRequest.recipientToken] = shareRequest.encryptedNoteKey

            application.log.info("Note $noteServerId shared by token starting ${senderToken.take(4)} with token starting ${shareRequest.recipientToken.take(4)}")

            call.respond(HttpStatusCode.OK, "Note shared successfully")
        }
    }
}
 