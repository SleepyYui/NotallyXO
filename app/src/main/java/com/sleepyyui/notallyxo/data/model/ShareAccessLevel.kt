package com.sleepyyui.notallyxo.data.model

import com.squareup.moshi.JsonClass

/** Represents the access level a user has to a shared note. */
@JsonClass(generateAdapter = false)
enum class ShareAccessLevel {
    /** The user can only view the note but cannot make changes. */
    READ_ONLY,

    /** The user can both view and edit the note. */
    READ_WRITE,
}
