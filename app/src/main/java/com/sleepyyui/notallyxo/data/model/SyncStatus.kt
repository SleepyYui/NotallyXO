package com.sleepyyui.notallyxo.data.model

/** Represents the synchronization status of a note or the sync system as a whole. */
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

    /** The sync system is idle, not actively syncing but ready. */
    IDLE,

    /** The sync operation failed. */
    FAILED,

    /** The sync system is not properly configured. */
    NOT_CONFIGURED,

    /** The note is pending deletion on the server. */
    PENDING_DELETE,

    /** The note is newly created locally and has not been synced yet. */
    NEW,
}
