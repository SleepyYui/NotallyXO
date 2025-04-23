package com.sleepyyui.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.SortedList
import com.sleepyyui.notallyx.data.model.ListItem

class SortedItemsList(val callback: ListItemParentSortCallback) :
    SortedList<ListItem>(ListItem::class.java, callback) {

    init {
        this.callback.setItems(this)
    }
}
