package com.sleepyyui.notallyxo.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.SyncStatus
import com.sleepyyui.notallyxo.utils.sync.SyncStatusIndicator
import com.sleepyyui.notallyxo.utils.sync.websocket.CloudWebSocketClient

/**
 * Custom view that displays sync status and WebSocket connection status in the toolbar.
 *
 * This view shows different icons based on the current sync status and WebSocket connection state,
 * with visual indicators for:
 * - Connected (green sync icon)
 * - Disconnected (gray sync icon)
 * - Syncing (animated progress indicator)
 * - Error (red sync icon)
 */
class SyncStatusIndicatorView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {

    private val syncIcon: ImageView
    private val syncProgress: ProgressBar

    // Access to sync services
    private val syncStatusIndicator = SyncStatusIndicator.getInstance(context)
    private var webSocketClient: CloudWebSocketClient? = null

    // Track current states
    private var currentSyncStatus: SyncStatus = SyncStatus.IDLE
    private var isWebSocketConnected = false

    // WebSocket connection listener
    private val webSocketConnectionListener =
        object : CloudWebSocketClient.ConnectionListener {
            override fun onConnected() {
                isWebSocketConnected = true
                updateIndicator()
            }

            override fun onDisconnected() {
                isWebSocketConnected = false
                updateIndicator()
            }

            override fun onError(error: String) {
                isWebSocketConnected = false
                updateIndicator()
            }
        }

    init {
        // Inflate layout
        val view =
            LayoutInflater.from(context).inflate(R.layout.layout_sync_status_indicator, this, true)
        syncIcon = view.findViewById(R.id.sync_status_icon)
        syncProgress = view.findViewById(R.id.sync_progress)

        // Set click listener
        setOnClickListener {
            when {
                currentSyncStatus == SyncStatus.SYNCING -> {
                    Toast.makeText(context, R.string.cloud_sync_in_progress, Toast.LENGTH_SHORT)
                        .show()
                }
                currentSyncStatus == SyncStatus.FAILED || !isWebSocketConnected -> {
                    Toast.makeText(context, R.string.sync_status_reconnecting, Toast.LENGTH_SHORT)
                        .show()
                    webSocketClient?.connect()
                }
                isWebSocketConnected -> {
                    Toast.makeText(context, R.string.sync_status_connected, Toast.LENGTH_SHORT)
                        .show()
                }
                else -> {
                    // Should not happen, but handle just in case
                    Toast.makeText(context, R.string.sync_status_disconnected, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    /** Sets the WebSocket client to monitor for connection status changes. */
    fun setWebSocketClient(client: CloudWebSocketClient) {
        // Remove listener from old client if exists
        webSocketClient?.removeConnectionListener(webSocketConnectionListener)

        // Set new client and add listener
        webSocketClient = client
        webSocketClient?.addConnectionListener(webSocketConnectionListener)

        // Update initial connection state
        isWebSocketConnected = webSocketClient?.isConnected() ?: false
        updateIndicator()
    }

    /**
     * Starts observing sync status and WebSocket connection state changes. Should be called when
     * the view is attached to a lifecycle.
     */
    fun startObserving(lifecycleOwner: LifecycleOwner) {
        // Observe sync status changes
        syncStatusIndicator.syncStatus.observe(lifecycleOwner) { status ->
            currentSyncStatus = status
            updateIndicator()
        }

        // Initialize with current status
        updateIndicator()
    }

    /** Updates the indicator based on current sync status and WebSocket connection state. */
    private fun updateIndicator() {
        when (currentSyncStatus) {
            SyncStatus.SYNCING -> {
                syncIcon.visibility = View.GONE
                syncProgress.visibility = View.VISIBLE
                contentDescription = context.getString(R.string.cloud_sync_in_progress)
            }
            SyncStatus.FAILED -> {
                syncIcon.visibility = View.VISIBLE
                syncProgress.visibility = View.GONE
                syncIcon.setImageResource(R.drawable.sync_error)
                contentDescription = context.getString(R.string.sync_status_error)
            }
            else -> {
                // For all other states, show connected/disconnected based on WebSocket state
                syncIcon.visibility = View.VISIBLE
                syncProgress.visibility = View.GONE

                if (isWebSocketConnected) {
                    syncIcon.setImageResource(R.drawable.sync_connected)
                    contentDescription = context.getString(R.string.sync_status_connected)
                } else {
                    syncIcon.setImageResource(R.drawable.sync_disconnected)
                    contentDescription = context.getString(R.string.sync_status_disconnected)
                }
            }
        }
    }

    /** Clean up resources when the view is detached from the window. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webSocketClient?.removeConnectionListener(webSocketConnectionListener)
    }
}
