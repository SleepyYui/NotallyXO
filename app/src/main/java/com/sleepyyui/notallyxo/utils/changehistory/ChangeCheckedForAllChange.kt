package com.sleepyyui.notallyxo.utils.changehistory

import com.sleepyyui.notallyxo.presentation.view.note.listitem.ListManager
import com.sleepyyui.notallyxo.presentation.view.note.listitem.ListState

class ChangeCheckedForAllChange(old: ListState, new: ListState, listManager: ListManager) :
    ListBatchChange(old, new, listManager)
