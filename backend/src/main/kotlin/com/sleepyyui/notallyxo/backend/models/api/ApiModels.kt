package com.sleepyyui.notallyxo.backend.models.api

/**
 * Authentication response sent to clients.
 */
data class AuthResponse(
    val success: Boolean,
    val userId: String? = null,
    val message: String? = null
)

/**
 * User profile information sent to clients.
 */
data class UserProfileResponse(
    val userId: String,
    val username: String?,
    val displayName: String?,
    val publicKey: String?
)

/**
 * Sync status information sent to clients.
 */
data class SyncStatusResponse(
    val lastSyncTimestamp: Long,
    val noteCount: Int,
    val pendingChanges: Boolean
)

/**
 * Request received from clients to sync notes.
 */
data class NoteSyncRequest(
    val changedNotes: List<NoteDto>,
    val deletedNoteIds: List<String>
)

/**
 * Response sent to clients after a sync operation.
 */
data class SyncResponse(
    val success: Boolean,
    val lastSyncTimestamp: Long,
    val updatedNotes: List<NoteDto> = emptyList(),
    val deletedNoteIds: List<String> = emptyList(),
    val conflicts: List<NoteConflict> = emptyList(),
    val message: String? = null
)

/**
 * Data transfer object for notes.
 */
data class NoteDto(
    val syncId: String,
    val title: String,
    val content: String,
    val encryptionIv: String? = null,
    val lastModifiedTimestamp: Long,
    val lastSyncedTimestamp: Long,
    val type: String,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val isShared: Boolean = false,
    val labels: List<String>? = null,
    val ownerUserId: String,
    val sharedAccesses: List<SharedAccessDto>? = null
)

/**
 * Data transfer object for shared access information.
 */
data class SharedAccessDto(
    val userId: String,
    val accessLevel: String
)

/**
 * Data about a note conflict.
 */
data class NoteConflict(
    val syncId: String,
    val localNote: NoteDto,
    val remoteNote: NoteDto,
    val conflictType: String
)

/**
 * Response for sharing token creation.
 */
data class SharingTokenResponse(
    val token: String
)

/**
 * Request to share a note with a specific user.
 */
data class ShareNoteRequest(
    val userId: String,
    val accessLevel: String
)

/**
 * Request to accept a shared note using a token.
 */
data class AcceptSharedNoteRequest(
    val token: String
)
