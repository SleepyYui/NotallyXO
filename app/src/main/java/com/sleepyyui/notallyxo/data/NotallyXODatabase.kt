package com.sleepyyui.notallyxo.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sleepyyui.notallyxo.data.dao.BaseNoteDao
import com.sleepyyui.notallyxo.data.dao.CommonDao
import com.sleepyyui.notallyxo.data.dao.LabelDao
import com.sleepyyui.notallyxo.data.model.BaseNote
import com.sleepyyui.notallyxo.data.model.Color
import com.sleepyyui.notallyxo.data.model.Converters
import com.sleepyyui.notallyxo.data.model.Label
import com.sleepyyui.notallyxo.data.model.NoteViewMode
import com.sleepyyui.notallyxo.data.model.toColorString
import com.sleepyyui.notallyxo.presentation.view.misc.NotNullLiveData
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.BiometricLock
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.NotallyXOPreferences
import com.sleepyyui.notallyxo.presentation.viewmodel.preference.observeForeverSkipFirst
import com.sleepyyui.notallyxo.utils.getExternalMediaDirectory
import com.sleepyyui.notallyxo.utils.security.SQLCipherUtils
import com.sleepyyui.notallyxo.utils.security.getInitializedCipherForDecryption
import java.io.File
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@TypeConverters(Converters::class)
@Database(entities = [BaseNote::class, Label::class], version = 10)
abstract class NotallyXODatabase : RoomDatabase() {

    abstract fun getLabelDao(): LabelDao

    abstract fun getCommonDao(): CommonDao

    abstract fun getBaseNoteDao(): BaseNoteDao

    fun checkpoint() {
        getBaseNoteDao().query(SimpleSQLiteQuery("pragma wal_checkpoint(FULL)"))
    }

    private var biometricLockObserver: Observer<BiometricLock>? = null
    private var dataInPublicFolderObserver: Observer<Boolean>? = null

