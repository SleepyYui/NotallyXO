package com.sleepyyui.notallyxo.utils

import com.sleepyyui.notallyxo.presentation.viewmodel.NotallyModel

data class FileError(
    val name: String,
    val description: String,
    val fileType: NotallyModel.FileType,
)
