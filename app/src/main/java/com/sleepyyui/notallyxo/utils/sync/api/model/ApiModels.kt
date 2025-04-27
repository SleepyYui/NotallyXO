package com.sleepyyui.notallyxo.utils.sync.api.model

import com.google.gson.annotations.SerializedName

/**
 * Authentication response from the server.
 *
 * @property success Whether authentication was successful
 * @property userId The authenticated user's ID
 * @property message Error message if authentication failed
 */
data class AuthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("message") val message: String?,
)

/**
 * User profile information from the server.
 *
 * @property userId The user's unique ID
 * @property username The user's unique username
 * @property displayName The user's display name
 * @property publicKey The user's public key for encryption
 */
data class UserProfileResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("username") val username: String?,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("public_key") val publicKey: String?,
)

/**
 * Sync status information from the server.
 *
 * @property lastSyncTimestamp The timestamp of the last successful sync
 * @property noteCount The total number of notes on the server
 * @property pendingChanges Whether there are remote changes to pull
 */
data class SyncStatusResponse(
    @SerializedName("last_sync_timestamp") val lastSyncTimestamp: Long,
    @SerializedName("note_count") val noteCount: Int,
    @SerializedName("pending_changes") val pendingChanges: Boolean,
)

/**
 * Request to sync notes with the server.
 *
 * @property changedNotes Notes that have been changed locally and need to be pushed to the server
 * @property deletedNoteIds IDs of notes that have been deleted locally
 */
data class NoteSyncRequest(
    @SerializedName("changed_notes") val changedNotes: List<NoteDto>,
    @SerializedName("deleted_note_ids") val deletedNoteIds: List<String>,
)

/**
 * Response from a sync operation.
 *
 * @property success Whether the sync was successful
 * @property lastSyncTimestamp The new last sync timestamp
 * @property updatedNotes Notes that were added or updated on the server
 * @property deletedNoteIds IDs of notes that were deleted on the server
 * @property conflicts Notes with conflicts that need resolution
 * @property message Error message if sync failed
 */
data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("last_sync_timestamp") val lastSyncTimestamp: Long,
    @SerializedName("updated_notes") val updatedNotes: List<NoteDto>,
    @SerializedName("deleted_note_ids") val deletedNoteIds: List<String>,
    @SerializedName("conflicts") val conflicts: List<NoteConflict>,
    @SerializedName("message") val message: String?,
)

/**
 * Data transfer object for notes.
 *
 * @property syncId Unique ID for synchronization
 * @property title Note title
 * @property content Note content (encrypted)
 * @property encryptionIv Initialization vector for decryption
 * @property lastModifiedTimestamp Last modified timestamp
 * @property lastSyncedTimestamp Last synced timestamp
 * @property type Type of note (TEXT or LIST)
 * @property isArchived Whether the note is archived
 * @property isPinned Whether the note is pinned
 * @property isShared Whether the note is shared
 * @property labels Labels attached to the note (encrypted)
 * @property ownerUserId ID of the note's owner
 * @property sharedAccesses Users with whom the note is shared and their access levels
 */
data class NoteDto(
    @SerializedName("sync_id") val syncId: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("encryption_iv") val encryptionIv: String?,
    @SerializedName("last_modified_timestamp") val lastModifiedTimestamp: Long,
    @SerializedName("last_synced_timestamp") val lastSyncedTimestamp: Long,
    @SerializedName("type") val type: String,
    @SerializedName("is_archived") val isArchived: Boolean,
    @SerializedName("is_pinned") val isPinned: Boolean,
    @SerializedName("is_shared") val isShared: Boolean,
    @SerializedName("labels") val labels: List<String>?,
    @SerializedName("owner_user_id") val ownerUserId: String,
    @SerializedName("shared_accesses") val sharedAccesses: List<SharedAccessDto>?,
)

/**
 * Data transfer object for shared access information.
 *
 * @property userId ID of the user with access
 * @property accessLevel Level of access (READ_ONLY or READ_WRITE)
 */
data class SharedAccessDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("access_level") val accessLevel: String,
)

/**
 * Data about a note conflict.
 *
 * @property syncId Unique ID of the conflicting note
 * @property localNote The local version of the note
 * @property remoteNote The remote version of the note
 * @property conflictType The type of conflict
 */
data class NoteConflict(
    @SerializedName("sync_id") val syncId: String,
    @SerializedName("local_note") val localNote: NoteDto,
    @SerializedName("remote_note") val remoteNote: NoteDto,
    @SerializedName("conflict_type") val conflictType: String,
)

/** Enum representing the possible types of note conflicts. */
enum class ConflictType {
    CONTENT_CONFLICT, // Both versions have different content changes
    METADATA_CONFLICT, // Conflicting changes to metadata (pinned, archived, etc.)
    SIMULTANEOUS_UPDATE, // Note was updated locally and remotely at the same time
}
