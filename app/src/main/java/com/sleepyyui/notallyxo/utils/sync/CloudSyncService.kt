package com.sleepyyui.notallyxo.utils.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sleepyyui.notallyxo.data.dao.BaseNoteDao
import com.sleepyyui.notallyxo.data.model.SyncStatus
import com.sleepyyui.notallyxo.utils.security.AndroidKeyStoreHelper
import com.sleepyyui.notallyxo.utils.security.CloudEncryptionService
import com.sleepyyui.notallyxo.utils.sync.api.CloudApiClient
import com.sleepyyui.notallyxo.utils.sync.api.model.AuthResponse
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteDto
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteSyncRequest
import com.sleepyyui.notallyxo.utils.sync.api.model.SyncStatusResponse
import com.sleepyyui.notallyxo.utils.sync.mappers.NoteMapper
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Service class for handling cloud synchronization operations.
 *
 * This class manages the Retrofit client, handles network connectivity, and provides methods for
 * interacting with the cloud sync API.
 */
class CloudSyncService(private val context: Context) {

    private val syncSettingsManager = SyncSettingsManager.getInstance(context)
    private val statusIndicator = SyncStatusIndicator.getInstance(context)
    private val encryptionService = CloudEncryptionService()
    private val conflictManager = ConflictManager.getInstance(context)
    private lateinit var noteDao: BaseNoteDao

    // Encryption key for note content
    private val encryptionKey: SecretKey by lazy {
        // Get the encryption key from Android's secure key storage
        // If the key doesn't exist yet, create and store it securely
        val keyStore = AndroidKeyStoreHelper.getInstance(context)
        keyStore.getOrCreateSecretKey(ENCRYPTION_KEY_ALIAS)
    }

    // Add a function to set the DAO from outside, since we can't inject it directly
    fun setNoteDao(dao: BaseNoteDao) {
        this.noteDao = dao
    }

    // Lazily initialize the API client when first needed
    private val apiClient by lazy {
        createApiClient(
            baseUrl =
                "https://${syncSettingsManager.serverUrl}:${syncSettingsManager.serverPort}/api/v1/",
            loggingEnabled = true,
        )
    }

