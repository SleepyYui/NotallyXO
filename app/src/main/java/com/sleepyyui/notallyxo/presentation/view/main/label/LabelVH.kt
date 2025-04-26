package com.sleepyyui.notallyxo.presentation.view.main.label

import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.databinding.RecyclerLabelBinding

class LabelVH(private val binding: RecyclerLabelBinding, listener: LabelListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.apply {
            LabelText.setOnClickListener { listener.onClick(absoluteAdapterPosition) }
            EditButton.setOnClickListener { listener.onEdit(absoluteAdapterPosition) }
            DeleteButton.setOnClickListener { listener.onDelete(absoluteAdapterPosition) }
            VisibilityButton.setOnClickListener {
                listener.onToggleVisibility(absoluteAdapterPosition)
            }
        }
    }

    fun bind(value: LabelData) {
        binding.LabelText.text = value.label
        binding.VisibilityButton.setImageResource(
            if (value.visibleInNavigation) R.drawable.visibility else R.drawable.visibility_off
        )
    }
}
