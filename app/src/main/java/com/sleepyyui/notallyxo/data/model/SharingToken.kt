package com.sleepyyui.notallyxo.data.model

import com.squareup.moshi.JsonClass

/** Represents a one-time token that can be shared with others to grant access to a note. */
@JsonClass(generateAdapter = true)
data class SharingToken(
    /** The unique hash value that identifies this token. */
    val token: String,

    /** The note ID this token grants access to. */
    val noteId: Long,

    /** The access level this token grants (read-only or read-write). */
    val accessLevel: ShareAccessLevel,

    /** When this token was created. */
    val createdTimestamp: Long,

    /**
     * Optional expiration timestamp, after which the token becomes invalid. A value of 0 indicates
     * no expiration.
     */
    val expirationTimestamp: Long = 0,

    /** Whether this token has been used. Once used, a token cannot be reused. */
    val isUsed: Boolean = false,
) {
    /** Checks if this token is currently valid (not expired and not used). */
    fun isValid(): Boolean {
        val now = System.currentTimeMillis()
        return !isUsed && (expirationTimestamp == 0L || expirationTimestamp > now)
    }

    /** Creates a copy of this token marked as used. */
    fun markAsUsed(): SharingToken = this.copy(isUsed = true)
}
