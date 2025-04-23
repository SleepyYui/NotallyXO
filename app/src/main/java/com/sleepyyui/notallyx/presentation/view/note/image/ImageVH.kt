package com.sleepyyui.notallyx.presentation.view.note.image

import android.net.Uri
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.DefaultOnImageEventListener
import com.sleepyyui.notallyx.databinding.RecyclerImageBinding
import java.io.File

class ImageVH(private val binding: RecyclerImageBinding) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.SSIV.apply {
            setDoubleTapZoomDpi(320)
            setDoubleTapZoomDuration(200)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
            setOnImageEventListener(
                object : DefaultOnImageEventListener() {

                    override fun onImageLoadError(e: Exception?) {
                        binding.Message.visibility = View.VISIBLE
                    }
                }
            )
        }
    }

    fun bind(file: File?) {
        binding.SSIV.recycle()
        if (file != null) {
            binding.Message.visibility = View.GONE
            val source = ImageSource.uri(Uri.fromFile(file))
            binding.SSIV.setImage(source)
        } else binding.Message.visibility = View.VISIBLE
    }
}
