package com.sleepyyui.notallyxo.utils.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manager for cloud synchronization settings.
 *
 * This class handles storing and retrieving user preferences related to cloud synchronization,
 * including server connection details and encryption settings.
 */
class SyncSettingsManager private constructor(private val context: Context) {

    companion object {
        // Preferences file name
        private const val PREFERENCES_NAME = "notallyxo_sync_preferences"

        // Preference keys
        private const val PREF_SYNC_ENABLED = "sync_enabled"
        private const val PREF_SERVER_URL = "server_url"
        private const val PREF_SERVER_PORT = "server_port"
        private const val PREF_AUTH_TOKEN = "auth_token"
        private const val PREF_USER_ID = "user_id"
        private const val PREF_ENCRYPTION_SALT = "encryption_salt"
        private const val PREF_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val PREF_WIFI_ONLY_SYNC = "wifi_only_sync"
        private const val PREF_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"

        // Default values
        private const val DEFAULT_PORT = 8080

        @Volatile private var INSTANCE: SyncSettingsManager? = null

        fun getInstance(context: Context): SyncSettingsManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: SyncSettingsManager(context.applicationContext).also { INSTANCE = it }
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

    /** Whether cloud synchronization is enabled. */
    var isSyncEnabled: Boolean
        get() = sharedPreferences.getBoolean(PREF_SYNC_ENABLED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(PREF_SYNC_ENABLED, value).apply()
        }

    /** The URL of the synchronization server. */
    var serverUrl: String
        get() = sharedPreferences.getString(PREF_SERVER_URL, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(PREF_SERVER_URL, value).apply()
        }

    /** The port of the synchronization server. */
    var serverPort: Int
        get() = sharedPreferences.getInt(PREF_SERVER_PORT, DEFAULT_PORT)
        set(value) {
            sharedPreferences.edit().putInt(PREF_SERVER_PORT, value).apply()
        }

    /** The authentication token for the synchronization server. */
    var authToken: String
        get() = sharedPreferences.getString(PREF_AUTH_TOKEN, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(PREF_AUTH_TOKEN, value).apply()
        }

    /** The user's unique ID for synchronization and sharing. */
    var userId: String
        get() = sharedPreferences.getString(PREF_USER_ID, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(PREF_USER_ID, value).apply()
        }

    /**
     * The salt used for deriving the encryption key from the master password. Stored as a
     * Base64-encoded string.
     */
    var encryptionSalt: String
        get() = sharedPreferences.getString(PREF_ENCRYPTION_SALT, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(PREF_ENCRYPTION_SALT, value).apply()
        }

    /** Whether automatic synchronization is enabled. */
    var isAutoSyncEnabled: Boolean
        get() = sharedPreferences.getBoolean(PREF_AUTO_SYNC_ENABLED, true)
        set(value) {
            sharedPreferences.edit().putBoolean(PREF_AUTO_SYNC_ENABLED, value).apply()
        }

    /** Whether synchronization should only occur over Wi-Fi. */
    var isWifiOnlySync: Boolean
        get() = sharedPreferences.getBoolean(PREF_WIFI_ONLY_SYNC, true)
        set(value) {
            sharedPreferences.edit().putBoolean(PREF_WIFI_ONLY_SYNC, value).apply()
        }

    /** The timestamp of the last successful synchronization. */
    var lastSyncTimestamp: Long
        get() = sharedPreferences.getLong(PREF_LAST_SYNC_TIMESTAMP, 0)
        set(value) {
            sharedPreferences.edit().putLong(PREF_LAST_SYNC_TIMESTAMP, value).apply()
        }

    /** The complete server URL including the protocol, domain, and port. */
    fun getFullServerUrl(): String {
        val url = serverUrl.trim()
        // Add https:// if the URL doesn't have a protocol
        val urlWithProtocol =
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

        // Add port if it's not the default HTTP(S) port
        return if (serverPort != 80 && serverPort != 443) {
            "$urlWithProtocol:$serverPort"
        } else {
            urlWithProtocol
        }
    }

    /** Checks if all required sync settings are configured. */
    fun areSettingsConfigured(): Boolean {
        return serverUrl.isNotBlank() && authToken.isNotBlank() && encryptionSalt.isNotBlank()
    }

    /** Clears all synchronization settings. */
    fun clearSettings() {
        sharedPreferences
            .edit()
            .apply {
                putBoolean(PREF_SYNC_ENABLED, false)
                putString(PREF_SERVER_URL, "")
                putInt(PREF_SERVER_PORT, DEFAULT_PORT)
                putString(PREF_AUTH_TOKEN, "")
                // Don't clear the user ID to maintain identity
                putString(PREF_ENCRYPTION_SALT, "")
                putBoolean(PREF_AUTO_SYNC_ENABLED, true)
                putBoolean(PREF_WIFI_ONLY_SYNC, true)
                putLong(PREF_LAST_SYNC_TIMESTAMP, 0)
            }
            .apply()
    }
}
