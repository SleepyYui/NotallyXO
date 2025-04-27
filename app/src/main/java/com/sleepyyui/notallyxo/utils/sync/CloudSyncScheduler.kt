package com.sleepyyui.notallyxo.utils.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sleepyyui.notallyxo.data.NotallyXODatabase
import com.sleepyyui.notallyxo.data.dao.BaseNoteDao
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scheduler for cloud synchronization using WorkManager.
 *
 * This class provides methods to schedule, cancel, and request immediate sync operations using
 * Android's WorkManager, allowing sync to happen in the background at regular intervals.
 */
class CloudSyncScheduler {

    companion object {
        // Work tags and names
        private const val PERIODIC_SYNC_WORK_NAME = "com.sleepyyui.notallyxo.PERIODIC_SYNC"
        private const val IMMEDIATE_SYNC_WORK_NAME = "com.sleepyyui.notallyxo.IMMEDIATE_SYNC"

        // Default sync intervals
        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 60L // 1 hour
        private const val MIN_SYNC_INTERVAL_MINUTES = 15L // Minimum recommended by WorkManager

        /**
         * Schedule periodic background synchronization based on user preferences.
         *
         * @param context Application context
         * @param forceReschedule If true, cancels existing work and reschedules
         */
        fun schedulePeriodicSync(context: Context, forceReschedule: Boolean = false) {
            val appContext = context.applicationContext
            val syncSettingsManager = SyncSettingsManager.getInstance(appContext)

            // Don't schedule if auto sync is disabled
            if (!syncSettingsManager.isSyncEnabled || !syncSettingsManager.isAutoSyncEnabled) {
                cancelPeriodicSync(appContext)
                return
            }

            // Create network constraints based on user preferences
            val constraints =
                Constraints.Builder()
                    .apply {
                        if (syncSettingsManager.isWifiOnlySync) {
                            setRequiredNetworkType(NetworkType.UNMETERED)
                        } else {
                            setRequiredNetworkType(NetworkType.CONNECTED)
                        }
                        // Optional: don't sync when battery is low
                        setRequiresBatteryNotLow(true)
                    }
                    .build()

            // Set a reasonable sync interval - minimum 15 minutes as recommended by WorkManager
            val syncInterval = DEFAULT_SYNC_INTERVAL_MINUTES

            // Create the periodic work request
            val periodicWorkRequest =
                PeriodicWorkRequestBuilder<SyncWorker>(
                        syncInterval,
                        TimeUnit.MINUTES,
                        // Flex period allows WorkManager to optimize exactly when the work runs
                        syncInterval / 4,
                        TimeUnit.MINUTES,
                    )
                    .apply { setConstraints(constraints) }
                    .build()

            // Schedule the work with appropriate policy
            val policy =
                if (forceReschedule) {
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }

            WorkManager.getInstance(appContext)
                .enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK_NAME, policy, periodicWorkRequest)
        }

        /**
         * Request an immediate one-time synchronization.
         *
         * @param context Application context
         */
        fun requestImmediateSync(context: Context) {
            val appContext = context.applicationContext
            val syncSettingsManager = SyncSettingsManager.getInstance(appContext)

            // Don't run if sync is disabled
            if (!syncSettingsManager.isSyncEnabled) {
                return
            }

            // Create network constraints based on user preferences
            val constraints =
                Constraints.Builder()
                    .apply {
                        if (syncSettingsManager.isWifiOnlySync) {
                            setRequiredNetworkType(NetworkType.UNMETERED)
                        } else {
                            setRequiredNetworkType(NetworkType.CONNECTED)
                        }
                    }
                    .build()

            // Create the one-time work request
            val syncWorkRequest =
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()

            // Enqueue the work
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(
                    IMMEDIATE_SYNC_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest,
                )
        }

        /**
         * Cancel all scheduled background synchronization.
         *
         * @param context Application context
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        }
    }

    /**
     * Worker class that performs the actual sync operation in the background. This inner class
     * connects WorkManager to our existing CloudSyncService.
     */
    class SyncWorker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {

        private val syncService = CloudSyncService.getInstance(appContext)
        // Wrap the context properly to match the expected ContextWrapper type
        private val database =
            NotallyXODatabase.getDatabase(android.content.ContextWrapper(appContext))
        private val noteDao: BaseNoteDao = database.value.getBaseNoteDao()

        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                try {
                    // Initialize the DAO in the sync service
                    syncService.setNoteDao(noteDao)

                    // Perform synchronization
                    val syncResult = syncService.syncNotes()

                    return@withContext if (syncResult.isSuccess) {
                        Result.success()
                    } else {
                        // Get the exception
                        val exception = syncResult.exceptionOrNull()

                        // Retry for network-related errors
                        if (
                            exception is java.net.SocketTimeoutException ||
                                exception is java.net.UnknownHostException ||
                                exception is java.io.IOException
                        ) {
                            Result.retry()
                        } else {
                            // Don't retry for authentication or other errors
                            Result.failure()
                        }
                    }
                } catch (e: Exception) {
                    // Unexpected error, don't retry
                    Result.failure()
                }
            }
    }
}
