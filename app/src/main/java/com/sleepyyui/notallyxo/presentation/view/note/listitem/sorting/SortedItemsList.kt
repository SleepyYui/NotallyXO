package com.sleepyyui.notallyxo.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.SortedList
import com.sleepyyui.notallyxo.data.model.ListItem

class SortedItemsList(val callback: ListItemParentSortCallback) :
    SortedList<ListItem>(ListItem::class.java, callback) {

    init {
        this.callback.setItems(this)
    }
}
