package com.sleepyyui.notallyxo.presentation.view.note.preview

import androidx.recyclerview.widget.RecyclerView
import com.sleepyyui.notallyxo.R
import com.sleepyyui.notallyxo.data.model.FileAttachment
import com.sleepyyui.notallyxo.databinding.RecyclerPreviewFileBinding
import com.sleepyyui.notallyxo.presentation.setControlsContrastColorForAllViews
import com.sleepyyui.notallyxo.utils.MIME_TYPE_ZIP

class PreviewFileVH(
    private val binding: RecyclerPreviewFileBinding,
    onClick: (position: Int) -> Unit,
    onLongClick: (position: Int) -> Boolean,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.FileName.apply {
            setOnClickListener { onClick(absoluteAdapterPosition) }
            setOnLongClickListener { onLongClick(absoluteAdapterPosition) }
        }
    }

    fun bind(fileAttachment: FileAttachment, color: Int?) {
        binding.FileName.apply {
            text = fileAttachment.originalName
            setChipIconResource(getIconForMimeType(fileAttachment.mimeType))
            color?.let { setControlsContrastColorForAllViews(it) }
        }
    }

    private fun getIconForMimeType(mimeType: String): Int {
        return when {
            mimeType.startsWith("image/") -> R.drawable.add_images
            mimeType.startsWith("video/") -> R.drawable.video
            mimeType.startsWith("audio/") -> R.drawable.record_audio
            mimeType.startsWith(MIME_TYPE_ZIP) -> R.drawable.archive
            else -> R.drawable.text_file
        }
    }
}
