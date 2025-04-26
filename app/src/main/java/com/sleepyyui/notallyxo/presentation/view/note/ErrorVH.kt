package com.sleepyyui.notallyxo.presentation.view.note

import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyxo.databinding.ErrorBinding
import com.sleepyyui.notallyxo.utils.FileError

class ErrorVH(private val binding: ErrorBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(error: FileError) {
        binding.Name.text = error.name
        binding.Description.text = error.description
    }
}
