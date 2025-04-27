package com.sleepyyui.notallyxo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.sleepyyui.notallyxo.presentation.setEnabledSecureFlag
import com.sleepyyui.notallyxo.presentation.view.misc.NotNullLiveData
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.BiometricLock
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotallyXOPreferences
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotallyXOPreferences.Companion.EMPTY_PATH
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.Theme
import com.sleepyyui.notallyxo.presentation.widget.WidgetProvider
import com.sleepyyui.notallyxo.utils.backup.AUTO_BACKUP_WORK_NAME
import com.sleepyyui.notallyxo.utils.backup.autoBackupOnSave
import com.sleepyyui.notallyxo.utils.backup.cancelAutoBackup
import com.sleepyyui.notallyxo.utils.backup.containsNonCancelled
import com.sleepyyui.notallyxo.utils.backup.deleteModifiedNoteBackup
import com.sleepyyui.notallyxo.utils.backup.isEqualTo
import com.sleepyyui.notallyxo.utils.backup.modifiedNoteBackupExists
import com.sleepyyui.notallyxo.utils.backup.scheduleAutoBackup
import com.sleepyyui.notallyxo.utils.backup.updateAutoBackup
import com.sleepyyui.notallyxo.utils.observeOnce
import com.sleepyyui.notallyxo.utils.security.UnlockReceiver
import com.sleepyyui.notallyxo.utils.sync.CloudSyncScheduler
import com.sleepyyui.notallyxo.utils.sync.SyncSettingsManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotallyXOApplication : Application(), Application.ActivityLifecycleCallbacks {

    private lateinit var biometricLockObserver: Observer<BiometricLock>
    private lateinit var preferences: NotallyXOPreferences
    private var unlockReceiver: UnlockReceiver? = null

    val locked = NotNullLiveData(true)

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        if (isTestRunner()) return
        preferences = NotallyXOPreferences.getInstance(this)

        // Initialize cloud sync if enabled
        initializeCloudSync()

        if (preferences.useDynamicColors.value) {
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivitiesIfAvailable(this)
            }
        } else {
            setTheme(R.style.AppTheme)
        }
        preferences.theme.observeForeverWithPrevious { (oldTheme, theme) ->
            when (theme) {
                Theme.DARK ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

                Theme.LIGHT ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

                Theme.FOLLOW_SYSTEM ->
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
            }
            if (oldTheme != null) {
                WidgetProvider.updateWidgets(this)
            }
        }

        preferences.backupsFolder.observeForeverWithPrevious { (backupFolderBefore, backupFolder) ->
            checkUpdatePeriodicBackup(
                backupFolderBefore,
                backupFolder,
                preferences.periodicBackups.value.periodInDays.toLong(),
            )
        }
        preferences.periodicBackups.observeForever { value ->
            val backupFolder = preferences.backupsFolder.value
            checkUpdatePeriodicBackup(backupFolder, backupFolder, value.periodInDays.toLong())
        }

        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF) }
        biometricLockObserver = Observer { biometricLock ->
            if (biometricLock == BiometricLock.ENABLED) {
                unlockReceiver = UnlockReceiver(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(unlockReceiver, filter)
                }
            } else {
                unlockReceiver?.let { unregisterReceiver(it) }
                if (locked.value) {
                    locked.postValue(false)
                }
            }
        }
        preferences.biometricLock.observeForever(biometricLockObserver)

        locked.observeForever { isLocked -> WidgetProvider.updateWidgets(this, locked = isLocked) }

        preferences.backupPassword.observeForeverWithPrevious {
            (previousBackupPassword, backupPassword) ->
            if (preferences.backupOnSave.value) {
                val backupPath = preferences.backupsFolder.value
                if (backupPath != EMPTY_PATH) {
                    if (
                        !modifiedNoteBackupExists(backupPath) ||
                            (previousBackupPassword != null &&
                                previousBackupPassword != backupPassword)
                    ) {
                        deleteModifiedNoteBackup(backupPath)
                        MainScope().launch {
                            withContext(Dispatchers.IO) {
                                autoBackupOnSave(
                                    backupPath,
                                    savedNote = null,
                                    password = backupPassword,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Initializes cloud synchronization based on user preferences. This sets up the background sync
     * scheduling if sync is enabled.
     */
    private fun initializeCloudSync() {
        val syncSettingsManager = SyncSettingsManager.getInstance(this)

        // Schedule background sync if enabled
        if (syncSettingsManager.isSyncEnabled && syncSettingsManager.isAutoSyncEnabled) {
            CloudSyncScheduler.schedulePeriodicSync(this)
        }
    }

    private fun checkUpdatePeriodicBackup(
        backupFolderBefore: String?,
        backupFolder: String,
        periodInDays: Long,
    ) {
        val workManager = getWorkManagerSafe() ?: return
        workManager.getWorkInfosForUniqueWorkLiveData(AUTO_BACKUP_WORK_NAME).observeOnce { workInfos
            ->
            if (backupFolder == EMPTY_PATH || periodInDays < 1) {
                if (workInfos?.containsNonCancelled() == true) {
                    workManager.cancelAutoBackup()
                }
            } else if (
                workInfos.isNullOrEmpty() ||
                    workInfos.all { it.state == WorkInfo.State.CANCELLED } ||
                    (backupFolderBefore != null && backupFolderBefore != backupFolder)
            ) {
                workManager.scheduleAutoBackup(this, periodInDays)
            } else if (
                workInfos.first().periodicityInfo?.isEqualTo(periodInDays, TimeUnit.DAYS) == false
            ) {
                workManager.updateAutoBackup(workInfos, periodInDays)
            }
        }
    }

    private fun getWorkManagerSafe(): WorkManager? {
        return try {
            WorkManager.getInstance(this)
        } catch (e: Exception) {
            // TODO: Happens when ErrorActivity is launched
            null
        }
    }

    companion object {
        private fun isTestRunner(): Boolean {
            return Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.setEnabledSecureFlag(preferences.secureFlag.value)
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
