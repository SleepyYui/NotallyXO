package com.sleepyyui.notallyx.data.model

import kotlinx.parcelize.Parcelize

@Parcelize
data class Audio(var name: String, var duration: Long?, var timestamp: Long) : Attachment
