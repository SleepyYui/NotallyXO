package com.sleepyyui.notallyxo.presentation.view.note.audio

import android.text.format.DateUtils
import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyxo.data.model.Audio
import com.sleepyyui.notallyxo.databinding.RecyclerAudioBinding
import com.sleepyyui.notallyxo.presentation.setControlsContrastColorForAllViews
import java.text.DateFormat

class AudioVH(
    private val binding: RecyclerAudioBinding,
    onClick: (Int) -> Unit,
    private val formatter: DateFormat,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.root.setOnClickListener { onClick(absoluteAdapterPosition) }
    }

    fun bind(audio: Audio, color: Int?) {
        binding.apply {
            Date.text = formatter.format(audio.timestamp)
            Length.text = audio.duration?.let { DateUtils.formatElapsedTime(it / 1000) } ?: "-"
            color?.let { root.setControlsContrastColorForAllViews(it) }
        }
    }
}
