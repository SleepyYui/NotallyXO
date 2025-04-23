package com.sleepyyui.notallyx.utils

import com.sleepyyui.notallyx.presentation.viewmodel.NotallyModel

data class FileError(
    val name: String,
    val description: String,
    val fileType: NotallyModel.FileType,
)