    companion object {

        const val DATABASE_NAME = "NotallyXODatabase"

        @Volatile private var instance: NotNullLiveData<NotallyXODatabase>? = null

        fun getCurrentDatabaseFile(context: ContextWrapper): File {
            return if (NotallyXOPreferences.getInstance(context).dataInPublicFolder.value) {
                getExternalDatabaseFile(context)
            } else {
                getInternalDatabaseFile(context)
            }
        }

        fun getExternalDatabaseFile(context: ContextWrapper): File {
            return File(context.getExternalMediaDirectory(), DATABASE_NAME)
        }

        fun getExternalDatabaseFiles(context: ContextWrapper): List<File> {
            return listOf(
                File(context.getExternalMediaDirectory(), DATABASE_NAME),
                File(context.getExternalMediaDirectory(), "$DATABASE_NAME-shm"),
                File(context.getExternalMediaDirectory(), "$DATABASE_NAME-wal"),
            )
        }

        fun getInternalDatabaseFile(context: Context): File {
            return context.getDatabasePath(DATABASE_NAME)
        }

        fun getInternalDatabaseFiles(context: ContextWrapper): List<File> {
            val directory = context.getDatabasePath(DATABASE_NAME).parentFile
            return listOf(
                File(directory, DATABASE_NAME),
                File(directory, "$DATABASE_NAME-shm"),
                File(directory, "$DATABASE_NAME-wal"),
            )
        }

        private fun getCurrentDatabaseName(context: ContextWrapper): String {
            return if (NotallyXOPreferences.getInstance(context).dataInPublicFolder.value) {
                getExternalDatabaseFile(context).absolutePath
            } else {
                DATABASE_NAME
            }
        }

        fun getDatabase(
            context: ContextWrapper,
            observePreferences: Boolean = true,
        ): NotNullLiveData<NotallyXODatabase> {
            return instance
                ?: synchronized(this) {
                    val preferences = NotallyXOPreferences.getInstance(context)
                    this.instance =
                        NotNullLiveData(createInstance(context, preferences, observePreferences))
                    return this.instance!!
                }
        }

        fun getFreshDatabase(context: ContextWrapper): NotallyXODatabase {
            return createInstance(context, NotallyXOPreferences.getInstance(context), false)
        }

        private fun createInstance(
            context: ContextWrapper,
            preferences: NotallyXOPreferences,
            observePreferences: Boolean,
        ): NotallyXODatabase {
            val instanceBuilder =
                Room.databaseBuilder(
                        context,
                        NotallyXODatabase::class.java,
                        getCurrentDatabaseName(context),
                    )
                    .addMigrations(
                        Migration2,
                        Migration3,
                        Migration4,
                        Migration5,
                        Migration6,
                        Migration7,
                        Migration8,
                        Migration9,
                        Migration10,
                    )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SQLiteDatabase.loadLibs(context)
                if (preferences.biometricLock.value == BiometricLock.ENABLED) {
                    if (
                        SQLCipherUtils.getDatabaseState(getCurrentDatabaseFile(context)) ==
                            SQLCipherUtils.State.ENCRYPTED
                    ) {
                        initializeDecryption(preferences, instanceBuilder)
                    } else {
                        preferences.biometricLock.save(BiometricLock.DISABLED)
                    }
                } else {
                    if (
                        SQLCipherUtils.getDatabaseState(getCurrentDatabaseFile(context)) ==
                            SQLCipherUtils.State.ENCRYPTED
                    ) {
                        preferences.biometricLock.save(BiometricLock.ENABLED)
                        initializeDecryption(preferences, instanceBuilder)
                    }
                }
                val instance = instanceBuilder.build()
                if (observePreferences) {
                    instance.biometricLockObserver = Observer {
                        NotallyXODatabase.instance?.value?.biometricLockObserver?.let {
                            preferences.biometricLock.removeObserver(it)
                        }
                        val newInstance = createInstance(context, preferences, true)
                        NotallyXODatabase.instance?.postValue(newInstance)
                        preferences.biometricLock.observeForeverSkipFirst(
                            newInstance.biometricLockObserver!!
                        )
                    }
                    preferences.biometricLock.observeForeverSkipFirst(
                        instance.biometricLockObserver!!
                    )

                    instance.dataInPublicFolderObserver = Observer {
                        NotallyXODatabase.instance?.value?.dataInPublicFolderObserver?.let {
                            preferences.dataInPublicFolder.removeObserver(it)
                        }
                        val newInstance = createInstance(context, preferences, true)
                        NotallyXODatabase.instance?.postValue(newInstance)
                        preferences.dataInPublicFolder.observeForeverSkipFirst(
                            newInstance.dataInPublicFolderObserver!!
                        )
                    }
                    preferences.dataInPublicFolder.observeForeverSkipFirst(
                        instance.dataInPublicFolderObserver!!
                    )
                }
                return instance
            }
            return instanceBuilder.build()
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun initializeDecryption(
            preferences: NotallyXOPreferences,
            instanceBuilder: Builder<NotallyXODatabase>,
        ) {
            val initializationVector = preferences.iv.value!!
            val cipher = getInitializedCipherForDecryption(iv = initializationVector)
            val encryptedPassphrase = preferences.databaseEncryptionKey.value
            val passphrase = cipher.doFinal(encryptedPassphrase)
            val factory = SupportFactory(passphrase)
            instanceBuilder.openHelperFactory(factory)
        }

        object Migration2 : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `color` TEXT NOT NULL DEFAULT 'DEFAULT'"
                )
            }
        }

        object Migration3 : Migration(2, 3) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `images` TEXT NOT NULL DEFAULT `[]`")
            }
        }

        object Migration4 : Migration(3, 4) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `audios` TEXT NOT NULL DEFAULT `[]`")
            }
        }

        object Migration5 : Migration(4, 5) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `files` TEXT NOT NULL DEFAULT `[]`")
            }
        }

        object Migration6 : Migration(5, 6) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `modifiedTimestamp` INTEGER NOT NULL DEFAULT 'timestamp'"
                )
            }
        }

        object Migration7 : Migration(6, 7) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `reminders` TEXT NOT NULL DEFAULT `[]`"
                )
            }
        }

        object Migration8 : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("SELECT id, color FROM BaseNote")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val colorString = cursor.getString(cursor.getColumnIndexOrThrow("color"))
                    val color = Color.valueOfOrDefault(colorString)
                    val hexColor = color.toColorString()
                    db.execSQL("UPDATE BaseNote SET color = ? WHERE id = ?", arrayOf(hexColor, id))
                }
                cursor.close()
            }
        }

        object Migration9 : Migration(8, 9) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `BaseNote` ADD COLUMN `viewMode` TEXT NOT NULL DEFAULT '${NoteViewMode.EDIT.name}'"
                )
            }
        }

        object Migration10 : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add columns safely by checking if they exist first
                addColumnIfNotExists(database, "BaseNote", "syncId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfNotExists(
                    database,
                    "BaseNote",
                    "syncStatus",
                    "TEXT NOT NULL DEFAULT 'NOT_SYNCED'",
                )
                addColumnIfNotExists(
                    database,
                    "BaseNote",
                    "lastSyncedTimestamp",
                    "INTEGER NOT NULL DEFAULT 0",
                )
                addColumnIfNotExists(database, "BaseNote", "isShared", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfNotExists(
                    database,
                    "BaseNote",
                    "sharedAccesses",
                    "TEXT NOT NULL DEFAULT '[]'",
                )
                addColumnIfNotExists(
                    database,
                    "BaseNote",
                    "sharingTokens",
                    "TEXT NOT NULL DEFAULT '[]'",
                )
                addColumnIfNotExists(
                    database,
                    "BaseNote",
                    "ownerUserId",
                    "TEXT NOT NULL DEFAULT ''",
                )
            }

            /** Safely adds a column to a table if it doesn't already exist */
            private fun addColumnIfNotExists(
                db: SupportSQLiteDatabase,
                table: String,
                column: String,
                type: String,
            ) {
                // Check if the column already exists
                val cursor = db.query("PRAGMA table_info($table)")
                val columnIndex = cursor.getColumnIndex("name")
                val columnExists =
                    if (columnIndex >= 0) {
                        var exists = false
                        while (cursor.moveToNext()) {
                            val existingColumn = cursor.getString(columnIndex)
                            if (existingColumn == column) {
                                exists = true
                                break
                            }
                        }
                        exists
                    } else {
                        false
                    }
                cursor.close()

                // Only add the column if it doesn't exist
                if (!columnExists) {
                    try {
                        db.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
                    } catch (e: Exception) {
                        // Log the exception but don't crash - this handles edge cases
                        android.util.Log.e(
                            "NotallyXODatabase",
                            "Error adding column $column to $table",
                            e,
                        )
                    }
                }
            }
        }
    }
}
