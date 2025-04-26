package com.sleepyyui.notallyxo.data.model

/** Represents a user who has access to a shared note. */
data class SharedAccess(
    /** The unique identifier for the user who has access to the note. */
    val userId: String,

    /** The access level the user has (read-only or read-write). */
    val accessLevel: ShareAccessLevel,

    /** When the user was granted access to the note. */
    val grantedTimestamp: Long,

    /** The hash token that was used to gain access to the note. */
    val usedToken: String,
)
