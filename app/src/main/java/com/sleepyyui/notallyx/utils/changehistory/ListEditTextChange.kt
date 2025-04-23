package com.sleepyyui.notallyx.utils.changehistory

import com.sleepyyui.notallyx.presentation.view.note.listitem.ListManager
import com.sleepyyui.notallyx.presentation.view.note.listitem.ListState

open class ListEditTextChange(old: ListState, new: ListState, listManager: ListManager) :
    ListBatchChange(old, new, listManager)
