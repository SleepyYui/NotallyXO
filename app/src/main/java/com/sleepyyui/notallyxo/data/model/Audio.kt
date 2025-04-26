package com.sleepyyui.notallyxo.data.model

import kotlinx.parcelize.Parcelize

@Parcelize
data class Audio(var name: String, var duration: Long?, var timestamp: Long) : Attachment
