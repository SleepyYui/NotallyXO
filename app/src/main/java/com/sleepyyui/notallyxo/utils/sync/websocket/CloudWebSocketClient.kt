package com.sleepyyui.notallyxo.utils.sync.websocket

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.sleepyyui.notallyxo.data.NotallyXODatabase
import com.sleepyyui.notallyxo.data.dao.BaseNoteDao
import com.sleepyyui.notallyxo.utils.security.CloudEncryptionService
import com.sleepyyui.notallyxo.utils.sync.SyncSettingsManager
import com.sleepyyui.notallyxo.utils.sync.mappers.NoteMapper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/**
 * Client for WebSocket connections to the backend server.
 *
 * This class handles establishing and maintaining a WebSocket connection to receive real-time
 * updates from the NotallyXO backend server. It processes incoming messages about note updates and
 * deletions, updating the local database accordingly.
 */
class CloudWebSocketClient private constructor(private val context: Context) {

    /** Interface for listening to WebSocket connection events. */
    interface ConnectionListener {
        /** Called when the WebSocket connection is established. */
        fun onConnected()

        /** Called when the WebSocket connection is closed. */
        fun onDisconnected()

        /** Called when a WebSocket connection error occurs. */
        fun onError(error: String)
    }

    companion object {
        private const val TAG = "CloudWebSocketClient"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val RECONNECT_DELAY_MS = 5000L // 5 seconds
        private const val MAX_RECONNECT_ATTEMPTS = 5

        @Volatile private var INSTANCE: CloudWebSocketClient? = null

        fun getInstance(context: Context): CloudWebSocketClient {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: CloudWebSocketClient(context.applicationContext).also { INSTANCE = it }
                }
        }
    }

    private val syncSettingsManager = SyncSettingsManager.getInstance(context)
    private val encryptionService = CloudEncryptionService()
    private val okHttpClient =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket connections
            .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    // List of connection listeners
    private val connectionListeners = CopyOnWriteArrayList<ConnectionListener>()

    // CoroutineScope for WebSocket operations
    private val webSocketScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Database access
    private lateinit var noteDao: BaseNoteDao

    init {
        // Initialize DAO in init block - wrap the Context in a ContextWrapper
        val contextWrapper = context as? ContextWrapper ?: ContextWrapper(context)
        NotallyXODatabase.getDatabase(contextWrapper).value.let { database ->
            noteDao = database.getBaseNoteDao()
        }
    }

    // Encryption key for note content
    private val encryptionKey: SecretKey? by lazy {
        if (syncSettingsManager.encryptionSalt.isNotEmpty()) {
            try {
                // Derive key from stored salt
                val salt =
                    android.util.Base64.decode(
                        syncSettingsManager.encryptionSalt,
                        android.util.Base64.NO_WRAP,
                    )
                encryptionService.deriveKeyFromPassword("master_password", salt)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to derive encryption key", e)
                null
            }
        } else null
    }

    /** Connect to the WebSocket endpoint on the backend server. */
    fun connect() {
        if (isConnected || webSocket != null) {
            return
        }

        if (!syncSettingsManager.areSettingsConfigured()) {
            Log.w(TAG, "Cannot connect WebSocket: sync settings not configured")
            notifyError("Sync settings not configured")
            return
        }

        val serverUrl = syncSettingsManager.getFullServerUrl()
        val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")
        val url = "$wsUrl/ws/updates"

        val request =
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${syncSettingsManager.authToken}")
                .build()

        Log.d(TAG, "Connecting to WebSocket: $url")
        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
    }

    /**
     * Disconnect from the WebSocket endpoint.
     *
     * @param code The closure code to use
     * @param reason The reason for closing the connection
     */
    fun disconnect(
        code: Int = NORMAL_CLOSURE_STATUS,
        reason: String = "User initiated disconnect",
    ) {
        Log.d(TAG, "Disconnecting WebSocket")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0

        webSocket?.close(code, reason)
        webSocket = null
        isConnected = false
        notifyDisconnected()
    }

    /** Send a ping message to the server to keep the connection alive. */
    fun ping() {
        if (isConnected && webSocket != null) {
            webSocket?.send("ping")
        }
    }

    /** Returns whether the WebSocket is currently connected. */
    fun isConnected(): Boolean {
        return isConnected
    }

    /** Add a connection listener to receive WebSocket connection events. */
    fun addConnectionListener(listener: ConnectionListener) {
        connectionListeners.add(listener)
        // Immediately notify the new listener of the current connection state
        if (isConnected) {
            listener.onConnected()
        } else {
            listener.onDisconnected()
        }
    }

    /** Remove a previously added connection listener. */
    fun removeConnectionListener(listener: ConnectionListener) {
        connectionListeners.remove(listener)
    }

    /** Notify all connection listeners that the WebSocket is connected. */
    private fun notifyConnected() {
        connectionListeners.forEach { it.onConnected() }
    }

    /** Notify all connection listeners that the WebSocket is disconnected. */
    private fun notifyDisconnected() {
        connectionListeners.forEach { it.onDisconnected() }
    }

    /** Notify all connection listeners that an error occurred. */
    private fun notifyError(error: String) {
        connectionListeners.forEach { it.onError(error) }
    }

    /** Attempt to reconnect to the WebSocket server after a disconnect. */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob =
            webSocketScope.launch {
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    Log.d(
                        TAG,
                        "Scheduling reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${RECONNECT_DELAY_MS}ms",
                    )
                    delay(RECONNECT_DELAY_MS)
                    if (isActive) {
                        connect()
                    }
                } else {
                    Log.w(TAG, "Max reconnect attempts reached, giving up")
                    reconnectAttempts = 0
                }
            }
    }

    /** Process a WebSocket message containing a note update. */
    private suspend fun processNoteUpdate(jsonObject: JSONObject) {
        try {
            val noteDto = NoteMapper.fromWebSocketJson(jsonObject.getJSONObject("note"))
            val existingNote = noteDao.getNotesBySyncId(noteDto.syncId)

            if (existingNote != null) {
                // If we have this note, update it
                val updatedNote =
                    NoteMapper.updateLocalNoteFromDto(
                        existingNote,
                        noteDto,
                        encryptionService,
                        encryptionKey,
                    )
                noteDao.insert(updatedNote)
                Log.d(TAG, "Updated note from WebSocket notification: ${noteDto.syncId}")
            } else {
                // If we don't have this note but it's shared with us, insert it
                if (noteDto.isShared && noteDto.ownerUserId != syncSettingsManager.userId) {
                    val newNote = NoteMapper.fromNoteDto(noteDto, encryptionService, encryptionKey)
                    noteDao.insert(newNote)
                    Log.d(
                        TAG,
                        "Inserted shared note from WebSocket notification: ${noteDto.syncId}",
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing note update", e)
        }
    }

    /** Process a WebSocket message containing a note deletion. */
    private suspend fun processNoteDeletion(jsonObject: JSONObject) {
        try {
            val syncId = jsonObject.getString("syncId")
            val existingNote = noteDao.getNotesBySyncId(syncId)

            if (existingNote != null) {
                // If the note is owned by someone else (shared with us), remove it
                if (
                    existingNote.ownerUserId != null &&
                        existingNote.ownerUserId != syncSettingsManager.userId
                ) {
                    noteDao.delete(existingNote.id)
                    Log.d(TAG, "Deleted note from WebSocket notification: $syncId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing note deletion", e)
        }
    }

    private val webSocketListener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                isConnected = true
                reconnectAttempts = 0
                notifyConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                try {
                    val jsonObject = JSONObject(text)
                    val type = jsonObject.optString("type", "")

                    webSocketScope.launch {
                        when (type) {
                            "NOTE_UPDATED" -> processNoteUpdate(jsonObject)
                            "NOTE_DELETED" -> processNoteDeletion(jsonObject)
                            "pong" -> Log.d(TAG, "Server responded with pong")
                            else -> Log.d(TAG, "Unknown message type: $type")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing WebSocket message", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "WebSocket binary message received: ${bytes.hex()}")
                // Not expected in the current implementation
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, null)
                isConnected = false
                notifyDisconnected()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                notifyDisconnected()

                // Only attempt to reconnect if it wasn't a normal closure
                if (code != NORMAL_CLOSURE_STATUS) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${response?.message ?: t.message}", t)
                isConnected = false
                notifyError(t.message ?: "Unknown error")
                notifyDisconnected()
                scheduleReconnect()
            }
        }

    /** Clean up resources when the client is no longer needed. */
    fun cleanup() {
        disconnect()
        webSocketScope.cancel()
    }
}
