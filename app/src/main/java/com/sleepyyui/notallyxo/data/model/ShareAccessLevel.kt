package com.sleepyyui.notallyxo.data.model

/** Represents the access level a user has to a shared note. */
enum class ShareAccessLevel {
    /** The user can only view the note but cannot make changes. */
    READ_ONLY,

    /** The user can both view and edit the note. */
    READ_WRITE,
}