    /**
     * Create and configure a Retrofit API client.
     *
     * @param baseUrl The base URL for the API
     * @param loggingEnabled Whether to enable HTTP request/response logging
     * @return Configured CloudApiClient instance
     */
    private fun createApiClient(baseUrl: String, loggingEnabled: Boolean): CloudApiClient {
        // Create a Gson instance for JSON serialization/deserialization
        val gson: Gson = GsonBuilder().setLenient().create()

        // Create an OkHttp client with logging and timeouts
        val httpBuilder =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

        // Add logging if enabled (typically for debug builds)
        if (loggingEnabled) {
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BODY
            httpBuilder.addInterceptor(logging)
        }

        // Build the Retrofit instance
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpBuilder.build())
            .build()
            .create(CloudApiClient::class.java)
    }

    /**
     * Check if network connectivity is available.
     *
     * @param wifiOnly Whether to only consider Wi-Fi connections
     * @return True if a viable connection exists, false otherwise
     */
    fun isNetworkAvailable(wifiOnly: Boolean): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                // If Wi-Fi only is required, check for Wi-Fi
                wifiOnly -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

                // Otherwise, any transport type is acceptable
                else ->
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        } else {
            // For older Android versions
            @Suppress("DEPRECATION") val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null &&
                networkInfo.isConnected &&
                (!wifiOnly || networkInfo.type == ConnectivityManager.TYPE_WIFI)
        }
    }

    /**
     * Test the connection to the sync server.
     *
     * @return A Result object containing success status and message
     */
    suspend fun testConnection(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (!syncSettingsManager.areSettingsConfigured()) {
                    return@withContext Result.failure<Boolean>(
                        IllegalStateException("Sync settings not fully configured")
                    )
                }

                if (!isNetworkAvailable(syncSettingsManager.isWifiOnlySync)) {
                    return@withContext Result.failure<Boolean>(
                        IOException("No network connection available")
                    )
                }

                val response = apiClient.getSyncStatus("Bearer ${syncSettingsManager.authToken}")

                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(
                        IOException(
                            "Server returned error: ${response.code()} ${response.message()}"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Authenticate with the server using the configured auth token.
     *
     * @return A Result object containing the authentication response
     */
    suspend fun authenticate(): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!syncSettingsManager.areSettingsConfigured()) {
                    return@withContext Result.failure<AuthResponse>(
                        IllegalStateException("Sync settings not fully configured")
                    )
                }

                if (!isNetworkAvailable(syncSettingsManager.isWifiOnlySync)) {
                    return@withContext Result.failure<AuthResponse>(
                        IOException("No network connection available")
                    )
                }

                statusIndicator.updateStatus(SyncStatus.SYNCING)

                val response = apiClient.authenticate("Bearer ${syncSettingsManager.authToken}")

                if (response.isSuccessful) {
                    val authResponse = response.body()
                    if (authResponse != null && authResponse.success) {
                        // Update the userId if it was returned and we don't have one yet
                        authResponse.userId?.let { userId ->
                            if (syncSettingsManager.userId.isEmpty()) {
                                syncSettingsManager.userId = userId
                            }
                        }
                        statusIndicator.updateStatus(SyncStatus.IDLE)
                        Result.success(authResponse)
                    } else {
                        val errorMsg = authResponse?.message ?: "Authentication failed"
                        statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
                        Result.failure(IOException(errorMsg))
                    }
                } else {
                    val errorMsg = "Server error: ${response.code()} ${response.message()}"
                    statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
                    Result.failure(IOException(errorMsg))
                }
            } catch (e: Exception) {
                statusIndicator.updateStatus(SyncStatus.FAILED, e.message ?: "Unknown error")
                Result.failure(e)
            }
        }
    }

    /**
     * Get the current sync status from the server.
     *
     * @return A Result object containing the sync status response
     */
    suspend fun getSyncStatus(): Result<SyncStatusResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!syncSettingsManager.areSettingsConfigured()) {
                    return@withContext Result.failure<SyncStatusResponse>(
                        IllegalStateException("Sync settings not fully configured")
                    )
                }

                if (!isNetworkAvailable(syncSettingsManager.isWifiOnlySync)) {
                    return@withContext Result.failure<SyncStatusResponse>(
                        IOException("No network connection available")
                    )
                }

                val response = apiClient.getSyncStatus("Bearer ${syncSettingsManager.authToken}")

                if (response.isSuccessful) {
                    val statusResponse = response.body()
                    if (statusResponse != null) {
                        Result.success(statusResponse)
                    } else {
                        Result.failure(IOException("Empty response body"))
                    }
                } else {
                    Result.failure(
                        IOException(
                            "Server returned error: ${response.code()} ${response.message()}"
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Handle common API response processing.
     *
     * @param response The Retrofit Response object
     * @param errorMessage Message to use if the response body is null
     * @return Processed Result object
     */
    private fun <T> processResponse(response: Response<T>, errorMessage: String): Result<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                Result.success(body)
            } else {
                Result.failure(IOException(errorMessage))
            }
        } else {
            Result.failure(IOException("Server error: ${response.code()} ${response.message()}"))
        }
    }

    /**
     * Fetch a specific note from the server.
     *
     * @param syncId The sync ID of the note to fetch
     * @return A Result object containing the note data
     */
    suspend fun fetchNote(syncId: String): Result<NoteDto> {
        return withContext(Dispatchers.IO) {
            try {
                if (!syncSettingsManager.areSettingsConfigured()) {
                    return@withContext Result.failure<NoteDto>(
                        IllegalStateException("Sync settings not fully configured")
                    )
                }

                if (!isNetworkAvailable(syncSettingsManager.isWifiOnlySync)) {
                    return@withContext Result.failure<NoteDto>(
                        IOException("No network connection available")
                    )
                }

                val response = apiClient.getNote("Bearer ${syncSettingsManager.authToken}", syncId)
                processResponse(response, "Empty note response")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Initiates a sync operation to bring the local database in sync with the server. This is the
     * main sync method that should be called to start a sync.
     *
     * @return A Result object indicating sync success or failure
     */
    suspend fun syncNotes(): Result<Boolean> {
        statusIndicator.updateStatus(SyncStatus.SYNCING)

        // Check if basic requirements are met
        if (!syncSettingsManager.areSettingsConfigured()) {
            val errorMsg = "Sync settings not fully configured"
            statusIndicator.updateStatus(SyncStatus.NOT_CONFIGURED, errorMsg)
            return Result.failure(IllegalStateException(errorMsg))
        }

        if (!isNetworkAvailable(syncSettingsManager.isWifiOnlySync)) {
            val errorMsg = "No network connection available"
            statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
            return Result.failure(IOException(errorMsg))
        }

        // Ensure DAO is initialized
        if (!::noteDao.isInitialized) {
            val errorMsg = "Note DAO not initialized"
            statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
            return Result.failure(IllegalStateException(errorMsg))
        }

        // First, authenticate to ensure we're connected and authorized
        val authResult = authenticate()
        if (authResult.isFailure) {
            val exception = authResult.exceptionOrNull()
            val errorMsg = exception?.message ?: "Authentication failed"
            statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
            return Result.failure(exception ?: IOException(errorMsg))
        }

        // Get the current sync status from the server
        val statusResult = getSyncStatus()
        if (statusResult.isFailure) {
            val exception = statusResult.exceptionOrNull()
            val errorMsg = exception?.message ?: "Failed to get sync status"
            statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
            return Result.failure(exception ?: IOException(errorMsg))
        }

        // Start the actual sync process
        return withContext(Dispatchers.IO) {
            try {
                // Set a timeout for the entire sync operation to prevent hanging
                withTimeout(120_000) { // 2 minutes timeout
                    performSync(statusResult.getOrThrow())
                }
            } catch (e: Exception) {
                statusIndicator.updateStatus(SyncStatus.FAILED, e.message ?: "Sync failed")
                Result.failure(e)
            }
        }
    }

    /** Performs the actual synchronization process. */
    private suspend fun performSync(serverStatus: SyncStatusResponse): Result<Boolean> {
        try {
            // 1. Get local notes that need to be synced (pending upload)
            val notesToSync = noteDao.getNotesNeedingSync()

            // 2. Get notes marked for deletion
            val notesToDelete = noteDao.getNotesNeedingDeletion()
            val deletedSyncIds = notesToDelete.map { it.syncId }

            // 3. Convert local notes to DTOs for the API
            val noteDtos =
                notesToSync.map { note ->
                    NoteMapper.toNoteDto(
                        note,
                        encryptionService,
                        encryptionKey,
                    ) // Pass the encryption key
                }

            // 4. Create the sync request
            val syncRequest =
                NoteSyncRequest(changedNotes = noteDtos, deletedNoteIds = deletedSyncIds)

            // 5. Get the last sync timestamp to only retrieve newer changes from server
            val lastSyncTimestamp = syncSettingsManager.lastSyncTimestamp

            // 6. Send the sync request to the server and get the response
            val response =
                apiClient.syncNotes(
                    "Bearer ${syncSettingsManager.authToken}",
                    lastSyncTimestamp,
                    syncRequest,
                )

            if (!response.isSuccessful) {
                val errorMsg = "Sync failed: ${response.code()} ${response.message()}"
                statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
                return Result.failure(IOException(errorMsg))
            }

            val syncResponse = response.body()
            if (syncResponse == null) {
                val errorMsg = "Empty response from server"
                statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
                return Result.failure(IOException(errorMsg))
            }

            if (!syncResponse.success) {
                val errorMsg = syncResponse.message ?: "Sync failed"
                statusIndicator.updateStatus(SyncStatus.FAILED, errorMsg)
                return Result.failure(IOException(errorMsg))
            }

            // 7. Process server changes and update local database

            // 7a. Handle server note updates
            syncResponse.updatedNotes.forEach { noteDto ->
                // Look for existing note with the same syncId
                val existingNote = noteDao.getNotesBySyncId(noteDto.syncId)

                if (existingNote != null) {
                    // Update existing note
                    val updatedNote =
                        NoteMapper.toBaseNote(
                            noteDto,
                            encryptionService,
                            encryptionKey, // Pass the encryption key
                            existingNote,
                        )
                    noteDao.insert(updatedNote)
                } else {
                    // Create new note
                    val newNote =
                        NoteMapper.toBaseNote(
                            noteDto,
                            encryptionService,
                            encryptionKey, // Pass the encryption key
                        )
                    noteDao.insert(newNote)
                }
            }

            // 7b. Handle server deletions
            syncResponse.deletedNoteIds.forEach { syncId ->
                val noteToDelete = noteDao.getNotesBySyncId(syncId)
                if (noteToDelete != null) {
                    noteDao.delete(noteToDelete.id)
                }
            }

            // 7c. Handle conflicts - store them for user resolution instead of auto-resolving
            var conflictsDetected = false
            syncResponse.conflicts.forEach { conflict ->
                val localNote = noteDao.getNotesBySyncId(conflict.syncId)
                if (localNote != null) {
                    // Instead of automatically resolving, store the conflict for later resolution
                    conflictManager.addConflict(localNote, conflict)
                    conflictsDetected = true
                }
            }

            // Update status if conflicts were detected
            if (conflictsDetected) {
                statusIndicator.updateStatus(
                    SyncStatus.CONFLICT_DETECTED,
                    "Conflicts detected: ${syncResponse.conflicts.size}",
                )
            }

            // 8. Update sync status of synced notes
            notesToSync.forEach { note ->
                noteDao.updateSyncStatus(note.id, SyncStatus.SYNCED, syncResponse.lastSyncTimestamp)
            }

            // 9. Delete notes that were pending deletion
            if (notesToDelete.isNotEmpty()) {
                noteDao.deleteNotesPendingDeletion()
            }

            // 10. Update last sync timestamp in settings
            syncSettingsManager.lastSyncTimestamp = syncResponse.lastSyncTimestamp

            // 11. Update sync status (only if no conflicts were detected)
            if (!conflictsDetected) {
                statusIndicator.updateStatus(SyncStatus.SYNCED, isTemporary = true)
            }
            return Result.success(true)
        } catch (e: Exception) {
            statusIndicator.updateStatus(
                SyncStatus.FAILED,
                e.message ?: "Unknown error during sync",
            )
            return Result.failure(e)
        }
    }

    companion object {
        @Volatile private var INSTANCE: CloudSyncService? = null
        private const val ENCRYPTION_KEY_ALIAS = "notallyxo_cloud_sync_encryption_key"

        fun getInstance(context: Context): CloudSyncService {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: CloudSyncService(context.applicationContext).also { INSTANCE = it }
                }
        }
    }
}
