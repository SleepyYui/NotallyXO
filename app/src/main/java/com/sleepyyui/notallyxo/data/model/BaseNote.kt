package com.sleepyyui.notallyxo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Format: `#RRGGBB` or `#AARRGGBB` or [BaseNote.COLOR_DEFAULT] */
typealias ColorString = String

@Entity(indices = [Index(value = ["id", "folder", "pinned", "timestamp", "labels"])])
data class BaseNote(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val type: Type,
    val folder: Folder,
    val color: ColorString,
    val title: String,
    val pinned: Boolean,
    val timestamp: Long,
    val modifiedTimestamp: Long,
    val labels: List<String>,
    val body: String,
    val spans: List<SpanRepresentation>,
    val items: List<ListItem>,
    val images: List<FileAttachment>,
    val files: List<FileAttachment>,
    val audios: List<Audio>,
    val reminders: List<Reminder>,
    val viewMode: NoteViewMode,

    // Cloud sync related fields
    val syncId: String = "",
    val syncStatus: SyncStatus = SyncStatus.NOT_SYNCED,
    val lastSyncedTimestamp: Long = 0,

    // Sharing related fields
    val isShared: Boolean = false,
    val sharedAccesses: List<SharedAccess> = emptyList(),
    val sharingTokens: List<SharingToken> = emptyList(),
    val ownerUserId: String = "",
) : Item {

    companion object {
        const val COLOR_DEFAULT = "DEFAULT"
        const val COLOR_NEW = "NEW"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseNote

        if (id != other.id) return false
        if (type != other.type) return false
        if (folder != other.folder) return false
        if (color != other.color) return false
        if (title != other.title) return false
        if (pinned != other.pinned) return false
        if (timestamp != other.timestamp) return false
        if (labels != other.labels) return false
        if (body != other.body) return false
        if (spans != other.spans) return false
        if (items != other.items) return false
        if (images != other.images) return false
        if (files != other.files) return false
        if (audios != other.audios) return false
        if (reminders != other.reminders) return false
        if (viewMode != other.viewMode) return false
        if (syncId != other.syncId) return false
        if (syncStatus != other.syncStatus) return false
        if (lastSyncedTimestamp != other.lastSyncedTimestamp) return false
        if (isShared != other.isShared) return false
        if (sharedAccesses != other.sharedAccesses) return false
        if (sharingTokens != other.sharingTokens) return false
        if (ownerUserId != other.ownerUserId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + folder.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + pinned.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + labels.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + spans.hashCode()
        result = 31 * result + items.hashCode()
        result = 31 * result + images.hashCode()
        result = 31 * result + files.hashCode()
        result = 31 * result + audios.hashCode()
        result = 31 * result + reminders.hashCode()
        result = 31 * result + viewMode.hashCode()
        result = 31 * result + syncId.hashCode()
        result = 31 * result + syncStatus.hashCode()
        result = 31 * result + lastSyncedTimestamp.hashCode()
        result = 31 * result + isShared.hashCode()
        result = 31 * result + sharedAccesses.hashCode()
        result = 31 * result + sharingTokens.hashCode()
        result = 31 * result + ownerUserId.hashCode()
        return result
    }
}

fun BaseNote.deepCopy(): BaseNote {
    return copy(
        labels = labels.toMutableList(),
        spans = spans.map { it.copy() }.toMutableList(),
        items = items.map { it.copy() }.toMutableList(),
        images = images.map { it.copy() }.toMutableList(),
        files = files.map { it.copy() }.toMutableList(),
        audios = audios.map { it.copy() }.toMutableList(),
        reminders = reminders.map { it.copy() }.toMutableList(),
        sharedAccesses = sharedAccesses.toMutableList(),
        sharingTokens = sharingTokens.toMutableList(),
    )
}

/**
 * Generates a new syncId for a note. Should be called when creating a note that will be
 * synchronized.
 */
fun BaseNote.withNewSyncId(): BaseNote {
    return this.copy(syncId = UUID.randomUUID().toString(), syncStatus = SyncStatus.PENDING_UPLOAD)
}

/**
 * Updates the sync status of a note to indicate it has pending changes that need to be uploaded to
 * the server.
 */
fun BaseNote.withPendingChanges(): BaseNote {
    return if (this.syncId.isNotEmpty()) {
        this.copy(syncStatus = SyncStatus.PENDING_UPLOAD)
    } else {
        this
    }
}

/** Marks this note as successfully synced with the server */
fun BaseNote.markSynced(): BaseNote {
    return this.copy(
        syncStatus = SyncStatus.SYNCED,
        lastSyncedTimestamp = System.currentTimeMillis(),
    )
}

/**
 * Creates a one-time sharing token for this note.
 *
 * @param accessLevel The level of access to grant with this token
 * @param expirationTimeMs Optional expiration time in milliseconds (0 for no expiration)
 * @return A new copy of this note with the generated sharing token
 */
fun BaseNote.createSharingToken(
    accessLevel: ShareAccessLevel,
    expirationTimeMs: Long = 0,
): Pair<BaseNote, String> {
    val token = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()

    val sharingToken =
        SharingToken(
            token = token,
            noteId = this.id,
            accessLevel = accessLevel,
            createdTimestamp = now,
            expirationTimestamp = if (expirationTimeMs > 0) now + expirationTimeMs else 0,
            isUsed = false,
        )

    val updatedNote = this.copy(isShared = true, sharingTokens = this.sharingTokens + sharingToken)

    return Pair(updatedNote, token)
}

/**
 * Adds a user to the shared access list using a token.
 *
 * @param tokenValue The token value to use
 * @param userId The ID of the user to grant access to
 * @return A new copy of this note with the user added if the token is valid, or the original note
 *   if the token is invalid
 */
fun BaseNote.addUserWithToken(tokenValue: String, userId: String): BaseNote {
    val token = this.sharingTokens.find { it.token == tokenValue } ?: return this // Token not found

    if (!token.isValid()) {
        return this // Token is expired or already used
    }

    // Mark token as used
    val updatedTokens =
        this.sharingTokens.map { if (it.token == tokenValue) it.markAsUsed() else it }

    // Create shared access entry
    val sharedAccess =
        SharedAccess(
            userId = userId,
            accessLevel = token.accessLevel,
            grantedTimestamp = System.currentTimeMillis(),
            usedToken = tokenValue,
        )

    return this.copy(
        isShared = true,
        sharedAccesses = this.sharedAccesses + sharedAccess,
        sharingTokens = updatedTokens,
    )
}

/**
 * Updates the access level for a user who already has access to this note.
 *
 * @param userId The ID of the user whose access level to update
 * @param newAccessLevel The new access level to give the user
 * @return A new copy of this note with the updated access level
 */
fun BaseNote.updateUserAccessLevel(userId: String, newAccessLevel: ShareAccessLevel): BaseNote {
    val updatedAccesses =
        this.sharedAccesses.map {
            if (it.userId == userId) it.copy(accessLevel = newAccessLevel) else it
        }

    return this.copy(sharedAccesses = updatedAccesses)
}

/**
 * Removes access for a user from this note.
 *
 * @param userId The ID of the user to remove
 * @return A new copy of this note with the user removed
 */
fun BaseNote.removeUserAccess(userId: String): BaseNote {
    val updatedAccesses = this.sharedAccesses.filter { it.userId != userId }
    val isStillShared = updatedAccesses.isNotEmpty() || this.sharingTokens.any { it.isValid() }

    return this.copy(isShared = isStillShared, sharedAccesses = updatedAccesses)
}

/**
 * Revokes an unused sharing token.
 *
 * @param tokenValue The token value to revoke
 * @return A new copy of this note with the token revoked
 */
fun BaseNote.revokeToken(tokenValue: String): BaseNote {
    val updatedTokens = this.sharingTokens.filter { it.token != tokenValue }
    val isStillShared = updatedTokens.any { it.isValid() } || this.sharedAccesses.isNotEmpty()

    return this.copy(isShared = isStillShared, sharingTokens = updatedTokens)
}
