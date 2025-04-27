package com.sleepyyui.notallyxo.presentation.activity.conflict

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.databinding.ItemConflictBinding
import com.sleepyyui.notallyxo.utils.sync.ConflictManager

/**
 * Adapter for displaying note sync conflicts in a RecyclerView.
 *
 * @param context The context
 * @param onConflictClick Callback for when a conflict item is clicked
 */
class ConflictAdapter(
    private val context: Context,
    private val onConflictClick: (syncId: String) -> Unit,
) : ListAdapter<ConflictManager.StoredConflict, ConflictAdapter.ConflictViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConflictViewHolder {
        val binding =
            ItemConflictBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConflictViewHolder(binding, onConflictClick)
    }

    override fun onBindViewHolder(holder: ConflictViewHolder, position: Int) {
        val conflict = getItem(position)
        holder.bind(conflict)
    }

    inner class ConflictViewHolder(
        private val binding: ItemConflictBinding,
        private val onConflictClick: (syncId: String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conflict: ConflictManager.StoredConflict) {
            // Set the conflict title
            binding.conflictTitle.text =
                context.getString(
                    R.string.conflict_title,
                    conflict.title.ifEmpty { context.getString(R.string.untitled_note) },
                )

            // Set the timestamp info
            val localTime = conflict.getFormattedLocalModificationTime()
            val serverTime = conflict.getFormattedServerModificationTime()
            val timeDiff = conflict.getTimeDifference()

            binding.conflictTimestamp.text = buildString {
                append(context.getString(R.string.conflict_local_version))
                append(": ")
                append(localTime)
                append(" • ")
                append(context.getString(R.string.conflict_server_version))
                append(": ")
                append(serverTime)
                append(" (")
                append(timeDiff)
                append(" apart)")
            }

            // Set up the resolve button
            binding.resolveConflictButton.setOnClickListener { onConflictClick(conflict.syncId) }

            // Make the entire item clickable
            binding.root.setOnClickListener { onConflictClick(conflict.syncId) }
        }
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ConflictManager.StoredConflict>() {
                override fun areItemsTheSame(
                    oldItem: ConflictManager.StoredConflict,
                    newItem: ConflictManager.StoredConflict,
                ): Boolean {
                    return oldItem.syncId == newItem.syncId
                }

                override fun areContentsTheSame(
                    oldItem: ConflictManager.StoredConflict,
                    newItem: ConflictManager.StoredConflict,
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}
