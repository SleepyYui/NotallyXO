package com.sleepyyui.notallyxo.utils.sync.api

import com.sleepyyui.notallyxo.utils.sync.api.model.AuthResponse
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteDto
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteSyncRequest
import com.sleepyyui.notallyxo.utils.sync.api.model.SyncResponse
import com.sleepyyui.notallyxo.utils.sync.api.model.SyncStatusResponse
import com.sleepyyui.notallyxo.utils.sync.api.model.UserProfileResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface defining the REST API endpoints for cloud synchronization.
 *
 * This interface works with Retrofit to provide type-safe HTTP client methods for communication
 * with the NotallyX cloud synchronization server.
 */
interface CloudApiClient {

    /**
     * Authenticate with the server using an auth token.
     *
     * @param authToken User's authentication token
     * @return Response containing auth status and user info
     */
    @POST("auth/token")
    suspend fun authenticate(@Header("Authorization") authToken: String): Response<AuthResponse>

    /**
     * Get user profile information.
     *
     * @param authToken User's authentication token
     * @return Response containing user profile data
     */
    @GET("users/profile")
    suspend fun getUserProfile(
        @Header("Authorization") authToken: String
    ): Response<UserProfileResponse>

    /**
     * Get the current sync status from the server.
     *
     * @param authToken User's authentication token
     * @return Response containing information about the latest sync timestamp and number of notes
     */
    @GET("sync/status")
    suspend fun getSyncStatus(
        @Header("Authorization") authToken: String
    ): Response<SyncStatusResponse>

    /**
     * Sync notes with the server.
     *
     * @param authToken User's authentication token
     * @param changedSince Only retrieve notes changed since this timestamp
     * @param syncRequest The local changes to push to the server
     * @return Response containing the sync result
     */
    @POST("sync")
    suspend fun syncNotes(
        @Header("Authorization") authToken: String,
        @Query("changed_since") changedSince: Long,
        @Body syncRequest: NoteSyncRequest,
    ): Response<SyncResponse>

    /**
     * Get a specific note by its sync ID.
     *
     * @param authToken User's authentication token
     * @param syncId The sync ID of the note
     * @return Response containing the note data
     */
    @GET("notes/{syncId}")
    suspend fun getNote(
        @Header("Authorization") authToken: String,
        @Path("syncId") syncId: String,
    ): Response<NoteDto>

    /**
     * Create or update a note on the server.
     *
     * @param authToken User's authentication token
     * @param syncId The sync ID of the note
     * @param note The note to create or update
     * @return Response containing the updated note data
     */
    @PUT("notes/{syncId}")
    suspend fun putNote(
        @Header("Authorization") authToken: String,
        @Path("syncId") syncId: String,
        @Body note: NoteDto,
    ): Response<NoteDto>

    /**
     * Delete a note from the server.
     *
     * @param authToken User's authentication token
     * @param syncId The sync ID of the note to delete
     * @return Empty response with status code
     */
    @DELETE("notes/{syncId}")
    suspend fun deleteNote(
        @Header("Authorization") authToken: String,
        @Path("syncId") syncId: String,
    ): Response<Void>

    /**
     * Share a note with another user.
     *
     * @param authToken User's authentication token
     * @param syncId The sync ID of the note to share
     * @param userId The user ID to share with
     * @param accessLevel The access level to grant (READ_ONLY or READ_WRITE)
     * @return Response confirming the share operation
     */
    @POST("notes/{syncId}/share")
    suspend fun shareNote(
        @Header("Authorization") authToken: String,
        @Path("syncId") syncId: String,
        @Query("user_id") userId: String,
        @Query("access_level") accessLevel: String,
    ): Response<NoteDto>

    /**
     * Create a one-time sharing token for a note.
     *
     * @param authToken User's authentication token
     * @param syncId The sync ID of the note
     * @param accessLevel The access level to grant (READ_ONLY or READ_WRITE)
     * @param expiryTime Optional expiry time in milliseconds since epoch
     * @return Response containing the sharing token
     */
    @POST("notes/{syncId}/sharing-token")
    suspend fun createSharingToken(
        @Header("Authorization") authToken: String,
        @Path("syncId") syncId: String,
        @Query("access_level") accessLevel: String,
        @Query("expiry_time") expiryTime: Long?,
    ): Response<Map<String, String>>

    /**
     * Accept a shared note using a token.
     *
     * @param authToken User's authentication token
     * @param sharingToken The sharing token
     * @return Response containing the shared note
     */
    @POST("shared/accept")
    suspend fun acceptSharedNote(
        @Header("Authorization") authToken: String,
        @Query("token") sharingToken: String,
    ): Response<NoteDto>
}
