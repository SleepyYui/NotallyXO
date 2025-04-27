package com.sleepyyui.notallyxo.presentation.activity.conflict

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.NotallyXODatabase
import com.sleepyyui.notallyxo.data.model.SyncStatus
import com.sleepyyui.notallyxo.databinding.ActivityConflictDetailBinding
import com.sleepyyui.notallyxo.presentation.activity.note.EditActivity
import com.sleepyyui.notallyxo.utils.security.CloudEncryptionService
import com.sleepyyui.notallyxo.utils.sync.ConflictManager
import com.sleepyyui.notallyxo.utils.sync.SyncStatusIndicator
import com.sleepyyui.notallyxo.utils.sync.mappers.NoteMapper
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Activity for displaying and resolving an individual synchronization conflict.
 *
 * This activity shows the details of a conflict between local and server versions of a note,
 * allowing the user to choose which version to keep or merge the changes.
 */
class ConflictDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConflictDetailBinding
    private lateinit var conflictManager: ConflictManager
    private lateinit var syncStatusIndicator: SyncStatusIndicator

    private var syncId: String? = null
    private var conflict: ConflictManager.StoredConflict? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConflictDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.conflict_resolution)

        // Initialize managers
        conflictManager = ConflictManager.getInstance(applicationContext)
        syncStatusIndicator = SyncStatusIndicator.getInstance(applicationContext)

        // Get the conflict syncId from the intent
        syncId = intent.getStringExtra(EXTRA_SYNC_ID)

        if (syncId.isNullOrEmpty()) {
            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load the conflict
        conflict = conflictManager.getConflict(syncId!!)

        if (conflict == null) {
            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Populate the UI with conflict data
        populateUI()

        // Set up button click listeners
        setupButtons()
    }

    private fun populateUI() {
        conflict?.let { conflict ->
            // Set conflict title
            binding.conflictTitle.text =
                getString(
                    R.string.conflict_title,
                    conflict.title.ifEmpty { getString(R.string.untitled_note) },
                )

            // Set timestamps
            binding.localTimestamp.text =
                getString(
                    R.string.conflict_last_modified,
                    conflict.getFormattedLocalModificationTime(),
                )

            binding.serverTimestamp.text =
                getString(
                    R.string.conflict_last_modified,
                    conflict.getFormattedServerModificationTime(),
                )

            // Set local version details
            binding.localVersionTitle.text =
                conflict.localVersion.title.ifEmpty { getString(R.string.untitled_note) }
            binding.localVersionContent.text = conflict.localVersion.body

            // Set server version details - handle safely
            binding.serverVersionTitle.text =
                conflict.serverVersion.title.ifEmpty { getString(R.string.untitled_note) }

            // Handle server content safely - it might be encrypted or in a different format
            try {
                // Just display content as-is for now - later we can implement proper decryption if
                // needed
                val serverContent =
                    conflict.serverVersion.content.let {
                        if (it.isNotEmpty()) it else getString(R.string.empty_note)
                    }
                binding.serverVersionContent.text = serverContent
            } catch (e: Exception) {
                // If there's any error, show a default message
                binding.serverVersionContent.text = "Encrypted content"
            }
        }
    }

    private fun setupButtons() {
        // Keep local version
        binding.keepLocalButton.setOnClickListener { resolveConflictWithLocal() }

        // Keep server version
        binding.keepServerButton.setOnClickListener { resolveConflictWithServer() }

        // Merge changes in a separate activity
        binding.mergeButton.setOnClickListener {
            // For the initial implementation, we'll use a simple approach:
            // Just launch the edit activity with the local version and let the user manually merge
            launchEditForMerge()
        }
    }

    private fun resolveConflictWithLocal() {
        conflict?.let { conflict ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Get the database instance
                    val database = NotallyXODatabase.getFreshDatabase(this@ConflictDetailActivity)
                    val noteDao = database.getBaseNoteDao()

                    // Update the local note to mark it as synced
                    val updatedNote =
                        conflict.localVersion.copy(syncStatus = SyncStatus.PENDING_UPLOAD)

                    // Save the updated note to the database
                    noteDao.insert(listOf(updatedNote))

                    // Remove the conflict
                    conflictManager.removeConflict(conflict.syncId)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                                this@ConflictDetailActivity,
                                R.string.conflict_resolution_success,
                                Toast.LENGTH_SHORT,
                            )
                            .show()

                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                                this@ConflictDetailActivity,
                                R.string.something_went_wrong,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            }
        }
    }

    private fun resolveConflictWithServer() {
        conflict?.let { conflict ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Get the database instance
                    val database = NotallyXODatabase.getFreshDatabase(this@ConflictDetailActivity)
                    val noteDao = database.getBaseNoteDao()

                    // Create CloudEncryptionService instance and dummy key
                    val encryptionService = CloudEncryptionService()
                    val secretKey = generateDummySecretKey()

                    // Handle server note content
                    val serverNote =
                        if (conflict.serverVersion.content.isNotEmpty()) {
                            // For an actual implementation, we'd need proper decryption
                            // Here we'll just pass the content directly to the BaseNote
                            val baseNote = conflict.localVersion.copy()

                            try {
                                // Create a simple JSON with the content as the body
                                val contentJson = JSONObject()
                                contentJson.put("body", conflict.serverVersion.content)

                                // Update the note with content from server
                                baseNote.copy(
                                    title = conflict.serverVersion.title,
                                    body = contentJson.getString("body"),
                                    syncId = conflict.serverVersion.syncId,
                                    syncStatus = SyncStatus.SYNCED,
                                    lastSyncedTimestamp = conflict.serverVersion.lastSyncedTimestamp,
                                )
                            } catch (e: Exception) {
                                // If JSON handling fails, just keep the existing structure
                                baseNote
                            }
                        } else {
                            // If no content, just use the local note structure with server metadata
                            NoteMapper.toBaseNote(
                                conflict.serverVersion,
                                encryptionService,
                                secretKey,
                                conflict.localVersion,
                            )
                        }

                    // Save the server version to the database
                    noteDao.insert(listOf(serverNote))

                    // Remove the conflict
                    conflictManager.removeConflict(conflict.syncId)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                                this@ConflictDetailActivity,
                                R.string.conflict_resolution_success,
                                Toast.LENGTH_SHORT,
                            )
                            .show()

                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                                this@ConflictDetailActivity,
                                R.string.something_went_wrong,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                }
            }
        }
    }

    private fun launchEditForMerge() {
        conflict?.let { conflict ->
            // For now, we'll open the edit activity with the local version
            // and let the user manually merge the changes
            // In a future enhancement, we could create a specialized merge UI

            val editIntent =
                Intent(this, EditActivity::class.java).apply {
                    putExtra("NOTE_ID", conflict.noteId)
                    // Add a flag to indicate this is for conflict resolution
                    putExtra("FROM_CONFLICT", true)
                }

            startActivity(editIntent)

            // Show toast with instructions
            Toast.makeText(
                    this,
                    "Please manually merge the changes using both versions shown above",
                    Toast.LENGTH_LONG,
                )
                .show()

            // We won't mark the conflict as resolved yet - this will happen when the user saves the
            // note
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val EXTRA_SYNC_ID = "extra_sync_id"

        /**
         * Create an intent to start the ConflictDetailActivity.
         *
         * @param context The context to create the intent from
         * @param syncId The sync ID of the conflict to display
         * @return Intent to start the activity
         */
        fun createIntent(context: Context, syncId: String): Intent {
            return Intent(context, ConflictDetailActivity::class.java).apply {
                putExtra(EXTRA_SYNC_ID, syncId)
            }
        }
    }

    /**
     * Generate a dummy SecretKey for use with CloudEncryptionService. This key is not used for
     * actual encryption/decryption in this context.
     */
    private fun generateDummySecretKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256, SecureRandom())
        return keyGen.generateKey()
    }
}
