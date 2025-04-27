package com.sleepyyui.notallyxo.utils.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.sleepyyui.notallyxo.data.model.BaseNote
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteConflict
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager for handling note synchronization conflicts.
 *
 * This class stores conflicts that occur during synchronization and provides methods to retrieve
 * and resolve them. When a conflict is detected, both the local and remote versions are stored to
 * allow the user to review differences and choose which version to keep.
 */
class ConflictManager private constructor(private val context: Context) {

    companion object {
        // Preferences file name
        private const val PREFERENCES_NAME = "notallyxo_sync_conflicts"

        // Preference keys
        private const val PREF_PENDING_CONFLICTS = "pending_conflicts"

        @Volatile private var INSTANCE: ConflictManager? = null

        fun getInstance(context: Context): ConflictManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: ConflictManager(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    private val masterKey =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private val sharedPreferences: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private val gson = GsonBuilder().create()

    private val _pendingConflicts = MutableLiveData<List<StoredConflict>>(emptyList())

    // Public LiveData to observe conflicts
    val pendingConflicts: LiveData<List<StoredConflict>> = _pendingConflicts

    init {
        // Load any existing conflicts
        loadConflicts()
    }

    /**
     * Add a new conflict to be resolved later by the user.
     *
     * @param localNote The local version of the note
     * @param conflict The conflict information from the server
     * @return True if the conflict was successfully stored
     */
    fun addConflict(localNote: BaseNote, conflict: NoteConflict): Boolean {
        val currentConflicts = _pendingConflicts.value?.toMutableList() ?: mutableListOf()

        // Check if a conflict for this note already exists
        val existingConflictIndex = currentConflicts.indexOfFirst { it.syncId == localNote.syncId }

        val storedConflict =
            StoredConflict(
                syncId = localNote.syncId,
                noteId = localNote.id,
                title = localNote.title,
                timestamp = System.currentTimeMillis(),
                localVersion = localNote,
                serverVersion = conflict.remoteNote,
                localModificationTime = localNote.modifiedTimestamp,
                serverModificationTime =
                    conflict.remoteNote
                        .lastModifiedTimestamp, // Changed from modifiedTimestamp to
                                               // lastModifiedTimestamp
            )

        if (existingConflictIndex != -1) {
            // Update existing conflict
            currentConflicts[existingConflictIndex] = storedConflict
        } else {
            // Add new conflict
            currentConflicts.add(storedConflict)
        }

        _pendingConflicts.postValue(currentConflicts)
        return saveConflicts()
    }

    /**
     * Mark a conflict as resolved and remove it from the pending list.
     *
     * @param syncId The sync ID of the resolved conflict
     * @return True if the conflict was successfully removed
     */
    fun removeConflict(syncId: String): Boolean {
        val currentConflicts = _pendingConflicts.value?.toMutableList() ?: mutableListOf()
        val initialSize = currentConflicts.size

        currentConflicts.removeAll { it.syncId == syncId }

        if (currentConflicts.size != initialSize) {
            _pendingConflicts.postValue(currentConflicts)
            return saveConflicts()
        }
        return false
    }

    /**
     * Get a specific conflict by sync ID.
     *
     * @param syncId The sync ID of the conflict to retrieve
     * @return The conflict if found, null otherwise
     */
    fun getConflict(syncId: String): StoredConflict? {
        return _pendingConflicts.value?.find { it.syncId == syncId }
    }

    /**
     * Get all pending conflicts.
     *
     * @return List of all pending conflicts
     */
    fun getAllConflicts(): List<StoredConflict> {
        return _pendingConflicts.value ?: emptyList()
    }

    /**
     * Check if there are any pending conflicts.
     *
     * @return True if there are conflicts that need resolution
     */
    fun hasConflicts(): Boolean {
        return _pendingConflicts.value?.isNotEmpty() == true
    }

    /**
     * Get the number of pending conflicts.
     *
     * @return The number of conflicts that need resolution
     */
    fun getConflictCount(): Int {
        return _pendingConflicts.value?.size ?: 0
    }

    /**
     * Save the current list of conflicts to persistent storage.
     *
     * @return True if the conflicts were successfully saved
     */
    private fun saveConflicts(): Boolean {
        return try {
            val jsonAdapter = object : TypeToken<List<StoredConflict>>() {}.type
            val conflictsJson = gson.toJson(_pendingConflicts.value, jsonAdapter)
            sharedPreferences.edit().putString(PREF_PENDING_CONFLICTS, conflictsJson).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Load stored conflicts from persistent storage. */
    private fun loadConflicts() {
        try {
            val conflictsJson = sharedPreferences.getString(PREF_PENDING_CONFLICTS, null)
            if (conflictsJson != null) {
                val jsonAdapter = object : TypeToken<List<StoredConflict>>() {}.type
                val conflicts = gson.fromJson<List<StoredConflict>>(conflictsJson, jsonAdapter)
                _pendingConflicts.postValue(conflicts ?: emptyList())
            }
        } catch (e: Exception) {
            _pendingConflicts.postValue(emptyList())
        }
    }

    /**
     * Clear all stored conflicts.
     *
     * @return True if the conflicts were successfully cleared
     */
    fun clearAll(): Boolean {
        _pendingConflicts.postValue(emptyList())
        return try {
            sharedPreferences.edit().remove(PREF_PENDING_CONFLICTS).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Data class representing a stored sync conflict. */
    data class StoredConflict(
        val syncId: String,
        val noteId: Long,
        val title: String,
        val timestamp: Long,
        val localVersion: BaseNote,
        val serverVersion: NoteDto,
        val localModificationTime: Long,
        val serverModificationTime: Long,
    ) {
        /** Format the local modification time as a readable string. */
        fun getFormattedLocalModificationTime(): String {
            return formatTimestamp(localModificationTime)
        }

        /** Format the server modification time as a readable string. */
        fun getFormattedServerModificationTime(): String {
            return formatTimestamp(serverModificationTime)
        }

        /** Calculate how much time difference there is between the two versions. */
        fun getTimeDifference(): String {
            val diffMs = Math.abs(localModificationTime - serverModificationTime)
            val diffSeconds = diffMs / 1000
            val diffMinutes = diffSeconds / 60
            val diffHours = diffMinutes / 60
            val diffDays = diffHours / 24

            return when {
                diffDays > 0 -> "$diffDays days"
                diffHours > 0 -> "$diffHours hours"
                diffMinutes > 0 -> "$diffMinutes minutes"
                else -> "$diffSeconds seconds"
            }
        }

        /**
         * Determine which version is newer based on modification time.
         *
         * @return true if local version is newer, false if server version is newer
         */
        fun isLocalVersionNewer(): Boolean {
            return localModificationTime > serverModificationTime
        }

        private fun formatTimestamp(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }
}
