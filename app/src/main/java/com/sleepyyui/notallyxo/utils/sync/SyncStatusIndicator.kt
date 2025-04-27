package com.sleepyyui.notallyxo.utils.sync

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the display of synchronization status information in the UI.
 *
 * This class provides methods to update the sync status, manages the visibility of status
 * indicators, and formats status messages.
 */
class SyncStatusIndicator(private val context: Context) {

    companion object {
        private const val STATUS_DURATION_MS = 3000L // How long to show temporary statuses

        @Volatile private var INSTANCE: SyncStatusIndicator? = null

        fun getInstance(context: Context): SyncStatusIndicator {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: SyncStatusIndicator(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    // LiveData to represent the current sync status
    private val _syncStatus = MutableLiveData<SyncStatus>(SyncStatus.IDLE)
    val syncStatus: LiveData<SyncStatus> = _syncStatus

    // LiveData for status message (e.g., error details)
    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    // Last successful sync timestamp
    private val syncSettingsManager = SyncSettingsManager.getInstance(context)

    /**
     * Updates the current sync status.
     *
     * @param status The new sync status
     * @param message Optional message with additional details (e.g., error information)
     * @param isTemporary Whether this status should automatically clear after a delay
     */
    fun updateStatus(status: SyncStatus, message: String = "", isTemporary: Boolean = false) {
        _syncStatus.postValue(status)
        _statusMessage.postValue(message)

        // Update last sync timestamp if successful
        if (status == SyncStatus.SYNCED) {
            syncSettingsManager.lastSyncTimestamp = System.currentTimeMillis()
        }

        // Clear temporary status after delay
        if (isTemporary) {
            android.os
                .Handler(android.os.Looper.getMainLooper())
                .postDelayed(
                    {
                        // Only clear if the status hasn't been changed by something else
                        if (_syncStatus.value == status) {
                            _syncStatus.postValue(SyncStatus.IDLE)
                            _statusMessage.postValue("")
                        }
                    },
                    STATUS_DURATION_MS,
                )
        }
    }

    /**
     * Updates a TextView to show the current sync status or last sync time.
     *
     * @param textView The TextView to update
     */
    fun updateStatusText(textView: TextView) {
        val timestamp = syncSettingsManager.lastSyncTimestamp

        if (timestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            val syncTime = dateFormat.format(Date(timestamp))
            textView.text = context.getString(R.string.cloud_sync_status, syncTime)
            textView.visibility = View.VISIBLE
        } else {
            textView.visibility = View.GONE
        }

        // Handle special cases for active sync operations
        when (_syncStatus.value) {
            SyncStatus.SYNCING -> {
                textView.text = context.getString(R.string.cloud_sync_in_progress)
                textView.visibility = View.VISIBLE
            }
            SyncStatus.FAILED -> {
                val errorMsg =
                    _statusMessage.value.takeIf { !it.isNullOrEmpty() }
                        ?: context.getString(R.string.cloud_connection_error)
                textView.text = context.getString(R.string.cloud_sync_failed, errorMsg)
                textView.visibility = View.VISIBLE
                // Also set text color to error color
                textView.setTextColor(
                    ContextCompat.getColor(context, R.color.md_theme_error)
                ) // Use md_theme_error
            }
            SyncStatus.SYNCED -> {
                textView.text = context.getString(R.string.cloud_sync_completed)
                textView.visibility = View.VISIBLE
                // Reset text color to normal
                textView.setTextColor(textView.textColors.defaultColor)
            }
            else -> {
                // For IDLE, NOT_CONFIGURED, CONFLICT, etc., show the last sync time if available
                if (textView.visibility == View.VISIBLE) {
                    // Reset text color to normal
                    textView.setTextColor(textView.textColors.defaultColor)
                }
            }
        }
    }

    /**
     * Checks if the sync is currently active.
     *
     * @return true if sync is in progress, false otherwise
     */
    fun isSyncing(): Boolean {
        return _syncStatus.value == SyncStatus.SYNCING
    }

    /** Resets the status to idle. */
    fun resetStatus() {
        _syncStatus.postValue(SyncStatus.IDLE)
        _statusMessage.postValue("")
    }
}
