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
    val isShared: Boolean = false,
    val sharedWithUsers: List<String> = emptyList(),
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
        if (sharedWithUsers != other.sharedWithUsers) return false
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
        result = 31 * result + sharedWithUsers.hashCode()
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
        sharedWithUsers = sharedWithUsers.toMutableList(),
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
