package com.sleepyyui.notallyxo.presentation.view.main.label

interface LabelListener {

    fun onClick(position: Int)

    fun onEdit(position: Int)

    fun onDelete(position: Int)

    fun onToggleVisibility(position: Int)
}
