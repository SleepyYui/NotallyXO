package com.sleepyyui.notallyxo.presentation.activity.conflict

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.databinding.ActivityConflictResolutionBinding
import com.sleepyyui.notallyxo.utils.sync.ConflictManager

/**
 * Activity for displaying and resolving synchronization conflicts.
 *
 * This activity shows a list of notes that have conflicts between local and server versions,
 * allowing the user to resolve each conflict individually.
 */
class ConflictResolutionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConflictResolutionBinding
    private lateinit var conflictManager: ConflictManager
    private lateinit var adapter: ConflictAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConflictResolutionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.conflict_resolution)

        // Initialize conflict manager
        conflictManager = ConflictManager.getInstance(applicationContext)

        // Set up RecyclerView
        adapter =
            ConflictAdapter(this) { syncId ->
                // Launch conflict detail activity when a conflict is selected
                startActivity(ConflictDetailActivity.createIntent(this, syncId))
            }

        binding.conflictsList.layoutManager = LinearLayoutManager(this)
        binding.conflictsList.adapter = adapter

        // Observe conflicts
        conflictManager.pendingConflicts.observe(this) { conflicts ->
            if (conflicts.isEmpty()) {
                binding.noConflictsText.visibility = View.VISIBLE
                binding.conflictsList.visibility = View.GONE
            } else {
                binding.noConflictsText.visibility = View.GONE
                binding.conflictsList.visibility = View.VISIBLE
                adapter.submitList(conflicts)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list of conflicts when returning to this screen
        // This is important after resolving a conflict in the detail view
        conflictManager.pendingConflicts.value?.let { conflicts ->
            adapter.submitList(conflicts.toList())
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
        /**
         * Create an intent to start the ConflictResolutionActivity.
         *
         * @param context The context to create the intent from
         * @return Intent to start the activity
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, ConflictResolutionActivity::class.java)
        }
    }
}
