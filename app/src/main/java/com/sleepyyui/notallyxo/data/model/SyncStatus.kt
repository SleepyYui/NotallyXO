package com.sleepyyui.notallyxo.data.model

/** Represents the synchronization status of a note. */
enum class SyncStatus {
    /** The note is synced with the server and up-to-date. */
    SYNCED,

    /** The note has local changes that need to be uploaded to the server. */
    PENDING_UPLOAD,

    /** The note has conflicts between local and server versions. */
    CONFLICT,

    /** The note is being currently synchronized. */
    SYNCING,

    /** The note is not synced with any server. */
    NOT_SYNCED,
}
